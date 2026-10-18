package com.example.procurement.supplier.controller.invitation;

import com.example.procurement.supplier.common.result.Result;
import com.example.procurement.supplier.dto.req.invitation.CreateInviteRequest;
import com.example.procurement.supplier.dto.resp.invitation.InviteResponse;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import com.example.procurement.supplier.service.invitation.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/srm/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping
    public Result<List<InviteResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inviteeName) {
        return Result.success(invitationService.list(status, inviteeName));
    }

    @GetMapping("/{id}")
    public Result<InviteResponse> get(@PathVariable Long id) {
        return Result.success(invitationService.get(id));
    }

    @PostMapping
    public Result<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request) {
        InviteResponse response = invitationService.create(request);
        return Result.success(response);
    }

    @PostMapping("/{id}/decline")
    public Result<Void> decline(@PathVariable Long id) {
        invitationService.decline(id);
        return Result.success();
    }

    @PostMapping("/{id}/push-to-registration")
    public Result<RegResponse> pushToReg(@PathVariable Long id) {
        return Result.success(invitationService.pushToReg(id));
    }
}
