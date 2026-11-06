package com.example.procurement.integration.dto.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

/** 时间优先、ID次之；ID不是变更序号。 */
public record SourceCursorDTO(Instant updatedAt,
        @JsonSerialize(using = ToStringSerializer.class) long sourceId) implements Comparable<SourceCursorDTO> {
    public int compareTo(SourceCursorDTO other) {
        int time = updatedAt.compareTo(other.updatedAt);
        return time != 0 ? time : Long.compare(sourceId, other.sourceId);
    }
    public static SourceCursorDTO of(SourceRecordDTO record) {
        return new SourceCursorDTO(record.updatedAt(), record.sourceId());
    }
}
