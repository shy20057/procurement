package com.example.procurement.integration.service;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.enums.RetryOwner;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.exception.IntegrationProblem;

import java.util.UUID;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface SyncJobService {
    UUID submit(String jobCode, String mode, String requestId, String reason, String trigger, UUID parent);
    XxlSubmission submitXxl(String jobCode, XxlTriggerMetadata metadata, String requestId);
    JobContext claim(String owner);
    JobContext claim(UUID runId, String owner);
    void failPending(UUID runId, String code, String summary);
    void success(JobContext context);
    void failure(JobContext context, IntegrationProblem error, RetryOwner retryOwner);
    void cancel(UUID runId);
    void cancelled(JobContext context);
    void recoverExpired();
    void enable(String jobCode, boolean enabled);
    void resetIncomplete(String jobCode, String reason);
    JobRunDO findRun(UUID runId);
}
