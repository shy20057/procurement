package com.example.procurement.sourcing.controller.bpa;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.bpa.BpaResponse;
import com.example.procurement.sourcing.service.bpa.BpaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/bpa")
@RequiredArgsConstructor
public class BpaController {

    private final BpaService bpaService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<BpaResponse> list = bpaService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<BpaResponse> get(@PathVariable Long id) {
        return Result.success(bpaService.get(id));
    }
}
