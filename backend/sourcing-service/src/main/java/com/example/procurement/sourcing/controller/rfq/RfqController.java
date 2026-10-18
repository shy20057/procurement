package com.example.procurement.sourcing.controller.rfq;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.rfq.RfqResponse;
import com.example.procurement.sourcing.service.rfq.RfqService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/rfqs")
@RequiredArgsConstructor
public class RfqController {

    private final RfqService rfqService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<RfqResponse> list = rfqService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<RfqResponse> get(@PathVariable Long id) {
        return Result.success(rfqService.get(id));
    }
}
