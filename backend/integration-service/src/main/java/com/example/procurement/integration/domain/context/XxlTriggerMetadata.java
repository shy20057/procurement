package com.example.procurement.integration.domain.context;

/*
* XXL调度元数据
* */
public record XxlTriggerMetadata(
        long jobId, // 调度任务ID
        String logKey, // 日志键
        int shardIndex, // 分片序号
        int shardTotal, // 分片总数
        String executorAddress // 执行器地址
) {
}
