package com.example.procurement.integration.dto.domain;
import lombok.Data;
/** 是否过期由 PostgreSQL 时钟计算，避免 JVM 与数据库时钟偏差。 */
@Data
public class LeaseStateDTO {
    private String ownerId;
    private Long fenceToken;
    private Boolean expired;
}
