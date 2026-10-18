package com.example.procurement.sourcing.service.contract;

import com.example.procurement.sourcing.dto.resp.contract.ContractResponse;

import java.util.List;

public interface ContractService {
    List<ContractResponse> list(String status, String keyword);

    ContractResponse get(Long id);
}
