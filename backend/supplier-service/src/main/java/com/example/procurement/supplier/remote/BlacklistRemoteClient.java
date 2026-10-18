package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/blacklists")
public interface BlacklistRemoteClient {

    @GetMapping
    Result<List<BlacklistResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String triggerSource);

    @GetMapping("/{id}")
    Result<BlacklistResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<BlacklistResponse> create(@RequestBody CreateBlacklistRequest request);

    @PostMapping("/{id}/submit")
    Result<Void> submit(@PathVariable("id") Long id);

    @PostMapping("/{id}/approve")
    Result<BlacklistResponse> approve(@PathVariable("id") Long id);

    @PostMapping("/{id}/reject")
    Result<Void> reject(@PathVariable("id") Long id, @RequestBody RejectRequest request);

    @PostMapping("/{id}/unapprove")
    Result<Void> unapprove(@PathVariable("id") Long id);
}
