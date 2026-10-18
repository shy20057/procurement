package com.example.procurement.integration.service;

import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.enums.RetryOwner;
import com.example.procurement.integration.domain.result.JobExecutionResult;

import java.util.List;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface JobExecutionCoordinator {
    JobExecutionResult executeFromXxl(String jobCode, XxlTriggerMetadata metadata, String requestId);
    JobExecutionResult executeClaimed(JobContext context, RetryOwner retryOwner);
    List<JobContext> activeContexts();
}
