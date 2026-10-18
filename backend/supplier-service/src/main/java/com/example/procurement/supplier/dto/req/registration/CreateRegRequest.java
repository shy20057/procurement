package com.example.procurement.supplier.dto.req.registration;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateRegRequest {
    @NotBlank @Size(max = 100) private String supplierName;
    private String unifiedCreditCode;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String regAddress;
    private String cooperationNote;
    @NotNull private String sourceType;
    private String sourceInviteBillNo;
    private List<CategoryEntry> categories;
    private List<CertEntry> certs;

    @Data
    public static class CategoryEntry {
        private Integer seq;
        @NotBlank private String categoryCode;
        @NotBlank private String categoryName;
        private String remark;
    }

    @Data
    public static class CertEntry {
        private Integer seq;
        @NotNull private String certType;
        private String certNo;
        private String issueOrg;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String attachmentUrl;
    }
}
