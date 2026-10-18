package com.example.procurement.sourcing.dto.req.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** D9 寻源立项单创建请求 */
@Data
public class CreateSourcingProjectRequest {
    @NotBlank @Size(max = 200) private String projectName;
    @NotBlank @Size(max = 32) private String categoryCode;
    @NotBlank @Size(max = 100) private String categoryName;
    @NotNull private BigDecimal budgetAmount;
    @Size(max = 10) private String currency;
    @NotNull private LocalDate planStartDate;
    @NotNull private LocalDate planEndDate;
    @NotBlank @Size(max = 20) private String sourcingMethod;
    private String singleSourceReason;
    @NotBlank @Size(max = 50) private String initiator;
    private List<CandidateEntry> candidates;

    @Data
    public static class CandidateEntry {
        private Integer seq;
        @NotBlank private String supplierCode;
        @NotBlank private String supplierName;
        private String matchResult;
        private String certStatus;
        private String perfGrade;
        private String remark;
    }
}
