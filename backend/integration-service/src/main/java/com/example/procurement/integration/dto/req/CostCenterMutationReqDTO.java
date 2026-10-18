package com.example.procurement.integration.dto.req;

import com.example.procurement.integration.dto.domain.CostCenterDTO;

public record CostCenterMutationReqDTO(CostCenterDTO data, long expectedVersion) {}
