package com.example.procurement.integration.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PurchaseOrderStoreTest {
    private final PurchaseOrderStore store =
            new PurchaseOrderStore(new NamedParameterJdbcTemplate(new JdbcTemplate()));

    @Test
    void hashDoesNotChangeWithLineOrderNumericScaleOrVersionTime() {
        var first = order("2026-11-06T02:00:00Z",
                line("00010", "1.00", "ACTIVE"), line("00020", "2", "ACTIVE"));
        var replay = order("2026-11-06T03:00:00Z",
                line("00020", "2.000", "ACTIVE"), line("00010", "1", "ACTIVE"));

        assertEquals(store.hash(first), store.hash(replay));
    }

    @Test
    void hashChangesWhenAnOrderLineIsCancelled() {
        var active = order("2026-11-06T02:00:00Z", line("00010", "1", "ACTIVE"));
        var cancelled = order("2026-11-06T02:00:00Z", line("00010", "1", "CANCELLED"));

        assertNotEquals(store.hash(active), store.hash(cancelled));
    }

    private static PurchaseOrderSnapshot order(String time, PurchaseOrderSnapshot.Line... lines) {
        return new PurchaseOrderSnapshot(7, "0000007000", "备件", "SUP-DEMO-001", "ORG01",
                LocalDate.of(2026, 11, 6), "CNY", "PUBLISHED", Instant.parse(time), List.of(lines));
    }

    private static PurchaseOrderSnapshot.Line line(String lineNo, String quantity, String status) {
        return new PurchaseOrderSnapshot.Line(lineNo, "MAT01", "配件", new BigDecimal(quantity),
                "EA", BigDecimal.TEN, new BigDecimal(quantity).multiply(BigDecimal.TEN),
                LocalDate.of(2026, 11, 15), status);
    }
}
