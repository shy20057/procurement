package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/freeze-requests")
public interface FreezeRemoteClient {

    @GetMapping
    Result<List<FreezeResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String applyType);

    @GetMapping("/{id}")
    Result<FreezeResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<FreezeResponse> create(@RequestBody CreateFreezeRequest request);

    @PostMapping("/{id}/submit")
    Result<Void> submit(@PathVariable("id") Long id);

    @PostMapping("/{id}/approve")
    Result<FreezeResponse> approve(@PathVariable("id") Long id);

    @PostMapping("/{id}/reject")
    Result<Void> reject(@PathVariable("id") Long id, @RequestBody RejectRequest request);

    @PostMapping("/{id}/unapprove")
    Result<Void> unapprove(@PathVariable("id") Long id);
}
