package com.example.procurement.integration.domain.context;

import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.domain.enums.TriggerType;

import java.util.UUID;

/*
*  任务运行上下文
*  一次任务运行（领取执行权后）携带的完整上下文
 * */
public record JobContext(
        UUID runId, // 本次运行的全局唯一ID
        MasterDataType type, // 主数据类型(成本中心 / 汇率等)
        String mode, // 运行模式(AUTO 增量 / RECONCILE 对账 / REPAIR 修复)
        TriggerType triggerType,
        String resourceKey, // 该任务占用的数据资源键(用于租约互斥)
        String ownerId, // 当前执行实例的标识(谁持有这个任务)
        long token, // 栅栏令牌，阻止已失去执行权的旧实例继续写入
        int attempt // 当前执行次数，从1开始，包含首次执行
) {
}
