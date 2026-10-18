package com.example.procurement.supplier.dto.req.certification;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateCertRequest {
    private String supplierCode;
    @NotBlank @Size(max = 100) private String supplierName;
    private String unifiedCreditCode;
    @NotNull private String certType;
    private LocalDate startDate;
    @NotBlank @Size(max = 50) private String responsiblePerson;
    private List<CertDimensionEntry> entries;

    @Data
    public static class CertDimensionEntry {
        private Integer seq;
        @NotNull private String dimension;
        private String result;
        private BigDecimal score;
        private String responsiblePerson;
        private String remark;
        private String attachmentUrl;
    }
}
