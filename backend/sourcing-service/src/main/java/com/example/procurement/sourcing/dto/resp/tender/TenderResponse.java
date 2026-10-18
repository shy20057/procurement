package com.example.procurement.sourcing.dto.resp.tender;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** D10 招标单响应（字段与前端 TenderBill 接口一致） */
@Data
public class TenderResponse {
    private Long id;
    private String billNo;
    private String sourceProjectBillNo;
    private String tenderTitle;
    private String tenderType;
    private LocalDate announceDate;
    private LocalDate bidDeadline;
    private LocalDate openDate;
    private Boolean needPrequalification;
    private String evaluationMethod;
    private String status;
    private List<BidderEntry> bidders;
    private List<EvaluationEntry> evaluations;

    @Data
    public static class BidderEntry {
        private Long id;
        private Integer seq;
        private String supplierCode;
        private String supplierName;
        private BigDecimal bidAmount;
        private Instant submitTime;
        private String prequalResult;
        private Boolean isShortlisted;
    }

    @Data
    public static class EvaluationEntry {
        private Long id;
        private Integer seq;
        private String supplierCode;
        private String supplierName;
        private BigDecimal techScore;
        private BigDecimal bizScore;
        private BigDecimal totalScore;
        private Integer rank;
        private String judgeComment;
    }
}
