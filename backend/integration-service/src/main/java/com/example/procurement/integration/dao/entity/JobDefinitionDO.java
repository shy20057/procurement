package com.example.procurement.integration.dao.entity;

import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class JobDefinitionDO {
    private String jobCode;
    private String entityType;
    private String name;
    private Boolean enabled;
    private String cronExpression;
    private String zoneId;
    private String handlerCode;
    private String resourceKey;
}
