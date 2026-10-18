package com.example.procurement.supplier.dao.entity.change;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_change_h")
public class SupplierChangeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String supplierCode;
    private String changeType;
    private String changeReason;
    private String applicant;
    private LocalDate applyDate;
    private String status;
    private Instant approvedAt;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
