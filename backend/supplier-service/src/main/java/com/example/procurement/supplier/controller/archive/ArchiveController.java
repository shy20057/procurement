package com.example.procurement.supplier.controller.archive;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.archive.UpdateArchiveRequest;
import com.example.procurement.supplier.dto.resp.archive.ArchiveResponse;
import com.example.procurement.supplier.dto.resp.archive.ExpiringCertResponse;
import com.example.procurement.supplier.dto.resp.archive.RelatedBillResponse;
import com.example.procurement.supplier.service.archive.ArchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/archives")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/{supplierCode}")
    public Result<ArchiveResponse> get(@PathVariable String supplierCode) {
        return Result.success(archiveService.getByCode(supplierCode));
    }

    @GetMapping
    public Result<List<ArchiveResponse>> list() {
        return Result.success(archiveService.list());
    }

    @PutMapping("/{supplierCode}")
    public Result<Void> update(@PathVariable String supplierCode, @Valid @RequestBody UpdateArchiveRequest request) {
        archiveService.update(supplierCode, request);
        return Result.success();
    }

    @GetMapping("/{supplierCode}/related-bills")
    public Result<List<RelatedBillResponse>> getRelatedBills(
            @PathVariable String supplierCode,
            @RequestParam(required = false) String billType) {
        return Result.success(archiveService.getRelatedBills(supplierCode, billType));
    }

    @GetMapping("/expiring-certs")
    public Result<List<ExpiringCertResponse>> getExpiringCerts(@RequestParam(defaultValue = "30") int days) {
        return Result.success(archiveService.getExpiringCerts(days));
    }
}
