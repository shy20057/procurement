package com.example.procurement.supplier.dto.req.archive;

import lombok.Data;

@Data
public class UpdateArchiveRequest {
    private String supplierType;
    private String bankName;
    private String bankAccount;
    private String bankAccountName;
}
