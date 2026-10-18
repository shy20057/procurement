package com.example.procurement.sourcing.dao.entity.award;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D12 定标单单头（C3 风控守门单据） */
@Data
@TableName("stc_schema.t_stc_award_h")
public class AwardDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceBillType;
    private String sourceBillNo;
    private String awardSupplierCode;
    private String awardSupplierName;
    private String awardConclusion;
    private BigDecimal totalAwardAmount;
    private String currency;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
