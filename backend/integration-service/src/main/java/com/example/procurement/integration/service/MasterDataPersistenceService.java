package com.example.procurement.integration.service;

import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;

import java.util.List;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface MasterDataPersistenceService {
    SyncCheckpoint checkpoint(MasterDataType type);
    boolean baselineComplete(MasterDataType type);
    void establishSnapshot(JobContext context, SyncCheckpoint checkpoint, long count);
    void savePage(JobContext context, String phase, SyncCheckpoint before, SyncCheckpoint after,
            boolean baselineComplete, List<SourceRecordDTO> records, long newTotal);
    void startReconciliation(JobContext context, long count);
    void reconcilePage(JobContext context, List<SourceRecordDTO> records, String from, String to);
    void finishReconciliation(JobContext context);
}
