package com.example.procurement.supplier.dao.entity.perf;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_perf_h")
public class SupplierPerfDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String supplierCode;
    private String period;
    private String evalType;
    private BigDecimal totalScore;
    private String grade;
    private String status;
    private String evaluator;
    private LocalDate evalDate;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
