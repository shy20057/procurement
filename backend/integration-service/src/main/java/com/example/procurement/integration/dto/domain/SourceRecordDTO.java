package com.example.procurement.integration.dto.domain;

import com.example.procurement.integration.domain.enums.MasterDataType;

import java.time.Instant;
public record SourceRecordDTO(MasterDataType entityType, String businessKey, long sourceVersion,
        Instant changedAt, CostCenterDTO costCenter, ExchangeRateDTO exchangeRate) {}
