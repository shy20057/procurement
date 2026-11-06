package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.client.SapMasterDataClient;
import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.SyncDifferenceDO;
import com.example.procurement.integration.domain.context.*;
import com.example.procurement.integration.dto.domain.*;
import com.example.procurement.integration.dto.resp.*;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.*;
import com.example.procurement.integration.support.MasterDataCodec;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 当前状态同步：固定窗口内按修改时间与源ID分页，最终一致而非变更历史。
 */
@Service
public class MasterDataSyncServiceImpl implements MasterDataSyncService {
    private final SapMasterDataClient source;
    private final MasterDataPersistenceService target;
    private final MasterDataCodec codec;
    private final SyncSettings settings;

    public MasterDataSyncServiceImpl(SapMasterDataClient source, MasterDataPersistenceService target,
                                     MasterDataCodec codec, SyncSettings settings) {
        this.source = source;
        this.target = target;
        this.codec = codec;
        this.settings = settings;
    }

    public void execute(JobContext context) {
        if (!"AUTO".equals(context.mode())) {
            reconcile(context);
            return;
        }
        // 最后页已提交但运行终态未写完时，只补齐终态，不为同一次运行新建窗口。
        if (target.runComplete(context)) return;
        SyncCheckpoint cp = target.checkpoint(context.type());
        if (!cp.baselineComplete()) { // 基线未完成，则建立基线
            if (cp.baselineAnchorAt() == null) { // 锚点未取，进行锚点初始化
                SourceAnchorRespDTO anchor = anchor(context); // 去SAP取一次锚点，把基线扫描的边界写进checkpoint
                SyncCheckpoint after = cp.beginBaseline(anchor); // 把 checkpoint 从初始态变成锚定态：
                target.saveCheckpoint(context, cp, after);
                cp = after;
            }
            while (!cp.baselineComplete()) { // 跑N次，建立基线，完成全量同步
                // 从SAP取一页数据
                SourcePageRespDTO page = source.fullPage(
                        context.type(),
                        cp.baselineLastSourceId(),
                        cp.baselineMaxSourceId(),  // 扫描上届锁死
                        settings.pageSize);
                // 校验合规性
                validateFull(context, page, cp.baselineLastSourceId(), cp.baselineMaxSourceId());
                // 算下一页起点
                long next = page.items().isEmpty() ? cp.baselineLastSourceId() : last(page).sourceId();
                // 推进checkpoint
                SyncCheckpoint after = cp.baselinePage(next, Instant.now());
                // 如果是最后一页，则完成基线
                if (!page.hasMore()) after = after.baselineDone(Instant.now());
                // 数据落库+持久化checkpoint
                target.savePage(context, "BASELINE_SCAN", cp, after, page.items(), false);
                cp = after;
            }
        } // 增量同步开窗的核心逻辑
        if (cp.activeWindowId() == null) { // 新开窗口
            SourceAnchorRespDTO anchor = anchor(context);
            if (cp.sourceTimeHighwater() != null && anchor.sourceNow().isBefore(cp.sourceTimeHighwater().minusSeconds(5)))
                throw new IntegrationProblem("SOURCE_TIME_REGRESSION", "源时间较已观测时间明显倒退，请核查源时钟", false, 409);
            Instant end = anchor.sourceNow().minusSeconds(settings.settleDelaySeconds);
            if (!end.isAfter(cp.completedWatermark())) {
                target.saveCheckpoint(context, cp, cp.observe(anchor.sourceNow()));
                return; // 源时间尚无可推进的安全窗口，不倒退水位。
            }
            Instant start = cp.completedWatermark().minusSeconds(settings.overlapSeconds);
            if (start.isBefore(Instant.EPOCH)) start = Instant.EPOCH;
            Instant high = cp.sourceTimeHighwater() != null && cp.sourceTimeHighwater().isAfter(anchor.sourceNow())
                    ? cp.sourceTimeHighwater() : anchor.sourceNow();
            SyncCheckpoint after = cp.openWindow(start, end, high);
            target.saveCheckpoint(context, cp, after);
            cp = after;
        } else { // 恢复已有窗口
            SyncCheckpoint after = cp;
            if (cp.lastPageAt() != null && cp.lastPageAt().isBefore(Instant.now().minusSeconds(settings.resumeRewindSeconds)))
                after = cp.rewind(Instant.now());
            // 新补偿run绑定旧活动窗口，保存上下文不改变完整水位。
            target.saveCheckpoint(context, cp, after);
            cp = after;
        }
        while (true) {
            SourcePageRespDTO page = source.changes(context.type(), cp.windowStartAt(), cp.windowEndAt(), cp.pageCursor(), settings.pageSize);
            validateDelta(context, page, cp);
            SyncCheckpoint after = page.hasMore() ? cp.advance(page.nextCursor(), Instant.now()) : cp.finishWindow(Instant.now());
            target.savePage(context, "DELTA_WINDOW", cp, after, page.items(), !page.hasMore());
            cp = after;
            if (!page.hasMore()) return;
        }
    }

    private void reconcile(JobContext context) {
        ReconciliationScan scan = target.scan(context);
        if (scan != null && scan.complete()) return;
        if (scan == null) {
            SourceAnchorRespDTO anchor = anchor(context);
            // 把本地已知最高ID也纳入，才能发现源端最高ID被物理删除或源库被清空。
            long upper = Math.max(anchor.maxSourceId(), target.targetMaxSourceId(context.type()));
            scan = new ReconciliationScan(anchor.sourceNow(), upper, 0, false, 0, false, false);
            target.startScan(context, scan);
        }
        while (!scan.scanComplete()) {
            SourcePageRespDTO page = source.fullPage(context.type(), scan.lastSourceId(), scan.maxSourceId(), settings.pageSize);
            validateFull(context, page, scan.lastSourceId(), scan.maxSourceId());
            long next = page.items().isEmpty() ? scan.lastSourceId() : last(page).sourceId();
            scan = scan.page(next, !page.hasMore());
            target.inspectPage(context, scan, page.items());
        }
        while (!scan.missingComplete()) {
            List<Long> ids = target.missingIds(context, scan, settings.pageSize);
            long next = ids.isEmpty() ? scan.missingAfterId() : ids.get(ids.size() - 1);
            scan = scan.missing(next, ids.size() < settings.pageSize);
            target.findMissing(context, scan, ids);
        }
        long after = 0;
        boolean waiting = false;
        while (true) {
            List<SyncDifferenceDO> differences = target.pendingDifferences(context, after, 100);
            if (differences.isEmpty()) break;
            for (SyncDifferenceDO diff : differences) {
                SourceRecordDTO fresh = source.find(context.type(), diff.getSourceId());
                if (fresh != null) {
                    codec.validate(fresh, context.type());
                    codec.require(fresh.sourceId() == diff.getSourceId(), "源点查响应ID不匹配");
                }
                if (!target.verifyDifference(context, diff, fresh)) waiting = true;
                after = diff.getId();
            }
        }
        if (waiting)
            throw new IntegrationProblem("RECONCILIATION_CONFIRM_WAIT", "源端缺失需至少30秒后再次确认，复核进度已保存", true, 503);
        target.finishReconciliation(context, scan);
    }

    private SourceAnchorRespDTO anchor(JobContext context) {
        SourceAnchorRespDTO a = source.anchor(context.type());
        codec.require(a != null && a.sourceNow() != null && a.maxSourceId() >= 0, "源端锚点不合法");
        return a;
    }

    private void validateBasic(JobContext context, SourcePageRespDTO page) {
        codec.require(page != null && page.items() != null && page.items().size() <= settings.pageSize, "源端未遵守分页大小约定");
        codec.require(!page.hasMore() || !page.items().isEmpty(), "源端空页不能声明还有后续");
        for (SourceRecordDTO record : page.items()) codec.validate(record, context.type());
        if (page.items().isEmpty()) codec.require(page.nextCursor() == null, "源端空页不能虚构游标");
        else codec.require(SourceCursorDTO.of(last(page)).equals(page.nextCursor()), "源端返回游标不等于最后一行");
    }

    private void validateFull(JobContext context, SourcePageRespDTO page, long after, long max) {
        validateBasic(context, page);
        long previous = after;
        for (SourceRecordDTO record : page.items()) {
            codec.require(record.sourceId() > previous && record.sourceId() <= max, "全量ID分页未前进或超出上界");
            previous = record.sourceId();
        }
    }

    private void validateDelta(JobContext context, SourcePageRespDTO page, SyncCheckpoint cp) {
        validateBasic(context, page);
        SourceCursorDTO previous = cp.pageCursor();
        for (SourceRecordDTO record : page.items()) {
            codec.require(!record.updatedAt().isBefore(cp.windowStartAt()) && record.updatedAt().isBefore(cp.windowEndAt()), "记录超出固定时间窗口");
            SourceCursorDTO cursor = SourceCursorDTO.of(record);
            codec.require(previous == null || cursor.compareTo(previous) > 0, "时间与ID组合分页没有严格前进");
            previous = cursor;
        }
    }

    private SourceRecordDTO last(SourcePageRespDTO page) {
        return page.items().get(page.items().size() - 1);
    }
}
