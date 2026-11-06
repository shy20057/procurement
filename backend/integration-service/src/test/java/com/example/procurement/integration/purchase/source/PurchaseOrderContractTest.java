package com.example.procurement.integration.purchase.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseOrderContractTest {
    @Test
    void rejectsDuplicateLineNumbersBeforeSnapshotCanBeApplied() {
        var line = new PurchaseOrderSnapshot.Line("00010", "M-1", "阀门",
                new BigDecimal("2.0000"), "EA", new BigDecimal("3.00"),
                new BigDecimal("6.0000"), LocalDate.parse("2026-11-20"), "ACTIVE");
        var snapshot = new PurchaseOrderSnapshot(10L, "4500000010", "设备备件",
                "S-01", "P-01", LocalDate.parse("2026-11-06"), "CNY", "PUBLISHED",
                Instant.parse("2026-11-06T01:00:00.000001Z"), List.of(line, line));

        var problem = assertThrows(IntegrationProblem.class, () -> PurchaseOrderContract.validate(snapshot));
        assertEquals("SOURCE_INVALID_ORDER", problem.code);
    }

    @Test
    void rejectsSubMicrosecondSourceVersionsThatPostgresWouldRound() {
        var line = new PurchaseOrderSnapshot.Line("00010", "M-1", "阀门",
                new BigDecimal("2"), "EA", new BigDecimal("3"), new BigDecimal("6"),
                LocalDate.parse("2026-11-20"), "ACTIVE");
        var snapshot = new PurchaseOrderSnapshot(10L, "4500000010", "设备备件",
                "S-01", "P-01", LocalDate.parse("2026-11-06"), "CNY", "PUBLISHED",
                Instant.parse("2026-11-06T01:00:00.000001001Z"), List.of(line));

        var problem = assertThrows(IntegrationProblem.class, () -> PurchaseOrderContract.validate(snapshot));
        assertEquals("SOURCE_INVALID_ORDER", problem.code);
    }
}
