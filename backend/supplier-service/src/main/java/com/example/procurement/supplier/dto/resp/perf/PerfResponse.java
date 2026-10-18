package com.example.procurement.supplier.dto.resp.perf;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class PerfResponse {
    private Long id;
    private String billNo;
    private String supplierCode;
    private String period;
    private String evalType;
    private BigDecimal totalScore;
    private String grade;
    private String status;
    private String evaluator;
    private LocalDate evalDate;
    private List<PerfDimensionEntry> entries;
    private Instant createdAt;

    @Data
    public static class PerfDimensionEntry {
        private Integer seq;
        private String dimension;
        private BigDecimal weight;
        private BigDecimal score;
        private String sourceBillNo;
        private String remark;
    }
}
