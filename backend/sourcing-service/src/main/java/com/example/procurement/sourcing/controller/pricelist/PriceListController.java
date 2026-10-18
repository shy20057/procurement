package com.example.procurement.sourcing.controller.pricelist;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.pricelist.PriceListResponse;
import com.example.procurement.sourcing.service.pricelist.PriceListService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/price-lists")
@RequiredArgsConstructor
public class PriceListController {

    private final PriceListService priceListService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<PriceListResponse> list = priceListService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<PriceListResponse> get(@PathVariable Long id) {
        return Result.success(priceListService.get(id));
    }
}
