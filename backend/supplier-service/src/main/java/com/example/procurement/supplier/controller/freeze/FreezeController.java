package com.example.procurement.supplier.controller.freeze;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import com.example.procurement.supplier.service.freeze.FreezeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/freeze-requests")
@RequiredArgsConstructor
public class FreezeController {

    private final FreezeService freezeService;

    @GetMapping
    public Result<List<FreezeResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String applyType) {
        return Result.success(freezeService.list(status, supplierCode, applyType));
    }

    @GetMapping("/{id}")
    public Result<FreezeResponse> get(@PathVariable Long id) {
        return Result.success(freezeService.get(id));
    }

    @PostMapping
    public Result<FreezeResponse> create(@Valid @RequestBody CreateFreezeRequest request) {
        return Result.success(freezeService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        freezeService.submit(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<FreezeResponse> approve(@PathVariable Long id) {
        return Result.success(freezeService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        freezeService.reject(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/unapprove")
    public Result<Void> unapprove(@PathVariable Long id) {
        freezeService.unapprove(id);
        return Result.success();
    }
}
