package com.example.procurement.sourcing.dto.resp.contract;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** D13 采购合同响应（字段与前端 PurchaseContract 接口一致） */
@Data
public class ContractResponse {
    private Long id;
    private String billNo;
    private String sourceAwardBillNo;
    private String supplierCode;
    private String supplierName;
    private String contractName;
    private BigDecimal contractAmount;
    private String currency;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String contractType;
    private String signatory;
    private String legalReviewStatus;
    private String sealStatus;
    private String status;
    private List<ClauseEntry> clauses;

    @Data
    public static class ClauseEntry {
        private Long id;
        private Integer seq;
        private String clauseType;
        private String categoryCode;
        private String itemName;
        private BigDecimal qty;
        private BigDecimal unitPrice;
        private String remark;
    }
}
