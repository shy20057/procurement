package com.example.procurement.supplier.dao.entity.freeze;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_freeze_h")
public class SupplierFreezeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String supplierCode;
    private String applyType;
    private String triggerSource;
    private String sourcePerfBillNo;
    private String reason;
    private String applicant;
    private LocalDate applyDate;
    private String status;
    private LocalDate effectiveDate;
    private String preStatusSnapshot;
    private String rectificationNote;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
