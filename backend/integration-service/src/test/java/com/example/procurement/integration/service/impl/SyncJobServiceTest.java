package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.JobDefinitionDO;
import com.example.procurement.integration.dao.entity.JobRequestKeyDO;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.JobDefinitionMapper;
import com.example.procurement.integration.dao.mapper.JobLeaseMapper;
import com.example.procurement.integration.dao.mapper.JobRequestKeyMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.dto.domain.ClaimableRunDTO;
import com.example.procurement.integration.dto.domain.LeaseStateDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 在 Service 边界验证原 Repository 的幂等、租约和重试归属，不需要数据库。 */
class SyncJobServiceTest {
    private JobDefinitionMapper definitions;
    private JobRunMapper runs;
    private JobRequestKeyMapper requests;
    private JobLeaseMapper leaseMapper;
    private SyncJobServiceImpl service;

    @BeforeEach
    void setUp() {
        definitions = mock(JobDefinitionMapper.class); runs = mock(JobRunMapper.class);
        requests = mock(JobRequestKeyMapper.class); leaseMapper = mock(JobLeaseMapper.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        service = new SyncJobServiceImpl(definitions, runs, requests, leaseMapper,
                mock(SyncCheckpointMapper.class), mock(SyncAuditMapper.class), mock(SyncLeaseService.class),
                mock(SyncAlertService.class), new SyncSettings(60, 300, 50), manager);
    }

    @Test
    void exactSuccessfulXxlReplayReusesOriginalRun() {
        UUID original = UUID.randomUUID();
        when(definitions.lockDefinition("SAP_PURCHASE_ORDER_DELTA")).thenReturn(definition(true));
        JobRunDO previous = new JobRunDO(); previous.setRunId(original); previous.setStatus("SUCCEEDED"); previous.setMode("AUTO"); previous.setReason("XXL-JOB调度触发");
        when(runs.lockXxlIdentity("SAP_PURCHASE_ORDER_DELTA", 12L, "a".repeat(64), 0)).thenReturn(previous);

        XxlSubmission result = service.submitXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata(), "request-xxl");

        assertThat(result.state()).isEqualTo(XxlSubmission.State.DUPLICATE_TERMINAL);
        assertThat(result.runId()).isEqualTo(original);
        verify(runs, never()).insert(any());
    }

    @Test
    void differentBusyXxlTriggerGetsItsOwnFailedRun() {
        JobRunDO active = new JobRunDO(); active.setRunId(UUID.randomUUID());
        when(definitions.lockDefinition("SAP_PURCHASE_ORDER_DELTA")).thenReturn(definition(true));
        when(runs.selectActiveResource(eq("SAP_PURCHASE_ORDER_WRITER"), any(UUID.class))).thenReturn(active);

        XxlSubmission result = service.submitXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata(), "request-new");

        assertThat(result.state()).isEqualTo(XxlSubmission.State.BUSY);
        assertThat(result.runId()).isNotEqualTo(active.getRunId());
        verify(runs).finishPending(result.runId(), "JOB_BUSY", "数据集资源正由其他业务运行占用");
    }

    @Test
    void recoveryRecognizesReplacedFenceEvenWhileNewLeaseIsAlive() {
        ClaimableRunDTO stale = candidate("SAP_PURCHASE_ORDER_DELTA", "SAP_PURCHASE_ORDER_WRITER");
        stale.setStatus("RUNNING"); stale.setTriggerType("XXL"); stale.setOwnerId("old-node"); stale.setFenceToken(3L);
        LeaseStateDTO current = new LeaseStateDTO(); current.setOwnerId("new-node"); current.setFenceToken(4L); current.setExpired(false);
        when(runs.lockStalePurchaseOrderRuns("SAP_PURCHASE_ORDER_DELTA", 60)).thenReturn(List.of(stale));
        when(definitions.lockDefinition("SAP_PURCHASE_ORDER_DELTA")).thenReturn(definition(true));
        when(leaseMapper.lockState("SAP_PURCHASE_ORDER_WRITER")).thenReturn(current);

        service.submitXxl("SAP_PURCHASE_ORDER_DELTA", "AUTO", metadata(), "request-recovery");

        verify(runs).recover(stale.getRunId(), "FAILED");
        verify(leaseMapper, never()).release(anyString(), anyString(), anyLong());
    }

    @Test
    void sameXxlDeliveryCannotChangeRepairTarget() {
        String code = "SAP_PURCHASE_ORDER_DELTA";
        when(definitions.lockDefinition(code)).thenReturn(definition(true));
        JobRunDO previous = new JobRunDO();
        previous.setMode("REPAIR");
        previous.setReason("处理差异 [differenceId=17]");
        when(runs.lockXxlIdentity(code, 12L, "a".repeat(64), 0)).thenReturn(previous);
        var parameters = new XxlTriggerMetadata.Parameters("operator", "处理差异", null, 17L, null);
        var metadata = new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "http://node:9999", parameters);

        assertThatThrownBy(() -> service.submitXxl(code, "REPAIR", metadata, "repair"))
                .isInstanceOfSatisfying(IntegrationProblem.class, problem ->
                        assertThat(problem.code).isEqualTo("IDEMPOTENCY_CONFLICT"));
        verify(runs, never()).insert(any());
    }

    private JobDefinitionDO definition(boolean enabled) {
        JobDefinitionDO value = new JobDefinitionDO(); value.setJobCode("SAP_PURCHASE_ORDER_DELTA");
        value.setEnabled(enabled); value.setResourceKey("SAP_PURCHASE_ORDER_WRITER"); return value;
    }
    private ClaimableRunDTO candidate(String code, String resource) {
        ClaimableRunDTO value = new ClaimableRunDTO(); value.setRunId(UUID.randomUUID()); value.setJobCode(code);
        value.setResourceKey(resource); value.setMode("AUTO"); value.setTriggerType("MANUAL"); value.setAttemptNo(0); return value;
    }
    private XxlTriggerMetadata metadata() { return new XxlTriggerMetadata(12L, "a".repeat(64), 0, 1, "http://node:9999", null); }
}
