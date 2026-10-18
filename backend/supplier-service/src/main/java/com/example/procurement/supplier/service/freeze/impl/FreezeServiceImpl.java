package com.example.procurement.supplier.service.freeze.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveDO;
import com.example.procurement.supplier.dao.entity.freeze.SupplierFreezeDO;
import com.example.procurement.supplier.dao.entity.freeze.SupplierFreezeEDO;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveMapper;
import com.example.procurement.supplier.dao.mapper.freeze.SupplierFreezeEMapper;
import com.example.procurement.supplier.dao.mapper.freeze.SupplierFreezeMapper;
import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import com.example.procurement.supplier.service.freeze.FreezeService;
import com.example.procurement.supplier.util.BillNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FreezeServiceImpl implements FreezeService {

    private final SupplierFreezeMapper freezeMapper;
    private final SupplierFreezeEMapper freezeEMapper;
    private final SupplierArchiveMapper archiveMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<FreezeResponse> list(String status, String supplierCode, String applyType) {
        LambdaQueryWrapper<SupplierFreezeDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierFreezeDO::getStatus, status);
        if (supplierCode != null && !supplierCode.isBlank()) wrapper.eq(SupplierFreezeDO::getSupplierCode, supplierCode);
        if (applyType != null && !applyType.isBlank()) wrapper.eq(SupplierFreezeDO::getApplyType, applyType);
        wrapper.orderByDesc(SupplierFreezeDO::getCreatedAt);
        return freezeMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public FreezeResponse get(Long id) {
        SupplierFreezeDO freeze = freezeMapper.selectById(id);
        if (freeze == null) throw new NotFoundException(SrmErrorCode.FREEZE_NOT_FOUND);
        return toResponse(freeze);
    }

    @Override
    @Transactional
    public FreezeResponse create(CreateFreezeRequest request) {
        SupplierFreezeDO freeze = new SupplierFreezeDO();
        freeze.setBillNo(billNoGenerator.generateFreeze());
        freeze.setSupplierCode(request.getSupplierCode());
        freeze.setApplyType(request.getApplyType());
        freeze.setTriggerSource(request.getTriggerSource());
        freeze.setSourcePerfBillNo(request.getSourcePerfBillNo());
        freeze.setReason(request.getReason());
        freeze.setApplicant(request.getApplicant());
        freeze.setApplyDate(LocalDate.now());
        freeze.setStatus("DRAFT");
        freeze.setCreatedAt(Instant.now());
        freeze.setUpdatedAt(Instant.now());
        freezeMapper.insert(freeze);
        if (request.getEntries() != null) {
            int seq = 1;
            for (var e : request.getEntries()) {
                SupplierFreezeEDO entry = new SupplierFreezeEDO();
                entry.setHeaderId(freeze.getId());
                entry.setSeq(e.getSeq() != null ? e.getSeq() : seq++);
                entry.setScopeType(e.getScopeType());
                entry.setCategoryCode(e.getCategoryCode());
                entry.setCategoryName(e.getCategoryName());
                entry.setRemark(e.getRemark());
                entry.setCreatedAt(Instant.now());
                entry.setUpdatedAt(Instant.now());
                freezeEMapper.insert(entry);
            }
        }
        return toResponse(freeze);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SupplierFreezeDO freeze = freezeMapper.selectById(id);
        if (freeze == null) throw new NotFoundException(SrmErrorCode.FREEZE_NOT_FOUND);
        if (!"DRAFT".equals(freeze.getStatus())) throw new BusinessException(SrmErrorCode.FREEZE_NOT_DRAFT);
        freeze.setStatus("SUBMITTED");
        freeze.setUpdatedAt(Instant.now());
        freezeMapper.updateById(freeze);
    }

    @Override
    @Transactional
    public FreezeResponse approve(Long id) {
        SupplierFreezeDO freeze = freezeMapper.selectById(id);
        if (freeze == null) throw new NotFoundException(SrmErrorCode.FREEZE_NOT_FOUND);
        if (!"SUBMITTED".equals(freeze.getStatus())) throw new BusinessException(SrmErrorCode.FREEZE_NOT_IN_REVIEW);

        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getSupplierCode, freeze.getSupplierCode()));
        if (archive == null) throw new NotFoundException(SrmErrorCode.FREEZE_ARCHIVE_NOT_FOUND);

        freeze.setPreStatusSnapshot(archive.getCertStatus());
        freeze.setStatus("APPROVED");
        freeze.setEffectiveDate(LocalDate.now());
        freeze.setUpdatedAt(Instant.now());
        freezeMapper.updateById(freeze);

        if ("FREEZE".equals(freeze.getApplyType())) {
            archive.setCertStatus("FROZEN");
        } else {
            archive.setCertStatus(freeze.getPreStatusSnapshot());
        }
        archiveMapper.updateById(archive);
        return toResponse(freeze);
    }

    @Override
    @Transactional
    public void reject(Long id, RejectRequest request) {
        SupplierFreezeDO freeze = freezeMapper.selectById(id);
        if (freeze == null) throw new NotFoundException(SrmErrorCode.FREEZE_NOT_FOUND);
        if (!"SUBMITTED".equals(freeze.getStatus())) throw new BusinessException(SrmErrorCode.FREEZE_NOT_IN_REVIEW);
        freeze.setStatus("REJECTED");
        freeze.setUpdatedAt(Instant.now());
        freezeMapper.updateById(freeze);
    }

    @Override
    @Transactional
    public void unapprove(Long id) {
        SupplierFreezeDO freeze = freezeMapper.selectById(id);
        if (freeze == null) throw new NotFoundException(SrmErrorCode.FREEZE_NOT_FOUND);
        if (!"APPROVED".equals(freeze.getStatus())) throw new BusinessException(SrmErrorCode.FREEZE_NOT_APPROVED);
        freeze.setStatus("DRAFT");
        freeze.setUpdatedAt(Instant.now());
        freezeMapper.updateById(freeze);
    }

    private FreezeResponse toResponse(SupplierFreezeDO freeze) {
        FreezeResponse resp = new FreezeResponse();
        resp.setId(freeze.getId());
        resp.setBillNo(freeze.getBillNo());
        resp.setSupplierCode(freeze.getSupplierCode());
        resp.setApplyType(freeze.getApplyType());
        resp.setTriggerSource(freeze.getTriggerSource());
        resp.setSourcePerfBillNo(freeze.getSourcePerfBillNo());
        resp.setReason(freeze.getReason());
        resp.setApplicant(freeze.getApplicant());
        resp.setApplyDate(freeze.getApplyDate());
        resp.setStatus(freeze.getStatus());
        resp.setEffectiveDate(freeze.getEffectiveDate());
        resp.setPreStatusSnapshot(freeze.getPreStatusSnapshot());
        resp.setRectificationNote(freeze.getRectificationNote());
        resp.setCreatedAt(freeze.getCreatedAt());

        List<SupplierFreezeEDO> entries = freezeEMapper.selectList(Wrappers.<SupplierFreezeEDO>lambdaQuery()
                .eq(SupplierFreezeEDO::getHeaderId, freeze.getId()));
        resp.setEntries(entries.stream().map(e -> {
            FreezeResponse.FreezeScopeEntry entry = new FreezeResponse.FreezeScopeEntry();
            entry.setSeq(e.getSeq()); entry.setScopeType(e.getScopeType());
            entry.setCategoryCode(e.getCategoryCode()); entry.setCategoryName(e.getCategoryName());
            entry.setRemark(e.getRemark());
            return entry;
        }).collect(Collectors.toList()));
        return resp;
    }
}
