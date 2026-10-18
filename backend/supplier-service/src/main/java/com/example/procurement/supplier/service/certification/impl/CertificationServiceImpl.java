package com.example.procurement.supplier.service.certification.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveDO;
import com.example.procurement.supplier.dao.entity.certification.SupplierCertDO;
import com.example.procurement.supplier.dao.entity.certification.SupplierCertEDO;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveMapper;
import com.example.procurement.supplier.dao.mapper.certification.SupplierCertEMapper;
import com.example.procurement.supplier.dao.mapper.certification.SupplierCertMapper;
import com.example.procurement.supplier.dto.req.certification.CreateCertRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import com.example.procurement.supplier.service.certification.CertificationService;
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
public class CertificationServiceImpl implements CertificationService {

    private final SupplierCertMapper certMapper;
    private final SupplierCertEMapper certEMapper;
    private final SupplierArchiveMapper archiveMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<CertResponse> list(String status, String supplierName, String conclusion) {
        LambdaQueryWrapper<SupplierCertDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierCertDO::getStatus, status);
        if (supplierName != null && !supplierName.isBlank()) wrapper.like(SupplierCertDO::getSupplierName, supplierName);
        if (conclusion != null && !conclusion.isBlank()) wrapper.eq(SupplierCertDO::getConclusion, conclusion);
        wrapper.orderByDesc(SupplierCertDO::getCreatedAt);
        return certMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public CertResponse get(Long id) {
        SupplierCertDO cert = certMapper.selectById(id);
        if (cert == null) throw new NotFoundException(SrmErrorCode.CERT_NOT_FOUND);
        return toResponse(cert);
    }

    @Override
    @Transactional
    public CertResponse create(CreateCertRequest request) {
        SupplierCertDO cert = new SupplierCertDO();
        cert.setBillNo(billNoGenerator.generateCert());
        cert.setSupplierName(request.getSupplierName());
        cert.setUnifiedCreditCode(request.getUnifiedCreditCode());
        cert.setCertType(request.getCertType());
        cert.setStartDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now());
        cert.setResponsiblePerson(request.getResponsiblePerson());
        cert.setStatus("DRAFT");
        cert.setCreatedAt(Instant.now());
        cert.setUpdatedAt(Instant.now());
        certMapper.insert(cert);
        if (request.getEntries() != null) {
            int seq = 1;
            for (var e : request.getEntries()) {
                SupplierCertEDO entry = new SupplierCertEDO();
                entry.setHeaderId(cert.getId());
                entry.setSeq(e.getSeq() != null ? e.getSeq() : seq++);
                entry.setDimension(e.getDimension());
                entry.setResult(e.getResult());
                entry.setScore(e.getScore());
                entry.setResponsiblePerson(e.getResponsiblePerson());
                entry.setRemark(e.getRemark());
                entry.setAttachmentUrl(e.getAttachmentUrl());
                entry.setCreatedAt(Instant.now());
                entry.setUpdatedAt(Instant.now());
                certEMapper.insert(entry);
            }
        }
        return toResponse(cert);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SupplierCertDO cert = certMapper.selectById(id);
        if (cert == null) throw new NotFoundException(SrmErrorCode.CERT_NOT_FOUND);
        if (!"DRAFT".equals(cert.getStatus())) throw new BusinessException(SrmErrorCode.CERT_NOT_DRAFT);
        cert.setStatus("IN_REVIEW");
        cert.setUpdatedAt(Instant.now());
        certMapper.updateById(cert);
    }

    @Override
    @Transactional
    public CertResponse approve(Long id) {
        SupplierCertDO cert = certMapper.selectById(id);
        if (cert == null) throw new NotFoundException(SrmErrorCode.CERT_NOT_FOUND);
        if (!"IN_REVIEW".equals(cert.getStatus())) throw new BusinessException(SrmErrorCode.CERT_NOT_IN_REVIEW);
        cert.setStatus("APPROVED");
        cert.setApprovedAt(Instant.now());
        cert.setUpdatedAt(Instant.now());

        // 建议结论
        List<SupplierCertEDO> entries = certEMapper.selectList(Wrappers.<SupplierCertEDO>lambdaQuery()
                .eq(SupplierCertEDO::getHeaderId, id));
        boolean hasFail = entries.stream().anyMatch(e -> "FAIL".equals(e.getResult()));
        String conclusion = hasFail ? "UNQUALIFIED" : "QUALIFIED";
        cert.setConclusion(conclusion);
        cert.setValidUntil(LocalDate.now().plusYears(1));

        certMapper.updateById(cert);

        // 写回档案
        writeBackToArchive(cert);
        return toResponse(cert);
    }

    private void writeBackToArchive(SupplierCertDO cert) {
        if ("UNQUALIFIED".equals(cert.getConclusion())) return;
        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getUnifiedCreditCode, cert.getUnifiedCreditCode()));
        if (archive == null) {
            archive = new SupplierArchiveDO();
            archive.setSupplierCode(billNoGenerator.generateSupplierCode());
            archive.setSupplierName(cert.getSupplierName());
            archive.setUnifiedCreditCode(cert.getUnifiedCreditCode());
            archive.setCreatedAt(Instant.now());
            archive.setUpdatedAt(Instant.now());
            archiveMapper.insert(archive);
        }
        archive.setCertStatus("QUALIFIED".equals(cert.getConclusion()) ? "QUALIFIED" : "TEMP_QUALIFIED");
        archive.setCertValidUntil(cert.getValidUntil());
        archive.setSourceCertBillNo(cert.getBillNo());
        archive.setUpdatedAt(Instant.now());
        archiveMapper.updateById(archive);
    }

    @Override
    @Transactional
    public void reject(Long id, RejectRequest request) {
        SupplierCertDO cert = certMapper.selectById(id);
        if (cert == null) throw new NotFoundException(SrmErrorCode.CERT_NOT_FOUND);
        if (!"IN_REVIEW".equals(cert.getStatus())) throw new BusinessException(SrmErrorCode.CERT_NOT_IN_REVIEW);
        cert.setStatus("REJECTED");
        cert.setUpdatedAt(Instant.now());
        certMapper.updateById(cert);
    }

    @Override
    @Transactional
    public void unapprove(Long id) {
        SupplierCertDO cert = certMapper.selectById(id);
        if (cert == null) throw new NotFoundException(SrmErrorCode.CERT_NOT_FOUND);
        if (!"APPROVED".equals(cert.getStatus())) throw new BusinessException(SrmErrorCode.CERT_NOT_APPROVED);
        cert.setStatus("DRAFT");
        cert.setApprovedAt(null);
        cert.setUpdatedAt(Instant.now());
        certMapper.updateById(cert);
    }

    private CertResponse toResponse(SupplierCertDO cert) {
        CertResponse resp = new CertResponse();
        resp.setId(cert.getId());
        resp.setBillNo(cert.getBillNo());
        resp.setSourceBillNo(cert.getSourceBillNo());
        resp.setSourceBillType(cert.getSourceBillType());
        resp.setSupplierName(cert.getSupplierName());
        resp.setUnifiedCreditCode(cert.getUnifiedCreditCode());
        resp.setCertType(cert.getCertType());
        resp.setStartDate(cert.getStartDate());
        resp.setResponsiblePerson(cert.getResponsiblePerson());
        resp.setConclusion(cert.getConclusion());
        resp.setValidUntil(cert.getValidUntil());
        resp.setStatus(cert.getStatus());
        resp.setApprovedAt(cert.getApprovedAt());
        resp.setCreatedAt(cert.getCreatedAt());

        List<SupplierCertEDO> entries = certEMapper.selectList(Wrappers.<SupplierCertEDO>lambdaQuery()
                .eq(SupplierCertEDO::getHeaderId, cert.getId()));
        resp.setEntries(entries.stream().map(e -> {
            CertResponse.CertDimensionEntry entry = new CertResponse.CertDimensionEntry();
            entry.setSeq(e.getSeq()); entry.setDimension(e.getDimension());
            entry.setResult(e.getResult()); entry.setScore(e.getScore());
            entry.setResponsiblePerson(e.getResponsiblePerson());
            entry.setRemark(e.getRemark()); entry.setAttachmentUrl(e.getAttachmentUrl());
            return entry;
        }).collect(Collectors.toList()));
        return resp;
    }
}
