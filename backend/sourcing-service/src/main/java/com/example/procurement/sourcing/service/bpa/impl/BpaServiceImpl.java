package com.example.procurement.sourcing.service.bpa.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.bpa.BpaCategoryDO;
import com.example.procurement.sourcing.dao.entity.bpa.BpaDO;
import com.example.procurement.sourcing.dao.mapper.bpa.BpaCategoryMapper;
import com.example.procurement.sourcing.dao.mapper.bpa.BpaMapper;
import com.example.procurement.sourcing.dto.resp.bpa.BpaResponse;
import com.example.procurement.sourcing.service.bpa.BpaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BpaServiceImpl implements BpaService {

    private final BpaMapper bpaMapper;
    private final BpaCategoryMapper bpaCategoryMapper;

    @Override
    public List<BpaResponse> list(String status, String keyword) {
        LambdaQueryWrapper<BpaDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(BpaDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(BpaDO::getAgreementName, keyword)
                    .or().like(BpaDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(BpaDO::getCreatedAt);
        return bpaMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public BpaResponse get(Long id) {
        BpaDO bpa = bpaMapper.selectById(id);
        if (bpa == null) throw new NotFoundException(StcErrorCode.BPA_NOT_FOUND);
        return toResponse(bpa);
    }

    private BpaResponse toResponse(BpaDO bpa) {
        BpaResponse resp = new BpaResponse();
        resp.setId(bpa.getId());
        resp.setBillNo(bpa.getBillNo());
        resp.setSourceAwardBillNo(bpa.getSourceAwardBillNo());
        resp.setAgreementName(bpa.getAgreementName());
        resp.setSupplierCode(bpa.getSupplierCode());
        resp.setSupplierName(bpa.getSupplierName());
        resp.setTotalQuotaAmount(bpa.getTotalQuotaAmount());
        resp.setUsedQuotaAmount(bpa.getUsedQuotaAmount());
        resp.setCurrency(bpa.getCurrency());
        resp.setValidFrom(bpa.getValidFrom());
        resp.setValidTo(bpa.getValidTo());
        resp.setStatus(bpa.getStatus());

        List<BpaCategoryDO> cats = bpaCategoryMapper.selectList(Wrappers.<BpaCategoryDO>lambdaQuery()
                .eq(BpaCategoryDO::getHeaderId, bpa.getId())
                .orderByAsc(BpaCategoryDO::getSeq));
        resp.setCategories(cats.stream().map(c -> {
            BpaResponse.CategoryEntry e = new BpaResponse.CategoryEntry();
            e.setId(c.getId());
            e.setSeq(c.getSeq());
            e.setCategoryCode(c.getCategoryCode());
            e.setCategoryName(c.getCategoryName());
            e.setItemCode(c.getItemCode());
            e.setUnitPrice(c.getUnitPrice());
            e.setMaxQty(c.getMaxQty());
            e.setRemark(c.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
