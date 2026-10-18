package com.example.procurement.integration.dto.resp;

import com.example.procurement.integration.dto.domain.SourceRecordDTO;

import java.util.List;
public record SourcePageRespDTO(List<SourceRecordDTO> items, String nextCursor,
        String upperCursor, boolean hasMore) {}
