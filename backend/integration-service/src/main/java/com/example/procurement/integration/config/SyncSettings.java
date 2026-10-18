package com.example.procurement.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/*
*
* */
@Component
public class SyncSettings {
    public final int pageSize; // 每次向SAP拉取多少条数据
    public final int writeBatchSize; // 每条批量写入SQL最多处理多少条
    public final int maxAttempts; // 本地任务最多执行次数，包含首次执行
    public final int leaseSeconds; // 取得执行权后，租约的有效秒数
    public SyncSettings(@Value("${integration.sync.page-size:500}") int pageSize,
                        @Value("${integration.sync.write-batch-size:200}") int writeBatchSize,
                        @Value("${integration.sync.max-attempts:5}") int maxAttempts,
                        @Value("${integration.sync.lease-seconds:60}") int leaseSeconds) {
        if (pageSize < 1 || pageSize > 1000 || writeBatchSize < 1 || writeBatchSize > 500
                || maxAttempts < 1 || maxAttempts > 10 || leaseSeconds < 45) {
            throw new IllegalArgumentException("同步配置超出允许范围");
        }
        this.pageSize = pageSize; this.writeBatchSize = writeBatchSize;
        this.maxAttempts = maxAttempts; this.leaseSeconds = leaseSeconds;
    }
}
