package com.example.procurement.integration.dto.req;

import com.example.procurement.integration.dto.domain.ExchangeRateDTO;

public record ExchangeRateMutationReqDTO(ExchangeRateDTO data, Long sourceId, java.time.Instant expectedUpdatedAt) {}
