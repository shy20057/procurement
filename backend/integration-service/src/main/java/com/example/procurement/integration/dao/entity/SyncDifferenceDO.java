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
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long sourceId;
    private Instant sourceUpdatedAt;
    private Instant targetUpdatedAt;
    private String sourceHash;
    private String targetHash;
    private String verificationStatus;
    private String repairStatus;
    private String detail;
    private Instant verifiedAt;
    private Instant repairedAt;
    private Boolean repaired;
    private Instant createdAt;
}
