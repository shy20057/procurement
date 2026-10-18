package com.example.procurement.sourcing.dao.entity.contract;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** D13 采购合同单头 */
@Data
@TableName("stc_schema.t_stc_contract_h")
public class ContractDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceAwardBillNo;
    private String supplierCode;
    private String supplierName;
    private String contractName;
    private BigDecimal contractAmount;
    private String currency;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String contractType;
    private String signatory;
    private String legalReviewStatus;
    private String sealStatus;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
