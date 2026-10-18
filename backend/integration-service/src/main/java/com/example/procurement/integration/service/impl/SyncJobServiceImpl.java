package com.example.procurement.integration.service.impl;

import com.example.procurement.integration.config.SyncSettings;
import com.example.procurement.integration.dao.entity.JobDefinitionDO;
import com.example.procurement.integration.dao.entity.JobRequestKeyDO;
import com.example.procurement.integration.dao.entity.JobRunDO;
import com.example.procurement.integration.dao.entity.SyncCheckpointDO;
import com.example.procurement.integration.dao.entity.SyncPageFailureDO;
import com.example.procurement.integration.dao.mapper.JobDefinitionMapper;
import com.example.procurement.integration.dao.mapper.JobLeaseMapper;
import com.example.procurement.integration.dao.mapper.JobRequestKeyMapper;
import com.example.procurement.integration.dao.mapper.JobRunMapper;
import com.example.procurement.integration.dao.mapper.SyncAuditMapper;
import com.example.procurement.integration.dao.mapper.SyncCheckpointMapper;
import com.example.procurement.integration.domain.context.JobContext;
import com.example.procurement.integration.domain.context.XxlTriggerMetadata;
import com.example.procurement.integration.domain.enums.MasterDataType;
import com.example.procurement.integration.domain.enums.RetryOwner;
import com.example.procurement.integration.domain.enums.TriggerType;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.dto.domain.ClaimableRunDTO;
import com.example.procurement.integration.dto.domain.LeaseStateDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 任务提交、状态流转和恢复规则。所有 SQL 均委托 DAO Mapper。 */
@Service
public class SyncJobServiceImpl implements SyncJobService {
    private final JobDefinitionMapper definitions;
    private final JobRunMapper runs;
    private final JobRequestKeyMapper requests;
    private final JobLeaseMapper leaseMapper;
    private final SyncCheckpointMapper checkpoints;
    private final SyncAuditMapper audits;
    private final SyncLeaseService leases;
    private final SyncAlertService alerts;
    private final SyncSettings settings;
    private final TransactionTemplate tx;

    public SyncJobServiceImpl(JobDefinitionMapper definitions, JobRunMapper runs,
            JobRequestKeyMapper requests, JobLeaseMapper leaseMapper, SyncCheckpointMapper checkpoints,
            SyncAuditMapper audits, SyncLeaseService leases, SyncAlertService alerts,
            SyncSettings settings, PlatformTransactionManager transactionManager) {
        this.definitions = definitions;
        this.runs = runs;
        this.requests = requests;
        this.leaseMapper = leaseMapper;
        this.checkpoints = checkpoints;
        this.audits = audits;
        this.leases = leases;
        this.alerts = alerts;
        this.settings = settings;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /* 幂等提交：同一个 request_id 返回原运行，不重复创建任务。 */
    @Override
    public UUID submit(String jobCode, String mode, String requestId, String reason, String trigger, UUID parent) {
        MasterDataType.forJob(jobCode);
        validateRequest(mode, requestId, reason);
        return tx.execute(status -> {
            JobDefinitionDO definition = lockDefinition(jobCode);
            JobRequestKeyDO previous = requests.selectByRequestId(requestId);
            if (previous != null) {
                if (!jobCode.equals(previous.getJobCode()) || !mode.equals(previous.getMode())) {
                    throw problem("IDEMPOTENCY_CONFLICT", "同一幂等键不能用于不同任务", 409);
                }
                return previous.getRunId();
            }
            if (!Boolean.TRUE.equals(definition.getEnabled())) throw problem("JOB_DISABLED", "当前任务已被业务执行开关停用", 409);
            JobRunDO active = runs.selectActiveResource(definition.getResourceKey(), null);
            UUID id;
            if (active != null) {
                if (!jobCode.equals(active.getJobCode()) || !mode.equals(active.getMode())) {
                    throw problem("JOB_BUSY", "当前数据流已有其他模式任务运行，请稍后操作", 409);
                }
                id = active.getRunId();
            } else {
                JobRunDO run = newRun(jobCode, mode, requestId, reason, trigger);
                run.setParentRunId(parent);
                runs.insert(run);
                id = run.getRunId();
            }
            insertRequest(requestId, id, jobCode, mode);
            return id;
        });
    }

    @Override
    public XxlSubmission submitXxl(String jobCode, XxlTriggerMetadata metadata, String requestId) {
        MasterDataType.forJob(jobCode);
        if (metadata == null || metadata.logKey() == null || !metadata.logKey().matches("[0-9a-f]{64}")) {
            throw problem("INVALID_XXL_CONTEXT", "XXL-JOB日志键不合法", 422);
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 180) throw problem("INVALID_REQUEST", "XXL-JOB幂等键不合法", 422);
        return tx.execute(status -> {
            JobDefinitionDO definition = lockDefinition(jobCode);
            JobRunDO previous = runs.lockXxlIdentity(jobCode, metadata.jobId(), metadata.logKey(), metadata.shardIndex());
            if (previous != null) {
                if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(previous.getStatus())) {
                    return XxlSubmission.terminal(previous.getRunId(), previous.getStatus(), previous.getErrorCode(), previous.getErrorSummary());
                }
                return XxlSubmission.busy(previous.getRunId(), "相同XXL调度请求仍在执行");
            }
            JobRunDO run = newRun(jobCode, "AUTO", requestId, "XXL-JOB调度触发", "XXL");
            run.setXxlJobId(metadata.jobId());
            run.setXxlLogKey(metadata.logKey());
            run.setXxlShardIndex(metadata.shardIndex());
            run.setXxlShardTotal(metadata.shardTotal());
            run.setExecutorAddress(metadata.executorAddress());
            runs.insert(run);
            UUID id = run.getRunId();
            insertRequest(requestId, id, jobCode, "AUTO");
            if (!Boolean.TRUE.equals(definition.getEnabled())) {
                failPending(id, "JOB_DISABLED", "当前任务已被业务执行开关停用");
                return XxlSubmission.disabled(id);
            }
            if (runs.selectActiveResource(definition.getResourceKey(), id) != null) {
                failPending(id, "JOB_BUSY", "数据集资源正由其他业务运行占用");
                return XxlSubmission.busy(id, "数据集资源正由其他业务运行占用");
            }
            return XxlSubmission.created(id);
        });
    }

    /* 租约机制：领取时 CAS 更新 lease_until 并递增 fence_token，多实例中只有取得租约的实例可执行。 */
    @Override
    public JobContext claim(String owner) {
        return tx.execute(status -> {
            for (ClaimableRunDTO run : runs.lockLocalCandidates()) {
                JobContext context = claimRow(run, owner);
                if (context != null) return context;
            }
            return null;
        });
    }

    @Override
    public JobContext claim(UUID runId, String owner) {
        return tx.execute(status -> {
            ClaimableRunDTO run = runs.lockExactCandidate(runId);
            if (run == null) throw problem("NOT_FOUND", "任务不存在", 404);
            if (!Boolean.TRUE.equals(run.getEnabled())) {
                failPending(runId, "JOB_DISABLED", "当前任务已被业务执行开关停用");
                return null;
            }
            if (!"PENDING".equals(run.getStatus()) || !"XXL".equals(run.getTriggerType())) return null;
            return claimRow(run, owner);
        });
    }

    private JobContext claimRow(ClaimableRunDTO run, String owner) {
        Long token = leaseMapper.acquire(run.getResourceKey(), owner, settings.leaseSeconds);
        if (token == null) return null;
        int attempt = run.getAttemptNo() + 1;
        runs.markRunning(run.getRunId(), owner, token, attempt);
        return new JobContext(run.getRunId(), MasterDataType.forJob(run.getJobCode()), run.getMode(),
                TriggerType.fromDatabase(run.getTriggerType()), run.getResourceKey(), owner, token, attempt);
    }

    @Override
    public void failPending(UUID runId, String code, String summary) { runs.finishPending(runId, code, truncate(summary)); }

    @Override
    public void success(JobContext context) {
        leases.fenced(context, () -> {
            runs.markSuccess(context.runId());
            if ("AUTO".equals(context.mode())) {
                checkpoints.markSuccess(context.type().jobCode);
                // 告警更新和成功终态与当前页事务使用同一个数据源。
                alerts.resolveSync(context.type().jobCode);
            }
            return null;
        });
    }

    /* 重试与退避：人工任务保留本地指数退避，XXL任务直接失败，由 XXL 负责后续尝试。 */
    @Override
    public void failure(JobContext context, IntegrationProblem error, RetryOwner retryOwner) {
        leases.fenced(context, () -> {
            String summary = truncate(error.getMessage());
            SyncCheckpointDO checkpoint = checkpoints.selectByJob(context.type().jobCode);
            SyncPageFailureDO failure = new SyncPageFailureDO();
            failure.setRunId(context.runId());
            failure.setAttemptNo(context.attempt());
            failure.setFromCursor(checkpoint == null ? null : checkpoint.getCommittedCursor());
            failure.setErrorCode(error.code);
            failure.setErrorSummary(summary);
            audits.insertFailure(failure);
            boolean retry = retryOwner == RetryOwner.LOCAL && error.retryable && context.attempt() < settings.maxAttempts;
            long delay = Math.min(300, 30L << Math.min(4, context.attempt() - 1));
            delay += ThreadLocalRandom.current().nextLong(Math.max(1, delay / 5));
            runs.markFailure(context.runId(), retry ? "RETRY_WAIT" : "FAILED", error.code, summary, retry, delay);
            if (!retry || context.attempt() >= 3) alerts.alert(context.type().jobCode, context.runId(), "SYNC_FAILURE", summary);
            return null;
        });
    }

    @Override
    public void cancel(UUID id) {
        if (runs.cancel(id) == 0 && runs.selectById(id) == null) throw problem("NOT_FOUND", "任务不存在", 404);
    }

    @Override
    public void cancelled(JobContext context) { runs.markCancelled(context.runId(), context.ownerId(), context.token()); }

    /* 失联恢复：过期、owner 变化或 token 变化都代表旧执行权失效；不修改新持有者的租约。 */
    @Override
    public void recoverExpired() {
        tx.executeWithoutResult(status -> {
            for (ClaimableRunDTO run : runs.lockStaleRuns()) {
                LeaseStateDTO lease = leaseMapper.lockState(run.getResourceKey());
                if (lease == null) continue;
                boolean changed = !Objects.equals(run.getOwnerId(), lease.getOwnerId())
                        || !Objects.equals(run.getFenceToken(), lease.getFenceToken());
                if (!Boolean.TRUE.equals(lease.getExpired()) && !changed) continue;
                boolean cancelled = "CANCEL_REQUESTED".equals(run.getStatus());
                boolean xxl = "XXL".equals(run.getTriggerType());
                boolean exhausted = run.getAttemptNo() >= settings.maxAttempts;
                String next = cancelled ? "CANCELLED" : (xxl || exhausted ? "FAILED" : "RETRY_WAIT");
                runs.recover(run.getRunId(), next);
                if ((xxl || exhausted) && !cancelled) {
                    alerts.alert(run.getJobCode(), run.getRunId(), "SYNC_FAILURE", "实例失联，本次运行已失败并等待所属调度器重试");
                }
            }
        });
    }

    @Override
    public void enable(String jobCode, boolean enabled) {
        MasterDataType.forJob(jobCode);
        definitions.setEnabled(jobCode, enabled);
    }

    @Override
    public void resetIncomplete(String jobCode, String reason) {
        MasterDataType.forJob(jobCode);
        if (reason == null || reason.isBlank() || reason.length() > 500) throw problem("INVALID_REASON", "请填写500字以内操作原因", 422);
        tx.executeWithoutResult(status -> {
            lockDefinition(jobCode);
            if (runs.countActive(jobCode) > 0) throw problem("JOB_BUSY", "请先取消活动任务再重建基线", 409);
            if (checkpoints.resetIncomplete(jobCode) == 0) throw problem("BASELINE_ALREADY_COMPLETE", "完整基线已建立，不允许通过该入口重置正常游标", 409);
            definitions.insertManagementAudit(UUID.randomUUID(), jobCode, reason);
        });
    }

    @Override
    public JobRunDO findRun(UUID runId) { return runs.selectById(runId); }

    private JobDefinitionDO lockDefinition(String jobCode) {
        JobDefinitionDO definition = definitions.lockDefinition(jobCode);
        if (definition == null) throw problem("NOT_FOUND", "任务定义不存在", 404);
        return definition;
    }

    private JobRunDO newRun(String jobCode, String mode, String requestId, String reason, String trigger) {
        JobRunDO run = new JobRunDO();
        run.setRunId(UUID.randomUUID()); run.setJobCode(jobCode); run.setMode(mode);
        run.setRequestId(requestId); run.setReason(reason); run.setTriggerType(trigger);
        return run;
    }

    private void insertRequest(String requestId, UUID runId, String jobCode, String mode) {
        JobRequestKeyDO request = new JobRequestKeyDO();
        request.setRequestId(requestId); request.setRunId(runId); request.setJobCode(jobCode); request.setMode(mode);
        requests.insert(request);
    }

    private void validateRequest(String mode, String requestId, String reason) {
        if (!List.of("AUTO", "RECONCILE", "REPAIR").contains(mode)) throw problem("INVALID_MODE", "不支持的任务模式", 422);
        if (requestId == null || requestId.isBlank() || requestId.length() > 180 || reason == null || reason.isBlank() || reason.length() > 500) {
            throw problem("INVALID_REQUEST", "幂等键和操作原因必填且不能超长", 422);
        }
    }

    private IntegrationProblem problem(String code, String message, int status) { return new IntegrationProblem(code, message, false, status); }
    private String truncate(String message) { return message == null ? "同步失败" : message.substring(0, Math.min(500, message.length())); }
}
