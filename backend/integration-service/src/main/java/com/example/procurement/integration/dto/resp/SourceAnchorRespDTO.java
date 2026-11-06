package com.example.procurement.integration.dto.resp;

import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 一次短查询的时间与ID上界，不是源端快照。 */
public record SourceAnchorRespDTO(Instant sourceNow,
        @JsonSerialize(using = ToStringSerializer.class) long maxSourceId) {}
