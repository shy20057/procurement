package com.example.procurement.integration.purchase;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderSnapshot(
        @JsonSerialize(using = ToStringSerializer.class) long sourceId,
        String orderNo, String title, String supplierCode, String purchasingOrg,
        LocalDate orderDate, String currency, String status, Instant updatedAt, List<Line> lines) {

    public record Line(
            String lineNo, String materialCode, String materialName,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal quantity, String unit,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal unitPrice,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount,
            LocalDate requiredDate, String status) {}
}
