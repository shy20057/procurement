package com.example.procurement.integration.dao.entity;

import java.util.UUID;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class JobRequestKeyDO {
    private String requestId;
    private UUID runId;
    private String jobCode;
    private String mode;
}
