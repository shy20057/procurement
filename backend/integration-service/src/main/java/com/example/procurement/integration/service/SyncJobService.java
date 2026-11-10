package com.example.procurement.integration.service;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.exception.IntegrationProblem;
import java.util.UUID;

public interface SyncJobService {
    XxlSubmission submitXxl(String jobCode, String mode, XxlTriggerMetadata metadata, String requestId);
    JobContext claim(UUID runId, String owner);
    void failPending(UUID runId, String code, String summary);
    void success(JobContext context);
    void failure(JobContext context, IntegrationProblem error);
    void cancelled(JobContext context);
    void enable(String jobCode, boolean enabled, String operator, String reason);
    JobRunDO findRun(UUID runId);
}
