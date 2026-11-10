package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.enums.SyncJobType;
import com.example.procurement.integration.domain.result.JobExecutionResult;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSyncService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionCoordinatorTest {
    @Mock SyncJobService jobs;
    @Mock PurchaseOrderSyncService sync;
    private JobExecutionCoordinatorImpl coordinator;
    @Mock SyncLeaseService leases;

    @BeforeEach
    void setUp() {
        coordinator = new JobExecutionCoordinatorImpl(jobs, leases, sync);
    }

    @Test
    void sourceFailureIsReportedToXxlAndReleasesLease() {
        UUID runId = UUID.randomUUID();
        JobContext context = context(runId);
        XxlTriggerMetadata metadata = new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "http://node:9999", null);
        when(jobs.submitXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata, "request-1"))
                .thenReturn(XxlSubmission.created(runId));
        when(jobs.claim(eq(runId), anyString())).thenReturn(context);
        IntegrationProblem failure = new IntegrationProblem("SAP_UNAVAILABLE", "SAP暂时不可用", true, 503);
        doThrow(failure).when(sync).execute(context, null);

        JobExecutionResult result = coordinator.executeFromXxl(
                "SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata, "request-1");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SAP_UNAVAILABLE");
        verify(jobs).failure(context, failure);
        verify(leases).release(context);
    }

    @Test
    void successfulRunIsCommittedBeforeLeaseRelease() {
        UUID runId = UUID.randomUUID();
        JobContext context = context(runId);
        XxlTriggerMetadata metadata = new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "", null);
        when(jobs.submitXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata, "request-3"))
                .thenReturn(XxlSubmission.created(runId));
        when(jobs.claim(eq(runId), anyString())).thenReturn(context);

        JobExecutionResult result = coordinator.executeFromXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata, "request-3");

        assertThat(result.success()).isTrue();
        var order = inOrder(jobs, leases);
        order.verify(jobs).success(context);
        order.verify(leases).release(context);
    }

    @Test
    void reportsDisabledWhenSwitchChangesBetweenSubmissionAndClaim() {
        UUID runId = UUID.randomUUID();
        XxlTriggerMetadata metadata = new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "http://node:9999", null);
        when(jobs.submitXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata, "request-2"))
                .thenReturn(XxlSubmission.created(runId));
        when(jobs.claim(eq(runId), anyString())).thenReturn(null);
        com.example.procurement.integration.dao.entity.JobRunDO persisted = new com.example.procurement.integration.dao.entity.JobRunDO();
        persisted.setStatus("FAILED"); persisted.setErrorCode("JOB_DISABLED"); persisted.setErrorSummary("当前任务已停用");
        when(jobs.findRun(runId)).thenReturn(persisted);

        JobExecutionResult result = coordinator.executeFromXxl(
                "SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata, "request-2");

        assertThat(result.errorCode()).isEqualTo("JOB_DISABLED");
        assertThat(result.message()).isEqualTo("当前任务已停用");
    }

    private JobContext context(UUID runId) {
        return new JobContext(runId, SyncJobType.PURCHASE_ORDER, "AUTO",
                "SAP_PURCHASE_ORDER_WRITER", "test-node", 9L, 1);
    }
}
