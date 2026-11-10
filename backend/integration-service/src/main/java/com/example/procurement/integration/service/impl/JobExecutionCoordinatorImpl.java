package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.result.JobExecutionResult;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSyncService;
import com.example.procurement.integration.service.JobExecutionCoordinator;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class JobExecutionCoordinatorImpl implements JobExecutionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JobExecutionCoordinatorImpl.class);
    private final SyncJobService jobs;
    private final SyncLeaseService leases;
    private final PurchaseOrderSyncService purchaseOrders;
    private final String node = "integration:" + UUID.randomUUID();

    public JobExecutionCoordinatorImpl(SyncJobService jobs, SyncLeaseService leases,
            PurchaseOrderSyncService purchaseOrders) {
        this.jobs = jobs;
        this.leases = leases;
        this.purchaseOrders = purchaseOrders;
    }

    @Override
    public JobExecutionResult executeFromXxl(String jobCode, String mode, XxlTriggerMetadata metadata, String requestId) {
        XxlSubmission submission = jobs.submitXxl(jobCode, mode, metadata, requestId);
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
        return executeClaimed(context, metadata.parameters());
    }

    private JobExecutionResult executeClaimed(JobContext context, XxlTriggerMetadata.Parameters parameters) {
        try {
            purchaseOrders.execute(context, parameters);
            jobs.success(context); // 落成功状态
            return JobExecutionResult.succeeded(context.runId(), "业务运行完成");
        } catch (CancellationException cancelled) {
            jobs.cancelled(context);
            return JobExecutionResult.failed(context.runId(), "CANCELLED", "任务已在安全页边界取消", false);
        } catch (Exception failure) {
            IntegrationProblem problem = classify(failure);
            log.error("同步任务失败 runId={} code={}", context.runId(), problem.code, failure);
            if (!"LEASE_LOST".equals(problem.code)) {
                try {
                    jobs.failure(context, problem);
                } catch (CancellationException cancelled) {
                    jobs.cancelled(context);
                } catch (Exception persistenceFailure) {
                    log.error("保存任务错误状态失败 runId={}", context.runId(), persistenceFailure);
                }
            }
            return JobExecutionResult.failed(context.runId(), problem.code, problem.getMessage(), problem.retryable);
        } finally {
            try {
                leases.release(context);
            } catch (Exception releaseFailure) {
                log.error("释放任务租约失败，等待租约自然过期 runId={}", context.runId(), releaseFailure);
            }
        }
    }

    private IntegrationProblem classify(Exception failure) {
        if (failure instanceof IntegrationProblem known) return known;
        if (failure instanceof DataAccessException) {
            boolean permanent = failure instanceof org.springframework.dao.DataIntegrityViolationException
                    || failure instanceof org.springframework.dao.InvalidDataAccessResourceUsageException;
            return new IntegrationProblem(permanent ? "DATABASE_CONSTRAINT_ERROR" : "DATABASE_ERROR",
                    "本页数据库操作失败，已回滚", !permanent, permanent ? 409 : 503);
        }
        return new IntegrationProblem("SYNC_INTERNAL_ERROR", "同步处理失败，请查看服务端日志", false, 500);
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
