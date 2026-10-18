package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.ExchangeRateMapper;
import com.example.procurement.integration.dao.mapper.JobDefinitionMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAlertMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.dto.resp.IntegrationOverviewRespDTO;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.support.MasterDataCodec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationQueryServiceTest {
    @Test
    void checkpointEndpointKeepsCursorStateJsonInsteadOfDatabaseRowShape() {
        SyncCheckpointMapper checkpoints = mock(SyncCheckpointMapper.class);
        SyncCheckpointDO stored = new SyncCheckpointDO();
        stored.setJobCode("SAP_COST_CENTER_DELTA");
        stored.setCommittedCursor("{\"mode\":\"SOURCE_CURSOR\",\"sourceCursor\":\"cc:12\",\"snapshotId\":null,\"snapshotPosition\":0,\"baselineCursor\":null,\"upperCursor\":\"cc:20\"}");
        when(checkpoints.selectByJob("SAP_COST_CENTER_DELTA")).thenReturn(stored);
        IntegrationQueryServiceImpl service = new IntegrationQueryServiceImpl(mock(CostCenterMapper.class),
                mock(ExchangeRateMapper.class), mock(JobDefinitionMapper.class), mock(JobRunMapper.class),
                checkpoints, mock(SyncAuditMapper.class), mock(SyncReconciliationMapper.class),
                mock(SyncAlertMapper.class), mock(IntegrationAccess.class), new SyncSettings(500, 200, 5, 60),
                new MasterDataCodec(new ObjectMapper().findAndRegisterModules()),
                true, true, "http://localhost:9090/xxl-job-admin", "procurement-integration-executor");

        var json = new ObjectMapper().valueToTree(service.checkpoint("SAP_COST_CENTER_DELTA"));

        assertThat(json.has("sourceCursor")).isTrue();
        assertThat(json.get("sourceCursor").asText()).isEqualTo("cc:12");
        assertThat(json.get("upperCursor").asText()).isEqualTo("cc:20");
        assertThat(json.has("committedCursor")).isFalse();
    }

    @Test
    void overviewKeepsExistingJsonContractWithoutSecrets() {
        IntegrationAccess access = mock(IntegrationAccess.class);
        when(access.enabled()).thenReturn(true);
        IntegrationQueryServiceImpl service = new IntegrationQueryServiceImpl(mock(CostCenterMapper.class),
                mock(ExchangeRateMapper.class), mock(JobDefinitionMapper.class), mock(JobRunMapper.class),
                mock(SyncCheckpointMapper.class), mock(SyncAuditMapper.class), mock(SyncReconciliationMapper.class),
                mock(SyncAlertMapper.class), access, new SyncSettings(500, 200, 5, 60),
                new MasterDataCodec(new ObjectMapper().findAndRegisterModules()), true, true,
                "http://localhost:9090/xxl-job-admin", "procurement-integration-executor");

        IntegrationOverviewRespDTO result = service.overview();
        var json = new ObjectMapper().valueToTree(result);

        assertThat(json.get("schedulerProvider").asText()).isEqualTo("XXL_JOB");
        assertThat(json.get("xxlEnabled").asBoolean()).isTrue();
        assertThat(json.get("xxlAdminUrl").asText()).isEqualTo("http://localhost:9090/xxl-job-admin");
        assertThat(json.get("xxlExecutorAppName").asText()).isEqualTo("procurement-integration-executor");
        assertThat(json.has("accessToken")).isFalse();
        assertThat(json.has("password")).isFalse();
    }

    @Test
    void rejectsUnsafeOrCredentialBearingAdminUrls() {
        assertThat(IntegrationQueryServiceImpl.safeAdminUrl("javascript:alert(1)")).isNull();
        assertThat(IntegrationQueryServiceImpl.safeAdminUrl("http://user:secret@localhost:9090")).isNull();
        assertThat(IntegrationQueryServiceImpl.safeAdminUrl("https://scheduler.example/xxl-job-admin"))
                .isEqualTo("https://scheduler.example/xxl-job-admin");
    }
}
