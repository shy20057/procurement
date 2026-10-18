package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.mapper.JobLeaseMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 执行权校验与页事务边界。锁顺序固定为 run → lease → 业务数据/检查点。 */
@Service
public class SyncLeaseServiceImpl implements SyncLeaseService {
    private final JobRunMapper runs;
    private final JobLeaseMapper leases;
    private final TransactionTemplate tx;
    private final SyncSettings settings;

    public SyncLeaseServiceImpl(JobRunMapper runs, JobLeaseMapper leases,
            PlatformTransactionManager transactionManager, SyncSettings settings) {
        this.runs = runs;
        this.leases = leases;
        this.tx = new TransactionTemplate(transactionManager);
        this.settings = settings;
    }

    /** 栅栏令牌：即使旧实例暂停后恢复，也必须重新核验 owner、token、有效期。 */
    @Override
    public <T> T fenced(JobContext context, Supplier<T> operation) {
        return tx.execute(status -> {
            JobRunDO run = runs.lockRun(context.runId());
            if (run == null || !context.ownerId().equals(run.getOwnerId())
                    || run.getFenceToken() == null || run.getFenceToken() != context.token()) throw lostLease();
            if ("CANCEL_REQUESTED".equals(run.getStatus())) throw new CancellationException("管理员请求取消");
            if (!"RUNNING".equals(run.getStatus())) throw lostLease();
            Long token = leases.lockOwned(context.resourceKey(), context.ownerId(), context.token());
            if (token == null) throw lostLease();
            // 这里的 Mapper 调用与 operation 中的写入共享同一事务绑定连接。
            return operation.get();
        });
    }

    @Override
    public boolean renew(JobContext context) {
        return leases.renew(context.resourceKey(), context.ownerId(), context.token(), settings.leaseSeconds) == 1;
    }

    @Override
    public void release(JobContext context) {
        leases.release(context.resourceKey(), context.ownerId(), context.token());
    }

    private IntegrationProblem lostLease() {
        return new IntegrationProblem("LEASE_LOST", "任务租约已失效，旧实例禁止继续写入", false, 409);
    }
}
