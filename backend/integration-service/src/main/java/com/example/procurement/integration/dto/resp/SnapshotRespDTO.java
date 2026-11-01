package com.example.procurement.integration.dto.resp;

import java.util.UUID;

public record SnapshotRespDTO(UUID snapshotId, String baselineCursor, long recordCount) {
}
