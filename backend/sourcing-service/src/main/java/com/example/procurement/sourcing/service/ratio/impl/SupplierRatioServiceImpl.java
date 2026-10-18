package com.example.procurement.sourcing.service.ratio.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.ratio.SupplierRatioDO;
import com.example.procurement.sourcing.dao.entity.ratio.SupplierRatioEntryDO;
import com.example.procurement.sourcing.dao.mapper.ratio.SupplierRatioEntryMapper;
import com.example.procurement.sourcing.dao.mapper.ratio.SupplierRatioMapper;
import com.example.procurement.sourcing.dao.mapper.srm.SrmArchiveMapper;
import com.example.procurement.sourcing.dto.resp.ratio.SupplierRatioResponse;
import com.example.procurement.sourcing.service.ratio.SupplierRatioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierRatioServiceImpl implements SupplierRatioService {

    private final SupplierRatioMapper ratioMapper;
    private final SupplierRatioEntryMapper ratioEntryMapper;
    private final SrmArchiveMapper srmArchiveMapper;

    @Override
    public List<SupplierRatioResponse> list(String status, String keyword) {
        LambdaQueryWrapper<SupplierRatioDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierRatioDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SupplierRatioDO::getCategoryName, keyword)
                    .or().like(SupplierRatioDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(SupplierRatioDO::getCreatedAt);
        return ratioMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public SupplierRatioResponse get(Long id) {
        SupplierRatioDO ratio = ratioMapper.selectById(id);
        if (ratio == null) throw new NotFoundException(StcErrorCode.RATIO_NOT_FOUND);
        return toResponse(ratio);
    }

    private SupplierRatioResponse toResponse(SupplierRatioDO ratio) {
        SupplierRatioResponse resp = new SupplierRatioResponse();
        resp.setId(ratio.getId());
        resp.setBillNo(ratio.getBillNo());
        resp.setCategoryCode(ratio.getCategoryCode());
        resp.setCategoryName(ratio.getCategoryName());
        resp.setRatioCycle(ratio.getRatioCycle());
        resp.setStatus(ratio.getStatus());

        List<SupplierRatioEntryDO> entries = ratioEntryMapper.selectList(Wrappers.<SupplierRatioEntryDO>lambdaQuery()
                .eq(SupplierRatioEntryDO::getHeaderId, ratio.getId())
                .orderByAsc(SupplierRatioEntryDO::getSeq));
        resp.setRatios(entries.stream().map(r -> {
            SupplierRatioResponse.RatioEntry e = new SupplierRatioResponse.RatioEntry();
            e.setId(r.getId());
            e.setSeq(r.getSeq());
            e.setSupplierCode(r.getSupplierCode());
            e.setSupplierName(r.getSupplierName());
            e.setRatioPercent(r.getRatioPercent());
            // 只读引用 SRM 档案绩效评级
            e.setPerfGradeRef(srmArchiveMapper.findPerfGrade(r.getSupplierCode()));
            e.setRemark(r.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
