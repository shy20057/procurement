package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class SyncDifferenceDO {
    private Long id;
    private UUID runId;
    private String businessKey;
    private String differenceType;
    private Long sourceVersion;
    private Long targetVersion;
    private Boolean repaired;
    private Instant createdAt;
}
