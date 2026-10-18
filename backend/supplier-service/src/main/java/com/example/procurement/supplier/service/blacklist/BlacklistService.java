package com.example.procurement.supplier.service.blacklist;

import com.example.procurement.supplier.dto.req.blacklist.CreateBlacklistRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.blacklist.BlacklistResponse;
import java.util.List;

public interface BlacklistService {
    List<BlacklistResponse> list(String status, String supplierCode, String triggerSource);
    BlacklistResponse get(Long id);
    BlacklistResponse create(CreateBlacklistRequest request);
    void submit(Long id);
    BlacklistResponse approve(Long id);
    void reject(Long id, RejectRequest request);
    void unapprove(Long id);
}
