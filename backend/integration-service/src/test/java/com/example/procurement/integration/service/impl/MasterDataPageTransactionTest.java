package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.ExchangeRateMapper;
import com.example.procurement.integration.dao.mapper.JobLeaseMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.domain.enums.TriggerType;
import com.example.procurement.integration.dto.domain.CostCenterDTO;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.support.MasterDataCodec;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 Spring 事务管理器和模拟 JDBC Connection，验证页事务边界，不连接数据库。 */
class MasterDataPageTransactionTest {
    private Connection connection;
    private CostCenterMapper costCenters;
    private SyncCheckpointMapper checkpoints;
    private SyncAuditMapper audits;
    private MasterDataPersistenceServiceImpl service;
    private JobContext context;
    private SyncCheckpoint before;
    private SyncCheckpoint after;
    private SourceRecordDTO record;

    @BeforeEach
    void setUp() throws Exception {
        DataSource source = mock(DataSource.class);
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        JobRunMapper runs = mock(JobRunMapper.class);
        JobLeaseMapper leaseMapper = mock(JobLeaseMapper.class);
        SyncSettings settings = new SyncSettings(500, 200, 5, 60);
        context = new JobContext(UUID.randomUUID(), MasterDataType.COST_CENTER, "AUTO", TriggerType.MANUAL,
                "SAP_COST_CENTER_WRITER", "node", 11L, 1);
        JobRunDO active = new JobRunDO(); active.setStatus("RUNNING"); active.setOwnerId("node"); active.setFenceToken(11L);
        when(runs.lockRun(context.runId())).thenReturn(active);
        when(leaseMapper.lockOwned("SAP_COST_CENTER_WRITER", "node", 11L)).thenReturn(11L);
        SyncLeaseServiceImpl leaseService = new SyncLeaseServiceImpl(runs, leaseMapper, new DataSourceTransactionManager(source), settings);
        costCenters = mock(CostCenterMapper.class); checkpoints = mock(SyncCheckpointMapper.class); audits = mock(SyncAuditMapper.class);
        when(costCenters.selectByKeys(anyList())).thenReturn(List.of());
        when(costCenters.batchUpsert(anyList(), eq(false))).thenReturn(1);
        service = new MasterDataPersistenceServiceImpl(costCenters, mock(ExchangeRateMapper.class), checkpoints,
                audits, runs, mock(SyncReconciliationMapper.class), leaseService, mock(SyncAlertService.class),
                new MasterDataCodec(new ObjectMapper().findAndRegisterModules()), settings);
        before = SyncCheckpoint.initial(MasterDataType.COST_CENTER);
        after = before.deltaPage("cc:1", "cc:1", false);
        record = new SourceRecordDTO(MasterDataType.COST_CENTER, "A100|CC_01", 1,
                Instant.parse("2026-01-01T00:00:00Z"),
                new CostCenterDTO("A100", "CC_01", "1000", "研发中心", LocalDate.of(2020, 1, 1),
                        LocalDate.of(2099, 12, 31), false, false), null);
    }

    @Test
    void commitsOnlyAfterDataCheckpointAndAuditAreWritten() throws Exception {
        service.savePage(context, "DELTA", before, after, true, List.of(record), 0);

        var order = inOrder(costCenters, checkpoints, audits, connection);
        order.verify(costCenters).batchUpsert(anyList(), eq(false));
        order.verify(checkpoints).save(eq("SAP_COST_CENTER_DELTA"), anyString(), eq(true));
        order.verify(audits).insertPage(any());
        order.verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void checkpointFailureRollsBackThePageAndSkipsSuccessAudit() throws Exception {
        when(checkpoints.save(eq("SAP_COST_CENTER_DELTA"), anyString(), eq(true)))
                .thenThrow(new DataIntegrityViolationException("checkpoint write failed"));

        assertThatThrownBy(() -> service.savePage(context, "DELTA", before, after, true, List.of(record), 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(audits, never()).insertPage(any());
    }
}
