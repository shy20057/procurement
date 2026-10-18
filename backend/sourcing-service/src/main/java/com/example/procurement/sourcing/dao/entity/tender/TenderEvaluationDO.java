package com.example.procurement.sourcing.dao.entity.tender;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D10 招标单评标记分录 */
@Data
@TableName("stc_schema.t_stc_tender_e_eval")
public class TenderEvaluationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String supplierCode;
    private String supplierName;
    private BigDecimal techScore;
    private BigDecimal bizScore;
    private BigDecimal totalScore;
    private Integer rank;
    private String judgeComment;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
