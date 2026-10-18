package com.example.procurement.sourcing.dao.entity.rfq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** D11 询价单（多轮比价）单头 */
@Data
@TableName("stc_schema.t_stc_rfq_h")
public class RfqDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private String sourceProjectBillNo;
    private String rfqTitle;
    private Integer maxRounds;
    private Integer currentRound;
    private LocalDate quoteDeadline;
    private String status;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
