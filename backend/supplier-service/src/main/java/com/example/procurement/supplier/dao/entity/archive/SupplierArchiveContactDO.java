package com.example.procurement.supplier.dao.entity.archive;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;

@Data
@TableName("t_srm_supplier_archive_e_contact")
public class SupplierArchiveContactDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String contactName;
    private String position;
    private String phone;
    private String email;
    private Boolean isPrimary;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
