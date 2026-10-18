package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.change.CreateChangeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.change.ChangeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/change-requests")
public interface ChangeRemoteClient {

    @GetMapping
    Result<List<ChangeResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String changeType);

    @GetMapping("/{id}")
    Result<ChangeResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<ChangeResponse> create(@RequestBody CreateChangeRequest request);

    @PostMapping("/{id}/submit")
    Result<Void> submit(@PathVariable("id") Long id);

    @PostMapping("/{id}/approve")
    Result<ChangeResponse> approve(@PathVariable("id") Long id);

    @PostMapping("/{id}/reject")
    Result<Void> reject(@PathVariable("id") Long id, @RequestBody RejectRequest request);

    @PostMapping("/{id}/unapprove")
    Result<Void> unapprove(@PathVariable("id") Long id);
}
