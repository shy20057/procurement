package com.example.procurement.supplier.dto.req.perf;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreatePerfRequest {
    @NotBlank private String supplierCode;
    @NotBlank private String period;
    @NotNull private String evalType;
    @NotBlank @Size(max = 50) private String evaluator;
    private List<PerfDimensionEntry> entries;

    @Data
    public static class PerfDimensionEntry {
        private Integer seq;
        @NotNull private String dimension;
        @NotNull private BigDecimal weight;
        @NotNull private BigDecimal score;
        private String sourceBillNo;
        private String remark;
    }
}
