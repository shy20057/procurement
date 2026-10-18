package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class SyncPageFailureDO {
    private Long id;
    private UUID runId;
    private Integer attemptNo;
    private String fromCursor;
    private String errorCode;
    private String errorSummary;
    private Instant occurredAt;
}
