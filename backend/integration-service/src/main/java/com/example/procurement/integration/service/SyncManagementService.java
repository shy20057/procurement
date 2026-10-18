package com.example.procurement.integration.service;

import com.example.procurement.integration.dto.req.SyncRunReqDTO;
import com.example.procurement.integration.dto.resp.JobRunRespDTO;

import java.util.UUID;

public interface SyncManagementService {
    JobRunRespDTO trigger(String jobCode, String requestId, SyncRunReqDTO request);
    JobRunRespDTO retry(UUID runId, String requestId, String reason);
    JobRunRespDTO cancel(UUID runId);
}
