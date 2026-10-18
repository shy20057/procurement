package com.example.procurement.supplier.dto.resp.registration;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class RegResponse {
    private Long id;
    private String billNo;
    private String supplierName;
    private String unifiedCreditCode;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String regAddress;
    private String cooperationNote;
    private LocalDate applyDate;
    private String sourceType;
    private String sourceInviteBillNo;
    private String status;
    private List<CategoryEntry> categories;
    private List<CertEntry> certs;
    private Instant createdAt;

    @Data
    public static class CategoryEntry {
        private Integer seq;
        private String categoryCode;
        private String categoryName;
        private String remark;
    }

    @Data
    public static class CertEntry {
        private Integer seq;
        private String certType;
        private String certNo;
        private String issueOrg;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String attachmentUrl;
    }
}
