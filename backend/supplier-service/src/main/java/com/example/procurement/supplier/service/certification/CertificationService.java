package com.example.procurement.supplier.service.certification;

import com.example.procurement.supplier.dto.req.certification.CreateCertRequest;
import com.example.procurement.supplier.dto.req.common.RejectRequest;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import java.util.List;

public interface CertificationService {
    List<CertResponse> list(String status, String supplierName, String conclusion);
    CertResponse get(Long id);
    CertResponse create(CreateCertRequest request);
    void submit(Long id);
    CertResponse approve(Long id);
    void reject(Long id, RejectRequest request);
    void unapprove(Long id);
}
