package com.example.procurement.supplier.service.archive.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveDO;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveCatDO;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveCertDO;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveContactDO;
import com.example.procurement.supplier.dao.entity.certification.SupplierCertDO;
import com.example.procurement.supplier.dao.entity.registration.SupplierRegDO;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveCatMapper;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveCertMapper;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveContactMapper;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveMapper;
import com.example.procurement.supplier.dao.mapper.certification.SupplierCertMapper;
import com.example.procurement.supplier.dao.mapper.registration.SupplierRegMapper;
import com.example.procurement.supplier.dto.req.archive.UpdateArchiveRequest;
import com.example.procurement.supplier.dto.resp.archive.ArchiveResponse;
import com.example.procurement.supplier.dto.resp.archive.ExpiringCertResponse;
import com.example.procurement.supplier.dto.resp.archive.RelatedBillResponse;
import com.example.procurement.supplier.service.archive.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements ArchiveService {

    private final SupplierArchiveMapper archiveMapper;
    private final SupplierArchiveCatMapper archiveCatMapper;
    private final SupplierArchiveCertMapper archiveCertMapper;
    private final SupplierArchiveContactMapper archiveContactMapper;
    private final SupplierRegMapper regMapper;
    private final SupplierCertMapper certMapper;

    @Override
    public ArchiveResponse getByCode(String supplierCode) {
        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getSupplierCode, supplierCode));
        if (archive == null) throw new NotFoundException(SrmErrorCode.ARCHIVE_NOT_FOUND);
        return toResponse(archive);
    }

    @Override
    public List<ArchiveResponse> list() {
        return archiveMapper.selectList(Wrappers.<SupplierArchiveDO>lambdaQuery()
                        .orderByDesc(SupplierArchiveDO::getCreatedAt))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void update(String supplierCode, UpdateArchiveRequest request) {
        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getSupplierCode, supplierCode));
        if (archive == null) throw new NotFoundException(SrmErrorCode.ARCHIVE_NOT_FOUND);
        if (request.getSupplierType() != null) archive.setSupplierType(request.getSupplierType());
        if (request.getBankName() != null) {
            archive.setBankName(request.getBankName());
            if (request.getBankAccount() != null) archive.setBankAccount(request.getBankAccount());
            if (request.getBankAccountName() != null) archive.setBankAccountName(request.getBankAccountName());
        }
        archiveMapper.updateById(archive);
    }

    @Override
    public List<RelatedBillResponse> getRelatedBills(String supplierCode, String billType) {
        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getSupplierCode, supplierCode));
        if (archive == null) throw new NotFoundException(SrmErrorCode.ARCHIVE_NOT_FOUND);
        List<RelatedBillResponse> result = new ArrayList<>();
        if (billType == null || "REGISTRATION".equals(billType)) {
            List<SupplierRegDO> regs = regMapper.selectList(Wrappers.<SupplierRegDO>lambdaQuery()
                    .eq(SupplierRegDO::getUnifiedCreditCode, archive.getUnifiedCreditCode()));
            regs.forEach(r -> result.add(new RelatedBillResponse("REGISTRATION", r.getBillNo(), r.getStatus(), r.getApplyDate())));
        }
        if (billType == null || "CERTIFICATION".equals(billType)) {
            List<SupplierCertDO> certs = certMapper.selectList(Wrappers.<SupplierCertDO>lambdaQuery()
                    .eq(SupplierCertDO::getUnifiedCreditCode, archive.getUnifiedCreditCode()));
            certs.forEach(c -> result.add(new RelatedBillResponse("CERTIFICATION", c.getBillNo(), c.getStatus(), c.getStartDate())));
        }
        return result;
    }

    @Override
    public List<ExpiringCertResponse> getExpiringCerts(int days) {
        LocalDate threshold = LocalDate.now().plusDays(days);
        return archiveMapper.selectList(Wrappers.<SupplierArchiveDO>lambdaQuery()
                        .isNotNull(SupplierArchiveDO::getCertValidUntil)
                        .lt(SupplierArchiveDO::getCertValidUntil, threshold))
                .stream().map(a -> {
                    ExpiringCertResponse resp = new ExpiringCertResponse();
                    resp.setSupplierCode(a.getSupplierCode());
                    resp.setSupplierName(a.getSupplierName());
                    resp.setCertType(null);
                    resp.setValidTo(a.getCertValidUntil());
                    return resp;
                }).collect(Collectors.toList());
    }

    private ArchiveResponse toResponse(SupplierArchiveDO archive) {
        ArchiveResponse resp = new ArchiveResponse();
        resp.setSupplierCode(archive.getSupplierCode());
        resp.setSupplierName(archive.getSupplierName());
        resp.setUnifiedCreditCode(archive.getUnifiedCreditCode());
        resp.setSupplierType(archive.getSupplierType());
        resp.setCertStatus(archive.getCertStatus());
        resp.setCertValidUntil(archive.getCertValidUntil());
        resp.setPerfGrade(archive.getPerfGrade());
        resp.setPerfEvalCount(archive.getPerfEvalCount());
        resp.setLastPerfDate(archive.getLastPerfDate());
        resp.setBankName(archive.getBankName());
        resp.setBankAccount(archive.getBankAccount());
        resp.setBankAccountName(archive.getBankAccountName());
        resp.setSourceCertBillNo(archive.getSourceCertBillNo());
        resp.setCreatedAt(archive.getCreatedAt());

        List<SupplierArchiveCatDO> cats = archiveCatMapper.selectList(Wrappers.<SupplierArchiveCatDO>lambdaQuery()
                .eq(SupplierArchiveCatDO::getHeaderId, archive.getId()));
        resp.setCategories(cats.stream().map(c -> {
            ArchiveResponse.CategoryEntry e = new ArchiveResponse.CategoryEntry();
            e.setSeq(c.getSeq()); e.setCategoryCode(c.getCategoryCode());
            e.setCategoryName(c.getCategoryName()); e.setRemark(null);
            return e;
        }).collect(Collectors.toList()));

        List<SupplierArchiveCertDO> certs = archiveCertMapper.selectList(Wrappers.<SupplierArchiveCertDO>lambdaQuery()
                .eq(SupplierArchiveCertDO::getHeaderId, archive.getId()));
        resp.setCerts(certs.stream().map(c -> {
            ArchiveResponse.CertEntry e = new ArchiveResponse.CertEntry();
            e.setSeq(c.getSeq()); e.setCertType(c.getCertType());
            e.setCertNo(c.getCertNo()); e.setIssueOrg(c.getIssueOrg());
            e.setValidFrom(c.getValidFrom()); e.setValidTo(c.getValidTo());
            e.setAttachmentUrl(c.getAttachmentUrl());
            return e;
        }).collect(Collectors.toList()));

        List<SupplierArchiveContactDO> contacts = archiveContactMapper.selectList(Wrappers.<SupplierArchiveContactDO>lambdaQuery()
                .eq(SupplierArchiveContactDO::getHeaderId, archive.getId()));
        resp.setContacts(contacts.stream().map(c -> {
            ArchiveResponse.ContactEntry e = new ArchiveResponse.ContactEntry();
            e.setSeq(c.getSeq()); e.setContactName(c.getContactName());
            e.setPosition(c.getPosition()); e.setPhone(c.getPhone());
            e.setEmail(c.getEmail()); e.setIsPrimary(c.getIsPrimary());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
