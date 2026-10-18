package com.example.procurement.sourcing.service.project.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.BusinessException;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.project.SourcingCandidateDO;
import com.example.procurement.sourcing.dao.entity.project.SourcingProjectDO;
import com.example.procurement.sourcing.dao.mapper.project.SourcingCandidateMapper;
import com.example.procurement.sourcing.dao.mapper.project.SourcingProjectMapper;
import com.example.procurement.sourcing.dto.req.project.CreateSourcingProjectRequest;
import com.example.procurement.sourcing.dto.resp.project.SourcingProjectResponse;
import com.example.procurement.sourcing.service.project.SourcingProjectService;
import com.example.procurement.sourcing.util.BillNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SourcingProjectServiceImpl implements SourcingProjectService {

    private final SourcingProjectMapper projectMapper;
    private final SourcingCandidateMapper candidateMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<SourcingProjectResponse> list(String status, String keyword) {
        LambdaQueryWrapper<SourcingProjectDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SourcingProjectDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SourcingProjectDO::getProjectName, keyword)
                    .or().like(SourcingProjectDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(SourcingProjectDO::getCreatedAt);
        return projectMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public SourcingProjectResponse get(Long id) {
        SourcingProjectDO project = projectMapper.selectById(id);
        if (project == null) throw new NotFoundException(StcErrorCode.PROJECT_NOT_FOUND);
        return toResponse(project);
    }

    @Override
    @Transactional
    public SourcingProjectResponse create(CreateSourcingProjectRequest request) {
        String prefix = "STC-SRC-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        SourcingProjectDO project = new SourcingProjectDO();
        project.setBillNo(billNoGenerator.next("SRC", projectMapper.findMaxBillNo(prefix)));
        project.setProjectName(request.getProjectName());
        project.setCategoryCode(request.getCategoryCode());
        project.setCategoryName(request.getCategoryName());
        project.setBudgetAmount(request.getBudgetAmount());
        project.setCurrency(request.getCurrency() != null ? request.getCurrency() : "CNY");
        project.setPlanStartDate(request.getPlanStartDate());
        project.setPlanEndDate(request.getPlanEndDate());
        project.setSourcingMethod(request.getSourcingMethod());
        project.setSingleSourceReason(request.getSingleSourceReason());
        project.setInitiator(request.getInitiator());
        project.setApplyDate(LocalDate.now());
        project.setStatus("DRAFT");
        project.setCreatedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        projectMapper.insert(project);
        if (request.getCandidates() != null) {
            int seq = 1;
            for (var cand : request.getCandidates()) {
                SourcingCandidateDO candDO = new SourcingCandidateDO();
                candDO.setHeaderId(project.getId());
                candDO.setSeq(cand.getSeq() != null ? cand.getSeq() : seq++);
                candDO.setSupplierCode(cand.getSupplierCode());
                candDO.setSupplierName(cand.getSupplierName());
                candDO.setMatchResult(cand.getMatchResult());
                candDO.setCertStatus(cand.getCertStatus());
                candDO.setPerfGrade(cand.getPerfGrade());
                candDO.setRemark(cand.getRemark());
                candDO.setCreatedAt(Instant.now());
                candDO.setUpdatedAt(Instant.now());
                candidateMapper.insert(candDO);
            }
        }
        return toResponse(project);
    }

    @Override
    @Transactional
    public SourcingProjectResponse submit(Long id) {
        SourcingProjectDO project = projectMapper.selectById(id);
        if (project == null) throw new NotFoundException(StcErrorCode.PROJECT_NOT_FOUND);
        if (!"DRAFT".equals(project.getStatus())) throw new BusinessException(StcErrorCode.PROJECT_NOT_DRAFT);
        project.setStatus("SUBMITTED");
        project.setUpdatedAt(Instant.now());
        projectMapper.updateById(project);
        return toResponse(project);
    }

    @Override
    @Transactional
    public SourcingProjectResponse approve(Long id) {
        SourcingProjectDO project = projectMapper.selectById(id);
        if (project == null) throw new NotFoundException(StcErrorCode.PROJECT_NOT_FOUND);
        if (!"SUBMITTED".equals(project.getStatus())) throw new BusinessException(StcErrorCode.PROJECT_NOT_SUBMITTED);
        project.setStatus("APPROVED");
        project.setUpdatedAt(Instant.now());
        projectMapper.updateById(project);
        return toResponse(project);
    }

    private SourcingProjectResponse toResponse(SourcingProjectDO project) {
        SourcingProjectResponse resp = new SourcingProjectResponse();
        resp.setId(project.getId());
        resp.setBillNo(project.getBillNo());
        resp.setProjectName(project.getProjectName());
        resp.setCategoryCode(project.getCategoryCode());
        resp.setCategoryName(project.getCategoryName());
        resp.setBudgetAmount(project.getBudgetAmount());
        resp.setCurrency(project.getCurrency());
        resp.setPlanStartDate(project.getPlanStartDate());
        resp.setPlanEndDate(project.getPlanEndDate());
        resp.setSourcingMethod(project.getSourcingMethod());
        resp.setSingleSourceReason(project.getSingleSourceReason());
        resp.setInitiator(project.getInitiator());
        resp.setApplyDate(project.getApplyDate());
        resp.setStatus(project.getStatus());
        resp.setCreatedDate(project.getCreatedAt());

        List<SourcingCandidateDO> cands = candidateMapper.selectList(Wrappers.<SourcingCandidateDO>lambdaQuery()
                .eq(SourcingCandidateDO::getHeaderId, project.getId())
                .orderByAsc(SourcingCandidateDO::getSeq));
        resp.setCandidates(cands.stream().map(c -> {
            SourcingProjectResponse.CandidateEntry e = new SourcingProjectResponse.CandidateEntry();
            e.setId(c.getId());
            e.setSeq(c.getSeq());
            e.setSupplierCode(c.getSupplierCode());
            e.setSupplierName(c.getSupplierName());
            e.setMatchResult(c.getMatchResult());
            e.setCertStatus(c.getCertStatus());
            e.setPerfGrade(c.getPerfGrade());
            e.setRemark(c.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
