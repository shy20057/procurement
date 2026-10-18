package com.example.procurement.supplier.controller.registration;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.registration.CreateRegRequest;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import com.example.procurement.supplier.service.registration.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @GetMapping
    public Result<List<RegResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String sourceType) {
        return Result.success(registrationService.list(status, supplierName, sourceType));
    }

    @GetMapping("/{id}")
    public Result<RegResponse> get(@PathVariable Long id) {
        return Result.success(registrationService.get(id));
    }

    @PostMapping
    public Result<RegResponse> create(@Valid @RequestBody CreateRegRequest request) {
        return Result.success(registrationService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        registrationService.submit(id);
        return Result.success();
    }

    @PostMapping("/{id}/push-to-certification")
    public Result<CertResponse> pushToCert(@PathVariable Long id) {
        return Result.success(registrationService.pushToCert(id));
    }
}
