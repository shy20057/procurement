package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class SyncDifferenceRespDTO {
    private Long id;
    private UUID runId;
    private String businessKey;
    private String differenceType;
    private Long sourceVersion;
    private Long targetVersion;
    private Boolean repaired;
    private Instant createdAt;
}
