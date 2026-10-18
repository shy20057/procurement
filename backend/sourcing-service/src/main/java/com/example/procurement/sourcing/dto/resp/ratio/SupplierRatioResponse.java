package com.example.procurement.sourcing.dto.resp.ratio;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** D16 SR 配额单响应（字段与前端 SupplierRatioBill 接口一致） */
@Data
public class SupplierRatioResponse {
    private Long id;
    private String billNo;
    private String categoryCode;
    private String categoryName;
    private String ratioCycle;
    private String status;
    private List<RatioEntry> ratios;

    @Data
    public static class RatioEntry {
        private Long id;
        private Integer seq;
        private String supplierCode;
        private String supplierName;
        private BigDecimal ratioPercent;
        /** 只读引用 SRM 档案绩效评级 */
        private String perfGradeRef;
        private String remark;
    }
}
