package com.example.procurement.supplier.dto.req.blacklist;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateBlacklistRequest {
    @NotBlank private String supplierCode;
    @NotNull private String triggerSource;
    private String sourcePerfBillNo;
    @NotBlank private String reason;
    @NotBlank @Size(max = 50) private String applicant;
    private String approvalLevel;
    private Boolean isPermanent;
    private LocalDate reviewDate;
    private List<BlacklistEvidenceEntry> entries;

    @Data
    public static class BlacklistEvidenceEntry {
        private Integer seq;
        private String evidenceType;
        private String evidenceDesc;
        private String attachmentUrl;
    }
}
