package com.example.procurement.sourcing.dto.resp.pricelist;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** D14 基准价目表响应（字段与前端 PriceList 接口一致） */
@Data
public class PriceListResponse {
    private Long id;
    private String billNo;
    private String sourceAwardBillNo;
    private String priceListName;
    private String supplierCode;
    private String supplierName;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private List<ItemEntry> items;

    @Data
    public static class ItemEntry {
        private Long id;
        private Integer seq;
        private String categoryCode;
        private String itemCode;
        private String itemName;
        private BigDecimal unitPrice;
        private String currency;
        private String tieredPriceNote;
        private String regionalDiffNote;
    }
}
