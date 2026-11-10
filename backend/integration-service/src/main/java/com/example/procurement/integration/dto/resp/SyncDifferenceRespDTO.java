package com.example.procurement.integration.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class SyncDifferenceRespDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private UUID runId;
    private String businessKey;
    private String differenceType;
    @JsonSerialize(using = ToStringSerializer.class)
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
