package com.example.procurement.integration.purchase;

import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import java.time.Instant;

/** Only progress shared across executions. Baseline scans restart from the beginning. */
public record PurchaseOrderCheckpoint(Instant completedWatermark, Instant windowEndAt, Cursor pageCursor) {
    public PurchaseOrderCheckpoint {
        if (completedWatermark == null
                || (windowEndAt != null && !windowEndAt.isAfter(completedWatermark))
                || (pageCursor != null && (windowEndAt == null || pageCursor.updatedAt() == null
                        || !pageCursor.updatedAt().isBefore(windowEndAt) || pageCursor.sourceId() <= 0))) {
            throw new IntegrationProblem("CHECKPOINT_INVALID", "采购订单检查点无效", false, 409);
        }
    }

    public static PurchaseOrderCheckpoint completed(Instant watermark) {
        return new PurchaseOrderCheckpoint(watermark, null, null);
    }

    public PurchaseOrderCheckpoint open(Instant sourceNow) {
        return new PurchaseOrderCheckpoint(completedWatermark, sourceNow, null);
    }

    public Instant startAt(int overlapSeconds) {
        return completedWatermark.minusSeconds(overlapSeconds);
    }

    public PurchaseOrderCheckpoint advance(Cursor cursor) {
        return new PurchaseOrderCheckpoint(completedWatermark, windowEndAt, cursor);
    }

    public PurchaseOrderCheckpoint finish() {
        return completed(windowEndAt);
    }
}
