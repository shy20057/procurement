package com.example.procurement.supplier.dto.resp.archive;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class ArchiveResponse {
    private String supplierCode;
    private String supplierName;
    private String unifiedCreditCode;
    private String supplierType;
    private String certStatus;
    private LocalDate certValidUntil;
    private String perfGrade;
    private Integer perfEvalCount;
    private LocalDate lastPerfDate;
    private String bankName;
    private String bankAccount;
    private String bankAccountName;
    private String sourceCertBillNo;
    private List<CategoryEntry> categories;
    private List<CertEntry> certs;
    private List<ContactEntry> contacts;
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

    @Data
    public static class ContactEntry {
        private Integer seq;
        private String contactName;
        private String position;
        private String phone;
        private String email;
        private Boolean isPrimary;
    }
}
