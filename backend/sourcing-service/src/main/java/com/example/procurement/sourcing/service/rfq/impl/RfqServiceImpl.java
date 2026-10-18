package com.example.procurement.sourcing.service.rfq.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.rfq.RfqDO;
import com.example.procurement.sourcing.dao.entity.rfq.RfqQuoteDO;
import com.example.procurement.sourcing.dao.mapper.rfq.RfqMapper;
import com.example.procurement.sourcing.dao.mapper.rfq.RfqQuoteMapper;
import com.example.procurement.sourcing.dto.resp.rfq.RfqResponse;
import com.example.procurement.sourcing.service.rfq.RfqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RfqServiceImpl implements RfqService {

    private final RfqMapper rfqMapper;
    private final RfqQuoteMapper quoteMapper;

    @Override
    public List<RfqResponse> list(String status, String keyword) {
        LambdaQueryWrapper<RfqDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(RfqDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(RfqDO::getRfqTitle, keyword)
                    .or().like(RfqDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(RfqDO::getCreatedAt);
        return rfqMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public RfqResponse get(Long id) {
        RfqDO rfq = rfqMapper.selectById(id);
        if (rfq == null) throw new NotFoundException(StcErrorCode.RFQ_NOT_FOUND);
        return toResponse(rfq);
    }

    private RfqResponse toResponse(RfqDO rfq) {
        RfqResponse resp = new RfqResponse();
        resp.setId(rfq.getId());
        resp.setBillNo(rfq.getBillNo());
        resp.setSourceProjectBillNo(rfq.getSourceProjectBillNo());
        resp.setRfqTitle(rfq.getRfqTitle());
        resp.setMaxRounds(rfq.getMaxRounds());
        resp.setCurrentRound(rfq.getCurrentRound());
        resp.setQuoteDeadline(rfq.getQuoteDeadline());
        resp.setStatus(rfq.getStatus());

        List<RfqQuoteDO> quotes = quoteMapper.selectList(Wrappers.<RfqQuoteDO>lambdaQuery()
                .eq(RfqQuoteDO::getHeaderId, rfq.getId())
                .orderByAsc(RfqQuoteDO::getRoundNo)
                .orderByAsc(RfqQuoteDO::getId));
        resp.setQuotes(quotes.stream().map(q -> {
            RfqResponse.QuoteEntry e = new RfqResponse.QuoteEntry();
            e.setId(q.getId());
            e.setRoundNo(q.getRoundNo());
            e.setSupplierCode(q.getSupplierCode());
            e.setSupplierName(q.getSupplierName());
            e.setItemCode(q.getItemCode());
            e.setItemName(q.getItemName());
            e.setQty(q.getQty());
            e.setUnit(q.getUnit());
            e.setQuoteUnitPrice(q.getQuoteUnitPrice());
            e.setQuoteAmount(q.getQuoteAmount());
            e.setQuoteDate(q.getQuoteDate());
            e.setDeliveryDays(q.getDeliveryDays());
            e.setRemark(q.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
