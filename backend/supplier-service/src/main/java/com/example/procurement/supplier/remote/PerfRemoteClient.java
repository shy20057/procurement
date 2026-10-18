package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.perf.CreatePerfRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import com.example.procurement.supplier.dto.resp.perf.PerfResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/performance-evaluations")
public interface PerfRemoteClient {

    @GetMapping
    Result<List<PerfResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String grade);

    @GetMapping("/{id}")
    Result<PerfResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<PerfResponse> create(@RequestBody CreatePerfRequest request);

    @PostMapping("/{id}/submit")
    Result<Void> submit(@PathVariable("id") Long id);

    @PostMapping("/{id}/confirm")
    Result<PerfResponse> confirm(@PathVariable("id") Long id);

    @PostMapping("/{id}/push-to-freeze")
    Result<FreezeResponse> pushToFreeze(@PathVariable("id") Long id, @RequestBody CreateFreezeRequest request);

    @PostMapping("/{id}/push-to-blacklist")
    Result<BlacklistResponse> pushToBlacklist(@PathVariable("id") Long id, @RequestBody CreateBlacklistRequest request);
}
