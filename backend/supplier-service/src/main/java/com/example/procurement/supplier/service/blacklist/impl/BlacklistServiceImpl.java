package com.example.procurement.supplier.service.blacklist.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveDO;
import com.example.procurement.supplier.dao.entity.blacklist.SupplierBlacklistDO;
import com.example.procurement.supplier.dao.entity.blacklist.SupplierBlacklistEDO;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveMapper;
import com.example.procurement.supplier.dao.mapper.blacklist.SupplierBlacklistEMapper;
import com.example.procurement.supplier.dao.mapper.blacklist.SupplierBlacklistMapper;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import com.example.procurement.supplier.service.blacklist.BlacklistService;
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
public class BlacklistServiceImpl implements BlacklistService {

    private final SupplierBlacklistMapper blacklistMapper;
    private final SupplierBlacklistEMapper blacklistEMapper;
    private final SupplierArchiveMapper archiveMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<BlacklistResponse> list(String status, String supplierCode, String triggerSource) {
        LambdaQueryWrapper<SupplierBlacklistDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierBlacklistDO::getStatus, status);
        if (supplierCode != null && !supplierCode.isBlank()) wrapper.eq(SupplierBlacklistDO::getSupplierCode, supplierCode);
        if (triggerSource != null && !triggerSource.isBlank()) wrapper.eq(SupplierBlacklistDO::getTriggerSource, triggerSource);
        wrapper.orderByDesc(SupplierBlacklistDO::getCreatedAt);
        return blacklistMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public BlacklistResponse get(Long id) {
        SupplierBlacklistDO blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) throw new NotFoundException(SrmErrorCode.BLACKLIST_NOT_FOUND);
        return toResponse(blacklist);
    }

    @Override
    @Transactional
    public BlacklistResponse create(CreateBlacklistRequest request) {
        SupplierBlacklistDO blacklist = new SupplierBlacklistDO();
        blacklist.setBillNo(billNoGenerator.generateBlacklist());
        blacklist.setSupplierCode(request.getSupplierCode());
        blacklist.setTriggerSource(request.getTriggerSource());
        blacklist.setSourcePerfBillNo(request.getSourcePerfBillNo());
        blacklist.setReason(request.getReason());
        blacklist.setApplicant(request.getApplicant());
        blacklist.setApprovalLevel(request.getApprovalLevel());
        blacklist.setIsPermanent(request.getIsPermanent() != null ? request.getIsPermanent() : true);
        blacklist.setReviewDate(request.getReviewDate());
        blacklist.setApplyDate(LocalDate.now());
        blacklist.setStatus("DRAFT");
        blacklist.setCreatedAt(Instant.now());
        blacklist.setUpdatedAt(Instant.now());
        blacklistMapper.insert(blacklist);
        if (request.getEntries() != null) {
            int seq = 1;
            for (var e : request.getEntries()) {
                SupplierBlacklistEDO entry = new SupplierBlacklistEDO();
                entry.setHeaderId(blacklist.getId());
                entry.setSeq(e.getSeq() != null ? e.getSeq() : seq++);
                entry.setEvidenceType(e.getEvidenceType());
                entry.setEvidenceDesc(e.getEvidenceDesc());
                entry.setAttachmentUrl(e.getAttachmentUrl());
                entry.setCreatedAt(Instant.now());
                entry.setUpdatedAt(Instant.now());
                blacklistEMapper.insert(entry);
            }
        }
        return toResponse(blacklist);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SupplierBlacklistDO blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) throw new NotFoundException(SrmErrorCode.BLACKLIST_NOT_FOUND);
        if (!"DRAFT".equals(blacklist.getStatus())) throw new BusinessException(SrmErrorCode.BLACKLIST_NOT_DRAFT);
        blacklist.setStatus("SUBMITTED");
        blacklist.setUpdatedAt(Instant.now());
        blacklistMapper.updateById(blacklist);
    }

    @Override
    @Transactional
    public BlacklistResponse approve(Long id) {
        SupplierBlacklistDO blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) throw new NotFoundException(SrmErrorCode.BLACKLIST_NOT_FOUND);
        if (!"SUBMITTED".equals(blacklist.getStatus())) throw new BusinessException(SrmErrorCode.BLACKLIST_NOT_IN_REVIEW);
        blacklist.setStatus("APPROVED");
        blacklist.setEffectiveDate(LocalDate.now());
        blacklist.setUpdatedAt(Instant.now());
        blacklistMapper.updateById(blacklist);

        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getSupplierCode, blacklist.getSupplierCode()));
        if (archive != null) {
            archive.setCertStatus("BLACKLIST");
            archive.setUpdatedAt(Instant.now());
            archiveMapper.updateById(archive);
        }
        return toResponse(blacklist);
    }

    @Override
    @Transactional
    public void reject(Long id, RejectRequest request) {
        SupplierBlacklistDO blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) throw new NotFoundException(SrmErrorCode.BLACKLIST_NOT_FOUND);
        if (!"SUBMITTED".equals(blacklist.getStatus())) throw new BusinessException(SrmErrorCode.BLACKLIST_NOT_IN_REVIEW);
        blacklist.setStatus("REJECTED");
        blacklist.setUpdatedAt(Instant.now());
        blacklistMapper.updateById(blacklist);
    }

    @Override
    @Transactional
    public void unapprove(Long id) {
        SupplierBlacklistDO blacklist = blacklistMapper.selectById(id);
        if (blacklist == null) throw new NotFoundException(SrmErrorCode.BLACKLIST_NOT_FOUND);
        if (!"APPROVED".equals(blacklist.getStatus())) throw new BusinessException(SrmErrorCode.BLACKLIST_NOT_APPROVED);
        blacklist.setStatus("DRAFT");
        blacklist.setUpdatedAt(Instant.now());
        blacklistMapper.updateById(blacklist);
    }

    private BlacklistResponse toResponse(SupplierBlacklistDO blacklist) {
        BlacklistResponse resp = new BlacklistResponse();
        resp.setId(blacklist.getId());
        resp.setBillNo(blacklist.getBillNo());
        resp.setSupplierCode(blacklist.getSupplierCode());
        resp.setTriggerSource(blacklist.getTriggerSource());
        resp.setSourcePerfBillNo(blacklist.getSourcePerfBillNo());
        resp.setReason(blacklist.getReason());
        resp.setApplicant(blacklist.getApplicant());
        resp.setApplyDate(blacklist.getApplyDate());
        resp.setApprovalLevel(blacklist.getApprovalLevel());
        resp.setIsPermanent(blacklist.getIsPermanent());
        resp.setReviewDate(blacklist.getReviewDate());
        resp.setStatus(blacklist.getStatus());
        resp.setEffectiveDate(blacklist.getEffectiveDate());
        resp.setCreatedAt(blacklist.getCreatedAt());

        List<SupplierBlacklistEDO> entries = blacklistEMapper.selectList(Wrappers.<SupplierBlacklistEDO>lambdaQuery()
                .eq(SupplierBlacklistEDO::getHeaderId, blacklist.getId()));
        resp.setEntries(entries.stream().map(e -> {
            BlacklistResponse.BlacklistEvidenceEntry entry = new BlacklistResponse.BlacklistEvidenceEntry();
            entry.setSeq(e.getSeq()); entry.setEvidenceType(e.getEvidenceType());
            entry.setEvidenceDesc(e.getEvidenceDesc()); entry.setAttachmentUrl(e.getAttachmentUrl());
            return entry;
        }).collect(Collectors.toList()));
        return resp;
    }
}
