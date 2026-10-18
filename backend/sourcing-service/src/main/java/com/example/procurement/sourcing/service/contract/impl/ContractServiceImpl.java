package com.example.procurement.sourcing.service.contract.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.procurement.sourcing.common.errorcode.StcErrorCode;
import com.example.procurement.sourcing.common.exception.NotFoundException;
import com.example.procurement.sourcing.dao.entity.contract.ContractClauseDO;
import com.example.procurement.sourcing.dao.entity.contract.ContractDO;
import com.example.procurement.sourcing.dao.mapper.contract.ContractClauseMapper;
import com.example.procurement.sourcing.dao.mapper.contract.ContractMapper;
import com.example.procurement.sourcing.dto.resp.contract.ContractResponse;
import com.example.procurement.sourcing.service.contract.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ContractMapper contractMapper;
    private final ContractClauseMapper clauseMapper;

    @Override
    public List<ContractResponse> list(String status, String keyword) {
        LambdaQueryWrapper<ContractDO> wrapper = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) wrapper.eq(ContractDO::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ContractDO::getContractName, keyword)
                    .or().like(ContractDO::getBillNo, keyword));
        }
        wrapper.orderByDesc(ContractDO::getCreatedAt);
        return contractMapper.selectList(wrapper).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ContractResponse get(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) throw new NotFoundException(StcErrorCode.CONTRACT_NOT_FOUND);
        return toResponse(contract);
    }

    private ContractResponse toResponse(ContractDO contract) {
        ContractResponse resp = new ContractResponse();
        resp.setId(contract.getId());
        resp.setBillNo(contract.getBillNo());
        resp.setSourceAwardBillNo(contract.getSourceAwardBillNo());
        resp.setSupplierCode(contract.getSupplierCode());
        resp.setSupplierName(contract.getSupplierName());
        resp.setContractName(contract.getContractName());
        resp.setContractAmount(contract.getContractAmount());
        resp.setCurrency(contract.getCurrency());
        resp.setValidFrom(contract.getValidFrom());
        resp.setValidTo(contract.getValidTo());
        resp.setContractType(contract.getContractType());
        resp.setSignatory(contract.getSignatory());
        resp.setLegalReviewStatus(contract.getLegalReviewStatus());
        resp.setSealStatus(contract.getSealStatus());
        resp.setStatus(contract.getStatus());

        List<ContractClauseDO> clauses = clauseMapper.selectList(Wrappers.<ContractClauseDO>lambdaQuery()
                .eq(ContractClauseDO::getHeaderId, contract.getId())
                .orderByAsc(ContractClauseDO::getSeq));
        resp.setClauses(clauses.stream().map(c -> {
            ContractResponse.ClauseEntry e = new ContractResponse.ClauseEntry();
            e.setId(c.getId());
            e.setSeq(c.getSeq());
            e.setClauseType(c.getClauseType());
            e.setCategoryCode(c.getCategoryCode());
            e.setItemName(c.getItemName());
            e.setQty(c.getQty());
            e.setUnitPrice(c.getUnitPrice());
            e.setRemark(c.getRemark());
            return e;
        }).collect(Collectors.toList()));
        return resp;
    }
}
