package com.example.procurement.sourcing.dao.entity.bpa;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** D15 框架协议（BPA）单头 */
@Data
@TableName("stc_schema.t_stc_bpa_h")
public class BpaDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceAwardBillNo;
    private String agreementName;
    private String supplierCode;
    private String supplierName;
    private BigDecimal totalQuotaAmount;
    private BigDecimal usedQuotaAmount;
    private String currency;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
