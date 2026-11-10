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
import com.example.procurement.integration.domain.context.XxlTriggerMetadata.Parameters;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Anchor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Cursor;
import com.example.procurement.integration.purchase.PurchaseOrderSource.Page;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncLeaseService;
import com.example.procurement.integration.support.SyncJsonCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderSyncService {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSyncService.class);
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
    private final SyncJsonCodec codec;
    private final int pageSize;
    private final int overlapSeconds;

    public PurchaseOrderSyncService(PurchaseOrderSource source, PurchaseOrderStore store, SyncLeaseService leases,
            SyncCheckpointMapper checkpoints, JobRunMapper runs, SyncAuditMapper audits,
            SyncReconciliationMapper differences, SyncAlertService alerts, SyncJsonCodec codec,
            SyncSettings settings) {
        this.source = source;
        this.store = store;
        this.leases = leases;
        this.checkpoints = checkpoints;
        this.runs = runs;
        this.audits = audits;
        this.differences = differences;
        this.alerts = alerts;
        this.codec = codec;
        this.pageSize = settings.orderPageSize;
        this.overlapSeconds = settings.overlapSeconds;
    }

    public void execute(JobContext context, Parameters parameters) {
        switch (context.mode()) {
            case "AUTO" -> synchronize(context);
            case "RECONCILE" -> reconcile(context);
            case "REPAIR" -> repair(context, parameters);
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
                leases.fenced(context, () -> runs.noNewWindow(context.runId()));
                return;
            }
            state = state.open(now);
            PurchaseOrderCheckpoint opened = state;
            leases.fenced(context, () -> checkpoints.save(job, codec.json(opened), true));
        }
        Instant start = state.startAt(overlapSeconds);
        Instant end = state.windowEndAt();
        leases.fenced(context, () -> runs.bindWindow(context.runId(), end, stored.getGeneration()));
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
        leases.fenced(context, () -> runs.startPhase(context.runId(), "BASELINE"));
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
        int applied = leases.fenced(context, () -> {
            int written = store.apply(items, false);
            var audit = new SyncPageAuditDO();
            audit.setRunId(context.runId());
            audit.setPageNo(pageNo);
            audit.setPhase(phase);
            audit.setFromCursor(before);
            audit.setToCursor(after);
            audit.setFetchedCount(items.size());
            audit.setAppliedCount(written);
            audit.setIgnoredCount(items.size() - written);
            audits.insertPage(audit);
            runs.updateProgress(context.runId(), phase, items.size(), written, items.size() - written);
            saveCheckpoint.run();
            return written;
        });
        log.debug("采购订单页已提交 runId={} phase={} page={} fetched={} applied={} from={} to={}",
                context.runId(), phase, pageNo, items.size(), applied, before, after);
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
        leases.fenced(context, () -> runs.startPhase(context.runId(), "RECONCILE"));
        long last = 0;
        while (true) {
            long afterId = last;
            Page page = fetch(context, () -> source.baseline(afterId, maxId, pageSize));
            last = validateBaseline(page, afterId, maxId);
            List<SyncDifferenceDO> verified = new ArrayList<>();
            for (PurchaseOrderSnapshot expected : page.items()) {
                var target = store.find(expected.sourceId()).orElse(null);
                if (compare(context, expected, target) != null) {
                    var difference = recheck(context, expected.sourceId());
                    if (difference != null) verified.add(difference);
                }
            }
            leases.fenced(context, () -> {
                for (PurchaseOrderSnapshot expected : page.items()) {
                    differences.insertSeen(context.runId(), expected.sourceId(), expected.orderNo(),
                            expected.updatedAt(), store.hash(expected));
                }
                verified.forEach(differences::insertDifference);
                runs.updateProgress(context.runId(), "RECONCILE", page.items().size(), 0, 0);
                return null;
            });
            if (!page.hasMore()) break;
        }

        long missingAfter = 0;
        while (true) {
            List<Long> missing = differences.missingIds(context.runId(), maxId, missingAfter, pageSize);
            if (missing.isEmpty()) break;
            List<SyncDifferenceDO> verified = new ArrayList<>();
            for (long id : missing) {
                var difference = recheck(context, id);
                if (difference != null) verified.add(difference);
            }
            leases.fenced(context, () -> {
                verified.forEach(differences::insertDifference);
                return null;
            });
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
            long lagSeconds = Duration.between(checkpoint.completedWatermark(), differences.databaseNow()).getSeconds();
            log.debug("采购订单同步延迟 runId={} lagSeconds={}", context.runId(), lagSeconds);
            if (lagSeconds > 600) {
                alerts.alert(context.type().jobCode, context.runId(), "STALE", "采购订单完成水位已落后超过 10 分钟");
            }
            return null;
        });
    }

    private SyncDifferenceDO recheck(JobContext context, long sourceId) {
        PurchaseOrderSnapshot latest = fetch(context, () -> source.find(sourceId)).orElse(null);
        if (latest != null && latest.sourceId() != sourceId) throw invalidPage();
        return compare(context, latest, store.find(sourceId).orElse(null));
    }

    private SyncDifferenceDO compare(JobContext context, PurchaseOrderSnapshot expected,
            PurchaseOrderStore.TargetOrder target) {
        PurchaseOrderSnapshot actual = target == null ? null : target.snapshot();
        if (expected == null && actual == null) return null;
        String sourceHash = expected == null ? null : store.hash(expected);
        String targetHash = actual == null ? null : store.hash(actual);
        if (expected != null && actual != null && matches(expected, target, sourceHash, targetHash)) return null;
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
        difference.setRepaired(false);
        difference.setRepairStatus("NOT_REQUESTED");
        difference.setVerificationStatus(expected == null ? "OBSERVED" : "CONFIRMED");
        return difference;
    }

    private boolean matches(PurchaseOrderSnapshot source, PurchaseOrderStore.TargetOrder target,
            String sourceHash, String targetHash) {
        return target.totalMatches() && target.storedHashMatches()
                && source.updatedAt().equals(target.snapshot().updatedAt()) && Objects.equals(sourceHash, targetHash);
    }

    private void repair(JobContext context, Parameters parameters) {
        long id;
        if (parameters.sourceId() != null) {
            id = parameters.sourceId();
        } else {
            Long resolved = differences.findOrderSourceId(context.type().jobCode, parameters.differenceId());
            if (resolved == null) {
                throw new IntegrationProblem("DIFFERENCE_NOT_FOUND", "未找到可关联 ERP 订单的对账差异", false, 404);
            }
            id = resolved;
        }
        var latest = fetch(context, () -> source.find(id)).orElseThrow(() ->
                new IntegrationProblem("SOURCE_ORDER_MISSING", "ERP 无此订单，未执行自动取消或删除", false, 404));
        if (latest.sourceId() != id) throw invalidPage();
        leases.fenced(context, () -> {
            var actual = store.find(id).orElse(null);
            if (actual != null && actual.snapshot().updatedAt().isAfter(latest.updatedAt())) {
                throw new IntegrationProblem("SOURCE_VERSION_OLDER", "ERP 当前版本早于 SRM，无法修复", false, 409);
            }
            var difference = compare(context, latest, actual);
            String sourceHash = store.hash(latest);
            int applied = difference == null ? 0 : store.apply(List.of(latest), true);
            var verified = store.find(id).orElseThrow();
            if (!matches(latest, verified, sourceHash, store.hash(verified.snapshot()))) {
                throw new IntegrationProblem("PO_REPAIR_UNVERIFIED", "采购订单修复后复核未通过", false, 409);
            }
            if (difference != null) {
                difference.setRepairStatus("REPAIRED");
                difference.setRepaired(true);
                differences.insertDifference(difference);
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
