package com.example.procurement.supplier.service.freeze;

import com.example.procurement.supplier.dto.req.freeze.CreateFreezeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.freeze.FreezeResponse;
import java.util.List;

public interface FreezeService {
    List<FreezeResponse> list(String status, String supplierCode, String applyType);
    FreezeResponse get(Long id);
    FreezeResponse create(CreateFreezeRequest request);
    void submit(Long id);
    FreezeResponse approve(Long id);
    void reject(Long id, RejectRequest request);
    void unapprove(Long id);
}
