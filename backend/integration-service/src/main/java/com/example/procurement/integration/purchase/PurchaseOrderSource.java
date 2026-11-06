package com.example.procurement.integration.purchase;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderSource {
    record Anchor(Instant sourceNow, @JsonSerialize(using = ToStringSerializer.class) long maxSourceId) {}
    record Cursor(Instant updatedAt, @JsonSerialize(using = ToStringSerializer.class) long sourceId) {}
    record Page(List<PurchaseOrderSnapshot> items, boolean hasMore) {}

    Anchor anchor();
    Page baseline(long afterId, long maxId, int limit);
    Page changes(Instant startAt, Instant endAt, Cursor after, int limit);
    Optional<PurchaseOrderSnapshot> find(long sourceId);
}
