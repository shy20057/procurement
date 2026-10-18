package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class SyncPageAuditRespDTO {
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
