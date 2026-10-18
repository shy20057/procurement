package com.example.procurement.supplier.dao.entity.archive;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_archive_e_cert")
public class SupplierArchiveCertDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String certType;
    private String certNo;
    private String issueOrg;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String attachmentUrl;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
