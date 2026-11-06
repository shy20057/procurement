package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.*;
import com.example.procurement.integration.dao.mapper.*;
import com.example.procurement.integration.domain.context.*;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.*;
import com.example.procurement.integration.support.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** 每个写入入口均在数据集执行权事务内；源HTTP由编排层在事务外调用。 */
@Service
public class MasterDataPersistenceServiceImpl implements MasterDataPersistenceService {
    private final CostCenterMapper costCenters;
    private final ExchangeRateMapper exchangeRates;
    private final SyncCheckpointMapper checkpoints;
    private final SyncAuditMapper audits;
    private final JobRunMapper runs;
    private final SyncReconciliationMapper reconciliation;
    private final SyncLeaseService leases;
    private final SyncAlertService alerts;
    private final MasterDataCodec codec;
    private final SyncSettings settings;

    public MasterDataPersistenceServiceImpl(CostCenterMapper costCenters,ExchangeRateMapper exchangeRates,
            SyncCheckpointMapper checkpoints,SyncAuditMapper audits,JobRunMapper runs,
            SyncReconciliationMapper reconciliation,SyncLeaseService leases,SyncAlertService alerts,
            MasterDataCodec codec,SyncSettings settings) {
        this.costCenters=costCenters;this.exchangeRates=exchangeRates;this.checkpoints=checkpoints;
        this.audits=audits;this.runs=runs;this.reconciliation=reconciliation;this.leases=leases;
        this.alerts=alerts;this.codec=codec;this.settings=settings;
    }
    public SyncCheckpoint checkpoint(MasterDataType type) {
        SyncCheckpointDO row=checkpoints.selectByJob(type.jobCode);
        if(row==null||row.getCommittedCursor()==null||row.getCommittedCursor().isBlank()) {
            long generation=row==null||row.getGeneration()==null?1:row.getGeneration();
            return new SyncCheckpoint("TIME_WINDOW_V2",generation,false,null,0,0,null,null,null,null,null,null,null,0);
        }
        SyncCheckpoint cp=codec.read(row.getCommittedCursor(),SyncCheckpoint.class);
        if(!"TIME_WINDOW_V2".equals(cp.mode())) throw problem("CHECKPOINT_MODE_MISMATCH","旧模式检查点不能用于时间窗口同步");
        return cp;
    }
    public boolean runComplete(JobContext context) {
        JobRunDO run=runs.selectById(context.runId());
        return run!=null&&Boolean.TRUE.equals(run.getCaughtUp());
    }
    private void expectCheckpoint(JobContext context,SyncCheckpoint before) {
        if(!codec.json(checkpoint(context.type())).equals(codec.json(before)))
            throw problem("CHECKPOINT_CONFLICT","检查点已变化，拒绝提交旧响应");
    }
    public void saveCheckpoint(JobContext context,SyncCheckpoint before,SyncCheckpoint after) {
        leases.fenced(context,()->{
            expectCheckpoint(context,before);
            checkpoints.save(context.type().jobCode,codec.json(after),after.baselineComplete());
            if(after.activeWindowId()!=null) runs.bindWindow(context.runId(),after.activeWindowId(),after.windowEndAt(),after.generation());
            else if(!after.baselineComplete()) runs.startBaseline(context.runId(),0);
            else runs.noSafeWindow(context.runId());
            return null;
        });
    }
    public void savePage(JobContext context,String phase,SyncCheckpoint before,SyncCheckpoint after,
            List<SourceRecordDTO> records,boolean windowComplete) {
        for(SourceRecordDTO record:records) codec.validate(record,context.type());
        leases.fenced(context,()->{
            expectCheckpoint(context,before);
            int applied=write(context.type(),records,false);
            if (!records.isEmpty()) {
                Set<Long> previouslyMissing = new HashSet<>(reconciliation.idsWithAbsence(
                        context.type().jobCode, records.stream().map(SourceRecordDTO::sourceId).distinct().toList()));
                for (SourceRecordDTO record : records) {
                    if (previouslyMissing.contains(record.sourceId())) {
                        reconciliation.insertSeen(context.runId(), record.sourceId(), record.businessKey(),
                                record.updatedAt(), codec.hash(record));
                    }
                }
            }
            checkpoints.save(context.type().jobCode,codec.json(after),after.baselineComplete());
            audit(context,phase,codec.json(before),codec.json(after),records.size(),applied);
            if(windowComplete) runs.completeWindow(context.runId());
            return null;
        });
    }

    private int write(MasterDataType type,List<SourceRecordDTO> source,boolean repair) {
        Map<Long,SourceRecordDTO> unique=new TreeMap<>();
        for(SourceRecordDTO record:source) {
            SourceRecordDTO prev=unique.get(record.sourceId());
            if(prev!=null&&prev.updatedAt().equals(record.updatedAt())&&!codec.hash(prev).equals(codec.hash(record)))
                throw problem("SOURCE_TIMESTAMP_CONFLICT","相同源ID及修改时间出现不同内容");
            if(prev==null||record.updatedAt().isAfter(prev.updatedAt())) unique.put(record.sourceId(),record);
        }
        List<SourceRecordDTO> records=new ArrayList<>(unique.values()); int applied=0;
        for(int start=0;start<records.size();start+=settings.writeBatchSize) {
            List<SourceRecordDTO> batch=records.subList(start,Math.min(records.size(),start+settings.writeBatchSize));
            Map<Long,SourceRecordDTO> existing=byIds(type,batch.stream().map(SourceRecordDTO::sourceId).toList());
            Map<String,SourceRecordDTO> identities=byKeys(type,batch.stream().map(SourceRecordDTO::businessKey).toList());
            for(SourceRecordDTO record:batch) {
                SourceRecordDTO previous=existing.get(record.sourceId()),sameKey=identities.get(record.businessKey());
                if((previous!=null&&!previous.businessKey().equals(record.businessKey()))
                        ||(sameKey!=null&&sameKey.sourceId()!=record.sourceId()))
                    throw problem("SOURCE_IDENTITY_CONFLICT","源ID与业务唯一键映射冲突");
                if(!repair&&previous!=null&&previous.updatedAt().equals(record.updatedAt())
                        &&!safeHash(previous).equals(codec.hash(record)))
                    throw problem("SOURCE_TIMESTAMP_CONFLICT","相同修改时间与本地内容不同，请对账复核");
            }
            if(type==MasterDataType.COST_CENTER) applied+=costCenters.batchUpsert(
                    batch.stream().map(r->MasterDataAssembler.costCenter(r,codec.hash(r))).toList(),repair);
            else applied+=exchangeRates.batchUpsert(
                    batch.stream().map(r->MasterDataAssembler.exchangeRate(r,codec.hash(r))).toList(),repair);
        }
        return applied;
    }
    private Map<Long,SourceRecordDTO> byIds(MasterDataType type,List<Long> ids) {
        Map<Long,SourceRecordDTO> result=new HashMap<>();if(ids.isEmpty())return result;
        if(type==MasterDataType.COST_CENTER) for(CostCenterDO row:costCenters.selectByIds(ids)) {
            SourceRecordDTO r=MasterDataAssembler.source(row,codec);result.put(r.sourceId(),r);
        } else for(ExchangeRateDO row:exchangeRates.selectByIds(ids)) {
            SourceRecordDTO r=MasterDataAssembler.source(row,codec);result.put(r.sourceId(),r);
        }
        return result;
    }
    private Map<String,SourceRecordDTO> byKeys(MasterDataType type,List<String> keys) {
        Map<String,SourceRecordDTO> result=new HashMap<>();if(keys.isEmpty())return result;
        if(type==MasterDataType.COST_CENTER) for(CostCenterDO row:costCenters.selectByKeys(keys)) {
            SourceRecordDTO r=MasterDataAssembler.source(row,codec);result.put(r.businessKey(),r);
        } else for(ExchangeRateDO row:exchangeRates.selectByKeys(keys)) {
            SourceRecordDTO r=MasterDataAssembler.source(row,codec);result.put(r.businessKey(),r);
        }
        return result;
    }
    private void audit(JobContext context,String phase,String from,String to,int fetched,int applied) {
        SyncPageAuditDO page=new SyncPageAuditDO();page.setRunId(context.runId());
        page.setPageNo(audits.nextPageNo(context.runId()));page.setPhase(phase);
        page.setFromCursor(from);page.setToCursor(to);page.setFetchedCount(fetched);
        page.setAppliedCount(applied);page.setIgnoredCount(fetched-applied);
        audits.insertPage(page);runs.updateProgress(context.runId(),phase,fetched,applied,fetched-applied);
    }

    public ReconciliationScan scan(JobContext context) {
        JobRunDO row=runs.selectById(context.runId());
        return row==null||row.getScanContext()==null?null:codec.read(row.getScanContext(),ReconciliationScan.class);
    }
    public long targetMaxSourceId(MasterDataType type) {
        return type==MasterDataType.COST_CENTER?costCenters.maxSourceId():exchangeRates.maxSourceId();
    }
    public void startScan(JobContext context,ReconciliationScan scan) {
        leases.fenced(context,()->{
            reconciliation.deleteSeen(context.runId());reconciliation.deleteDifferences(context.runId());
            runs.startReconciliation(context.runId(),0);runs.saveScanContext(context.runId(),codec.json(scan));
            return null;
        });
    }
    public void inspectPage(JobContext context,ReconciliationScan scan,List<SourceRecordDTO> records) {
        leases.fenced(context,()->{
            Map<Long,SourceRecordDTO> targets=byIds(context.type(),records.stream().map(SourceRecordDTO::sourceId).toList());
            Map<String,SourceRecordDTO> keys=byKeys(context.type(),records.stream().map(SourceRecordDTO::businessKey).toList());
            for(SourceRecordDTO source:records) {
                reconciliation.insertSeen(context.runId(),source.sourceId(),source.businessKey(),source.updatedAt(),codec.hash(source));
                SourceRecordDTO target=targets.get(source.sourceId()),identity=keys.get(source.businessKey());
                String difference=classify(source,target);
                if(identity!=null&&identity.sourceId()!=source.sourceId())difference="SOURCE_IDENTITY_CONFLICT";
                if(difference!=null) reconciliation.insertDifference(difference(context,source,target,difference));
            }
            audit(context,"RECONCILE_SCAN",null,codec.json(scan),records.size(),0);
            runs.saveScanContext(context.runId(),codec.json(scan));return null;
        });
    }
    public List<Long> missingIds(JobContext context,ReconciliationScan scan,int limit) {
        return reconciliation.missingIds(context.runId(),context.type().name(),scan.maxSourceId(),scan.missingAfterId(),limit);
    }
    public void findMissing(JobContext context,ReconciliationScan scan,List<Long> ids) {
        leases.fenced(context,()->{
            for(SourceRecordDTO target:byIds(context.type(),ids).values())
                reconciliation.insertDifference(difference(context,null,target,"MISSING_SOURCE"));
            runs.saveScanContext(context.runId(),codec.json(scan));return null;
        });
    }
    public List<SyncDifferenceDO> pendingDifferences(JobContext context,long after,int limit) {
        return reconciliation.pendingDifferences(context.runId(),after,limit);
    }
    /** false 表示第一次有效404还需跨至少30秒再次确认，状态已持久化。 */
    public boolean verifyDifference(JobContext context,SyncDifferenceDO diff,SourceRecordDTO fresh) {
        return leases.fenced(context,()->{
            SourceRecordDTO target=byIds(context.type(),List.of(diff.getSourceId())).get(diff.getSourceId());
            if(fresh==null&&target!=null) {
                Instant observedNow=reconciliation.databaseNow();
                Instant first=reconciliation.firstAbsence(context.type().jobCode,diff.getSourceId());
                if(first==null||observedNow.isBefore(first.plusSeconds(30))) {
                    diff.setDifferenceType("MISSING_SOURCE");
                    diff.setVerifiedAt(Instant.now());diff.setDetail("已有效点查404；缺失观察跨运行保留，至少间隔30秒复核");
                    diff.setVerificationStatus("OBSERVED");diff.setRepairStatus("NOT_REQUESTED");diff.setRepaired(false);
                    reconciliation.updateDifference(diff);return false;
                }
                diff.setDifferenceType("MISSING_SOURCE");diff.setVerificationStatus("CONFIRMED");
                diff.setRepairStatus("SKIPPED_MISSING_SOURCE");diff.setRepaired(false);
                diff.setDetail("两次有效404确认源端缺失；保留本地历史数据");
            } else {
                String kind=fresh==null?null:classify(fresh,target);
                if(fresh!=null) {
                    codec.validate(fresh,context.type());
                    reconciliation.insertSeen(context.runId(),fresh.sourceId(),fresh.businessKey(),fresh.updatedAt(),codec.hash(fresh));
                    diff.setSourceUpdatedAt(fresh.updatedAt());diff.setSourceHash(codec.hash(fresh));
                    Map<String,SourceRecordDTO> identities=byKeys(context.type(),List.of(fresh.businessKey()));
                    SourceRecordDTO identity=identities.get(fresh.businessKey());
                    if(!diff.getBusinessKey().equals(fresh.businessKey())||
                            (identity!=null&&identity.sourceId()!=fresh.sourceId()))kind="SOURCE_IDENTITY_CONFLICT";
                }
                diff.setTargetUpdatedAt(target==null?null:target.updatedAt());
                diff.setTargetHash(target==null?null:safeHash(target));diff.setRepaired(false);
                if(kind==null) {
                    diff.setVerificationStatus("RESOLVED_BY_RECHECK");diff.setRepairStatus("NOT_REQUESTED");
                    diff.setDetail("复核时差异已消失");
                } else {
                    diff.setDifferenceType(kind);diff.setVerificationStatus("CONFIRMED");
                    if("LOCAL_NEWER".equals(kind)||"SOURCE_IDENTITY_CONFLICT".equals(kind)) {
                        diff.setRepairStatus("LOCAL_NEWER".equals(kind)?"SKIPPED_LOCAL_NEWER":"CONFLICT");
                        diff.setDetail("保护较新状态或身份映射，未自动覆盖");
                    } else if("REPAIR".equals(context.mode())) {
                        int applied=write(context.type(),List.of(fresh),true);
                        diff.setRepaired(applied>0);diff.setRepairStatus(applied>0?"REPAIRED":"CONFLICT");
                        diff.setDetail("基于最新源行复核修复；后续变化仍由增量和对账收敛");
                        audit(context,"REPAIR_VERIFY",null,codec.json(diff),1,applied);
                    } else diff.setRepairStatus("NOT_REQUESTED");
                }
                diff.setVerifiedAt(Instant.now());
            }
            reconciliation.updateDifference(diff);return true;
        });
    }
    public void finishReconciliation(JobContext context,ReconciliationScan scan) {
        leases.fenced(context,()->{
            long remaining=reconciliation.countUnrepaired(context.runId());
            if(remaining>0) alerts.alert(context.type().jobCode,context.runId(),"RECONCILIATION","发现"+remaining+"条未修复差异");
            else alerts.resolveReconciliation(context.type().jobCode);
            runs.saveScanContext(context.runId(),codec.json(scan.finished()));return null;
        });
    }
    private SyncDifferenceDO difference(JobContext context,SourceRecordDTO source,SourceRecordDTO target,String kind) {
        SyncDifferenceDO d=new SyncDifferenceDO();d.setRunId(context.runId());
        d.setSourceId(source!=null?source.sourceId():target.sourceId());
        d.setBusinessKey(source!=null?source.businessKey():target.businessKey());d.setDifferenceType(kind);
        d.setSourceUpdatedAt(source==null?null:source.updatedAt());d.setTargetUpdatedAt(target==null?null:target.updatedAt());
        d.setSourceHash(source==null?null:codec.hash(source));d.setTargetHash(target==null?null:safeHash(target));
        d.setDetail("当前状态扫描观察到的差异，待点查复核");return d;
    }
    private String classify(SourceRecordDTO source,SourceRecordDTO target) {
        if(target==null)return "MISSING_TARGET";
        if(!source.businessKey().equals(target.businessKey()))return "SOURCE_IDENTITY_CONFLICT";
        if(target.updatedAt().isAfter(source.updatedAt()))return "LOCAL_NEWER";
        if(target.updatedAt().isBefore(source.updatedAt()))return "TARGET_STALE";
        return safeHash(target).equals(codec.hash(source))?null:"CONTENT_DIFFERENT";
    }
    private String safeHash(SourceRecordDTO record) {
        try{return codec.hash(record);}catch(RuntimeException invalid){return "INVALID_TARGET";}
    }
    private IntegrationProblem problem(String code,String message) {
        return new IntegrationProblem(code,message,false,409);
    }
}
