package com.example.procurement.integration.dto.domain;
import java.time.LocalDate;
/** SAP 接口传输的成本中心内容，不等同于本地数据库实体。 */
public record CostCenterDTO(String controllingArea, String costCenterCode, String companyCode,
        String name, LocalDate validFrom, LocalDate validTo, boolean blocked, boolean deleted) {}
