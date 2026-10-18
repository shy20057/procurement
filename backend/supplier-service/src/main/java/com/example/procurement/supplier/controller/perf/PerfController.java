package com.example.procurement.supplier.controller.perf;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.perf.CreatePerfRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import com.example.procurement.supplier.dto.resp.perf.PerfResponse;
import com.example.procurement.supplier.service.perf.PerfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/performance-evaluations")
@RequiredArgsConstructor
public class PerfController {

    private final PerfService perfService;

    @GetMapping
    public Result<List<PerfResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String grade) {
        return Result.success(perfService.list(status, supplierCode, period, grade));
    }

    @GetMapping("/{id}")
    public Result<PerfResponse> get(@PathVariable Long id) {
        return Result.success(perfService.get(id));
    }

    @PostMapping
    public Result<PerfResponse> create(@Valid @RequestBody CreatePerfRequest request) {
        return Result.success(perfService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        perfService.submit(id);
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    public Result<PerfResponse> confirm(@PathVariable Long id) {
        return Result.success(perfService.confirm(id));
    }

    @PostMapping("/{id}/push-to-freeze")
    public Result<FreezeResponse> pushToFreeze(@PathVariable Long id, @Valid @RequestBody CreateFreezeRequest request) {
        return Result.success(perfService.pushToFreeze(id, request));
    }

    @PostMapping("/{id}/push-to-blacklist")
    public Result<BlacklistResponse> pushToBlacklist(@PathVariable Long id, @Valid @RequestBody CreateBlacklistRequest request) {
        return Result.success(perfService.pushToBlacklist(id, request));
    }
}
