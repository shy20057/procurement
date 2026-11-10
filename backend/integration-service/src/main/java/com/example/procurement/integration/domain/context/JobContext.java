package com.example.procurement.integration.domain.context;

import com.example.procurement.integration.domain.enums.SyncJobType;
import java.util.UUID;

public record JobContext(UUID runId, SyncJobType type, String mode, String resourceKey,
        String ownerId, long token, int attempt) {}
