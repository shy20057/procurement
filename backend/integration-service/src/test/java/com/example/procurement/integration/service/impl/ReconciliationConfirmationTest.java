package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.*;
import com.example.procurement.integration.dao.mapper.*;
import com.example.procurement.integration.dao.typehandler.UuidTypeHandler;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.enums.*;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Missing-source evidence survives run boundaries and is invalidated by a later positive sighting. */
class ReconciliationConfirmationTest {
    private final MasterDataCodec codec=new MasterDataCodec(new ObjectMapper().findAndRegisterModules());
    private final Instant databaseTime=Instant.parse("2026-01-01T00:00:00.123456Z");
    private final JobContext context=new JobContext(UUID.randomUUID(),MasterDataType.COST_CENTER,
            "RECONCILE",TriggerType.MANUAL,"SAP_COST_CENTER_WRITER","node",11L,1);
    private CostCenterMapper centers;
    private SyncReconciliationMapper reconciliation;
    private MasterDataPersistenceServiceImpl service;
    private PlatformTransactionManager manager;
    private SourceRecordDTO current;
    private SyncDifferenceDO difference;

    @BeforeEach
    void setUp() {
        JobRunMapper runs=mock(JobRunMapper.class);
        JobLeaseMapper locks=mock(JobLeaseMapper.class);
        JobRunDO active=new JobRunDO();active.setStatus("RUNNING");active.setOwnerId("node");active.setFenceToken(11L);
        when(runs.lockRun(context.runId())).thenReturn(active);
        when(locks.lockOwned(context.resourceKey(),"node",11L)).thenReturn(11L);
        manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        SyncSettings settings=new SyncSettings(500,200,5,60);
        SyncLeaseServiceImpl leases=new SyncLeaseServiceImpl(runs,locks,manager,settings);
        centers=mock(CostCenterMapper.class);
        reconciliation=mock(SyncReconciliationMapper.class);
        service=new MasterDataPersistenceServiceImpl(centers,mock(ExchangeRateMapper.class),mock(SyncCheckpointMapper.class),
                mock(SyncAuditMapper.class),runs,reconciliation,leases,mock(SyncAlertService.class),codec,settings);
        CostCenterDTO data=new CostCenterDTO("A100","CC_01","1000","采购部",LocalDate.of(2020,1,1),
                LocalDate.of(2099,12,31),false,true,false);
        current=new SourceRecordDTO(context.type(),codec.businessKey(data),9007199254740993L,databaseTime.minusSeconds(300),data,null);
        CostCenterDO target=MasterDataAssembler.costCenter(current,codec.hash(current));
        when(centers.selectByIds(List.of(current.sourceId()))).thenReturn(List.of(target));
        when(centers.selectByKeys(List.of(current.businessKey()))).thenReturn(List.of(target));
        when(reconciliation.databaseNow()).thenReturn(databaseTime);
        difference=new SyncDifferenceDO();difference.setId(17L);difference.setRunId(context.runId());
        difference.setSourceId(current.sourceId());difference.setBusinessKey(current.businessKey());
        difference.setDifferenceType("MISSING_SOURCE");difference.setVerificationStatus("OBSERVED");
        difference.setVerifiedAt(null);
    }

    @Test
    void olderEvidenceFromAnotherRunConfirmsMissingEvenWhenNewDifferenceHasNoVerifiedTime() {
        when(reconciliation.firstAbsence(context.type().jobCode,current.sourceId())).thenReturn(databaseTime.minusSeconds(30));
        assertThat(difference.getVerifiedAt()).isNull();
        assertThat(service.verifyDifference(context,difference,null)).isTrue();
        assertThat(difference.getVerificationStatus()).isEqualTo("CONFIRMED");
        assertThat(difference.getRepairStatus()).isEqualTo("SKIPPED_MISSING_SOURCE");
        assertThat(difference.getRepaired()).isFalse();
        verify(reconciliation).updateDifference(difference);
        verify(centers,never()).batchUpsert(anyList(),anyBoolean());
        verify(manager).commit(any(TransactionStatus.class));
    }

    @Test
    void firstActual404SavesObservedEvidenceAndRequestsAnotherConfirmation() {
        when(reconciliation.firstAbsence(context.type().jobCode,current.sourceId())).thenReturn(null);
        assertThat(service.verifyDifference(context,difference,null)).isFalse();
        assertThat(difference.getVerificationStatus()).isEqualTo("OBSERVED");
        assertThat(difference.getRepairStatus()).isEqualTo("NOT_REQUESTED");
        assertThat(difference.getVerifiedAt()).isNotNull();
        verify(reconciliation).updateDifference(difference);
        verify(reconciliation,never()).insertSeen(any(),anyLong(),anyString(),any(),anyString());
    }

    @Test
    void evidenceOneMicrosecondShortOfThirtySecondsIsStillUnconfirmed() {
        when(reconciliation.firstAbsence(context.type().jobCode,current.sourceId()))
                .thenReturn(databaseTime.minusSeconds(30).plusNanos(1000));
        assertThat(service.verifyDifference(context,difference,null)).isFalse();
        assertThat(difference.getVerificationStatus()).isEqualTo("OBSERVED");
    }

    @Test
    void sourceReappearanceTouchesPositiveObservationBeforeResolvingDifference() {
        assertThat(service.verifyDifference(context,difference,current)).isTrue();
        var order=inOrder(reconciliation);
        order.verify(reconciliation).insertSeen(context.runId(),current.sourceId(),current.businessKey(),current.updatedAt(),codec.hash(current));
        order.verify(reconciliation).updateDifference(difference);
        assertThat(difference.getVerificationStatus()).isEqualTo("RESOLVED_BY_RECHECK");
        verify(reconciliation,never()).firstAbsence(anyString(),anyLong());
        verify(centers,never()).batchUpsert(anyList(),anyBoolean());
    }

    @Test
    void mapperRefreshesPositiveSightingsAndUsesOnlyUnresolvedOrConfirmedAbsenceEvidenceAcrossRuns() throws Exception {
        Configuration configuration=new Configuration();
        configuration.getTypeHandlerRegistry().register(UUID.class,new UuidTypeHandler());
        ClassPathResource resource=new ClassPathResource("mapper/integration/SyncReconciliationMapper.xml");
        try(var stream=resource.getInputStream()) {
            new XMLMapperBuilder(stream,configuration,resource.toString(),configuration.getSqlFragments()).parse();
        }
        String prefix=SyncReconciliationMapper.class.getName()+".";
        String seen=configuration.getMappedStatement(prefix+"insertSeen").getBoundSql(Map.of(
                "runId",context.runId(),"sourceId",current.sourceId(),"businessKey",current.businessKey(),
                "updatedAt",current.updatedAt(),"hash",codec.hash(current))).getSql().replaceAll("\\s+"," ");
        assertThat(seen).contains("on conflict(run_id,source_id) do update", "observed_at=clock_timestamp()");
        String absence=configuration.getMappedStatement(prefix+"firstAbsence")
                .getBoundSql(Map.of("jobCode",context.type().jobCode,"sourceId",current.sourceId())).getSql().replaceAll("\\s+"," ");
        assertThat(absence).contains("d.verification_status in ('OBSERVED','CONFIRMED')",
                "d.verified_at>coalesce", "max(s.observed_at)", "seen_run.job_code=?", "r.job_code=?");
        assertThat(absence).doesNotContain("RESOLVED_BY_RECHECK", "d.run_id=?");
    }
}
