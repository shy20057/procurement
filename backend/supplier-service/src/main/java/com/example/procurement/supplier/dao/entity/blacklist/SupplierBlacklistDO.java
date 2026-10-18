package com.example.procurement.supplier.dao.entity.blacklist;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_blacklist_h")
public class SupplierBlacklistDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String supplierCode;
    private String triggerSource;
    private String sourcePerfBillNo;
    private String reason;
    private String applicant;
    private LocalDate applyDate;
    private String approvalLevel;
    private Boolean isPermanent;
    private LocalDate reviewDate;
    private String status;
    private LocalDate effectiveDate;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
