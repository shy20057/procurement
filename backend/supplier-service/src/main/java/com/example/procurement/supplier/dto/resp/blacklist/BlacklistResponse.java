package com.example.procurement.supplier.dto.resp.blacklist;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class BlacklistResponse {
    private Long id;
    private String billNo;
    private String supplierCode;
    private String triggerSource;
    private String sourcePerfBillNo;
    private String reason;
    private String applicant;
    private LocalDate applyDate;
    private String approvalLevel;
    private Boolean isPermanent;
    private LocalDate reviewDate;
    private String status;
    private LocalDate effectiveDate;
    private List<BlacklistEvidenceEntry> entries;
    private Instant createdAt;

    @Data
    public static class BlacklistEvidenceEntry {
        private Integer seq;
        private String evidenceType;
        private String evidenceDesc;
        private String attachmentUrl;
    }
}
