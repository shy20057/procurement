package com.example.procurement.integration.domain.context;

import java.util.UUID;

public record XxlTriggerMetadata(long jobId, String logKey, int shardIndex, int shardTotal,
        String executorAddress, Parameters parameters) {

    public record Parameters(String operator, String reason, UUID retryRunId, Long sourceId, Long differenceId) {}
}
