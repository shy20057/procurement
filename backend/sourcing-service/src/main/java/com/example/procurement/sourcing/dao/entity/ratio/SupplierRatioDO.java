package com.example.procurement.sourcing.dao.entity.ratio;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** D16 SR 配额单单头 */
@Data
@TableName("stc_schema.t_stc_supplier_ratio_h")
public class SupplierRatioDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String categoryCode;
    private String categoryName;
    private String ratioCycle;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
