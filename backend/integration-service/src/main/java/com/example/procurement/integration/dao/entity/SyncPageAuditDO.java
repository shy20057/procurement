package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class SyncPageAuditDO {
    private Long id;
    private UUID runId;
    private Integer pageNo;
    private String phase;
    private String fromCursor;
    private String toCursor;
    private Integer fetchedCount;
    private Integer appliedCount;
    private Integer ignoredCount;
    private Instant committedAt;
}
