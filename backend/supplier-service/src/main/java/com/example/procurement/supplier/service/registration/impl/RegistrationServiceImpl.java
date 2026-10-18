package com.example.procurement.supplier.service.registration.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.certification.SupplierCertDO;
import com.example.procurement.supplier.dao.entity.registration.SupplierRegDO;
import com.example.procurement.supplier.dao.entity.registration.SupplierRegCatDO;
import com.example.procurement.supplier.dao.entity.registration.SupplierRegCertDO;
import com.example.procurement.supplier.dao.mapper.certification.SupplierCertMapper;
import com.example.procurement.supplier.dao.mapper.registration.SupplierRegCatMapper;
import com.example.procurement.supplier.dao.mapper.registration.SupplierRegCertMapper;
import com.example.procurement.supplier.dao.mapper.registration.SupplierRegMapper;
import com.example.procurement.supplier.dto.req.registration.CreateRegRequest;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import com.example.procurement.supplier.service.registration.RegistrationService;
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
public class RegistrationServiceImpl implements RegistrationService {

    private final SupplierRegMapper regMapper;
    private final SupplierRegCatMapper regCatMapper;
    private final SupplierRegCertMapper regCertMapper;
    private final SupplierCertMapper certMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<RegResponse> list(String status, String supplierName, String sourceType) {
        LambdaQueryWrapper<SupplierRegDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierRegDO::getStatus, status);
        if (supplierName != null && !supplierName.isBlank()) wrapper.like(SupplierRegDO::getSupplierName, supplierName);
        if (sourceType != null && !sourceType.isBlank()) wrapper.eq(SupplierRegDO::getSourceType, sourceType);
        wrapper.orderByDesc(SupplierRegDO::getCreatedAt);
        return regMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public RegResponse get(Long id) {
        SupplierRegDO reg = regMapper.selectById(id);
        if (reg == null) throw new NotFoundException(SrmErrorCode.REG_NOT_FOUND);
        return toResponse(reg);
    }

    @Override
    @Transactional
    public RegResponse create(CreateRegRequest request) {
        SupplierRegDO reg = new SupplierRegDO();
        reg.setBillNo(billNoGenerator.generateReg());
        reg.setSupplierName(request.getSupplierName());
        reg.setUnifiedCreditCode(request.getUnifiedCreditCode());
        reg.setContactName(request.getContactName());
        reg.setContactPhone(request.getContactPhone());
        reg.setContactEmail(request.getContactEmail());
        reg.setRegAddress(request.getRegAddress());
        reg.setCooperationNote(request.getCooperationNote());
        reg.setSourceType(request.getSourceType());
        reg.setSourceInviteBillNo(request.getSourceInviteBillNo());
        reg.setApplyDate(LocalDate.now());
        reg.setStatus("DRAFT");
        reg.setCreatedAt(Instant.now());
        reg.setUpdatedAt(Instant.now());
        regMapper.insert(reg);
        if (request.getCategories() != null) {
            int seq = 1;
            for (var cat : request.getCategories()) {
                SupplierRegCatDO catDO = new SupplierRegCatDO();
                catDO.setHeaderId(reg.getId());
                catDO.setSeq(cat.getSeq() != null ? cat.getSeq() : seq++);
                catDO.setCategoryCode(cat.getCategoryCode());
                catDO.setCategoryName(cat.getCategoryName());
                catDO.setRemark(cat.getRemark());
                catDO.setCreatedAt(Instant.now());
                catDO.setUpdatedAt(Instant.now());
                regCatMapper.insert(catDO);
            }
        }
        if (request.getCerts() != null) {
            int seq = 1;
            for (var cert : request.getCerts()) {
                SupplierRegCertDO certDO = new SupplierRegCertDO();
                certDO.setHeaderId(reg.getId());
                certDO.setSeq(cert.getSeq() != null ? cert.getSeq() : seq++);
                certDO.setCertType(cert.getCertType());
                certDO.setCertNo(cert.getCertNo());
                certDO.setIssueOrg(cert.getIssueOrg());
                certDO.setValidFrom(cert.getValidFrom());
                certDO.setValidTo(cert.getValidTo());
                certDO.setAttachmentUrl(cert.getAttachmentUrl());
                certDO.setCreatedAt(Instant.now());
                certDO.setUpdatedAt(Instant.now());
                regCertMapper.insert(certDO);
            }
        }
        return toResponse(reg);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SupplierRegDO reg = regMapper.selectById(id);
        if (reg == null) throw new NotFoundException(SrmErrorCode.REG_NOT_FOUND);
        if (!"DRAFT".equals(reg.getStatus())) throw new BusinessException(SrmErrorCode.REG_NOT_DRAFT);
        reg.setStatus("SUBMITTED");
        reg.setUpdatedAt(Instant.now());
        regMapper.updateById(reg);
    }

    @Override
    @Transactional
    public CertResponse pushToCert(Long id) {
        SupplierRegDO reg = regMapper.selectById(id);
        if (reg == null) throw new NotFoundException(SrmErrorCode.REG_NOT_FOUND);
        if (!"SUBMITTED".equals(reg.getStatus())) throw new BusinessException(SrmErrorCode.REG_NOT_SUBMITTED);

        SupplierCertDO cert = new SupplierCertDO();
        cert.setBillNo(billNoGenerator.generateCert());
        cert.setSupplierName(reg.getSupplierName());
        cert.setUnifiedCreditCode(reg.getUnifiedCreditCode());
        cert.setCertType("FIRST");
        cert.setStartDate(LocalDate.now());
        cert.setResponsiblePerson("待定");
        cert.setStatus("IN_REVIEW");
        cert.setSourceBillNo(reg.getBillNo());
        cert.setSourceBillType("REGISTRATION");
        cert.setCreatedAt(Instant.now());
        cert.setUpdatedAt(Instant.now());
        certMapper.insert(cert);

        reg.setStatus("IN_CERT");
        reg.setUpdatedAt(Instant.now());
        regMapper.updateById(reg);

        return toCertResponse(cert);
    }

    private RegResponse toResponse(SupplierRegDO reg) {
        RegResponse resp = new RegResponse();
        resp.setId(reg.getId());
        resp.setBillNo(reg.getBillNo());
        resp.setSupplierName(reg.getSupplierName());
        resp.setUnifiedCreditCode(reg.getUnifiedCreditCode());
        resp.setContactName(reg.getContactName());
        resp.setContactPhone(reg.getContactPhone());
        resp.setContactEmail(reg.getContactEmail());
        resp.setRegAddress(reg.getRegAddress());
        resp.setCooperationNote(reg.getCooperationNote());
        resp.setApplyDate(reg.getApplyDate());
        resp.setSourceType(reg.getSourceType());
        resp.setSourceInviteBillNo(reg.getSourceInviteBillNo());
        resp.setStatus(reg.getStatus());
        resp.setCreatedAt(reg.getCreatedAt());

        List<SupplierRegCatDO> cats = regCatMapper.selectList(Wrappers.<SupplierRegCatDO>lambdaQuery()
                .eq(SupplierRegCatDO::getHeaderId, reg.getId()));
        resp.setCategories(cats.stream().map(c -> {
            RegResponse.CategoryEntry e = new RegResponse.CategoryEntry();
            e.setSeq(c.getSeq()); e.setCategoryCode(c.getCategoryCode());
            e.setCategoryName(c.getCategoryName()); e.setRemark(c.getRemark());
            return e;
        }).collect(Collectors.toList()));

        List<SupplierRegCertDO> certs = regCertMapper.selectList(Wrappers.<SupplierRegCertDO>lambdaQuery()
                .eq(SupplierRegCertDO::getHeaderId, reg.getId()));
        resp.setCerts(certs.stream().map(c -> {
            RegResponse.CertEntry e = new RegResponse.CertEntry();
            e.setSeq(c.getSeq()); e.setCertType(c.getCertType());
            e.setCertNo(c.getCertNo()); e.setIssueOrg(c.getIssueOrg());
            e.setValidFrom(c.getValidFrom()); e.setValidTo(c.getValidTo());
            e.setAttachmentUrl(c.getAttachmentUrl());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }

    private CertResponse toCertResponse(SupplierCertDO cert) {
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
        resp.setEntries(List.of());
        return resp;
    }
}
