package com.example.procurement.integration.dto.resp;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
import com.example.procurement.integration.purchase.PurchaseOrderCheckpoint;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
/** 直接继承检查点字段契约，由Controller返回具体状态。 */
public record SyncCheckpointRespDTO(@JsonUnwrapped SyncCheckpoint state, @JsonUnwrapped PurchaseOrderState purchaseOrder) {
    public SyncCheckpointRespDTO(SyncCheckpoint state) {
        this(state, null);
    }

    public static SyncCheckpointRespDTO purchaseOrder(boolean baselineComplete, PurchaseOrderCheckpoint checkpoint) {
        return new SyncCheckpointRespDTO(null, new PurchaseOrderState("PO_TIME_WINDOW_V1", baselineComplete,
                checkpoint == null ? null : checkpoint.completedWatermark(),
                checkpoint == null ? null : checkpoint.windowEndAt(),
                checkpoint == null ? null : checkpoint.pageCursor()));
    }

    public record PurchaseOrderState(String mode, boolean baselineComplete, Instant completedWatermark,
            Instant windowEndAt, Cursor pageCursor) {}
}
