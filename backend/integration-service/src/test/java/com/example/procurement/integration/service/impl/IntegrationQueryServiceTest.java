package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.mapper.JobDefinitionMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAlertMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.dto.resp.IntegrationOverviewRespDTO;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.security.IntegrationAccess;
import com.example.procurement.integration.support.SyncJsonCodec;
import com.example.procurement.integration.purchase.PurchaseOrderCheckpoint;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationQueryServiceTest {
    @Test
    void checkpointEndpointKeepsCursorStateJsonInsteadOfDatabaseRowShape() {
        SyncCheckpointMapper checkpoints = mock(SyncCheckpointMapper.class);
        SyncCheckpointDO stored = new SyncCheckpointDO();
        stored.setJobCode("SAP_PURCHASE_ORDER_DELTA");
        ObjectMapper wire = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Instant time = Instant.parse("2026-01-01T00:00:00.123456Z");
        PurchaseOrderCheckpoint state = PurchaseOrderCheckpoint.completed(time).open(time.plusSeconds(60))
                .advance(new Cursor(time.plusNanos(1000), 9007199254740993L));
        stored.setBaselineComplete(true);
        stored.setCommittedCursor(new SyncJsonCodec(wire).json(state));
        when(checkpoints.selectByJob("SAP_PURCHASE_ORDER_DELTA")).thenReturn(stored);
        IntegrationQueryServiceImpl service = new IntegrationQueryServiceImpl(mock(JobDefinitionMapper.class), mock(JobRunMapper.class),
                checkpoints, mock(SyncAuditMapper.class), mock(SyncReconciliationMapper.class),
                mock(SyncAlertMapper.class), mock(IntegrationAccess.class), new SyncSettings(60, 300, 50),
                new SyncJsonCodec(new ObjectMapper().findAndRegisterModules()),
                true, true, "http://localhost:9090/xxl-job-admin", "procurement-integration-executor");

        var json = wire.valueToTree(service.checkpoint("SAP_PURCHASE_ORDER_DELTA"));

        assertThat(json.get("mode").asText()).isEqualTo("PO_TIME_WINDOW_V1");
        assertThat(json.get("completedWatermark").asText()).isEqualTo(time.toString());
        assertThat(json.get("pageCursor").get("sourceId").asText()).isEqualTo("9007199254740993");
        assertThat(json.get("pageCursor").get("updatedAt").asText()).isEqualTo("2026-01-01T00:00:00.123457Z");
        assertThat(json.has("baselineMaxSourceId")).isFalse();
        assertThat(json.has("sourceCursor")).isFalse();
        assertThat(json.has("snapshotId")).isFalse();
        assertThat(json.has("committedCursor")).isFalse();
    }

    @Test
    void overviewKeepsExistingJsonContractWithoutSecrets() {
        IntegrationAccess access = mock(IntegrationAccess.class);
        when(access.enabled()).thenReturn(true);
        IntegrationQueryServiceImpl service = new IntegrationQueryServiceImpl(mock(JobDefinitionMapper.class), mock(JobRunMapper.class),
                mock(SyncCheckpointMapper.class), mock(SyncAuditMapper.class), mock(SyncReconciliationMapper.class),
                mock(SyncAlertMapper.class), access, new SyncSettings(60, 300, 50),
                new SyncJsonCodec(new ObjectMapper().findAndRegisterModules()), true, true,
                "http://localhost:9090/xxl-job-admin", "procurement-integration-executor");

        IntegrationOverviewRespDTO result = service.overview();
        var json = new ObjectMapper().valueToTree(result);

        assertThat(json.get("schedulerProvider").asText()).isEqualTo("XXL_JOB");
        assertThat(json.get("xxlEnabled").asBoolean()).isTrue();
        assertThat(json.get("xxlAdminUrl").asText()).isEqualTo("http://localhost:9090/xxl-job-admin");
        assertThat(json.get("xxlExecutorAppName").asText()).isEqualTo("procurement-integration-executor");
        assertThat(json.has("settleDelaySeconds")).isFalse();
        assertThat(json.get("overlapSeconds").asInt()).isEqualTo(300);
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
