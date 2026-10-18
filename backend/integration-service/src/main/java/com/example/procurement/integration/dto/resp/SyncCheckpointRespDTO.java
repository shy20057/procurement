package com.example.procurement.integration.dto.resp;

import java.util.UUID;

/** 检查点接口返回的是游标状态，保持原接口字段，不能直接返回数据库行。 */
public record SyncCheckpointRespDTO(String mode, String sourceCursor, UUID snapshotId,
        long snapshotPosition, String baselineCursor, String upperCursor) {}
