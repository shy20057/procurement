package com.example.procurement.integration.dto.req;

import com.example.procurement.integration.dto.domain.ExchangeRateDTO;

public record ExchangeRateMutationReqDTO(ExchangeRateDTO data, long expectedVersion) {}
