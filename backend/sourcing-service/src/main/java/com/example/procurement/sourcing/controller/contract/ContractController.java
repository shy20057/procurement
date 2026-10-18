package com.example.procurement.sourcing.controller.contract;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.contract.ContractResponse;
import com.example.procurement.sourcing.service.contract.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<ContractResponse> list = contractService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<ContractResponse> get(@PathVariable Long id) {
        return Result.success(contractService.get(id));
    }
}
