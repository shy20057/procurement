package com.example.procurement.sourcing.dao.entity.contract;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D13 采购合同标的/条款分录 */
@Data
@TableName("stc_schema.t_stc_contract_e_clause")
public class ContractClauseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String clauseType;
    private String categoryCode;
    private String itemName;
    private BigDecimal qty;
    private BigDecimal unitPrice;
    private String remark;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
