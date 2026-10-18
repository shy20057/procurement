package com.example.procurement.supplier.dao.entity.certification;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_cert_h")
public class SupplierCertDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceBillNo;
    private String sourceBillType;
    private String supplierName;
    private String unifiedCreditCode;
    private String certType;
    private LocalDate startDate;
    private String responsiblePerson;
    private String conclusion;
    private LocalDate validUntil;
    private String status;
    private Instant approvedAt;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
