package com.example.procurement.sourcing.controller.ratio;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.ratio.SupplierRatioResponse;
import com.example.procurement.sourcing.service.ratio.SupplierRatioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/supplier-ratios")
@RequiredArgsConstructor
public class SupplierRatioController {

    private final SupplierRatioService supplierRatioService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<SupplierRatioResponse> list = supplierRatioService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<SupplierRatioResponse> get(@PathVariable Long id) {
        return Result.success(supplierRatioService.get(id));
    }
}
