package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.*;
import com.example.procurement.integration.dao.mapper.*;
import com.example.procurement.integration.domain.context.*;
import com.example.procurement.integration.domain.enums.*;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.dto.resp.SourceAnchorRespDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring transaction boundary with a fake JDBC connection; never connects to a database. */
class MasterDataPageTransactionTest {
    private Connection connection;
    private CostCenterMapper costCenters;
    private SyncCheckpointMapper checkpoints;
    private SyncAuditMapper audits;
    private JobRunMapper runs;
    private MasterDataPersistenceServiceImpl service;
    private JobContext context;
    private SyncCheckpoint before, after;
    private SourceRecordDTO record;
    private final MasterDataCodec codec = new MasterDataCodec(new ObjectMapper().findAndRegisterModules());
    private final Instant time = Instant.parse("2026-01-01T00:00:00.123456Z");

    @BeforeEach
    void setUp() throws Exception {
        DataSource source = mock(DataSource.class);
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        runs = mock(JobRunMapper.class);
        JobLeaseMapper leaseMapper = mock(JobLeaseMapper.class);
        SyncSettings settings = new SyncSettings(500, 1, 5, 60);
        context = new JobContext(UUID.randomUUID(), MasterDataType.COST_CENTER, "AUTO", TriggerType.MANUAL,
                "SAP_COST_CENTER_WRITER", "node", 11L, 1);
        JobRunDO active = new JobRunDO(); active.setStatus("RUNNING"); active.setOwnerId("node"); active.setFenceToken(11L);
        when(runs.lockRun(context.runId())).thenReturn(active);
        when(leaseMapper.lockOwned("SAP_COST_CENTER_WRITER", "node", 11L)).thenReturn(11L);
        SyncLeaseServiceImpl leases = new SyncLeaseServiceImpl(runs, leaseMapper, new DataSourceTransactionManager(source), settings);
        costCenters = mock(CostCenterMapper.class); checkpoints = mock(SyncCheckpointMapper.class); audits = mock(SyncAuditMapper.class);
        when(costCenters.selectByKeys(anyList())).thenReturn(List.of());
        when(costCenters.selectByIds(anyList())).thenReturn(List.of());
        when(costCenters.batchUpsert(anyList(), eq(false))).thenReturn(1);
        service = new MasterDataPersistenceServiceImpl(costCenters, mock(ExchangeRateMapper.class), checkpoints,
                audits, runs, mock(SyncReconciliationMapper.class), leases, mock(SyncAlertService.class), codec, settings);
        before = SyncCheckpoint.initial(MasterDataType.COST_CENTER)
                .beginBaseline(new SourceAnchorRespDTO(time.minusSeconds(300), 10)).baselineDone(time)
                .openWindow(time.minusSeconds(600), time.plusSeconds(60), time.plusSeconds(180));
        after = before.finishWindow(Instant.now());
        SyncCheckpointDO stored = new SyncCheckpointDO(); stored.setCommittedCursor(codec.json(before));
        when(checkpoints.selectByJob(context.type().jobCode)).thenReturn(stored);
        record = record(1, "CC_01", "研发中心");
    }

    private SourceRecordDTO record(long id, String code, String name) {
        CostCenterDTO data = new CostCenterDTO("A100", code, "1000", name, LocalDate.of(2020,1,1),
                LocalDate.of(2099,12,31), false, true, false);
        return new SourceRecordDTO(MasterDataType.COST_CENTER, codec.businessKey(data), id, time, data, null);
    }

    @Test
    void finalPageCommitsDataWatermarkAuditAndCompletionTogether() throws Exception {
        service.savePage(context, "DELTA_WINDOW", before, after, List.of(record), true);
        var order = inOrder(costCenters, checkpoints, audits, runs, connection);
        order.verify(costCenters).batchUpsert(anyList(), eq(false));
        order.verify(checkpoints).save(eq(context.type().jobCode), eq(codec.json(after)), eq(true));
        order.verify(audits).insertPage(any());
        order.verify(runs).updateProgress(context.runId(), "DELTA_WINDOW", 1, 1, 0);
        order.verify(runs).completeWindow(context.runId());
        order.verify(connection).commit();
        verify(connection, never()).rollback();
        assertThat(after.completedWatermark()).isEqualTo(before.windowEndAt());
    }

    @Test
    void failureWritingSecondRecordRollsBackWithoutAdvancingCheckpoint() throws Exception {
        when(costCenters.batchUpsert(anyList(), eq(false))).thenReturn(1)
                .thenThrow(new DataIntegrityViolationException("second batch failed"));
        assertThatThrownBy(() -> service.savePage(context, "DELTA_WINDOW", before, after,
                List.of(record, record(2,"CC_02","采购部")), true)).isInstanceOf(DataIntegrityViolationException.class);
        verify(costCenters, times(2)).batchUpsert(anyList(), eq(false));
        verify(connection).rollback(); verify(connection, never()).commit();
        verify(checkpoints, never()).save(anyString(), anyString(), anyBoolean());
        verify(audits, never()).insertPage(any());
        verify(runs, never()).completeWindow(any());
    }

    @Test
    void checkpointFailureRollsBackAndSkipsSuccessAuditAndCompletion() throws Exception {
        when(checkpoints.save(anyString(), anyString(), eq(true)))
                .thenThrow(new DataIntegrityViolationException("checkpoint write failed"));
        assertThatThrownBy(() -> service.savePage(context,"DELTA_WINDOW",before,after,List.of(record),true))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(connection).rollback(); verify(connection, never()).commit();
        verify(audits, never()).insertPage(any()); verify(runs, never()).completeWindow(any());
    }

    @Test
    void sameTimestampDifferentActualContentFailsBeforeAnyUpsert() throws Exception {
        CostCenterDO previous = MasterDataAssembler.costCenter(record(1,"CC_01","旧名称"), codec.hash(record));
        // Deliberately keep the incoming hash: comparing the stored hash would miss actual field corruption.
        when(costCenters.selectByIds(List.of(1L))).thenReturn(List.of(previous));
        assertThatThrownBy(() -> service.savePage(context,"DELTA_WINDOW",before,after,List.of(record),true))
                .isInstanceOfSatisfying(IntegrationProblem.class, error -> assertThat(error.code).isEqualTo("SOURCE_TIMESTAMP_CONFLICT"));
        verify(costCenters, never()).batchUpsert(anyList(), anyBoolean());
        verify(checkpoints, never()).save(anyString(), anyString(), anyBoolean());
        verify(connection).rollback();
    }

    @Test
    void conflictingDuplicatesWithinOnePageCannotAdvanceProgress() throws Exception {
        assertThatThrownBy(() -> service.savePage(context,"DELTA_WINDOW",before,after,
                List.of(record,record(1,"CC_01","另一个名称")),true))
                .isInstanceOfSatisfying(IntegrationProblem.class, error -> assertThat(error.code).isEqualTo("SOURCE_TIMESTAMP_CONFLICT"));
        verify(costCenters, never()).batchUpsert(anyList(), anyBoolean());
        verify(checkpoints, never()).save(anyString(), anyString(), anyBoolean());
        verify(connection).rollback();
    }

    @Test
    void staleCheckpointResponseIsRejectedEvenWhileLeaseIsStillOwned() throws Exception {
        SyncCheckpointDO changed = new SyncCheckpointDO();
        changed.setCommittedCursor(codec.json(before.advance(SourceCursorDTO.of(record), Instant.now())));
        when(checkpoints.selectByJob(context.type().jobCode)).thenReturn(changed);
        assertThatThrownBy(() -> service.savePage(context,"DELTA_WINDOW",before,after,List.of(record),true))
                .isInstanceOfSatisfying(IntegrationProblem.class, error -> assertThat(error.code).isEqualTo("CHECKPOINT_CONFLICT"));
        verify(costCenters, never()).batchUpsert(anyList(), anyBoolean());
        verify(checkpoints, never()).save(anyString(), anyString(), anyBoolean());
        verify(connection).rollback();
    }
}
