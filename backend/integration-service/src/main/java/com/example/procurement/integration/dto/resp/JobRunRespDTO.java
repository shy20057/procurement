package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class JobRunRespDTO {
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
