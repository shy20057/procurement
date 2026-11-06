package com.example.procurement.integration.purchase;

import static org.junit.jupiter.api.Assertions.*;

import com.example.procurement.integration.exception.IntegrationProblem;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PurchaseOrderCheckpointTest {
    @Test
    void windowUsesSourceTimeAndPageCommitDoesNotAdvanceWatermark() {
        Instant completed = Instant.parse("2026-11-06T03:30:00Z");
        Instant sourceNow = Instant.parse("2026-11-06T03:35:00Z");
        var initial = PurchaseOrderCheckpoint.completed(completed);
        var window = initial.open(sourceNow);
        var cursor = new PurchaseOrderSource.Cursor(Instant.parse("2026-11-06T03:32:00Z"), 1258);
        var saved = window.advance(cursor);
        assertEquals(sourceNow, saved.windowEndAt());
        assertEquals(Instant.parse("2026-11-06T03:25:00Z"), saved.startAt(300));
        assertEquals(completed, saved.completedWatermark());
        assertEquals(cursor, saved.pageCursor());
        assertEquals(PurchaseOrderCheckpoint.completed(sourceNow), saved.finish());
    }

    @Test
    void rejectsCursorAtExclusiveWindowEnd() {
        Instant start = Instant.parse("2026-11-06T03:30:00Z");
        Instant end = Instant.parse("2026-11-06T03:35:00Z");
        var window = PurchaseOrderCheckpoint.completed(start).open(end);
        assertThrows(IntegrationProblem.class,
                () -> window.advance(new PurchaseOrderSource.Cursor(end, 1258)));
    }
}
