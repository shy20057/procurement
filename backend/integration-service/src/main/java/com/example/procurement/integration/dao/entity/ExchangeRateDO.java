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
    private Boolean deleted;
    private Long sourceVersion;
    private Instant sourceChangedAt;
    private String payloadHash;
    private Instant syncedAt;
}
