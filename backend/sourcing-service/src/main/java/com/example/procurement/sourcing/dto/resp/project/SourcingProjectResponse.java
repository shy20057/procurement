package com.example.procurement.sourcing.dto.resp.project;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** D9 寻源立项单响应（字段与前端 SourcingProject 接口一致） */
@Data
public class SourcingProjectResponse {
    private Long id;
    private String billNo;
    private String projectName;
    private String categoryCode;
    private String categoryName;
    private BigDecimal budgetAmount;
    private String currency;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private String sourcingMethod;
    private String singleSourceReason;
    private String initiator;
    private LocalDate applyDate;
    private String status;
    private List<CandidateEntry> candidates;
    private Instant createdDate;

    @Data
    public static class CandidateEntry {
        private Long id;
        private Integer seq;
        private String supplierCode;
        private String supplierName;
        private String matchResult;
        private String certStatus;
        private String perfGrade;
        private String remark;
    }
}
