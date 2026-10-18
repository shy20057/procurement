package com.example.procurement.supplier.service.change.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.change.SupplierChangeDO;
import com.example.procurement.supplier.dao.entity.change.SupplierChangeEDO;
import com.example.procurement.supplier.dao.mapper.change.SupplierChangeEMapper;
import com.example.procurement.supplier.dao.mapper.change.SupplierChangeMapper;
import com.example.procurement.supplier.dto.req.change.CreateChangeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.change.ChangeResponse;
import com.example.procurement.supplier.service.change.ChangeService;
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
public class ChangeServiceImpl implements ChangeService {

    private final SupplierChangeMapper changeMapper;
    private final SupplierChangeEMapper changeEMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<ChangeResponse> list(String status, String supplierCode, String changeType) {
        LambdaQueryWrapper<SupplierChangeDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierChangeDO::getStatus, status);
        if (supplierCode != null && !supplierCode.isBlank()) wrapper.eq(SupplierChangeDO::getSupplierCode, supplierCode);
        if (changeType != null && !changeType.isBlank()) wrapper.eq(SupplierChangeDO::getChangeType, changeType);
        wrapper.orderByDesc(SupplierChangeDO::getCreatedAt);
        return changeMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ChangeResponse get(Long id) {
        SupplierChangeDO change = changeMapper.selectById(id);
        if (change == null) throw new NotFoundException(SrmErrorCode.CHANGE_NOT_FOUND);
        return toResponse(change);
    }

    @Override
    @Transactional
    public ChangeResponse create(CreateChangeRequest request) {
        SupplierChangeDO change = new SupplierChangeDO();
        change.setBillNo(billNoGenerator.generateChange());
        change.setSupplierCode(request.getSupplierCode());
        change.setChangeType(request.getChangeType());
        change.setChangeReason(request.getChangeReason());
        change.setApplicant(request.getApplicant());
        change.setApplyDate(LocalDate.now());
        change.setStatus("DRAFT");
        change.setCreatedAt(Instant.now());
        change.setUpdatedAt(Instant.now());
        changeMapper.insert(change);
        if (request.getEntries() != null) {
            int seq = 1;
            for (var e : request.getEntries()) {
                SupplierChangeEDO entry = new SupplierChangeEDO();
                entry.setHeaderId(change.getId());
                entry.setSeq(e.getSeq() != null ? e.getSeq() : seq++);
                entry.setFieldName(e.getFieldName());
                entry.setOldValue(e.getOldValue());
                entry.setNewValue(e.getNewValue());
                entry.setCreatedAt(Instant.now());
                entry.setUpdatedAt(Instant.now());
                changeEMapper.insert(entry);
            }
        }
        return toResponse(change);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SupplierChangeDO change = changeMapper.selectById(id);
        if (change == null) throw new NotFoundException(SrmErrorCode.CHANGE_NOT_FOUND);
        if (!"DRAFT".equals(change.getStatus())) throw new BusinessException(SrmErrorCode.CHANGE_NOT_DRAFT);
        change.setStatus("SUBMITTED");
        change.setUpdatedAt(Instant.now());
        changeMapper.updateById(change);
    }

    @Override
    @Transactional
    public ChangeResponse approve(Long id) {
        SupplierChangeDO change = changeMapper.selectById(id);
        if (change == null) throw new NotFoundException(SrmErrorCode.CHANGE_NOT_FOUND);
        if (!"SUBMITTED".equals(change.getStatus())) throw new BusinessException(SrmErrorCode.CHANGE_NOT_IN_REVIEW);
        change.setStatus("APPROVED");
        change.setApprovedAt(Instant.now());
        change.setUpdatedAt(Instant.now());
        changeMapper.updateById(change);
        return toResponse(change);
    }

    @Override
    @Transactional
    public void reject(Long id, RejectRequest request) {
        SupplierChangeDO change = changeMapper.selectById(id);
        if (change == null) throw new NotFoundException(SrmErrorCode.CHANGE_NOT_FOUND);
        if (!"SUBMITTED".equals(change.getStatus())) throw new BusinessException(SrmErrorCode.CHANGE_NOT_IN_REVIEW);
        change.setStatus("REJECTED");
        change.setUpdatedAt(Instant.now());
        changeMapper.updateById(change);
    }

    @Override
    @Transactional
    public void unapprove(Long id) {
        SupplierChangeDO change = changeMapper.selectById(id);
        if (change == null) throw new NotFoundException(SrmErrorCode.CHANGE_NOT_FOUND);
        if (!"APPROVED".equals(change.getStatus())) throw new BusinessException(SrmErrorCode.CHANGE_NOT_APPROVED);
        change.setStatus("DRAFT");
        change.setApprovedAt(null);
        change.setUpdatedAt(Instant.now());
        changeMapper.updateById(change);
    }

    private ChangeResponse toResponse(SupplierChangeDO change) {
        ChangeResponse resp = new ChangeResponse();
        resp.setId(change.getId());
        resp.setBillNo(change.getBillNo());
        resp.setSupplierCode(change.getSupplierCode());
        resp.setChangeType(change.getChangeType());
        resp.setChangeReason(change.getChangeReason());
        resp.setApplicant(change.getApplicant());
        resp.setApplyDate(change.getApplyDate());
        resp.setStatus(change.getStatus());
        resp.setApprovedAt(change.getApprovedAt());
        resp.setCreatedAt(change.getCreatedAt());

        List<SupplierChangeEDO> entries = changeEMapper.selectList(Wrappers.<SupplierChangeEDO>lambdaQuery()
                .eq(SupplierChangeEDO::getHeaderId, change.getId()));
        resp.setEntries(entries.stream().map(e -> {
            ChangeResponse.ChangeFieldEntry entry = new ChangeResponse.ChangeFieldEntry();
            entry.setSeq(e.getSeq()); entry.setFieldName(e.getFieldName());
            entry.setOldValue(e.getOldValue()); entry.setNewValue(e.getNewValue());
            return entry;
        }).collect(Collectors.toList()));
        return resp;
    }
}
