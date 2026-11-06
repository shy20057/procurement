package com.example.procurement.integration.domain.context;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceCursorDTO;
import com.example.procurement.integration.dto.resp.SourceAnchorRespDTO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.UUID;

/** 完整水位与页游标分离；所有状态仅在目标页事务提交后生效。 */
public record SyncCheckpoint(
        String mode, long generation, boolean baselineComplete, Instant baselineAnchorAt,
        @JsonSerialize(using=ToStringSerializer.class) long baselineMaxSourceId,
        @JsonSerialize(using=ToStringSerializer.class) long baselineLastSourceId,
        Instant completedWatermark, UUID activeWindowId, Instant windowStartAt, Instant windowEndAt,
        SourceCursorDTO pageCursor, Instant sourceTimeHighwater, Instant lastPageAt, long pageSequence) {
    public static SyncCheckpoint initial(MasterDataType ignored) {
        return new SyncCheckpoint("TIME_WINDOW_V2",1,false,null,0,0,null,null,null,null,null,null,null,0);
    }
    public SyncCheckpoint beginBaseline(SourceAnchorRespDTO anchor) {
        return new SyncCheckpoint(mode,generation,false,anchor.sourceNow(),anchor.maxSourceId(),0,
                null,null,null,null,null,anchor.sourceNow(),anchor.sourceNow(),0);
    }
    public SyncCheckpoint baselinePage(long id, Instant now) {
        return new SyncCheckpoint(mode,generation,false,baselineAnchorAt,baselineMaxSourceId,id,
                completedWatermark,null,null,null,null,sourceTimeHighwater,now,pageSequence+1);
    }
    public SyncCheckpoint baselineDone(Instant now) {
        return new SyncCheckpoint(mode,generation,true,baselineAnchorAt,baselineMaxSourceId,baselineMaxSourceId,
                baselineAnchorAt,null,null,null,null,sourceTimeHighwater,now,pageSequence);
    }
    public SyncCheckpoint openWindow(Instant start, Instant end, Instant sourceNow) {
        if(!start.isBefore(end)) throw new IllegalArgumentException("时间窗口必须为正向区间");
        return new SyncCheckpoint(mode,generation,true,baselineAnchorAt,baselineMaxSourceId,baselineLastSourceId,
                completedWatermark,UUID.randomUUID(),start,end,null,sourceNow,Instant.now(),0);
    }
    public SyncCheckpoint advance(SourceCursorDTO cursor, Instant now) {
        return new SyncCheckpoint(mode,generation,baselineComplete,baselineAnchorAt,baselineMaxSourceId,baselineLastSourceId,
                completedWatermark,activeWindowId,windowStartAt,windowEndAt,cursor,sourceTimeHighwater,now,pageSequence+1);
    }
    public SyncCheckpoint finishWindow(Instant now) {
        return new SyncCheckpoint(mode,generation,true,baselineAnchorAt,baselineMaxSourceId,baselineLastSourceId,
                windowEndAt,null,null,null,null,sourceTimeHighwater,now,pageSequence+1);
    }
    public SyncCheckpoint rewind(Instant now) {
        return new SyncCheckpoint(mode,generation,baselineComplete,baselineAnchorAt,baselineMaxSourceId,baselineLastSourceId,
                completedWatermark,activeWindowId,windowStartAt,windowEndAt,null,sourceTimeHighwater,now,0);
    }
    public SyncCheckpoint observe(Instant sourceNow) {
        Instant high=sourceTimeHighwater!=null&&sourceTimeHighwater.isAfter(sourceNow)?sourceTimeHighwater:sourceNow;
        return new SyncCheckpoint(mode,generation,baselineComplete,baselineAnchorAt,baselineMaxSourceId,baselineLastSourceId,
                completedWatermark,activeWindowId,windowStartAt,windowEndAt,pageCursor,high,lastPageAt,pageSequence);
    }
}
