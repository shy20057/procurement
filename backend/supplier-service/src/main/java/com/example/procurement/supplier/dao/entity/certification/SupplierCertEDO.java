package com.example.procurement.supplier.dao.entity.certification;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@TableName("t_srm_supplier_cert_e")
public class SupplierCertEDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String dimension;
    private String result;
    private BigDecimal score;
    private String responsiblePerson;
    private String remark;
    private String attachmentUrl;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
