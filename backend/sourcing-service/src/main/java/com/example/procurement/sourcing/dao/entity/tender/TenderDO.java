package com.example.procurement.sourcing.dao.entity.tender;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** D10 招标单（开评标）单头 */
@Data
@TableName("stc_schema.t_stc_tender_h")
public class TenderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceProjectBillNo;
    private String tenderTitle;
    private String tenderType;
    private LocalDate announceDate;
    private LocalDate bidDeadline;
    private LocalDate openDate;
    private Boolean needPrequalification;
    private String evaluationMethod;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
