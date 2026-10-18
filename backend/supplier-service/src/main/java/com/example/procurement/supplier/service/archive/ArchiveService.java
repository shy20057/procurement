package com.example.procurement.supplier.service.archive;

import com.example.procurement.supplier.dto.req.archive.UpdateArchiveRequest;
import com.example.procurement.supplier.dto.resp.archive.ArchiveResponse;
import com.example.procurement.supplier.dto.resp.archive.ExpiringCertResponse;
import com.example.procurement.supplier.dto.resp.archive.RelatedBillResponse;
import java.util.List;

public interface ArchiveService {
    ArchiveResponse getByCode(String supplierCode);
    List<ArchiveResponse> list();
    void update(String supplierCode, UpdateArchiveRequest request);
    List<RelatedBillResponse> getRelatedBills(String supplierCode, String billType);
    List<ExpiringCertResponse> getExpiringCerts(int days);
}
