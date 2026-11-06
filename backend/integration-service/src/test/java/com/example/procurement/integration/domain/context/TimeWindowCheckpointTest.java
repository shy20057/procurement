package com.example.procurement.integration.domain.context;

import com.example.procurement.integration.dto.domain.SourceCursorDTO;
import com.example.procurement.integration.dto.resp.SourceAnchorRespDTO;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TimeWindowCheckpointTest {
    private final Instant start = Instant.parse("2026-11-05T02:00:00.123456Z");

    @Test void pageProgressMustNotAdvanceCompleteWatermark() {
        SyncCheckpoint baseline = SyncCheckpoint.initial(null)
                .beginBaseline(new SourceAnchorRespDTO(start, 800)).baselinePage(800, start).baselineDone(start);
        SyncCheckpoint window = baseline.openWindow(start.minusSeconds(300), start.plusSeconds(600), start);
        SyncCheckpoint page = window.advance(new SourceCursorDTO(start.plusSeconds(10), 501), start.plusSeconds(20));
        assertEquals(start, page.completedWatermark());
        assertEquals(501, page.pageCursor().sourceId());
        assertEquals(start.plusSeconds(600), page.finishWindow(start.plusSeconds(30)).completedWatermark());
        assertNull(page.finishWindow(start.plusSeconds(30)).activeWindowId());
    }

    @Test void cursorMustCompareTimeBeforeIdWithoutLosingMicroseconds() {
        SourceCursorDTO previous = new SourceCursorDTO(start, 9007199254740993L);
        assertTrue(new SourceCursorDTO(start, 9007199254740994L).compareTo(previous) > 0);
        assertTrue(new SourceCursorDTO(start.plusNanos(1000), 1).compareTo(previous) > 0);
        assertEquals(123456000, previous.updatedAt().getNano());
    }

    @Test void rewindMustRetainWindowAndCompleteWatermark() {
        SyncCheckpoint cp = SyncCheckpoint.initial(null)
                .beginBaseline(new SourceAnchorRespDTO(start, 5)).baselineDone(start)
                .openWindow(start.minusSeconds(300), start.plusSeconds(600), start)
                .advance(new SourceCursorDTO(start.plusSeconds(1), 4), start);
        SyncCheckpoint rewound = cp.rewind(start.plusSeconds(2000));
        assertEquals(cp.activeWindowId(), rewound.activeWindowId());
        assertEquals(cp.windowEndAt(), rewound.windowEndAt());
        assertEquals(start, rewound.completedWatermark());
        assertNull(rewound.pageCursor());
    }
}
