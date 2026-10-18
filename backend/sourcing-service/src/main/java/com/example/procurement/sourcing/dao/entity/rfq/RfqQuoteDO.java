package com.example.procurement.sourcing.dao.entity.rfq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** D11 询价单多轮报价分录 */
@Data
@TableName("stc_schema.t_stc_rfq_e_quote")
public class RfqQuoteDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
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
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
