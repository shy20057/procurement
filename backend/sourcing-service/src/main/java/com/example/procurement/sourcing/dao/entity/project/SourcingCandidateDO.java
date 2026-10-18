package com.example.procurement.sourcing.dao.entity.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** D9 寻源立项单候选供应商分录 */
@Data
@TableName("stc_schema.t_stc_sourcing_project_e_cand")
public class SourcingCandidateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String supplierCode;
    private String supplierName;
    private String matchResult;
    private String certStatus;
    private String perfGrade;
    private String remark;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
