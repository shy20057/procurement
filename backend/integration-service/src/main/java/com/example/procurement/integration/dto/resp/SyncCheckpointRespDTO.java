package com.example.procurement.integration.dto.resp;

import com.example.procurement.integration.purchase.PurchaseOrderCheckpoint;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import java.time.Instant;

public record SyncCheckpointRespDTO(String mode, boolean baselineComplete, Instant completedWatermark,
        Instant windowEndAt, Cursor pageCursor) {

    public static SyncCheckpointRespDTO purchaseOrder(boolean baselineComplete, PurchaseOrderCheckpoint checkpoint) {
        return new SyncCheckpointRespDTO("PO_TIME_WINDOW_V1", baselineComplete,
                checkpoint == null ? null : checkpoint.completedWatermark(),
                checkpoint == null ? null : checkpoint.windowEndAt(),
                checkpoint == null ? null : checkpoint.pageCursor());
    }
}
