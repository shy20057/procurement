package com.example.procurement.integration.dao.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

/** 数据库持久化对象；业务规则位于 Service。 */
@Data
public class ExchangeRateDO {
    private Long id;
    private String rateType;
    private String fromCurrency;
    private String toCurrency;
    private LocalDate effectiveDate;
    private BigDecimal rawRate;
    private BigDecimal fromFactor;
    private BigDecimal toFactor;
    private String quotationMethod;
    private BigDecimal normalizedRate;
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
