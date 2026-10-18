package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class SyncCheckpointDO {
    private String jobCode;
    private String committedCursor;
    private Boolean baselineComplete;
    private Instant lastSuccessAt;
    private Long version;
}
