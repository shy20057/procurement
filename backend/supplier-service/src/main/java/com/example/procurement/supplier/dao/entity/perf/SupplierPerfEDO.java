package com.example.procurement.supplier.dao.entity.perf;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@TableName("t_srm_supplier_perf_e")
public class SupplierPerfEDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String dimension;
    private BigDecimal weight;
    private BigDecimal score;
    private String sourceBillNo;
    private String remark;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
