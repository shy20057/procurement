package com.example.procurement.sourcing.dao.entity.pricelist;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** D14 基准价目表单头 */
@Data
@TableName("stc_schema.t_stc_price_list_h")
public class PriceListDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceAwardBillNo;
    private String priceListName;
    private String supplierCode;
    private String supplierName;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
