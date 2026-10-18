package com.example.procurement.supplier.dao.entity.archive;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_archive_h")
public class SupplierArchiveDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String supplierCode;
    private String supplierName;
    private String unifiedCreditCode;
    private String supplierType;
    private String certStatus;
    private LocalDate certValidUntil;
    private String perfGrade;
    private Integer perfEvalCount;
    private LocalDate lastPerfDate;
    private String bankName;
    private String bankAccount;
    private String bankAccountName;
    private String sourceCertBillNo;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
