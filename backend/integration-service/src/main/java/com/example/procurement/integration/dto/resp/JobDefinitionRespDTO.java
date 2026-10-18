package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class JobDefinitionRespDTO {
    private String jobCode;
    private String entityType;
    private String name;
    private Boolean enabled;
    private String cronExpression;
    private String zoneId;
    private String handlerCode;
    private String resourceKey;
    private Boolean baselineComplete;
    private String committedCursor;
    private Instant lastSuccessAt;
    private Long checkpointVersion;
    private UUID latestRunId;
    private String latestStatus;
}
