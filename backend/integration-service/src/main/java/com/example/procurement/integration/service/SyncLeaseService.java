package com.example.procurement.integration.service;

import com.example.procurement.integration.domain.context.JobContext;

/** 业务能力接口：Controller、Job 入口只依赖这一层。 */
public interface SyncLeaseService {
    <T> T fenced(JobContext context, java.util.function.Supplier<T> operation);
    boolean renew(JobContext context);
    void release(JobContext context);
}
