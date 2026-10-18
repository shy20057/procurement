package com.example.procurement.supplier.dto.resp.freeze;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class FreezeResponse {
    private Long id;
    private String billNo;
    private String supplierCode;
    private String applyType;
    private String triggerSource;
    private String sourcePerfBillNo;
    private String reason;
    private String applicant;
    private LocalDate applyDate;
    private String status;
    private LocalDate effectiveDate;
    private String preStatusSnapshot;
    private String rectificationNote;
    private List<FreezeScopeEntry> entries;
    private Instant createdAt;

    @Data
    public static class FreezeScopeEntry {
        private Integer seq;
        private String scopeType;
        private String categoryCode;
        private String categoryName;
        private String remark;
    }
}
