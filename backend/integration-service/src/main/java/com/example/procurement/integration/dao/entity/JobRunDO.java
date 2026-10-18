package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class JobRunDO {
    private UUID runId;
    private String jobCode;
    private String mode;
    private String triggerType;
    private UUID parentRunId;
    private String requestId;
    private String reason;
    private String status;
    private Integer attemptNo;
    private Instant nextRetryAt;
    private String ownerId;
    private Long fenceToken;
    private String phase;
    private Long fetchedCount;
    private Long appliedCount;
    private Long ignoredCount;
    private Long totalCount;
    private String errorCode;
    private String errorSummary;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
    private Long xxlJobId;
    private String xxlLogKey;
    private Integer xxlShardIndex;
    private Integer xxlShardTotal;
    private String executorAddress;
}
