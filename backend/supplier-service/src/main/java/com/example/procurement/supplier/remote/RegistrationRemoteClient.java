package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.registration.CreateRegRequest;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/registrations")
public interface RegistrationRemoteClient {

    @GetMapping
    Result<List<RegResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String sourceType);

    @GetMapping("/{id}")
    Result<RegResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<RegResponse> create(@RequestBody CreateRegRequest request);

    @PostMapping("/{id}/submit")
    Result<Void> submit(@PathVariable("id") Long id);

    @PostMapping("/{id}/push-to-certification")
    Result<CertResponse> pushToCert(@PathVariable("id") Long id);
}
