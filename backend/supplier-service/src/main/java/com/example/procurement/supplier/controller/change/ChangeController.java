package com.example.procurement.supplier.controller.change;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.change.CreateChangeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.change.ChangeResponse;
import com.example.procurement.supplier.service.change.ChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/change-requests")
@RequiredArgsConstructor
public class ChangeController {

    private final ChangeService changeService;

    @GetMapping
    public Result<List<ChangeResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String changeType) {
        return Result.success(changeService.list(status, supplierCode, changeType));
    }

    @GetMapping("/{id}")
    public Result<ChangeResponse> get(@PathVariable Long id) {
        return Result.success(changeService.get(id));
    }

    @PostMapping
    public Result<ChangeResponse> create(@Valid @RequestBody CreateChangeRequest request) {
        return Result.success(changeService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        changeService.submit(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<ChangeResponse> approve(@PathVariable Long id) {
        return Result.success(changeService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        changeService.reject(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/unapprove")
    public Result<Void> unapprove(@PathVariable Long id) {
        changeService.unapprove(id);
        return Result.success();
    }
}
