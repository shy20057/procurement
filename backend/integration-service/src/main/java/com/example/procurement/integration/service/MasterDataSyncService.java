package com.example.procurement.integration.service;

import com.example.procurement.integration.domain.context.JobContext;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface MasterDataSyncService {
    void execute(JobContext context);
}
