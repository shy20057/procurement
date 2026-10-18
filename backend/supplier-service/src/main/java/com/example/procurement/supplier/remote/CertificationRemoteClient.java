package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.certification.CreateCertRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/certifications")
public interface CertificationRemoteClient {

    @GetMapping
    Result<List<CertResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String conclusion);

    @GetMapping("/{id}")
    Result<CertResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<CertResponse> create(@RequestBody CreateCertRequest request);

    @PostMapping("/{id}/submit")
    Result<Void> submit(@PathVariable("id") Long id);

    @PostMapping("/{id}/approve")
    Result<CertResponse> approve(@PathVariable("id") Long id);

    @PostMapping("/{id}/reject")
    Result<Void> reject(@PathVariable("id") Long id, @RequestBody RejectRequest request);

    @PostMapping("/{id}/unapprove")
    Result<Void> unapprove(@PathVariable("id") Long id);
}
