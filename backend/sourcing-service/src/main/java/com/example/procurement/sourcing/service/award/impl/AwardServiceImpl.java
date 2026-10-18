package com.example.procurement.sourcing.service.award.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.BusinessException;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.award.AwardDO;
import com.example.procurement.sourcing.dao.entity.award.AwardItemDO;
import com.example.procurement.sourcing.dao.mapper.award.AwardItemMapper;
import com.example.procurement.sourcing.dao.mapper.award.AwardMapper;
import com.example.procurement.sourcing.dao.mapper.srm.SrmArchiveMapper;
import com.example.procurement.sourcing.dto.resp.award.AwardResponse;
import com.example.procurement.sourcing.service.award.AwardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AwardServiceImpl implements AwardService {

    private final AwardMapper awardMapper;
    private final AwardItemMapper awardItemMapper;
    private final SrmArchiveMapper srmArchiveMapper;

    @Override
    public List<AwardResponse> list(String status, String keyword) {
        LambdaQueryWrapper<AwardDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(AwardDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(AwardDO::getBillNo, keyword)
                    .or().like(AwardDO::getAwardSupplierName, keyword));
        }
        wrapper.orderByDesc(AwardDO::getCreatedAt);
        return awardMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public AwardResponse get(Long id) {
        AwardDO award = awardMapper.selectById(id);
        if (award == null) throw new NotFoundException(StcErrorCode.AWARD_NOT_FOUND);
        return toResponse(award);
    }

    /**
     * C3 风控守门：定标审批前强一致性校验中标供应商在 SRM 档案库的实时状态。
     * 若供应商处于已冻结（FROZEN）或黑名单（BLACKLIST）状态，直接阻断审批。
     */
    @Override
    @Transactional
    public AwardResponse approve(Long id) {
        AwardDO award = awardMapper.selectById(id);
        if (award == null) throw new NotFoundException(StcErrorCode.AWARD_NOT_FOUND);
        if (!"SUBMITTED".equals(award.getStatus())) throw new BusinessException(StcErrorCode.AWARD_NOT_SUBMITTED);

        String certStatus = srmArchiveMapper.findCertStatus(award.getAwardSupplierCode());
        if ("FROZEN".equals(certStatus) || "BLACKLIST".equals(certStatus)) {
            throw new BusinessException(StcErrorCode.AWARD_SUPPLIER_RISK);
        }

        award.setStatus("APPROVED");
        award.setUpdatedAt(Instant.now());
        awardMapper.updateById(award);
        return toResponse(award);
    }

    private AwardResponse toResponse(AwardDO award) {
        AwardResponse resp = new AwardResponse();
        resp.setId(award.getId());
        resp.setBillNo(award.getBillNo());
        resp.setSourceBillType(award.getSourceBillType());
        resp.setSourceBillNo(award.getSourceBillNo());
        resp.setAwardSupplierCode(award.getAwardSupplierCode());
        resp.setAwardSupplierName(award.getAwardSupplierName());
        // 实时引用 SRM 档案认证状态，供前端 C3 风控横幅展示
        resp.setAwardSupplierCertStatus(srmArchiveMapper.findCertStatus(award.getAwardSupplierCode()));
        resp.setAwardConclusion(award.getAwardConclusion());
        resp.setTotalAwardAmount(award.getTotalAwardAmount());
        resp.setCurrency(award.getCurrency());
        resp.setStatus(award.getStatus());

        List<AwardItemDO> items = awardItemMapper.selectList(Wrappers.<AwardItemDO>lambdaQuery()
                .eq(AwardItemDO::getHeaderId, award.getId())
                .orderByAsc(AwardItemDO::getSeq));
        resp.setItems(items.stream().map(i -> {
            AwardResponse.ItemEntry e = new AwardResponse.ItemEntry();
            e.setId(i.getId());
            e.setSeq(i.getSeq());
            e.setCategoryCode(i.getCategoryCode());
            e.setItemCode(i.getItemCode());
            e.setItemName(i.getItemName());
            e.setSpec(i.getSpec());
            e.setQty(i.getQty());
            e.setUnit(i.getUnit());
            e.setUnitPrice(i.getUnitPrice());
            e.setTotalAmount(i.getTotalAmount());
            e.setRemark(i.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
