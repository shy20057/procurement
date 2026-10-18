package com.example.procurement.supplier.controller.certification;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.certification.CreateCertRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import com.example.procurement.supplier.service.certification.CertificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/certifications")
@RequiredArgsConstructor
public class CertificationController {

    private final CertificationService certificationService;

    @GetMapping
    public Result<List<CertResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String conclusion) {
        return Result.success(certificationService.list(status, supplierName, conclusion));
    }

    @GetMapping("/{id}")
    public Result<CertResponse> get(@PathVariable Long id) {
        return Result.success(certificationService.get(id));
    }

    @PostMapping
    public Result<CertResponse> create(@Valid @RequestBody CreateCertRequest request) {
        return Result.success(certificationService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        certificationService.submit(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<CertResponse> approve(@PathVariable Long id) {
        return Result.success(certificationService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        certificationService.reject(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/unapprove")
    public Result<Void> unapprove(@PathVariable Long id) {
        certificationService.unapprove(id);
        return Result.success();
    }
}
