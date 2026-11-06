package com.example.procurement.integration.purchase;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.dao.entity.SyncDifferenceDO;
import com.example.procurement.integration.dao.entity.SyncPageAuditDO;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.dao.mapper.SyncReconciliationMapper;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Anchor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Page;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncLeaseService;
import com.example.procurement.integration.support.MasterDataCodec;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderSyncService {
    private static final Comparator<Cursor> CURSOR_ORDER =
            Comparator.comparing(Cursor::updatedAt).thenComparingLong(Cursor::sourceId);
    private final PurchaseOrderSource source;
    private final PurchaseOrderStore store;
    private final SyncLeaseService leases;
    private final SyncCheckpointMapper checkpoints;
    private final JobRunMapper runs;
    private final SyncAuditMapper audits;
    private final SyncReconciliationMapper differences;
    private final SyncAlertService alerts;
    private final MasterDataCodec codec;
    private final int pageSize;
    private final int overlapSeconds;

    public PurchaseOrderSyncService(PurchaseOrderSource source, PurchaseOrderStore store, SyncLeaseService leases,
            SyncCheckpointMapper checkpoints, JobRunMapper runs, SyncAuditMapper audits,
            SyncReconciliationMapper differences, SyncAlertService alerts, MasterDataCodec codec,
            SyncSettings settings, @Value("${integration.purchase-order.page-size:50}") int pageSize) {
        if (pageSize < 1 || pageSize > 200) throw new IllegalArgumentException("采购订单页大小必须为 1～200");
        this.source = source;
        this.store = store;
        this.leases = leases;
        this.checkpoints = checkpoints;
        this.runs = runs;
        this.audits = audits;
        this.differences = differences;
        this.alerts = alerts;
        this.codec = codec;
        this.pageSize = pageSize;
        this.overlapSeconds = settings.overlapSeconds;
    }

    public void execute(JobContext context, Long repairSourceId) {
        switch (context.mode()) {
            case "AUTO" -> synchronize(context);
            case "RECONCILE" -> reconcile(context);
            case "REPAIR" -> repair(context, repairSourceId);
            default -> throw new IntegrationProblem("INVALID_MODE", "未知采购订单执行模式", false, 422);
        }
    }

    private void synchronize(JobContext context) {
        String job = context.type().jobCode;
        SyncCheckpointDO stored = checkpoints.selectByJob(job);
        if (stored == null) {
            throw new IntegrationProblem("CHECKPOINT_MISSING", "采购订单检查点尚未初始化", false, 409);
        }
        if (!Boolean.TRUE.equals(stored.getBaselineComplete())) {
            baseline(context);
            return;
        }
        PurchaseOrderCheckpoint state = codec.read(stored.getCommittedCursor(), PurchaseOrderCheckpoint.class);
        recordLag(context, state);
        if (state.windowEndAt() == null) {
            Instant now = fetch(context, source::anchor).sourceNow();
            if (now.isBefore(state.completedWatermark())) {
                throw new IntegrationProblem("SOURCE_CLOCK_REGRESSION", "ERP 时间早于已完成水位", true, 503);
            }
            if (now.equals(state.completedWatermark())) {
                leases.fenced(context, () -> runs.noSafeWindow(context.runId()));
                return;
            }
            state = state.open(now);
            PurchaseOrderCheckpoint opened = state;
            leases.fenced(context, () -> checkpoints.save(job, codec.json(opened), true));
        }
        Instant start = state.startAt(overlapSeconds);
        Instant end = state.windowEndAt();
        leases.fenced(context, () -> runs.bindWindow(context.runId(), null, end, stored.getGeneration()));
        for (int pageNo = 1; ; pageNo++) {
            PurchaseOrderCheckpoint before = state;
            Page page = fetch(context, () -> source.changes(start, end, before.pageCursor(), pageSize));
            Cursor last = validateChanges(page, start, end, before.pageCursor());
            PurchaseOrderCheckpoint after = page.hasMore() ? before.advance(last) : before.finish();
            commitPage(context, pageNo, "DELTA", codec.json(before), codec.json(after), page.items(), () -> {
                checkpoints.save(job, codec.json(after), true);
                if (!page.hasMore()) runs.completeWindow(context.runId());
            });
            if (!page.hasMore()) return;
            state = after;
        }
    }

    private void baseline(JobContext context) {
        Anchor anchor = fetch(context, source::anchor);
        leases.fenced(context, () -> runs.startBaseline(context.runId(), 0));
        long last = 0;
        for (int pageNo = 1; ; pageNo++) {
            long afterId = last;
            Page page = fetch(context, () -> source.baseline(afterId, anchor.maxSourceId(), pageSize));
            last = validateBaseline(page, afterId, anchor.maxSourceId());
            commitPage(context, pageNo, "BASELINE", Long.toString(afterId), Long.toString(last), page.items(), () -> {
                if (!page.hasMore()) {
                    var completed = PurchaseOrderCheckpoint.completed(anchor.sourceNow());
                    checkpoints.save(context.type().jobCode, codec.json(completed), true);
                    runs.completeWindow(context.runId());
                }
            });
            if (!page.hasMore()) return;
        }
    }

    private void commitPage(JobContext context, int pageNo, String phase, String before, String after,
            List<PurchaseOrderSnapshot> items, Runnable saveCheckpoint) {
        leases.fenced(context, () -> {
            int applied = store.apply(items, false);
            var audit = new SyncPageAuditDO();
            audit.setRunId(context.runId());
            audit.setPageNo(pageNo);
            audit.setPhase(phase);
            audit.setFromCursor(before);
            audit.setToCursor(after);
            audit.setFetchedCount(items.size());
            audit.setAppliedCount(applied);
            audit.setIgnoredCount(items.size() - applied);
            audits.insertPage(audit);
            runs.updateProgress(context.runId(), phase, items.size(), applied, items.size() - applied);
            saveCheckpoint.run();
            return null;
        });
    }

    private Cursor validateChanges(Page page, Instant start, Instant end, Cursor previous) {
        validatePage(page);
        Cursor last = previous;
        for (PurchaseOrderSnapshot order : page.items()) {
            if (order == null || order.sourceId() <= 0 || order.updatedAt() == null
                    || order.updatedAt().isBefore(start) || !order.updatedAt().isBefore(end)) throw invalidPage();
            Cursor current = new Cursor(order.updatedAt(), order.sourceId());
            if (last != null && CURSOR_ORDER.compare(current, last) <= 0) throw invalidPage();
            last = current;
        }
        return last;
    }

    private long validateBaseline(Page page, long previous, long maxId) {
        validatePage(page);
        long last = previous;
        for (PurchaseOrderSnapshot order : page.items()) {
            if (order == null || order.sourceId() <= last || order.sourceId() > maxId
                    || order.updatedAt() == null) throw invalidPage();
            last = order.sourceId();
        }
        return last;
    }

    private void validatePage(Page page) {
        if (page == null || page.items() == null || page.items().size() > pageSize
                || (page.hasMore() && page.items().isEmpty())) throw invalidPage();
    }

    private void reconcile(JobContext context) {
        SyncCheckpointDO checkpoint = checkpoints.selectByJob(context.type().jobCode);
        if (checkpoint != null && Boolean.TRUE.equals(checkpoint.getBaselineComplete())) {
            recordLag(context, codec.read(checkpoint.getCommittedCursor(), PurchaseOrderCheckpoint.class));
        }
        long maxId = Math.max(fetch(context, source::anchor).maxSourceId(), store.maxSourceId());
        leases.fenced(context, () -> runs.startReconciliation(context.runId(), 0));
        long last = 0;
        while (true) {
            long afterId = last;
            Page page = fetch(context, () -> source.baseline(afterId, maxId, pageSize));
            last = validateBaseline(page, afterId, maxId);
            leases.fenced(context, () -> {
                for (PurchaseOrderSnapshot expected : page.items()) {
                    differences.insertSeen(context.runId(), expected.sourceId(), expected.orderNo(),
                            expected.updatedAt(), store.hash(expected));
                    observeDifference(context, expected, store.find(expected.sourceId()).orElse(null));
                }
                runs.updateProgress(context.runId(), "RECONCILE", page.items().size(), 0, 0);
                return null;
            });
            if (!page.hasMore()) break;
        }

        long missingAfter = 0;
        while (true) {
            List<Long> missing = differences.missingIds(context.runId(), "PURCHASE_ORDER", maxId, missingAfter, pageSize);
            if (missing.isEmpty()) break;
            for (long id : missing) {
                PurchaseOrderSnapshot latest = fetch(context, () -> source.find(id)).orElse(null);
                leases.fenced(context, () -> {
                    observeDifference(context, latest, store.find(id).orElse(null));
                    return null;
                });
            }
            missingAfter = missing.get(missing.size() - 1);
        }
        leases.fenced(context, () -> {
            long count = differences.countDifferences(context.runId());
            if (count == 0) alerts.resolveReconciliation(context.type().jobCode);
            else alerts.alert(context.type().jobCode, context.runId(), "RECONCILIATION", "采购订单存在 " + count + " 条对账差异");
            return null;
        });
    }

    private void recordLag(JobContext context, PurchaseOrderCheckpoint checkpoint) {
        leases.fenced(context, () -> {
            if (checkpoint.completedWatermark().isBefore(differences.databaseNow().minusSeconds(600))) {
                alerts.alert(context.type().jobCode, context.runId(), "STALE", "采购订单完成水位已落后超过 10 分钟");
            }
            return null;
        });
    }

    private void observeDifference(JobContext context, PurchaseOrderSnapshot expected,
            PurchaseOrderStore.TargetOrder target) {
        PurchaseOrderSnapshot actual = target == null ? null : target.snapshot();
        if (expected == null && actual == null) return;
        String sourceHash = expected == null ? null : store.hash(expected);
        String targetHash = actual == null ? null : store.hash(actual);
        if (expected != null && actual != null && matches(expected, target, sourceHash, targetHash)) return;
        var difference = new SyncDifferenceDO();
        PurchaseOrderSnapshot identity = expected == null ? actual : expected;
        difference.setRunId(context.runId());
        difference.setSourceId(identity.sourceId());
        difference.setBusinessKey(identity.orderNo());
        difference.setSourceUpdatedAt(expected == null ? null : expected.updatedAt());
        difference.setTargetUpdatedAt(actual == null ? null : actual.updatedAt());
        difference.setSourceHash(sourceHash);
        difference.setTargetHash(targetHash);
        if (expected == null) difference.setDifferenceType("MISSING_SOURCE");
        else if (actual == null) difference.setDifferenceType("MISSING_TARGET");
        else if (expected.updatedAt().isAfter(actual.updatedAt())) difference.setDifferenceType("TARGET_BEHIND");
        else if (expected.updatedAt().isBefore(actual.updatedAt())) difference.setDifferenceType("TARGET_AHEAD");
        else {
            difference.setDifferenceType("PAYLOAD_MISMATCH");
            if (!target.totalMatches()) difference.setDetail("SRM 订单头总额与有效行汇总不一致");
            else if (!target.storedHashMatches()) difference.setDetail("SRM 保存的摘要与实际订单内容不一致");
        }
        differences.insertDifference(difference);
    }

    private boolean matches(PurchaseOrderSnapshot source, PurchaseOrderStore.TargetOrder target,
            String sourceHash, String targetHash) {
        return target.totalMatches() && target.storedHashMatches()
                && source.updatedAt().equals(target.snapshot().updatedAt()) && Objects.equals(sourceHash, targetHash);
    }

    private void repair(JobContext context, Long id) {
        if (id == null || id <= 0) throw new IntegrationProblem("INVALID_REPAIR_ID", "请提供 ERP 源订单 ID", false, 422);
        var latest = fetch(context, () -> source.find(id)).orElseThrow(() ->
                new IntegrationProblem("SOURCE_ORDER_MISSING", "ERP 无此订单，未执行自动取消或删除", false, 404));
        if (latest.sourceId() != id) throw invalidPage();
        leases.fenced(context, () -> {
            var actual = store.find(id).orElse(null);
            if (actual != null && actual.snapshot().updatedAt().isAfter(latest.updatedAt())) {
                throw new IntegrationProblem("SOURCE_VERSION_OLDER", "ERP 当前版本早于 SRM，无法修复", false, 409);
            }
            observeDifference(context, latest, actual);
            String sourceHash = store.hash(latest);
            boolean alreadyConsistent = actual != null && matches(latest, actual, sourceHash, store.hash(actual.snapshot()));
            int applied = alreadyConsistent ? 0 : store.apply(List.of(latest), true);
            var verified = store.find(id).orElseThrow();
            if (!matches(latest, verified, sourceHash, store.hash(verified.snapshot()))) {
                throw new IntegrationProblem("PO_REPAIR_UNVERIFIED", "采购订单修复后复核未通过", false, 409);
            }
            for (var difference : differences.selectDifferences(context.runId())) {
                difference.setVerificationStatus("CONFIRMED");
                difference.setRepairStatus("REPAIRED");
                difference.setRepaired(true);
                differences.updateDifference(difference);
            }
            differences.resolveOrderDifferences(context.type().jobCode, id, context.runId(), applied > 0);
            runs.updateProgress(context.runId(), "REPAIR", 1, applied, 1 - applied);
            return null;
        });
    }

    private <T> T fetch(JobContext context, Supplier<T> request) {
        if (!leases.renew(context)) {
            throw new IntegrationProblem("LEASE_LOST", "采购订单执行租约已失效", true, 409);
        }
        return request.get();
    }

    private IntegrationProblem invalidPage() {
        return new IntegrationProblem("INVALID_PAGE", "ERP 采购订单页不符合游标契约", false, 422);
    }
}
