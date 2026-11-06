package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.*;
import com.example.procurement.integration.dao.mapper.*;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.enums.*;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.SyncAlertService;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
    private final JobContext context=new JobContext(UUID.randomUUID(),MasterDataType.COST_CENTER,"AUTO",TriggerType.MANUAL,
            "SAP_COST_CENTER_WRITER","node",11L,1);

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
        SyncSettings settings=new SyncSettings(500,200,5,60);
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
        JobContext reconcile=new JobContext(context.runId(),context.type(),"RECONCILE",context.triggerType(),
                context.resourceKey(),context.ownerId(),context.token(),context.attempt());
        JobRunDO persisted=new JobRunDO();persisted.setCaughtUp(true);
        when(runs.selectById(context.runId())).thenReturn(persisted);
        service.success(reconcile);
        verify(checkpoints,never()).markSuccess(anyString());
        verify(alerts,never()).resolveSync(anyString());
    }

    private AtomicReference<JobRequestKeyDO> rememberFirstRequest() {
        JobDefinitionDO definition=new JobDefinitionDO();definition.setEnabled(true);
        definition.setJobCode(context.type().jobCode);definition.setResourceKey(context.resourceKey());
        when(definitions.lockDefinition(context.type().jobCode)).thenReturn(definition);
        AtomicReference<JobRequestKeyDO> stored=new AtomicReference<>();
        when(requests.selectByRequestId("fingerprint-request")).thenAnswer(call -> stored.get());
        doAnswer(call -> {stored.set(call.getArgument(0));return 1;}).when(requests).insert(any(JobRequestKeyDO.class));
        return stored;
    }

    @Test
    void replayWithSameRequestKeyAndDifferentReasonConflictsInsteadOfReusingRun() {
        AtomicReference<JobRequestKeyDO> stored=rememberFirstRequest();
        service.submit(context.type().jobCode,"REPAIR","fingerprint-request","修复审批一","MANUAL",null);
        assertThat(stored.get().getRequestFingerprint()).isNotBlank();
        assertThatThrownBy(() -> service.submit(context.type().jobCode,"REPAIR","fingerprint-request","不同审批原因","MANUAL",null))
                .isInstanceOfSatisfying(IntegrationProblem.class,error -> {
                    assertThat(error.code).isEqualTo("IDEMPOTENCY_CONFLICT");assertThat(error.status).isEqualTo(409);
                });
        verify(runs,times(1)).insert(any(JobRunDO.class));
        verify(requests,times(1)).insert(any(JobRequestKeyDO.class));
        verify(manager).rollback(any(TransactionStatus.class));
    }

    @Test
    void replayWithIdenticalParametersReusesOriginalRunAndFingerprint() {
        AtomicReference<JobRequestKeyDO> stored=rememberFirstRequest();
        UUID parent=UUID.randomUUID();
        UUID original=service.submit(context.type().jobCode,"REPAIR","fingerprint-request","相同审批原因","MANUAL",parent);
        String fingerprint=stored.get().getRequestFingerprint();
        UUID replay=service.submit(context.type().jobCode,"REPAIR","fingerprint-request","相同审批原因","MANUAL",parent);
        assertThat(replay).isEqualTo(original);
        assertThat(stored.get().getRequestFingerprint()).isEqualTo(fingerprint);
        verify(runs,times(1)).insert(any(JobRunDO.class));
        verify(requests,times(1)).insert(any(JobRequestKeyDO.class));
    }
}
