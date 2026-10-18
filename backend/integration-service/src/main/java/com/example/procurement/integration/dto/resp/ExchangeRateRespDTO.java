package com.example.procurement.integration.dto.resp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

/** 接口响应对象，与数据库实体分开维护。 */
@Data
public class ExchangeRateRespDTO {
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
