package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.client.SapMasterDataClient;
import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.domain.context.*;
import com.example.procurement.integration.domain.enums.*;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.dto.resp.*;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.MasterDataPersistenceService;
import com.example.procurement.integration.support.MasterDataCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MasterDataSyncWindowTest {
    private final SapMasterDataClient source = mock(SapMasterDataClient.class);
    private final MasterDataPersistenceService target = mock(MasterDataPersistenceService.class);
    private final MasterDataCodec codec = new MasterDataCodec(new ObjectMapper().findAndRegisterModules());
    private final Instant watermark = Instant.parse("2026-01-01T00:10:00.123456Z");
    private final JobContext context = new JobContext(UUID.randomUUID(), MasterDataType.COST_CENTER,
            "AUTO", TriggerType.MANUAL,"SAP_COST_CENTER_WRITER","node",1L,1);
    private final AtomicReference<SyncCheckpoint> committed = new AtomicReference<>();
    private MasterDataSyncServiceImpl service;
    private SyncCheckpoint window;

    @BeforeEach
    void setUp() {
        service = new MasterDataSyncServiceImpl(source,target,codec,new SyncSettings(2,2,5,60,120,300,1800));
        window = SyncCheckpoint.initial(context.type()).beginBaseline(new SourceAnchorRespDTO(watermark,99))
                .baselineDone(watermark).openWindow(watermark.minusSeconds(300),watermark.plusSeconds(300),watermark.plusSeconds(420));
        committed.set(window);
        when(target.checkpoint(context.type())).thenAnswer(call -> committed.get());
        doAnswer(call -> {committed.set(call.getArgument(2));return null;})
                .when(target).saveCheckpoint(eq(context),any(),any());
        doAnswer(call -> {committed.set(call.getArgument(3));return null;})
                .when(target).savePage(eq(context),anyString(),any(),any(),anyList(),anyBoolean());
    }
    private SourceRecordDTO row(long id,Instant time) {
        CostCenterDTO data = new CostCenterDTO("A100","CC_"+id,"1000","采购部",LocalDate.of(2020,1,1),
                LocalDate.of(2099,12,31),false,true,false);
        return new SourceRecordDTO(context.type(),codec.businessKey(data),id,time,data,null);
    }
    private SourcePageRespDTO page(boolean more,SourceRecordDTO... records) {
        return new SourcePageRespDTO(List.of(records),records.length==0?null:SourceCursorDTO.of(records[records.length-1]),more);
    }
    private void reply(SourceCursorDTO cursor,SourcePageRespDTO response) {
        when(source.changes(context.type(),window.windowStartAt(),window.windowEndAt(),cursor,2)).thenReturn(response);
    }

    @Test
    void equalMicrosecondRowsAcrossPagesPreserveLargeIdAndOnlyFinishAtLastPage() {
        Instant shared = watermark.plusNanos(1000);
        SourceRecordDTO first=row(9007199254740993L,shared), second=row(9007199254740994L,shared);
        reply(null,page(true,first)); reply(SourceCursorDTO.of(first),page(false,second));
        service.execute(context);
        var beforePages = org.mockito.ArgumentCaptor.forClass(SyncCheckpoint.class);
        var afterPages = org.mockito.ArgumentCaptor.forClass(SyncCheckpoint.class);
        verify(target,times(2)).savePage(eq(context),eq("DELTA_WINDOW"),beforePages.capture(),afterPages.capture(),anyList(),anyBoolean());
        SyncCheckpoint partial=afterPages.getAllValues().get(0), complete=afterPages.getAllValues().get(1);
        assertThat(partial.completedWatermark()).isEqualTo(watermark);
        assertThat(partial.pageCursor()).isEqualTo(new SourceCursorDTO(shared,9007199254740993L));
        assertThat(beforePages.getAllValues().get(1)).isEqualTo(partial);
        assertThat(complete.completedWatermark()).isEqualTo(window.windowEndAt());
        assertThat(complete.activeWindowId()).isNull();
        verify(source).changes(context.type(),window.windowStartAt(),window.windowEndAt(),SourceCursorDTO.of(first),2);
    }

    @Test
    void failedSecondPageKeepsFirstPageCursorAndOriginalWindowForRetry() {
        SourceRecordDTO first=row(1,watermark), second=row(2,watermark);
        reply(null,page(true,first)); reply(SourceCursorDTO.of(first),page(false,second));
        doAnswer(call -> {
            List<SourceRecordDTO> rows=call.getArgument(4);
            if(rows.get(0).sourceId()==2) throw new IllegalStateException("page transaction failed");
            committed.set(call.getArgument(3));return null;
        }).when(target).savePage(eq(context),anyString(),any(),any(),anyList(),anyBoolean());
        assertThatThrownBy(() -> service.execute(context)).hasMessage("page transaction failed");
        assertThat(committed.get().pageCursor()).isEqualTo(SourceCursorDTO.of(first));
        assertThat(committed.get().completedWatermark()).isEqualTo(watermark);
        assertThat(committed.get().activeWindowId()).isEqualTo(window.activeWindowId());
        clearInvocations(source);
        assertThatThrownBy(() -> service.execute(context)).hasMessage("page transaction failed");
        verify(source).changes(context.type(),window.windowStartAt(),window.windowEndAt(),SourceCursorDTO.of(first),2);
        verify(source,never()).anchor(any());
        verify(source,never()).changes(context.type(),window.windowStartAt(),window.windowEndAt(),null,2);
    }

    @Test
    void acceptsInclusiveStartAndLastMicrosecondBeforeExclusiveEnd() {
        reply(null,page(false,row(9,window.windowStartAt()),row(1,window.windowEndAt().minusNanos(1000))));
        service.execute(context);
        assertThat(committed.get().completedWatermark()).isEqualTo(window.windowEndAt());
    }

    @Test
    void rejectsExclusiveEndWithoutCommittingAnyPage() {
        reply(null,page(false,row(1,window.windowEndAt())));
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(IntegrationProblem.class);
        verify(target,never()).savePage(any(),anyString(),any(),any(),anyList(),anyBoolean());
        assertThat(committed.get().completedWatermark()).isEqualTo(watermark);
    }

    @Test
    void rejectsSameTimeDecreasingIdsAndForgedEmptyPageCursor() {
        reply(null,page(false,row(2,watermark),row(1,watermark)));
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(IntegrationProblem.class);
        reply(null,new SourcePageRespDTO(List.of(),new SourceCursorDTO(watermark,4),false));
        assertThatThrownBy(() -> service.execute(context)).isInstanceOf(IntegrationProblem.class);
        verify(target,never()).savePage(any(),anyString(),any(),any(),anyList(),anyBoolean());
    }

    @Test
    void emptyBaselineAndEmptyDeltaStillAdvanceCompletedWatermark() {
        committed.set(SyncCheckpoint.initial(context.type()));
        when(source.anchor(context.type())).thenReturn(new SourceAnchorRespDTO(watermark,0),
                new SourceAnchorRespDTO(watermark.plusSeconds(600),0));
        when(source.fullPage(context.type(),0,0,2)).thenReturn(page(false));
        when(source.changes(context.type(),watermark.minusSeconds(300),watermark.plusSeconds(480),null,2)).thenReturn(page(false));
        service.execute(context);
        assertThat(committed.get().baselineComplete()).isTrue();
        assertThat(committed.get().baselineMaxSourceId()).isZero();
        assertThat(committed.get().completedWatermark()).isEqualTo(watermark.plusSeconds(480));
        verify(target).savePage(eq(context),eq("BASELINE_SCAN"),any(),any(),eq(List.of()),eq(false));
        verify(target).savePage(eq(context),eq("DELTA_WINDOW"),any(),any(),eq(List.of()),eq(true));
    }
}
