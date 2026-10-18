package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.archive.UpdateArchiveRequest;
import com.example.procurement.supplier.dto.resp.archive.ArchiveResponse;
import com.example.procurement.supplier.dto.resp.archive.ExpiringCertResponse;
import com.example.procurement.supplier.dto.resp.archive.RelatedBillResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/archives")
public interface ArchiveRemoteClient {

    @GetMapping("/{supplierCode}")
    Result<ArchiveResponse> get(@PathVariable("supplierCode") String supplierCode);

    @GetMapping
    Result<List<ArchiveResponse>> list();

    @PutMapping("/{supplierCode}")
    Result<Void> update(@PathVariable("supplierCode") String supplierCode, @RequestBody UpdateArchiveRequest request);

    @GetMapping("/{supplierCode}/related-bills")
    Result<List<RelatedBillResponse>> getRelatedBills(
            @PathVariable("supplierCode") String supplierCode,
            @RequestParam(required = false) String billType);

    @GetMapping("/expiring-certs")
    Result<List<ExpiringCertResponse>> getExpiringCerts(@RequestParam(defaultValue = "30") int days);
}
