package com.example.procurement.supplier.service.invitation;

import com.example.procurement.supplier.dto.req.invitation.CreateInviteRequest;
import com.example.procurement.supplier.dto.resp.invitation.InviteResponse;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import java.util.List;

public interface InvitationService {
    List<InviteResponse> list(String status, String inviteeName);
    InviteResponse get(Long id);
    InviteResponse create(CreateInviteRequest request);
    void decline(Long id);
    RegResponse pushToReg(Long id);
}
