package com.example.procurement.sourcing.controller.tender;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.tender.TenderResponse;
import com.example.procurement.sourcing.service.tender.TenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/tenders")
@RequiredArgsConstructor
public class TenderController {

    private final TenderService tenderService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<TenderResponse> list = tenderService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<TenderResponse> get(@PathVariable Long id) {
        return Result.success(tenderService.get(id));
    }
}
