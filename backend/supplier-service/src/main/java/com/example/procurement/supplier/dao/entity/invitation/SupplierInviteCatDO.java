package com.example.procurement.supplier.dao.entity.invitation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;

@Data
@TableName("t_srm_supplier_invite_e_cat")
public class SupplierInviteCatDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String categoryCode;
    private String categoryName;
    private String remark;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
