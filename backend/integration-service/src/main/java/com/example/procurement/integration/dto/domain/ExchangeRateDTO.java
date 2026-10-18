package com.example.procurement.integration.dto.domain;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ExchangeRateDTO(String rateType, String fromCurrency, String toCurrency,
        LocalDate effectiveDate, BigDecimal rawRate, BigDecimal fromFactor,
        BigDecimal toFactor, String quotationMethod, BigDecimal normalizedRate, boolean deleted) {}
