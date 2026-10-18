package com.example.procurement.sourcing.dao.entity.award;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D12 定标单标的信息分录 */
@Data
@TableName("stc_schema.t_stc_award_e_item")
public class AwardItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
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
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
