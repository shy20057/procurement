package com.example.procurement.sourcing.dao.entity.bpa;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D15 框架协议品类分录 */
@Data
@TableName("stc_schema.t_stc_bpa_e_cat")
public class BpaCategoryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String categoryCode;
    private String categoryName;
    private String itemCode;
    private BigDecimal unitPrice;
    private BigDecimal maxQty;
    private String remark;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
