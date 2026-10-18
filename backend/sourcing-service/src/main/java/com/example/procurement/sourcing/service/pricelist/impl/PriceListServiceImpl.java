package com.example.procurement.sourcing.service.pricelist.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.pricelist.PriceListDO;
import com.example.procurement.sourcing.dao.entity.pricelist.PriceListItemDO;
import com.example.procurement.sourcing.dao.mapper.pricelist.PriceListItemMapper;
import com.example.procurement.sourcing.dao.mapper.pricelist.PriceListMapper;
import com.example.procurement.sourcing.dto.resp.pricelist.PriceListResponse;
import com.example.procurement.sourcing.service.pricelist.PriceListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceListServiceImpl implements PriceListService {

    private final PriceListMapper priceListMapper;
    private final PriceListItemMapper priceListItemMapper;

    @Override
    public List<PriceListResponse> list(String status, String keyword) {
        LambdaQueryWrapper<PriceListDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(PriceListDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(PriceListDO::getPriceListName, keyword)
                    .or().like(PriceListDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(PriceListDO::getCreatedAt);
        return priceListMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public PriceListResponse get(Long id) {
        PriceListDO priceList = priceListMapper.selectById(id);
        if (priceList == null) throw new NotFoundException(StcErrorCode.PRICE_LIST_NOT_FOUND);
        return toResponse(priceList);
    }

    private PriceListResponse toResponse(PriceListDO priceList) {
        PriceListResponse resp = new PriceListResponse();
        resp.setId(priceList.getId());
        resp.setBillNo(priceList.getBillNo());
        resp.setSourceAwardBillNo(priceList.getSourceAwardBillNo());
        resp.setPriceListName(priceList.getPriceListName());
        resp.setSupplierCode(priceList.getSupplierCode());
        resp.setSupplierName(priceList.getSupplierName());
        resp.setEffectiveFrom(priceList.getEffectiveFrom());
        resp.setEffectiveTo(priceList.getEffectiveTo());
        resp.setStatus(priceList.getStatus());

        List<PriceListItemDO> items = priceListItemMapper.selectList(Wrappers.<PriceListItemDO>lambdaQuery()
                .eq(PriceListItemDO::getHeaderId, priceList.getId())
                .orderByAsc(PriceListItemDO::getSeq));
        resp.setItems(items.stream().map(i -> {
            PriceListResponse.ItemEntry e = new PriceListResponse.ItemEntry();
            e.setId(i.getId());
            e.setSeq(i.getSeq());
            e.setCategoryCode(i.getCategoryCode());
            e.setItemCode(i.getItemCode());
            e.setItemName(i.getItemName());
            e.setUnitPrice(i.getUnitPrice());
            e.setCurrency(i.getCurrency());
            e.setTieredPriceNote(i.getTieredPriceNote());
            e.setRegionalDiffNote(i.getRegionalDiffNote());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
