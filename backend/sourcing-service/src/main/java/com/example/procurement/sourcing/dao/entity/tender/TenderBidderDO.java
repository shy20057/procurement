package com.example.procurement.sourcing.dao.entity.tender;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** D10 招标单投标供应商分录 */
@Data
@TableName("stc_schema.t_stc_tender_e_bidder")
public class TenderBidderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long headerId;
    private Integer seq;
    private String supplierCode;
    private String supplierName;
    private BigDecimal bidAmount;
    private Instant submitTime;
    private String prequalResult;
    private Boolean isShortlisted;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
}
