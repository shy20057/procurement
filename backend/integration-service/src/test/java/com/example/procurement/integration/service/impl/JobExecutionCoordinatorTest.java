package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.domain.enums.RetryOwner;
import com.example.procurement.integration.domain.enums.TriggerType;
import com.example.procurement.integration.domain.result.JobExecutionResult;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.MasterDataSyncService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionCoordinatorTest {
    @Mock SyncJobService jobs;
    @Mock MasterDataSyncService sync;
    private JobExecutionCoordinatorImpl coordinator;
    @Mock SyncLeaseService leases;

    @BeforeEach
    void setUp() {
        coordinator = new JobExecutionCoordinatorImpl(jobs, sync, leases, "test-node");
    }

    @Test
    void xxlFailureIsTerminalAndNeverUsesLocalRetryOwner() {
        UUID runId = UUID.randomUUID();
        JobContext context = context(runId, TriggerType.XXL);
        XxlTriggerMetadata metadata = new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "http://node:9999");
        when(jobs.submitXxl("SAP_COST_CENTER_DELTA", metadata, "request-1"))
                .thenReturn(XxlSubmission.created(runId));
        when(jobs.claim(runId, "test-node")).thenReturn(context);
        IntegrationProblem failure = new IntegrationProblem("SAP_UNAVAILABLE", "SAP暂时不可用", true, 503);
        doThrow(failure).when(sync).execute(context);

        JobExecutionResult result = coordinator.executeFromXxl(
                "SAP_COST_CENTER_DELTA", metadata, "request-1");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SAP_UNAVAILABLE");
        verify(jobs).failure(context, failure, RetryOwner.XXL);
        verify(leases).release(context);
    }

    @Test
    void localFailureKeepsLocalRetryOwnerAndReleasesLease() {
        JobContext context = context(UUID.randomUUID(), TriggerType.MANUAL);
        IntegrationProblem failure = new IntegrationProblem("SAP_UNAVAILABLE", "SAP暂时不可用", true, 503);
        doThrow(failure).when(sync).execute(context);

        JobExecutionResult result = coordinator.executeClaimed(context, RetryOwner.LOCAL);

        assertThat(result.success()).isFalse();
        verify(jobs).failure(context, failure, RetryOwner.LOCAL);
        verify(leases).release(context);
    }

    @Test
    void successfulRunIsCommittedBeforeLeaseRelease() {
        JobContext context = context(UUID.randomUUID(), TriggerType.MANUAL);

        JobExecutionResult result = coordinator.executeClaimed(context, RetryOwner.LOCAL);

        assertThat(result.success()).isTrue();
        verify(jobs).success(context);
        verify(leases).release(context);
    }

    @Test
    void reportsDisabledWhenSwitchChangesBetweenSubmissionAndClaim() {
        UUID runId = UUID.randomUUID();
        XxlTriggerMetadata metadata = new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "http://node:9999");
        when(jobs.submitXxl("SAP_COST_CENTER_DELTA", metadata, "request-2"))
                .thenReturn(XxlSubmission.created(runId));
        when(jobs.claim(runId, "test-node")).thenReturn(null);
        com.example.procurement.integration.dao.entity.JobRunDO persisted = new com.example.procurement.integration.dao.entity.JobRunDO();
        persisted.setStatus("FAILED"); persisted.setErrorCode("JOB_DISABLED"); persisted.setErrorSummary("当前任务已停用");
        when(jobs.findRun(runId)).thenReturn(persisted);

        JobExecutionResult result = coordinator.executeFromXxl(
                "SAP_COST_CENTER_DELTA", metadata, "request-2");

        assertThat(result.errorCode()).isEqualTo("JOB_DISABLED");
        assertThat(result.message()).isEqualTo("当前任务已停用");
    }

    private JobContext context(UUID runId, TriggerType triggerType) {
        return new JobContext(runId, MasterDataType.COST_CENTER, "AUTO", triggerType,
                "SAP_COST_CENTER_WRITER", "test-node", 9L, 1);
    }
}
