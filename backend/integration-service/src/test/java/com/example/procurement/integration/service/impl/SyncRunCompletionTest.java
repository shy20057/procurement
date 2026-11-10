package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.*;
import com.example.procurement.integration.dao.mapper.*;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.enums.*;
import com.example.procurement.integration.service.SyncAlertService;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Run success alone does not prove an AUTO window was fully consumed. */
class SyncRunCompletionTest {
    private JobDefinitionMapper definitions;
    private JobRunMapper runs;
    private JobRequestKeyMapper requests;
    private SyncCheckpointMapper checkpoints;
    private SyncAlertService alerts;
    private SyncJobServiceImpl service;
    private PlatformTransactionManager manager;
    private final JobContext context=new JobContext(UUID.randomUUID(),SyncJobType.PURCHASE_ORDER,"AUTO",
            "SAP_PURCHASE_ORDER_WRITER","node",11L,1);

    @BeforeEach
    void setUp() {
        definitions=mock(JobDefinitionMapper.class);runs=mock(JobRunMapper.class);requests=mock(JobRequestKeyMapper.class);
        checkpoints=mock(SyncCheckpointMapper.class);alerts=mock(SyncAlertService.class);
        JobLeaseMapper locks=mock(JobLeaseMapper.class);
        manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        JobRunDO active=new JobRunDO();active.setStatus("RUNNING");active.setOwnerId("node");active.setFenceToken(11L);
        when(runs.lockRun(context.runId())).thenReturn(active);
        when(locks.lockOwned(context.resourceKey(),"node",11L)).thenReturn(11L);
        SyncSettings settings=new SyncSettings(60, 300, 50);
        service=new SyncJobServiceImpl(definitions,runs,requests,locks,checkpoints,mock(SyncAuditMapper.class),
                new SyncLeaseServiceImpl(runs,locks,manager,settings),alerts,settings,manager);
    }

    @Test
    void autoRunWithoutCompletedWindowDoesNotMarkCheckpointSuccessOrResolveSyncAlerts() {
        JobRunDO persisted=new JobRunDO();persisted.setCaughtUp(false);
        when(runs.selectById(context.runId())).thenReturn(persisted);
        service.success(context);
        verify(runs).markSuccess(context.runId());
        verify(checkpoints,never()).markSuccess(anyString());
        verify(alerts,never()).resolveSync(anyString());
        verify(manager).commit(any(TransactionStatus.class));
    }

    @Test
    void caughtUpAutoRunMarksCheckpointSuccessAndResolvesSyncAlertsInsideItsTransaction() {
        JobRunDO persisted=new JobRunDO();persisted.setCaughtUp(true);
        when(runs.selectById(context.runId())).thenReturn(persisted);
        service.success(context);
        var order=inOrder(runs,checkpoints,alerts,manager);
        order.verify(runs).markSuccess(context.runId());
        order.verify(runs).selectById(context.runId());
        order.verify(checkpoints).markSuccess(context.type().jobCode);
        order.verify(alerts).resolveSync(context.type().jobCode);
        order.verify(manager).commit(any(TransactionStatus.class));
    }

    @Test
    void reconcileSuccessNeverChangesAutoSuccessMarkerEvenIfCaughtUpFlagIsPresent() {
        JobContext reconcile=new JobContext(context.runId(),context.type(),"RECONCILE",
                context.resourceKey(),context.ownerId(),context.token(),context.attempt());
        JobRunDO persisted=new JobRunDO();persisted.setCaughtUp(true);
        when(runs.selectById(context.runId())).thenReturn(persisted);
        service.success(reconcile);
        verify(checkpoints,never()).markSuccess(anyString());
        verify(alerts,never()).resolveSync(anyString());
    }

}
