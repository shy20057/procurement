package com.example.procurement.supplier.dto.req.freeze;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class CreateFreezeRequest {
    @NotBlank private String supplierCode;
    @NotNull private String applyType;
    @NotNull private String triggerSource;
    private String sourcePerfBillNo;
    @NotBlank private String reason;
    @NotBlank @Size(max = 50) private String applicant;
    private List<FreezeScopeEntry> entries;

    @Data
    public static class FreezeScopeEntry {
        private Integer seq;
        @NotNull private String scopeType;
        private String categoryCode;
        private String categoryName;
        private String remark;
    }
}
