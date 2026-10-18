package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class SyncAlertRespDTO {
    private Long id;
    private String jobCode;
    private UUID runId;
    private String category;
    private String severity;
    private String dedupKey;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Long occurrenceCount;
    private String status;
    private String summary;
}
