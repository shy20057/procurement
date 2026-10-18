package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class SyncAlertDO {
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
