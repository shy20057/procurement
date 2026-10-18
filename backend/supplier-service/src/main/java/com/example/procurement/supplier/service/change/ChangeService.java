package com.example.procurement.supplier.service.change;

import com.example.procurement.supplier.dto.req.change.CreateChangeRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.change.ChangeResponse;
import java.util.List;

public interface ChangeService {
    List<ChangeResponse> list(String status, String supplierCode, String changeType);
    ChangeResponse get(Long id);
    ChangeResponse create(CreateChangeRequest request);
    void submit(Long id);
    ChangeResponse approve(Long id);
    void reject(Long id, RejectRequest request);
    void unapprove(Long id);
}
