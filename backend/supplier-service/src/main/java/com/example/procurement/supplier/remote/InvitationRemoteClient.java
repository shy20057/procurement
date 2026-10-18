package com.example.procurement.supplier.remote;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.invitation.CreateInviteRequest;
import com.example.procurement.supplier.dto.resp.invitation.InviteResponse;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "supplier-remote", path = "/api/srm/invitations")
public interface InvitationRemoteClient {

    @GetMapping
    Result<List<InviteResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inviteeName);

    @GetMapping("/{id}")
    Result<InviteResponse> get(@PathVariable("id") Long id);

    @PostMapping
    Result<InviteResponse> create(@RequestBody CreateInviteRequest request);

    @PostMapping("/{id}/decline")
    Result<Void> decline(@PathVariable("id") Long id);

    @PostMapping("/{id}/push-to-registration")
    Result<RegResponse> pushToReg(@PathVariable("id") Long id);
}
