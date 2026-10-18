package com.example.procurement.supplier.dto.resp.archive;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ExpiringCertResponse {
    private String supplierCode;
    private String supplierName;
    private String certType;
    private LocalDate validTo;
}
