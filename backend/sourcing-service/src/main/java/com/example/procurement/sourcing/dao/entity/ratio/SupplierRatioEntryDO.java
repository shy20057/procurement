package com.example.procurement.sourcing.dao.entity.ratio;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D16 SR 配额单供应商配额分录 */
@Data
@TableName("stc_schema.t_stc_supplier_ratio_e")
public class SupplierRatioEntryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String supplierCode;
    private String supplierName;
    private BigDecimal ratioPercent;
    private String remark;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
