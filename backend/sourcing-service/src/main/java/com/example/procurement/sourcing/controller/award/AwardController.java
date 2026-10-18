package com.example.procurement.sourcing.controller.award;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.resp.award.AwardResponse;
import com.example.procurement.sourcing.service.award.AwardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/awards")
@RequiredArgsConstructor
public class AwardController {

    private final AwardService awardService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<AwardResponse> list = awardService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<AwardResponse> get(@PathVariable Long id) {
        return Result.success(awardService.get(id));
    }

    /** C3 风控守门：冻结/黑名单供应商将被阻断 */
    @PostMapping("/{id}/approve")
    public Result<AwardResponse> approve(@PathVariable Long id) {
        return Result.success(awardService.approve(id));
    }
}
