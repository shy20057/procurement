package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.client.SapMasterDataClient;
import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.SyncCheckpoint;
import com.example.procurement.integration.dto.domain.SourceRecordDTO;
import com.example.procurement.integration.dto.resp.SnapshotRespDTO;
import com.example.procurement.integration.dto.resp.SourcePageRespDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.MasterDataPersistenceService;
import com.example.procurement.integration.service.MasterDataSyncService;
import com.example.procurement.integration.support.MasterDataCodec;

import org.springframework.stereotype.Service;

@Service
public class MasterDataSyncServiceImpl implements MasterDataSyncService {
    private final SapMasterDataClient source;
    private final MasterDataPersistenceService target;
    private final MasterDataCodec codec;
    private final SyncSettings settings;
    public MasterDataSyncServiceImpl(SapMasterDataClient source, MasterDataPersistenceService target,
                                 MasterDataCodec codec, SyncSettings settings) {
        this.source = source; this.target = target; this.codec = codec; this.settings = settings;
    }
    public void execute(JobContext context) {
        if (!"AUTO".equals(context.mode())) { reconcile(context); return; }
        SyncCheckpoint checkpoint = target.checkpoint(context.type());
        if (!target.baselineComplete(context.type())) {
            if (checkpoint.snapshotId() == null) {
                SnapshotRespDTO snapshot = source.snapshot(context.type());
                context.type().position(snapshot.baselineCursor());
                checkpoint = checkpoint.snapshot(snapshot);
                target.establishSnapshot(context, checkpoint, snapshot.recordCount());
            }
            while (true) {
                SourcePageRespDTO page = source.snapshotPage(context.type(), checkpoint.snapshotId(), checkpoint.snapshotPosition(), settings.pageSize);
                validatePage(context, page);
                long next;
                try { next = Long.parseLong(page.nextCursor()); }
                catch (NumberFormatException e) { throw new IntegrationProblem("INVALID_PAGE", "快照游标不是合法数字", false, 422); }
                codec.require(next >= checkpoint.snapshotPosition() && (!page.hasMore() || next > checkpoint.snapshotPosition()), "快照分页没有前进");
                codec.require(checkpoint.baselineCursor().equals(page.upperCursor()), "源快照基准位置发生变化");
                SyncCheckpoint updated = page.hasMore() ? checkpoint.snapshotPage(next) : checkpoint.baselineDone();
                target.savePage(context, "BASELINE", checkpoint, updated, !page.hasMore(), page.items(), 0);
                checkpoint = updated;
                if (!page.hasMore()) break;
            }
        }
        // Fix a finite upper bound for this run; writes arriving later belong to the next run.
        while (true) {
            SourcePageRespDTO page = source.changes(context.type(), checkpoint.sourceCursor(), checkpoint.upperCursor(), settings.pageSize);
            validatePage(context, page);
            long before = context.type().position(checkpoint.sourceCursor());
            long next = context.type().position(page.nextCursor());
            long upper = context.type().position(page.upperCursor());
            codec.require(next >= before && next <= upper && (!page.hasMore() || next > before), "增量分页没有前进或越过上界");
            codec.require(checkpoint.upperCursor() == null || checkpoint.upperCursor().equals(page.upperCursor()), "增量分页上界不稳定");
            codec.require(page.hasMore() ? next < upper : next == upper, "增量分页结束标识不一致");
            SyncCheckpoint updated = checkpoint.deltaPage(page.nextCursor(), page.upperCursor(), page.hasMore());
            target.savePage(context, "DELTA", checkpoint, updated, true, page.items(), page.hasMore() ? upper - next : 0);
            checkpoint = updated;
            if (!page.hasMore()) break;
        }
    }
    private void reconcile(JobContext context) {
        SnapshotRespDTO snapshot = source.snapshot(context.type());
        target.startReconciliation(context, snapshot.recordCount());
        long position = 0;
        while (true) {
            SourcePageRespDTO page = source.snapshotPage(context.type(), snapshot.snapshotId(), position, settings.pageSize);
            validatePage(context, page);
            long next;
            try { next = Long.parseLong(page.nextCursor()); }
            catch (NumberFormatException e) { throw new IntegrationProblem("INVALID_PAGE", "对账快照游标不合法", false, 422); }
            codec.require(next >= position && (!page.hasMore() || next > position), "对账快照页没有前进");
            codec.require(snapshot.baselineCursor().equals(page.upperCursor()), "对账快照位置发生变化");
            target.reconcilePage(context, page.items(), Long.toString(position), page.nextCursor());
            position = next;
            if (!page.hasMore()) break;
        }
        target.finishReconciliation(context);
    }
    private void validatePage(JobContext context, SourcePageRespDTO page) {
        codec.require(page != null && page.items() != null && page.items().size() <= settings.pageSize, "源端没有遵守分页契约");
        codec.require(!page.hasMore() || !page.items().isEmpty(), "源端返回空页却声明仍有后续数据");
        for (SourceRecordDTO record : page.items()) codec.validate(record, context.type());
    }
}
