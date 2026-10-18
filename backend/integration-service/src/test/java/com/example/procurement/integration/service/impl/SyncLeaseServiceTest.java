package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.JobLeaseMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.domain.enums.TriggerType;
import com.example.procurement.integration.exception.IntegrationProblem;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncLeaseServiceTest {
    private JobRunMapper runs;
    private JobLeaseMapper leases;
    private SyncLeaseServiceImpl service;
    private JobContext context;

    @BeforeEach
    void setUp() {
        runs = mock(JobRunMapper.class); leases = mock(JobLeaseMapper.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        service = new SyncLeaseServiceImpl(runs, leases, manager, new SyncSettings(500, 200, 5, 60));
        context = new JobContext(UUID.randomUUID(), MasterDataType.COST_CENTER, "AUTO", TriggerType.MANUAL,
                "SAP_COST_CENTER_WRITER", "node", 11L, 1);
    }

    @Test
    void releaseUsesDatasetResourceKeyInsteadOfJobCode() {
        service.release(context);
        verify(leases).release("SAP_COST_CENTER_WRITER", "node", 11L);
    }

    @Test
    void fencedWriteNeverRunsAfterOwnershipWasReplaced() {
        JobRunDO run = new JobRunDO(); run.setStatus("RUNNING"); run.setOwnerId("new-node"); run.setFenceToken(12L);
        when(runs.lockRun(context.runId())).thenReturn(run);
        AtomicBoolean wrote = new AtomicBoolean();

        assertThatThrownBy(() -> service.fenced(context, () -> { wrote.set(true); return null; }))
                .isInstanceOfSatisfying(IntegrationProblem.class, problem -> assertThat(problem.code).isEqualTo("LEASE_LOST"));
        assertThat(wrote.get()).isFalse();
    }
}
