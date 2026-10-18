package com.example.procurement.supplier.dto.resp.change;

import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
public class ChangeResponse {
    private Long id;
    private String billNo;
    private String supplierCode;
    private String changeType;
    private String changeReason;
    private String applicant;
    private LocalDate applyDate;
    private String status;
    private List<ChangeFieldEntry> entries;
    private Instant approvedAt;
    private Instant createdAt;

    @Data
    public static class ChangeFieldEntry {
        private Integer seq;
        private String fieldName;
        private String oldValue;
        private String newValue;
    }
}
