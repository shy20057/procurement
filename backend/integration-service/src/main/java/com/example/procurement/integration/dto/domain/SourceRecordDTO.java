package com.example.procurement.integration.dto.domain;

import com.example.procurement.integration.domain.enums.MasterDataType;

import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
public record SourceRecordDTO(MasterDataType entityType, String businessKey,
        @JsonSerialize(using = ToStringSerializer.class) long sourceId,
        Instant updatedAt, CostCenterDTO costCenter, ExchangeRateDTO exchangeRate) {}
