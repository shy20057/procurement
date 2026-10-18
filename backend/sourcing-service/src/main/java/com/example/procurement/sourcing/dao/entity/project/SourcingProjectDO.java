package com.example.procurement.sourcing.dao.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** D9 寻源立项单单头 */
@Data
@TableName("stc_schema.t_stc_sourcing_project_h")
public class SourcingProjectDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String projectName;
    private String categoryCode;
    private String categoryName;
    private BigDecimal budgetAmount;
    private String currency;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private String sourcingMethod;
    private String singleSourceReason;
    private String initiator;
    private LocalDate applyDate;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
