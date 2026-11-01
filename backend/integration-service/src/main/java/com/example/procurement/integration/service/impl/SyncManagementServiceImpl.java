package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dto.req.SyncRunReqDTO;
import com.example.procurement.integration.dto.resp.JobRunRespDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.IntegrationQueryService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncManagementService;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class SyncManagementServiceImpl implements SyncManagementService {
    private final SyncJobService jobs;
    private final IntegrationQueryService queries;

    public SyncManagementServiceImpl(SyncJobService jobs, IntegrationQueryService queries) {
        this.jobs = jobs;
        this.queries = queries;
    }

    @Override
    public JobRunRespDTO trigger(String jobCode, String requestId, SyncRunReqDTO request) {
        String mode = request.mode() == null ? "AUTO" : request.mode();
        return queries.run(jobs.submit(jobCode, mode, requestId, request.reason(), "MANUAL", null));
    }

    @Override
    public JobRunRespDTO retry(UUID runId, String requestId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new IntegrationProblem("INVALID_REASON", "请填写500字以内操作原因", false, 422);
        JobRunDO original = jobs.findRun(runId); // 找到这个任务 status: CANCELLED（已经取消了）
        if (original == null) throw new IntegrationProblem("NOT_FOUND", "任务不存在", false, 404);
        if (!List.of("FAILED", "CANCELLED").contains(original.getStatus())) { // 这个判断真正拦的是已成功的任务
            throw new IntegrationProblem("NOT_RETRYABLE", "只能重试已失败或已取消任务", false, 409);
        }  // queries.run() 回查提交之后刷新的值
        return queries.run(jobs.submit(original.getJobCode(), original.getMode(), requestId, reason, "RETRY", runId));
    }

    @Override
    public JobRunRespDTO cancel(UUID runId) {
        jobs.cancel(runId); // 取消这条运行，并判断是否 404。 PENDING / RETRY_WAIT ——>  	CANCELLED   and  RUNNING（在跑） ——>	CANCEL_REQUESTED
        return queries.run(runId); // 取消后重新查一次这条 run，把最新状态返回给前端。
    }
}

/*
job_run.status 状态一览

状态              含义                            谁改的
PENDING           已提交，排队等领取              insert SQL 写死
RUNNING           执行中（已持有租约）            markRunning（claim 时）
SUCCEEDED         成功终态                        markSuccess
RETRY_WAIT        失败但可重试，退避等待中        markFailure(retry=true)，到期后被 lockLocalCandidates 重新领走
FAILED            失败终态                        markFailure / finishPending / recover
CANCEL_REQUESTED  已请求取消，等执行器在页边界停  runs.cancel（RUNNING 时）
CANCELLED         已取消终态                      runs.cancel（非 RUNNING 时）/ markCancelled / recover

活跃（仍占用资源）：PENDING / RUNNING / RETRY_WAIT / CANCEL_REQUESTED
终态：SUCCEEDED / FAILED / CANCELLED
*/
