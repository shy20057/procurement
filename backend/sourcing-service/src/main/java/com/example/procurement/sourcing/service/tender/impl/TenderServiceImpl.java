package com.example.procurement.sourcing.service.tender.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.tender.TenderBidderDO;
import com.example.procurement.sourcing.dao.entity.tender.TenderDO;
import com.example.procurement.sourcing.dao.entity.tender.TenderEvaluationDO;
import com.example.procurement.sourcing.dao.mapper.tender.TenderBidderMapper;
import com.example.procurement.sourcing.dao.mapper.tender.TenderEvaluationMapper;
import com.example.procurement.sourcing.dao.mapper.tender.TenderMapper;
import com.example.procurement.sourcing.dto.resp.tender.TenderResponse;
import com.example.procurement.sourcing.service.tender.TenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenderServiceImpl implements TenderService {

    private final TenderMapper tenderMapper;
    private final TenderBidderMapper bidderMapper;
    private final TenderEvaluationMapper evaluationMapper;

    @Override
    public List<TenderResponse> list(String status, String keyword) {
        LambdaQueryWrapper<TenderDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(TenderDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(TenderDO::getTenderTitle, keyword)
                    .or().like(TenderDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(TenderDO::getCreatedAt);
        return tenderMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public TenderResponse get(Long id) {
        TenderDO tender = tenderMapper.selectById(id);
        if (tender == null) throw new NotFoundException(StcErrorCode.TENDER_NOT_FOUND);
        return toResponse(tender);
    }

    private TenderResponse toResponse(TenderDO tender) {
        TenderResponse resp = new TenderResponse();
        resp.setId(tender.getId());
        resp.setBillNo(tender.getBillNo());
        resp.setSourceProjectBillNo(tender.getSourceProjectBillNo());
        resp.setTenderTitle(tender.getTenderTitle());
        resp.setTenderType(tender.getTenderType());
        resp.setAnnounceDate(tender.getAnnounceDate());
        resp.setBidDeadline(tender.getBidDeadline());
        resp.setOpenDate(tender.getOpenDate());
        resp.setNeedPrequalification(tender.getNeedPrequalification());
        resp.setEvaluationMethod(tender.getEvaluationMethod());
        resp.setStatus(tender.getStatus());

        List<TenderBidderDO> bidders = bidderMapper.selectList(Wrappers.<TenderBidderDO>lambdaQuery()
                .eq(TenderBidderDO::getHeaderId, tender.getId())
                .orderByAsc(TenderBidderDO::getSeq));
        resp.setBidders(bidders.stream().map(b -> {
            TenderResponse.BidderEntry e = new TenderResponse.BidderEntry();
            e.setId(b.getId());
            e.setSeq(b.getSeq());
            e.setSupplierCode(b.getSupplierCode());
            e.setSupplierName(b.getSupplierName());
            e.setBidAmount(b.getBidAmount());
            e.setSubmitTime(b.getSubmitTime());
            e.setPrequalResult(b.getPrequalResult());
            e.setIsShortlisted(b.getIsShortlisted());
            return e;
        }).collect(Collectors.toList()));

        List<TenderEvaluationDO> evals = evaluationMapper.selectList(Wrappers.<TenderEvaluationDO>lambdaQuery()
                .eq(TenderEvaluationDO::getHeaderId, tender.getId())
                .orderByAsc(TenderEvaluationDO::getSeq));
        resp.setEvaluations(evals.stream().map(v -> {
            TenderResponse.EvaluationEntry e = new TenderResponse.EvaluationEntry();
            e.setId(v.getId());
            e.setSeq(v.getSeq());
            e.setSupplierCode(v.getSupplierCode());
            e.setSupplierName(v.getSupplierName());
            e.setTechScore(v.getTechScore());
            e.setBizScore(v.getBizScore());
            e.setTotalScore(v.getTotalScore());
            e.setRank(v.getRank());
            e.setJudgeComment(v.getJudgeComment());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
