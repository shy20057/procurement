package com.example.procurement.supplier.service.registration;

import com.example.procurement.supplier.dto.req.registration.CreateRegRequest;
import com.example.procurement.supplier.dto.resp.registration.RegResponse;
import com.example.procurement.supplier.dto.resp.certification.CertResponse;
import java.util.List;

public interface RegistrationService {
    List<RegResponse> list(String status, String supplierName, String sourceType);
    RegResponse get(Long id);
    RegResponse create(CreateRegRequest request);
    void submit(Long id);
    CertResponse pushToCert(Long id);
}
