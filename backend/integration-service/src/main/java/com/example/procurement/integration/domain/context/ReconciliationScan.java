package com.example.procurement.integration.domain.context;
import java.time.Instant;
/** 独立于AUTO检查点的全量观察进度，不代表固定快照。 */
public record ReconciliationScan(Instant startedAt,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long maxSourceId,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long lastSourceId,
        boolean scanComplete,
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) long missingAfterId,
        boolean missingComplete,boolean complete) {
    public ReconciliationScan page(long id,boolean end) {
        return new ReconciliationScan(startedAt,maxSourceId,id,end,missingAfterId,missingComplete,false);
    }
    public ReconciliationScan missing(long id,boolean end) {
        return new ReconciliationScan(startedAt,maxSourceId,lastSourceId,scanComplete,id,end,false);
    }
    public ReconciliationScan finished() {
        return new ReconciliationScan(startedAt,maxSourceId,lastSourceId,true,missingAfterId,true,true);
    }
}
