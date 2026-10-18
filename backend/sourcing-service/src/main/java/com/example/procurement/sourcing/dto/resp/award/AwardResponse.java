package com.example.procurement.sourcing.dto.resp.award;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** D12 定标单响应（字段与前端 AwardBill 接口一致） */
@Data
public class AwardResponse {
    private Long id;
    private String billNo;
    private String sourceBillType;
    private String sourceBillNo;
    private String awardSupplierCode;
    private String awardSupplierName;
    /** 实时引用 SRM 档案认证状态，用于 C3 风控校验展示 */
    private String awardSupplierCertStatus;
    private String awardConclusion;
    private BigDecimal totalAwardAmount;
    private String currency;
    private String status;
    private List<ItemEntry> items;

    @Data
    public static class ItemEntry {
        private Long id;
        private Integer seq;
        private String categoryCode;
        private String itemCode;
        private String itemName;
        private String spec;
        private BigDecimal qty;
        private String unit;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
        private String remark;
    }
}
