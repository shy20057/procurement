package com.example.procurement.supplier.controller.blacklist;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import com.example.procurement.supplier.service.blacklist.BlacklistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/blacklists")
@RequiredArgsConstructor
public class BlacklistController {

    private final BlacklistService blacklistService;

    @GetMapping
    public Result<List<BlacklistResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierCode,
            @RequestParam(required = false) String triggerSource) {
        return Result.success(blacklistService.list(status, supplierCode, triggerSource));
    }

    @GetMapping("/{id}")
    public Result<BlacklistResponse> get(@PathVariable Long id) {
        return Result.success(blacklistService.get(id));
    }

    @PostMapping
    public Result<BlacklistResponse> create(@Valid @RequestBody CreateBlacklistRequest request) {
        return Result.success(blacklistService.create(request));
    }

    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        blacklistService.submit(id);
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    public Result<BlacklistResponse> approve(@PathVariable Long id) {
        return Result.success(blacklistService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        blacklistService.reject(id, request);
        return Result.success();
    }

    @PostMapping("/{id}/unapprove")
    public Result<Void> unapprove(@PathVariable Long id) {
        blacklistService.unapprove(id);
        return Result.success();
    }
}
