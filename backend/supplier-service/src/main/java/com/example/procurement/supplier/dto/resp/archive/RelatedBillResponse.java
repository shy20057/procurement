package com.example.procurement.supplier.dto.resp.archive;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatedBillResponse {
    private String billType;
    private String billNo;
    private String status;
    private LocalDate billDate;
}
