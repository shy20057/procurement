package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class SyncPageFailureRespDTO {
    private Long id;
    private UUID runId;
    private Integer attemptNo;
    private String fromCursor;
    private String errorCode;
    private String errorSummary;
    private Instant occurredAt;
}
