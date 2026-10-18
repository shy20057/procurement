package com.example.procurement.supplier.service.invitation.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.invitation.SupplierInviteDO;
import com.example.procurement.supplier.dao.entity.invitation.SupplierInviteCatDO;
import com.example.procurement.supplier.dao.entity.registration.SupplierRegDO;
import com.example.procurement.supplier.dao.entity.registration.SupplierRegCatDO;
import com.example.procurement.supplier.dao.mapper.invitation.SupplierInviteCatMapper;
import com.example.procurement.supplier.dao.mapper.invitation.SupplierInviteMapper;
import com.example.procurement.supplier.dao.mapper.registration.SupplierRegCatMapper;
import com.example.procurement.supplier.dao.mapper.registration.SupplierRegMapper;
import com.example.procurement.supplier.dto.req.invitation.CreateInviteRequest;
import com.example.procurement.supplier.dto.resp.invitation.InviteResponse;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import com.example.procurement.supplier.service.invitation.InvitationService;
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
public class InvitationServiceImpl implements InvitationService {

    private final SupplierInviteMapper inviteMapper;
    private final SupplierInviteCatMapper inviteCatMapper;
    private final SupplierRegMapper regMapper;
    private final SupplierRegCatMapper regCatMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<InviteResponse> list(String status, String inviteeName) {
        LambdaQueryWrapper<SupplierInviteDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierInviteDO::getStatus, status);
        if (inviteeName != null && !inviteeName.isBlank()) wrapper.like(SupplierInviteDO::getInviteeName, inviteeName);
        wrapper.orderByDesc(SupplierInviteDO::getCreatedAt);
        List<SupplierInviteDO> invites = inviteMapper.selectList(wrapper);
        return invites.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public InviteResponse get(Long id) {
        SupplierInviteDO invite = inviteMapper.selectById(id);
        if (invite == null) throw new NotFoundException(SrmErrorCode.INVITE_NOT_FOUND);
        return toResponse(invite);
    }

    @Override
    @Transactional
    public InviteResponse create(CreateInviteRequest request) {
        if (request.getInviteeCreditCode() != null && !request.getInviteeCreditCode().isBlank()) {
            long count = inviteMapper.selectCount(Wrappers.<SupplierInviteDO>lambdaQuery()
                    .eq(SupplierInviteDO::getInviteeCreditCode, request.getInviteeCreditCode())
                    .eq(SupplierInviteDO::getStatus, "PENDING"));
            if (count > 0) throw new BusinessException(SrmErrorCode.INVITE_DUPLICATE);
        }
        SupplierInviteDO invite = new SupplierInviteDO();
        invite.setBillNo(billNoGenerator.generateInvite());
        invite.setInviteeName(request.getInviteeName());
        invite.setInviteeCreditCode(request.getInviteeCreditCode());
        invite.setInviteeContact(request.getInviteeContact());
        invite.setInviteePhone(request.getInviteePhone());
        invite.setInviteeEmail(request.getInviteeEmail());
        invite.setInvitedBy(request.getInvitedBy());
        invite.setInviteDate(LocalDate.now());
        invite.setExpireDate(request.getExpireDate());
        invite.setStatus("PENDING");
        invite.setCreatedAt(Instant.now());
        invite.setUpdatedAt(Instant.now());
        inviteMapper.insert(invite);
        if (request.getCategories() != null) {
            int seq = 1;
            for (var cat : request.getCategories()) {
                SupplierInviteCatDO catDO = new SupplierInviteCatDO();
                catDO.setHeaderId(invite.getId());
                catDO.setSeq(cat.getSeq() != null ? cat.getSeq() : seq++);
                catDO.setCategoryCode(cat.getCategoryCode());
                catDO.setCategoryName(cat.getCategoryName());
                catDO.setRemark(cat.getRemark());
                catDO.setCreatedAt(Instant.now());
                catDO.setUpdatedAt(Instant.now());
                inviteCatMapper.insert(catDO);
            }
        }
        return toResponse(invite);
    }

    @Override
    @Transactional
    public void decline(Long id) {
        SupplierInviteDO invite = inviteMapper.selectById(id);
        if (invite == null) throw new NotFoundException(SrmErrorCode.INVITE_NOT_FOUND);
        if (!"PENDING".equals(invite.getStatus())) throw new BusinessException(SrmErrorCode.INVITE_NOT_PENDING);
        invite.setStatus("DECLINED");
        invite.setUpdatedAt(Instant.now());
        inviteMapper.updateById(invite);
    }

    @Override
    @Transactional
    public RegResponse pushToReg(Long id) {
        SupplierInviteDO invite = inviteMapper.selectById(id);
        if (invite == null) throw new NotFoundException(SrmErrorCode.INVITE_NOT_FOUND);
        if (!"PENDING".equals(invite.getStatus())) throw new BusinessException(SrmErrorCode.INVITE_NOT_PENDING);

        SupplierRegDO reg = new SupplierRegDO();
        reg.setBillNo(billNoGenerator.generateReg());
        reg.setSupplierName(invite.getInviteeName());
        reg.setUnifiedCreditCode(invite.getInviteeCreditCode());
        reg.setContactName(invite.getInviteeContact());
        reg.setContactPhone(invite.getInviteePhone());
        reg.setContactEmail(invite.getInviteeEmail());
        reg.setSourceType("INVITED");
        reg.setSourceInviteBillNo(invite.getBillNo());
        reg.setApplyDate(LocalDate.now());
        reg.setStatus("SUBMITTED");
        reg.setCreatedAt(Instant.now());
        reg.setUpdatedAt(Instant.now());
        regMapper.insert(reg);

        List<SupplierInviteCatDO> cats = inviteCatMapper.selectList(Wrappers.<SupplierInviteCatDO>lambdaQuery()
                .eq(SupplierInviteCatDO::getHeaderId, id));
        for (SupplierInviteCatDO cat : cats) {
            SupplierRegCatDO catDO = new SupplierRegCatDO();
            catDO.setHeaderId(reg.getId());
            catDO.setSeq(cat.getSeq());
            catDO.setCategoryCode(cat.getCategoryCode());
            catDO.setCategoryName(cat.getCategoryName());
            catDO.setRemark(cat.getRemark());
            catDO.setCreatedAt(Instant.now());
            catDO.setUpdatedAt(Instant.now());
            regCatMapper.insert(catDO);
        }

        invite.setStatus("REGISTERED");
        invite.setRelatedRegBillNo(reg.getBillNo());
        invite.setUpdatedAt(Instant.now());
        inviteMapper.updateById(invite);

        return toRegResponse(reg);
    }

    private InviteResponse toResponse(SupplierInviteDO invite) {
        InviteResponse resp = new InviteResponse();
        resp.setId(invite.getId());
        resp.setBillNo(invite.getBillNo());
        resp.setInviteeName(invite.getInviteeName());
        resp.setInviteeCreditCode(invite.getInviteeCreditCode());
        resp.setInviteeContact(invite.getInviteeContact());
        resp.setInviteePhone(invite.getInviteePhone());
        resp.setInviteeEmail(invite.getInviteeEmail());
        resp.setInvitedBy(invite.getInvitedBy());
        resp.setInviteDate(invite.getInviteDate());
        resp.setExpireDate(invite.getExpireDate());
        resp.setStatus(invite.getStatus());
        resp.setRelatedRegBillNo(invite.getRelatedRegBillNo());
        resp.setCreatedAt(invite.getCreatedAt());

        List<SupplierInviteCatDO> cats = inviteCatMapper.selectList(Wrappers.<SupplierInviteCatDO>lambdaQuery()
                .eq(SupplierInviteCatDO::getHeaderId, invite.getId()));
        resp.setCategories(cats.stream().map(c -> {
            InviteResponse.CategoryEntry e = new InviteResponse.CategoryEntry();
            e.setSeq(c.getSeq());
            e.setCategoryCode(c.getCategoryCode());
            e.setCategoryName(c.getCategoryName());
            e.setRemark(c.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }

    private RegResponse toRegResponse(SupplierRegDO reg) {
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
        resp.setCategories(List.of());
        resp.setCerts(List.of());
        return resp;
    }
}
