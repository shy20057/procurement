package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.dao.entity.SyncDifferenceDO;
import com.example.procurement.integration.dao.entity.SyncPageAuditDO;
import com.example.procurement.integration.dao.mapper.CostCenterMapper;
import com.example.procurement.integration.dao.mapper.ExchangeRateMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.MasterDataPersistenceService;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncLeaseService;
import com.example.procurement.integration.support.MasterDataAssembler;
import com.example.procurement.integration.support.MasterDataCodec;

import java.util.*;
import org.springframework.stereotype.Service;

/** 页级写入与对账业务。事务由 SyncLeaseService.fenced 统一包围；本类不包含 SQL。 */
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

    public MasterDataPersistenceServiceImpl(CostCenterMapper costCenters, ExchangeRateMapper exchangeRates,
            SyncCheckpointMapper checkpoints, SyncAuditMapper audits, JobRunMapper runs,
            SyncReconciliationMapper reconciliation, SyncLeaseService leases, SyncAlertService alerts,
            MasterDataCodec codec, SyncSettings settings) {
        this.costCenters = costCenters; this.exchangeRates = exchangeRates; this.checkpoints = checkpoints;
        this.audits = audits; this.runs = runs; this.reconciliation = reconciliation;
        this.leases = leases; this.alerts = alerts; this.codec = codec; this.settings = settings;
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    @Override
    public SyncCheckpoint checkpoint(MasterDataType type) {
        SyncCheckpointDO checkpoint = checkpoints.selectByJob(type.jobCode);
        return checkpoint == null || isBlank(checkpoint.getCommittedCursor()) ? SyncCheckpoint.initial(type)
                : codec.read(checkpoint.getCommittedCursor(), SyncCheckpoint.class);
    }

    @Override
    public boolean baselineComplete(MasterDataType type) {
        SyncCheckpointDO checkpoint = checkpoints.selectByJob(type.jobCode);
        return checkpoint != null && Boolean.TRUE.equals(checkpoint.getBaselineComplete());
    }

    @Override
    public void establishSnapshot(JobContext context, SyncCheckpoint checkpoint, long count) {
        leases.fenced(context, () -> {
            checkpoints.save(context.type().jobCode, codec.json(checkpoint), false);
            runs.startBaseline(context.runId(), count);
            return null;
        });
    }

    @Override
    public void savePage(JobContext context, String phase, SyncCheckpoint before, SyncCheckpoint after,
            boolean baselineComplete, List<SourceRecordDTO> records, long newTotal) {
        List<SourceRecordDTO> unique = unique(records);
        for (SourceRecordDTO record : records) codec.validate(record, context.type());
        leases.fenced(context, () -> {
            int applied = write(context.type(), unique, false);
            // 数据、检查点、审计共用这个页事务，失败时全部回滚。
            checkpoints.save(context.type().jobCode, codec.json(after), baselineComplete);
            audit(context, phase, codec.json(before), codec.json(after), records.size(), applied);
            if (newTotal > 0) runs.updateTotal(context.runId(), newTotal);
            return null;
        });
    }

    private List<SourceRecordDTO> unique(List<SourceRecordDTO> records) {
        Map<String, SourceRecordDTO> result = new LinkedHashMap<>();
        for (SourceRecordDTO record : records) {
            SourceRecordDTO previous = result.get(record.businessKey());
            if (previous != null && previous.sourceVersion() == record.sourceVersion()
                    && !codec.hash(previous).equals(codec.hash(record))) {
                throw new IntegrationProblem("SOURCE_VERSION_CONFLICT", "同一业务版本出现不同源内容", false, 422);
            }
            if (previous == null || record.sourceVersion() > previous.sourceVersion()) result.put(record.businessKey(), record);
        }
        return result.values().stream().sorted(Comparator.comparing(SourceRecordDTO::businessKey)).toList();
    }

    private int write(MasterDataType type, List<SourceRecordDTO> records, boolean repair) {
        int applied = 0;
        for (int start = 0; start < records.size(); start += settings.writeBatchSize) {
            List<SourceRecordDTO> batch = records.subList(start, Math.min(records.size(), start + settings.writeBatchSize));
            if (!repair) {
                Map<String, SourceRecordDTO> existing = find(type, batch.stream().map(SourceRecordDTO::businessKey).toList());
                for (SourceRecordDTO record : batch) {
                    SourceRecordDTO previous = existing.get(record.businessKey());
                    if (previous != null && previous.sourceVersion() == record.sourceVersion()
                            && !safeHash(previous).equals(codec.hash(record))) {
                        throw new IntegrationProblem("SOURCE_VERSION_CONFLICT", "同版本数据与本地内容不一致，请先执行对账核查", false, 409);
                    }
                }
            }
            if (type == MasterDataType.COST_CENTER) {
                applied += costCenters.batchUpsert(batch.stream()
                        .map(record -> MasterDataAssembler.costCenter(record, codec.hash(record))).toList(), repair);
            } else {
                applied += exchangeRates.batchUpsert(batch.stream()
                        .map(record -> MasterDataAssembler.exchangeRate(record, codec.hash(record))).toList(), repair);
            }
        }
        return applied;
    }

    private Map<String, SourceRecordDTO> find(MasterDataType type, List<String> keys) {
        Map<String, SourceRecordDTO> result = new LinkedHashMap<>();
        if (keys.isEmpty()) return result;
        List<SourceRecordDTO> records = type == MasterDataType.COST_CENTER
                ? costCenters.selectByKeys(keys).stream().map(MasterDataAssembler::source).toList()
                : exchangeRates.selectByKeys(keys).stream().map(MasterDataAssembler::source).toList();
        for (SourceRecordDTO record : records) result.put(record.businessKey(), record);
        return result;
    }

    private void audit(JobContext context, String phase, String from, String to, int fetched, int applied) {
        SyncPageAuditDO page = new SyncPageAuditDO();
        page.setRunId(context.runId()); page.setPageNo(audits.nextPageNo(context.runId()));
        page.setPhase(phase); page.setFromCursor(from); page.setToCursor(to);
        page.setFetchedCount(fetched); page.setAppliedCount(applied); page.setIgnoredCount(fetched - applied);
        audits.insertPage(page);
        runs.updateProgress(context.runId(), phase, fetched, applied, fetched - applied);
    }

    @Override
    public void startReconciliation(JobContext context, long count) {
        leases.fenced(context, () -> {
            // 每次从新快照重建报告，不推进正常同步检查点。
            reconciliation.deleteSeen(context.runId());
            reconciliation.deleteDifferences(context.runId());
            audits.deletePages(context.runId());
            runs.startReconciliation(context.runId(), count);
            return null;
        });
    }

    @Override
    public void reconcilePage(JobContext context, List<SourceRecordDTO> records, String from, String to) {
        for (SourceRecordDTO record : records) codec.validate(record, context.type());
        leases.fenced(context, () -> {
            int applied = 0;
            for (SourceRecordDTO source : records) {
                reconciliation.insertSeen(context.runId(), source.businessKey());
                SourceRecordDTO target = find(context.type(), List.of(source.businessKey())).get(source.businessKey());
                String difference = null;
                if (target == null) difference = "MISSING_TARGET";
                else if (target.sourceVersion() > source.sourceVersion()) difference = "LOCAL_NEWER";
                else if (!safeHash(target).equals(codec.hash(source)) || target.sourceVersion() != source.sourceVersion()) difference = "CONTENT_DIFFERENT";
                if (difference != null) {
                    boolean repair = "REPAIR".equals(context.mode()) && !"LOCAL_NEWER".equals(difference);
                    if (repair) applied += write(context.type(), List.of(source), true);
                    SyncDifferenceDO row = new SyncDifferenceDO();
                    row.setRunId(context.runId()); row.setBusinessKey(source.businessKey()); row.setDifferenceType(difference);
                    row.setSourceVersion(source.sourceVersion()); row.setTargetVersion(target == null ? null : target.sourceVersion());
                    row.setRepaired(repair);
                    reconciliation.insertDifference(row);
                }
            }
            audit(context, "RECONCILE", from, to, records.size(), applied);
            return null;
        });
    }

    @Override
    public void finishReconciliation(JobContext context) {
        leases.fenced(context, () -> {
            reconciliation.insertMissingSource(context.runId(), context.type().name());
            long count = reconciliation.countUnrepaired(context.runId());
            if (count > 0) alerts.alert(context.type().jobCode, context.runId(), "RECONCILIATION", "发现" + count + "条未修复差异；源端缺失不会触发自动删除");
            else alerts.resolveReconciliation(context.type().jobCode);
            return null;
        });
    }

    private String safeHash(SourceRecordDTO target) {
        try { return codec.hash(target); }
        catch (IntegrationProblem error) { return "INVALID_TARGET"; }
    }
}
