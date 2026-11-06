package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class CostCenterRespDTO {
    private Long id;
    private String controllingArea;
    private String costCenterCode;
    private String companyCode;
    private String name;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Boolean blocked;
    private Boolean isDeleted;
    private Boolean enabled;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
    private Long sourceId;
    private String sourceSystem;
    private Instant sourceUpdatedAt;
    private String payloadHash;
    private Instant syncedAt;
}
