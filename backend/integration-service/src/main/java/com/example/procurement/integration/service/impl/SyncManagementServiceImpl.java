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
        this.jobs = jobs; this.queries = queries;
    }

    @Override
    public JobRunRespDTO trigger(String jobCode, String requestId, SyncRunReqDTO request) {
        String mode = request.mode() == null ? "AUTO" : request.mode();
        return queries.run(jobs.submit(jobCode, mode, requestId, request.reason(), "MANUAL", null));
    }

    @Override
    public JobRunRespDTO retry(UUID runId, String requestId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IntegrationProblem("INVALID_REASON", "请填写500字以内操作原因", false, 422);
        JobRunDO original = jobs.findRun(runId);
        if (original == null) throw new IntegrationProblem("NOT_FOUND", "任务不存在", false, 404);
        if (!List.of("FAILED", "CANCELLED").contains(original.getStatus())) {
            throw new IntegrationProblem("NOT_RETRYABLE", "只能重试已失败或已取消任务", false, 409);
        }
        return queries.run(jobs.submit(original.getJobCode(), original.getMode(), requestId, reason, "RETRY", runId));
    }

    @Override
    public JobRunRespDTO cancel(UUID runId) { jobs.cancel(runId); return queries.run(runId); }
}
