package com.example.procurement.integration.service;
import com.example.procurement.integration.domain.context.*;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dao.entity.SyncDifferenceDO;
import java.util.List;

public interface MasterDataPersistenceService {
    SyncCheckpoint checkpoint(MasterDataType type);
    boolean runComplete(JobContext context);
    void saveCheckpoint(JobContext context,SyncCheckpoint before,SyncCheckpoint after);
    void savePage(JobContext context,String phase,SyncCheckpoint before,SyncCheckpoint after,
            List<SourceRecordDTO> records,boolean windowComplete);
    ReconciliationScan scan(JobContext context);
    long targetMaxSourceId(MasterDataType type);
    void startScan(JobContext context,ReconciliationScan scan);
    void inspectPage(JobContext context,ReconciliationScan scan,List<SourceRecordDTO> records);
    void findMissing(JobContext context,ReconciliationScan scan,List<Long> ids);
    List<Long> missingIds(JobContext context,ReconciliationScan scan,int limit);
    List<SyncDifferenceDO> pendingDifferences(JobContext context,long after,int limit);
    boolean verifyDifference(JobContext context,SyncDifferenceDO difference,SourceRecordDTO fresh);
    void finishReconciliation(JobContext context,ReconciliationScan scan);
}
