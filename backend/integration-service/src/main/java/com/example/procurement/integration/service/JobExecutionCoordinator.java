package com.example.procurement.integration.service;

import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.result.JobExecutionResult;

public interface JobExecutionCoordinator {
    JobExecutionResult executeFromXxl(String jobCode, String mode, XxlTriggerMetadata metadata, String requestId);
}
