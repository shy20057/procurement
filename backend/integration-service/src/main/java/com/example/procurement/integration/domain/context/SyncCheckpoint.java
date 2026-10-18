package com.example.procurement.integration.domain.context;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.dto.resp.SnapshotRespDTO;

import java.util.UUID;

/*
 * SyncCheckpoint 是一个不可变的「同步断点」值对象,它回答一个核心问题:这次同步任务上一次停在哪里了?
 * 用它能实现断点续传——任务中途挂了,下次从记录的位置继续,而不是重头再来。
 * */
public record SyncCheckpoint(
        String mode,             // 同步模式判别(当前固定为 "SOURCE_CURSOR",表示直接按源游标推进)
        String sourceCursor,     // 源游标:增量拉取的当前位置,形如 "cc:123" / "fx:123"(前缀隔离两类主数据)
        UUID   snapshotId,       // 快照 ID:仅在「快照基线」阶段使用
        long   snapshotPosition, // 快照分页读到的内部偏移(读到快照第几条)
        String baselineCursor,   // 基线游标:快照创建那一刻源侧对应的游标位置(基线锚点)
        String upperCursor       // 增量拉取的上界游标(配合「还有没有更多」判断)
) {
    /** 初始断点:一个全新任务的起点。sourceCursor 置为 type.cursor(0)(即 "cc:0" 或 "fx:0"),表示从源第 0 条开始。 */
    public static SyncCheckpoint initial(MasterDataType type) {
        return new SyncCheckpoint("SOURCE_CURSOR", type.cursor(0), null, 0, null, null);
    }

    /** 建快照后的状态:记录 snapshotId 与 baselineCursor(快照对应的源游标),snapshotPosition 归 0(还没开始读快照)。 */
    public SyncCheckpoint snapshot(SnapshotRespDTO snapshot) {
        return new SyncCheckpoint(mode, sourceCursor, snapshot.snapshotId(), 0, snapshot.baselineCursor(), null);
    }

    /** 读快照分页后的状态:每读完一页更新快照内部偏移 snapshotPosition,中断后据此知道读到第几条。 */
    public SyncCheckpoint snapshotPage(long position) {
        return new SyncCheckpoint(mode, sourceCursor, snapshotId, position, baselineCursor, null);
    }

    /** 基线读完后的状态:把 sourceCursor 推进到 baselineCursor(快照覆盖到的源位置)并清空 snapshotId,使全量基线与后续增量无缝衔接。 */
    public SyncCheckpoint baselineDone() {
        return new SyncCheckpoint(mode, baselineCursor, null, 0, null, null);
    }

    /** 拉增量后的状态:sourceCursor 前进到 next;more=true 时设置 upperCursor(本批临时上界),否则清空。 */
    public SyncCheckpoint deltaPage(String next, String upper, boolean more) {
        return new SyncCheckpoint(mode, next, null, 0, null, more ? upper : null);
    }
}
