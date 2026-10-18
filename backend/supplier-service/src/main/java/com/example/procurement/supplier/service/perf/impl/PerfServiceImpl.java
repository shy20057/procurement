package com.example.procurement.supplier.service.perf.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.supplier.common.errorcode.SrmErrorCode;
import com.example.procurement.supplier.common.exception.BusinessException;
import com.example.procurement.supplier.common.exception.NotFoundException;
import com.example.procurement.supplier.dao.entity.archive.SupplierArchiveDO;
import com.example.procurement.supplier.dao.entity.blacklist.SupplierBlacklistDO;
import com.example.procurement.supplier.dao.entity.blacklist.SupplierBlacklistEDO;
import com.example.procurement.supplier.dao.entity.freeze.SupplierFreezeDO;
import com.example.procurement.supplier.dao.entity.freeze.SupplierFreezeEDO;
import com.example.procurement.supplier.dao.entity.perf.SupplierPerfDO;
import com.example.procurement.supplier.dao.entity.perf.SupplierPerfEDO;
import com.example.procurement.supplier.dao.mapper.archive.SupplierArchiveMapper;
import com.example.procurement.supplier.dao.mapper.blacklist.SupplierBlacklistEMapper;
import com.example.procurement.supplier.dao.mapper.blacklist.SupplierBlacklistMapper;
import com.example.procurement.supplier.dao.mapper.freeze.SupplierFreezeEMapper;
import com.example.procurement.supplier.dao.mapper.freeze.SupplierFreezeMapper;
import com.example.procurement.supplier.dao.mapper.perf.SupplierPerfEMapper;
import com.example.procurement.supplier.dao.mapper.perf.SupplierPerfMapper;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.perf.CreatePerfRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import com.example.procurement.supplier.dto.resp.perf.PerfResponse;
import com.example.procurement.supplier.service.perf.PerfService;
import com.example.procurement.supplier.util.BillNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerfServiceImpl implements PerfService {

    private final SupplierPerfMapper perfMapper;
    private final SupplierPerfEMapper perfEMapper;
    private final SupplierArchiveMapper archiveMapper;
    private final SupplierFreezeMapper freezeMapper;
    private final SupplierFreezeEMapper freezeEMapper;
    private final SupplierBlacklistMapper blacklistMapper;
    private final SupplierBlacklistEMapper blacklistEMapper;
    private final BillNoGenerator billNoGenerator;

    @Override
    public List<PerfResponse> list(String status, String supplierCode, String period, String grade) {
        LambdaQueryWrapper<SupplierPerfDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(SupplierPerfDO::getStatus, status);
        if (supplierCode != null && !supplierCode.isBlank()) wrapper.eq(SupplierPerfDO::getSupplierCode, supplierCode);
        if (period != null && !period.isBlank()) wrapper.eq(SupplierPerfDO::getPeriod, period);
        if (grade != null && !grade.isBlank()) wrapper.eq(SupplierPerfDO::getGrade, grade);
        wrapper.orderByDesc(SupplierPerfDO::getCreatedAt);
        return perfMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public PerfResponse get(Long id) {
        SupplierPerfDO perf = perfMapper.selectById(id);
        if (perf == null) throw new NotFoundException(SrmErrorCode.PERF_NOT_FOUND);
        return toResponse(perf);
    }

    @Override
    @Transactional
    public PerfResponse create(CreatePerfRequest request) {
        SupplierPerfDO perf = new SupplierPerfDO();
        perf.setBillNo(billNoGenerator.generatePerf());
        perf.setSupplierCode(request.getSupplierCode());
        perf.setPeriod(request.getPeriod());
        perf.setEvalType(request.getEvalType());
        perf.setEvaluator(request.getEvaluator());
        perf.setEvalDate(LocalDate.now());
        perf.setStatus("DRAFT");
        perf.setCreatedAt(Instant.now());
        perf.setUpdatedAt(Instant.now());
        perfMapper.insert(perf);

        BigDecimal total = BigDecimal.ZERO;
        if (request.getEntries() != null) {
            int seq = 1;
            for (var e : request.getEntries()) {
                SupplierPerfEDO entry = new SupplierPerfEDO();
                entry.setHeaderId(perf.getId());
                entry.setSeq(e.getSeq() != null ? e.getSeq() : seq++);
                entry.setDimension(e.getDimension());
                entry.setWeight(e.getWeight());
                entry.setScore(e.getScore());
                entry.setSourceBillNo(e.getSourceBillNo());
                entry.setRemark(e.getRemark());
                entry.setCreatedAt(Instant.now());
                entry.setUpdatedAt(Instant.now());
                perfEMapper.insert(entry);
                if (e.getWeight() != null && e.getScore() != null) {
                    total = total.add(e.getWeight().multiply(e.getScore()).divide(BigDecimal.valueOf(100)));
                }
            }
        }
        perf.setTotalScore(total);
        if (total.compareTo(new BigDecimal("85")) >= 0) perf.setGrade("A");
        else if (total.compareTo(new BigDecimal("60")) >= 0) perf.setGrade("B");
        else perf.setGrade("C");
        perf.setUpdatedAt(Instant.now());
        perfMapper.updateById(perf);
        return toResponse(perf);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SupplierPerfDO perf = perfMapper.selectById(id);
        if (perf == null) throw new NotFoundException(SrmErrorCode.PERF_NOT_FOUND);
        if (!"DRAFT".equals(perf.getStatus())) throw new BusinessException(SrmErrorCode.PERF_NOT_DRAFT);
        perf.setStatus("SUBMITTED");
        perf.setUpdatedAt(Instant.now());
        perfMapper.updateById(perf);
    }

    @Override
    @Transactional
    public PerfResponse confirm(Long id) {
        SupplierPerfDO perf = perfMapper.selectById(id);
        if (perf == null) throw new NotFoundException(SrmErrorCode.PERF_NOT_FOUND);
        if (!"SUBMITTED".equals(perf.getStatus())) throw new BusinessException(SrmErrorCode.PERF_NOT_SUBMITTED);
        perf.setStatus("CONFIRMED");
        perf.setUpdatedAt(Instant.now());
        perfMapper.updateById(perf);

        SupplierArchiveDO archive = archiveMapper.selectOne(Wrappers.<SupplierArchiveDO>lambdaQuery()
                .eq(SupplierArchiveDO::getSupplierCode, perf.getSupplierCode()));
        if (archive != null) {
            archive.setPerfGrade(perf.getGrade());
            archive.setPerfEvalCount(archive.getPerfEvalCount() != null ? archive.getPerfEvalCount() + 1 : 1);
            archive.setLastPerfDate(perf.getEvalDate());
            archive.setUpdatedAt(Instant.now());
            archiveMapper.updateById(archive);
        }
        return toResponse(perf);
    }

    @Override
    @Transactional
    public FreezeResponse pushToFreeze(Long id, CreateFreezeRequest request) {
        SupplierPerfDO perf = perfMapper.selectById(id);
        if (perf == null) throw new NotFoundException(SrmErrorCode.PERF_NOT_FOUND);
        SupplierFreezeDO freeze = new SupplierFreezeDO();
        freeze.setBillNo(billNoGenerator.generateFreeze());
        freeze.setSupplierCode(perf.getSupplierCode());
        freeze.setApplyType("FREEZE");
        freeze.setTriggerSource("PERF_SEVERE");
        freeze.setSourcePerfBillNo(perf.getBillNo());
        freeze.setReason("绩效不合格自动触发");
        freeze.setApplicant(request.getApplicant());
        freeze.setApplyDate(LocalDate.now());
        freeze.setStatus("DRAFT");
        freeze.setCreatedAt(Instant.now());
        freeze.setUpdatedAt(Instant.now());
        freezeMapper.insert(freeze);

        FreezeResponse resp = new FreezeResponse();
        resp.setId(freeze.getId()); resp.setBillNo(freeze.getBillNo());
        resp.setSupplierCode(freeze.getSupplierCode()); resp.setApplyType(freeze.getApplyType());
        resp.setTriggerSource(freeze.getTriggerSource()); resp.setSourcePerfBillNo(freeze.getSourcePerfBillNo());
        resp.setReason(freeze.getReason()); resp.setApplicant(freeze.getApplicant());
        resp.setApplyDate(freeze.getApplyDate()); resp.setStatus(freeze.getStatus());
        resp.setCreatedAt(freeze.getCreatedAt()); resp.setEntries(List.of());
        return resp;
    }

    @Override
    @Transactional
    public BlacklistResponse pushToBlacklist(Long id, CreateBlacklistRequest request) {
        SupplierPerfDO perf = perfMapper.selectById(id);
        if (perf == null) throw new NotFoundException(SrmErrorCode.PERF_NOT_FOUND);
        SupplierBlacklistDO blacklist = new SupplierBlacklistDO();
        blacklist.setBillNo(billNoGenerator.generateBlacklist());
        blacklist.setSupplierCode(perf.getSupplierCode());
        blacklist.setTriggerSource("PERF_SEVERE");
        blacklist.setSourcePerfBillNo(perf.getBillNo());
        blacklist.setReason(request.getReason());
        blacklist.setApplicant(request.getApplicant());
        blacklist.setIsPermanent(true);
        blacklist.setApplyDate(LocalDate.now());
        blacklist.setStatus("DRAFT");
        blacklist.setCreatedAt(Instant.now());
        blacklist.setUpdatedAt(Instant.now());
        blacklistMapper.insert(blacklist);

        BlacklistResponse resp = new BlacklistResponse();
        resp.setId(blacklist.getId()); resp.setBillNo(blacklist.getBillNo());
        resp.setSupplierCode(blacklist.getSupplierCode()); resp.setTriggerSource(blacklist.getTriggerSource());
        resp.setSourcePerfBillNo(blacklist.getSourcePerfBillNo()); resp.setReason(blacklist.getReason());
        resp.setApplicant(blacklist.getApplicant()); resp.setApplyDate(blacklist.getApplyDate());
        resp.setIsPermanent(blacklist.getIsPermanent()); resp.setStatus(blacklist.getStatus());
        resp.setCreatedAt(blacklist.getCreatedAt()); resp.setEntries(List.of());
        return resp;
    }

    private PerfResponse toResponse(SupplierPerfDO perf) {
        PerfResponse resp = new PerfResponse();
        resp.setId(perf.getId()); resp.setBillNo(perf.getBillNo());
        resp.setSupplierCode(perf.getSupplierCode()); resp.setPeriod(perf.getPeriod());
        resp.setEvalType(perf.getEvalType()); resp.setTotalScore(perf.getTotalScore());
        resp.setGrade(perf.getGrade()); resp.setStatus(perf.getStatus());
        resp.setEvaluator(perf.getEvaluator()); resp.setEvalDate(perf.getEvalDate());
        resp.setCreatedAt(perf.getCreatedAt());

        List<SupplierPerfEDO> entries = perfEMapper.selectList(Wrappers.<SupplierPerfEDO>lambdaQuery()
                .eq(SupplierPerfEDO::getHeaderId, perf.getId()));
        resp.setEntries(entries.stream().map(e -> {
            PerfResponse.PerfDimensionEntry entry = new PerfResponse.PerfDimensionEntry();
            entry.setSeq(e.getSeq()); entry.setDimension(e.getDimension());
            entry.setWeight(e.getWeight()); entry.setScore(e.getScore());
            entry.setSourceBillNo(e.getSourceBillNo()); entry.setRemark(e.getRemark());
            return entry;
        }).collect(Collectors.toList()));
        return resp;
    }
}
