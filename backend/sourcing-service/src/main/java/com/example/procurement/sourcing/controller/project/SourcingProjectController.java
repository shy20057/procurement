package com.example.procurement.sourcing.controller.project;

import com.example.procurement.sourcing.common.result.Result;
import com.example.procurement.sourcing.dto.req.project.CreateSourcingProjectRequest;
import com.example.procurement.sourcing.dto.resp.project.SourcingProjectResponse;
import com.example.procurement.sourcing.service.project.SourcingProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stc/sourcing-projects")
@RequiredArgsConstructor
public class SourcingProjectController {

    private final SourcingProjectService sourcingProjectService;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<SourcingProjectResponse> list = sourcingProjectService.list(status, keyword);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result<SourcingProjectResponse> get(@PathVariable Long id) {
        return Result.success(sourcingProjectService.get(id));
    }

    @PostMapping
    public Result<SourcingProjectResponse> create(@Valid @RequestBody CreateSourcingProjectRequest request) {
        return Result.success(sourcingProjectService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<SourcingProjectResponse> submit(@PathVariable Long id) {
        return Result.success(sourcingProjectService.submit(id));
    }

    @PostMapping("/{id}/approve")
    public Result<SourcingProjectResponse> approve(@PathVariable Long id) {
        return Result.success(sourcingProjectService.approve(id));
    }
}
