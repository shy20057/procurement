package com.example.procurement.sourcing.dto.resp.bpa;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** D15 框架协议响应（字段与前端 BPABill 接口一致） */
@Data
public class BpaResponse {
    private Long id;
    private String billNo;
    private String sourceAwardBillNo;
    private String agreementName;
    private String supplierCode;
    private String supplierName;
    private BigDecimal totalQuotaAmount;
    private BigDecimal usedQuotaAmount;
    private String currency;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status;
    private List<CategoryEntry> categories;

    @Data
    public static class CategoryEntry {
        private Long id;
        private Integer seq;
        private String categoryCode;
        private String categoryName;
        private String itemCode;
        private BigDecimal unitPrice;
        private BigDecimal maxQty;
        private String remark;
    }
}
