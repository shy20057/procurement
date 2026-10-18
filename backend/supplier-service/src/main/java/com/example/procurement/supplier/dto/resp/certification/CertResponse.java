package com.example.procurement.supplier.dto.resp.certification;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class CertResponse {
    private Long id;
    private String billNo;
    private String sourceBillNo;
    private String sourceBillType;
    private String supplierName;
    private String unifiedCreditCode;
    private String certType;
    private LocalDate startDate;
    private String responsiblePerson;
    private String conclusion;
    private LocalDate validUntil;
    private String status;
    private List<CertDimensionEntry> entries;
    private Instant approvedAt;
    private Instant createdAt;

    @Data
    public static class CertDimensionEntry {
        private Integer seq;
        private String dimension;
        private String result;
        private BigDecimal score;
        private String responsiblePerson;
        private String remark;
        private String attachmentUrl;
    }
}
