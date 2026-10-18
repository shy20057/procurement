package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.enums.RetryOwner;
import com.example.procurement.integration.domain.result.JobExecutionResult;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.JobExecutionCoordinator;
import com.example.procurement.integration.service.MasterDataSyncService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/*
* 这是执行的核心编排层,协调"领取 → 执行 → 落结果"的完整生命周期:
* */
@Service
public class JobExecutionCoordinatorImpl implements JobExecutionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JobExecutionCoordinatorImpl.class);
    private final SyncJobService jobs;
    private final SyncLeaseService leases;
    private final MasterDataSyncService sync;
    private final String node;
    private final ConcurrentMap<UUID, JobContext> active = new ConcurrentHashMap<>();

    @Autowired
    public JobExecutionCoordinatorImpl(SyncJobService jobs, MasterDataSyncService sync, SyncLeaseService leases) {
        this(jobs, sync, leases, "integration:" + UUID.randomUUID());
    }

    JobExecutionCoordinatorImpl(SyncJobService jobs, MasterDataSyncService sync, SyncLeaseService leases, String node) {
        this.jobs = jobs;
        this.leases = leases;
        this.sync = sync;
        this.node = node;
    }

    /*
    * executeFromXxl():先 submitXxl() 落库,然后根据返回状态处理——如果是重复/忙/停用,直接返回对应结果;否则 claim() 领取执行权,再走 executeClaimed()。
    * */
    public JobExecutionResult executeFromXxl(String jobCode, XxlTriggerMetadata metadata, String requestId) {
        XxlSubmission submission = jobs.submitXxl(jobCode, metadata, requestId);
        if (submission.state() == XxlSubmission.State.DUPLICATE_TERMINAL) {
            if ("SUCCEEDED".equals(submission.terminalStatus())) {
                return JobExecutionResult.succeeded(submission.runId(), "幂等复用已成功的业务运行");
            }
            return JobExecutionResult.failed(submission.runId(), value(submission.errorCode(), "XXL_DUPLICATE_FAILED"),
                    value(submission.errorSummary(), "原业务运行已失败"), false);
        }
        if (submission.state() == XxlSubmission.State.BUSY || submission.state() == XxlSubmission.State.DISABLED) {
            return JobExecutionResult.failed(submission.runId(), submission.errorCode(), submission.errorSummary(), true);
        }

        JobContext context = jobs.claim(submission.runId(), node);
        if (context == null) {
            JobRunDO persisted = jobs.findRun(submission.runId());
            if (persisted != null && "FAILED".equals(persisted.getStatus())) {
                String code = value(persisted.getErrorCode(), "JOB_NOT_CLAIMED");
                String message = value(persisted.getErrorSummary(), "业务运行未能取得执行权");
                return JobExecutionResult.failed(submission.runId(), code, message, "JOB_BUSY".equals(code));
            }
            jobs.failPending(submission.runId(), "JOB_BUSY", "数据集资源租约正被其他运行占用");
            return JobExecutionResult.failed(submission.runId(), "JOB_BUSY", "数据集资源租约正被其他运行占用", true);
        }
        return executeClaimed(context, RetryOwner.XXL);
    }

    /*
    * executeClaimed():把任务放进内存的 active 映射(用于心跳续租),调用 sync.execute() 执行真正的同步业务,然后 jobs.success() 或 jobs.failure() 落库。finally 里移除 active 并 leases.release() 释放租约。
    * */
    public JobExecutionResult executeClaimed(JobContext context, RetryOwner retryOwner) {
        active.put(context.runId(), context);
        try {
            sync.execute(context);
            jobs.success(context);
            return JobExecutionResult.succeeded(context.runId(), "业务运行完成");
        } catch (CancellationException cancelled) {
            jobs.cancelled(context);
            return JobExecutionResult.failed(context.runId(), "CANCELLED", "任务已在安全页边界取消", false);
        } catch (Exception failure) {
            IntegrationProblem problem = classify(failure);
            log.error("同步任务失败 runId={} code={}", context.runId(), problem.code, failure);
            if (!"LEASE_LOST".equals(problem.code)) {
                try {
                    jobs.failure(context, problem, retryOwner);
                } catch (CancellationException cancelled) {
                    jobs.cancelled(context);
                } catch (Exception persistenceFailure) {
                    log.error("保存任务错误状态失败 runId={}", context.runId(), persistenceFailure);
                }
            }
            return JobExecutionResult.failed(context.runId(), problem.code, problem.getMessage(), problem.retryable);
        } finally {
            active.remove(context.runId());
            try {
                leases.release(context);
            } catch (Exception releaseFailure) {
                log.error("释放任务租约失败，等待租约自然过期 runId={}", context.runId(), releaseFailure);
            }
        }
    }

    public List<JobContext> activeContexts() {
        return List.copyOf(active.values());
    }

    /*
    * classify():把异常分类——业务异常(IntegrationProblem)、数据库异常(可重试)、未知异常(不可重试),决定错误码和是否可重试。
    * */
    private IntegrationProblem classify(Exception failure) {
        if (failure instanceof IntegrationProblem known) return known;
        if (failure instanceof DataAccessException) {
            return new IntegrationProblem("DATABASE_ERROR", "本页数据库操作失败，已回滚", true, 503);
        }
        return new IntegrationProblem("SYNC_INTERNAL_ERROR", "同步处理失败，请查看服务端日志", false, 500);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
