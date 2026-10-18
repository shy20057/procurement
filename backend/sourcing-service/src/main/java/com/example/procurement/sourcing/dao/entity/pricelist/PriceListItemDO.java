package com.example.procurement.sourcing.dao.entity.pricelist;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D14 基准价目表明细分录 */
@Data
@TableName("stc_schema.t_stc_price_list_e_item")
public class PriceListItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String categoryCode;
    private String itemCode;
    private String itemName;
    private BigDecimal unitPrice;
    private String currency;
    private String tieredPriceNote;
    private String regionalDiffNote;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
