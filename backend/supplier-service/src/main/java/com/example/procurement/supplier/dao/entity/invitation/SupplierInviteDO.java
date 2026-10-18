package com.example.procurement.supplier.dao.entity.invitation;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;

@Data
@TableName("t_srm_supplier_invite_h")
public class SupplierInviteDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String inviteeName;
    private String inviteeCreditCode;
    private String inviteeContact;
    private String inviteePhone;
    private String inviteeEmail;
    private String invitedBy;
    private LocalDate inviteDate;
    private LocalDate expireDate;
    private String status;
    private String relatedRegBillNo;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
