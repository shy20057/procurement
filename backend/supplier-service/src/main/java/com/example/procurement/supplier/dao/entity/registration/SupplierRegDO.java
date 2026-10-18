package com.example.procurement.supplier.dao.entity.registration;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_reg_h")
public class SupplierRegDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String supplierName;
    private String unifiedCreditCode;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String regAddress;
    private String cooperationNote;
    private LocalDate applyDate;
    private String sourceType;
    private String sourceInviteBillNo;
    private String status;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
