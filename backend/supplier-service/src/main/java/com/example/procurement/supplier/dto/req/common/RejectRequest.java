package com.example.procurement.supplier.dto.req.common;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectRequest {
    @NotBlank private String reason;
}
