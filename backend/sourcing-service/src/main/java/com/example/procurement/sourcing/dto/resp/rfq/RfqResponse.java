package com.example.procurement.sourcing.dto.resp.rfq;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** D11 询价单响应（字段与前端 RFQBill 接口一致） */
@Data
public class RfqResponse {
    private Long id;
    private String billNo;
    private String sourceProjectBillNo;
    private String rfqTitle;
    private Integer maxRounds;
    private Integer currentRound;
    private LocalDate quoteDeadline;
    private String status;
    private List<QuoteEntry> quotes;

    @Data
    public static class QuoteEntry {
        private Long id;
        private Integer roundNo;
        private String supplierCode;
        private String supplierName;
        private String itemCode;
        private String itemName;
        private BigDecimal qty;
        private String unit;
        private BigDecimal quoteUnitPrice;
        private BigDecimal quoteAmount;
        private LocalDate quoteDate;
        private Integer deliveryDays;
        private String remark;
    }
}
