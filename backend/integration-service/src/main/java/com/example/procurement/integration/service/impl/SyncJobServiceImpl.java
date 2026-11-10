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
import com.example.procurement.integration.domain.enums.SyncJobType;
import com.example.procurement.integration.domain.result.XxlSubmission;
import com.example.procurement.integration.dto.domain.ClaimableRunDTO;
import com.example.procurement.integration.dto.domain.LeaseStateDTO;
import com.example.procurement.integration.exception.IntegrationProblem;
import com.example.procurement.integration.service.SyncAlertService;
import com.example.procurement.integration.service.SyncJobService;
import com.example.procurement.integration.service.SyncLeaseService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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

    @Override
    public XxlSubmission submitXxl(String jobCode, String mode, XxlTriggerMetadata metadata, String requestId) {
        SyncJobType.forJob(jobCode);
        if (!List.of("AUTO", "RECONCILE", "REPAIR").contains(mode))
            throw problem("INVALID_MODE", "不支持该 XXL-JOB 运行模式", 422);
        if (metadata == null || metadata.logKey() == null || !metadata.logKey().matches("[0-9a-f]{64}")) {
            throw problem("INVALID_XXL_CONTEXT", "XXL-JOB 日志键不合法", 422);
        }
        if (requestId == null || requestId.isBlank() || requestId.length() > 180)
            throw problem("INVALID_REQUEST", "XXL-JOB 幂等键不合法", 422);

        var parameters = metadata.parameters();
        boolean repair = "REPAIR".equals(mode);
        if (repair || parameters != null) validateParameters(repair, parameters);
        String actor = parameters == null ? "XXL-JOB" : parameters.operator().trim();
        String reason = parameters == null ? "XXL-JOB调度触发" : parameters.reason().trim();
        UUID parent = parameters == null ? null : parameters.retryRunId();
        String operation = repair ? "REPAIR" : parent != null ? "RETRY" : parameters != null ? "MANUAL_TRIGGER" : "XXL_DISPATCH";
        if (repair) {
            reason += parameters.sourceId() != null
                    ? " [sourceId=" + parameters.sourceId() + "]"
                    : " [differenceId=" + parameters.differenceId() + "]";
        }
        String runReason = reason;
        String fingerprint = fingerprint(jobCode, mode, runReason, parameters == null ? "XXL" : "operator:" + actor, parent);
        return tx.execute(status -> {
            JobDefinitionDO definition = lockDefinition(jobCode);
            recoverPurchaseOrderRuns(jobCode);
            JobRunDO previous = runs.lockXxlIdentity(jobCode, metadata.jobId(), metadata.logKey(), metadata.shardIndex());
            if (previous != null) {
                JobRequestKeyDO key = requests.selectByRequestId(previous.getRequestId());
                boolean changed = !mode.equals(previous.getMode()) || !runReason.equals(previous.getReason())
                        || !Objects.equals(parent, previous.getParentRunId());
                if (key != null && key.getRequestFingerprint() != null) {
                    changed |= !fingerprint.equals(key.getRequestFingerprint());
                }
                if (changed) throw problem("IDEMPOTENCY_CONFLICT", "同次调度不能切换模式、操作者或目标", 409);
                if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(previous.getStatus())) {
                    return XxlSubmission.terminal(previous.getRunId(), previous.getStatus(), previous.getErrorCode(), previous.getErrorSummary());
                }
                return XxlSubmission.busy(previous.getRunId(), "相同 XXL 调度请求仍在执行");
            }
            if (parent != null) requireRetryableParent(parent, jobCode, mode);
            JobRunDO run = newRun(jobCode, mode, requestId, runReason);
            run.setParentRunId(parent);
            run.setXxlJobId(metadata.jobId());
            run.setXxlLogKey(metadata.logKey());
            run.setXxlShardIndex(metadata.shardIndex());
            run.setXxlShardTotal(metadata.shardTotal());
            run.setExecutorAddress(metadata.executorAddress());
            runs.insert(run);
            UUID id = run.getRunId();
            insertRequest(requestId, id, jobCode, mode, fingerprint);
            audit(jobCode, id, operation, actor, runReason);
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

    private void validateParameters(boolean repair, XxlTriggerMetadata.Parameters parameters) {
        if (parameters == null) throw problem("INVALID_REPAIR_ID", "修复需要操作者、原因以及源订单 ID 或差异 ID", 422);
        validateActorAndReason(parameters.operator(), parameters.reason());
        Long sourceId = parameters.sourceId();
        Long differenceId = parameters.differenceId();
        if (repair) {
            if ((sourceId == null) == (differenceId == null)
                    || (sourceId != null && sourceId <= 0) || (differenceId != null && differenceId <= 0)) {
                throw problem("INVALID_REPAIR_ID", "修复须指定一个有效的 sourceId 或 differenceId", 422);
            }
        } else if (sourceId != null || differenceId != null) {
            throw problem("INVALID_REPAIR_ID", "订单和差异 ID 仅适用于修复", 422);
        }
    }

    private void requireRetryableParent(UUID parent, String jobCode, String mode) {
        JobRunDO original = runs.selectById(parent);
        if (original == null || !jobCode.equals(original.getJobCode()) || !mode.equals(original.getMode())
                || !List.of("FAILED", "CANCELLED").contains(original.getStatus())) {
            throw problem("NOT_RETRYABLE", "补偿须引用同任务、同模式的失败或取消运行", 409);
        }
    }

    private void recoverPurchaseOrderRuns(String jobCode) {
        for (ClaimableRunDTO run : runs.lockStalePurchaseOrderRuns(jobCode, settings.leaseSeconds)) {
            if ("PENDING".equals(run.getStatus())) {
                failPending(run.getRunId(), "CLAIM_TIMEOUT", "上次调度未领取，下一次调度继续");
                continue;
            }
            LeaseStateDTO lease = leaseMapper.lockState(run.getResourceKey());
            if (lease == null) continue;
            boolean sameOwner = Objects.equals(run.getOwnerId(), lease.getOwnerId())
                    && Objects.equals(run.getFenceToken(), lease.getFenceToken());
            if (sameOwner && !Boolean.TRUE.equals(lease.getExpired())) continue;
            boolean cancelled = "CANCEL_REQUESTED".equals(run.getStatus());
            runs.recover(run.getRunId(), cancelled ? "CANCELLED" : "FAILED");
            if (!cancelled) alerts.alert(jobCode, run.getRunId(), "SYNC_FAILURE", "采购订单执行器失联，从已提交进度恢复");
        }
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
        return new JobContext(run.getRunId(), SyncJobType.forJob(run.getJobCode()), run.getMode(),
                run.getResourceKey(), owner, token, attempt);
    }

    @Override
    public void failPending(UUID runId, String code, String summary) { runs.finishPending(runId, code, truncate(summary)); }

    @Override
    public void success(JobContext context) {
        leases.fenced(context, () -> {
            runs.markSuccess(context.runId());
            JobRunDO completed=runs.selectById(context.runId());
            if ("AUTO".equals(context.mode()) && completed!=null && Boolean.TRUE.equals(completed.getCaughtUp())) {
                checkpoints.markSuccess(context.type().jobCode);
                // 告警更新和成功终态与当前页事务使用同一个数据源。
                alerts.resolveSync(context.type().jobCode);
            }
            return null;
        });
    }

    @Override
    public void failure(JobContext context, IntegrationProblem error) {
        leases.fenced(context, () -> {
            String jobCode = context.type().jobCode;
            String summary = truncate(error.getMessage());
            SyncCheckpointDO checkpoint = checkpoints.selectByJob(jobCode);
            SyncPageFailureDO failure = new SyncPageFailureDO();
            failure.setRunId(context.runId());
            failure.setAttemptNo(context.attempt());
            failure.setFromCursor(checkpoint == null ? null : checkpoint.getCommittedCursor());
            failure.setErrorCode(error.code);
            failure.setErrorSummary(summary);
            audits.insertFailure(failure);
            runs.markFailure(context.runId(), error.code, summary);
            if (error.code.startsWith("SOURCE_") || error.code.startsWith("CHECKPOINT_") || "INVALID_PAGE".equals(error.code)) {
                alerts.alert(jobCode, context.runId(), error.code, summary);
            }
            alerts.alert(jobCode, context.runId(), "SYNC_FAILURE", summary);
            return null;
        });
    }

    @Override
    public void cancelled(JobContext context) { runs.markCancelled(context.runId(), context.ownerId(), context.token()); }

    @Override
    public void enable(String jobCode, boolean enabled, String operator, String reason) {
        SyncJobType.forJob(jobCode);
        validateActorAndReason(operator, reason);
        tx.executeWithoutResult(status -> {
            JobDefinitionDO definition = lockDefinition(jobCode);
            if (Boolean.valueOf(enabled).equals(definition.getEnabled())) return;
            definitions.setEnabled(jobCode, enabled);
            audit(jobCode, null, enabled ? "ENABLE_JOB" : "DISABLE_JOB", operator.trim(), reason.trim());
        });
    }

    private void audit(String jobCode, UUID runId, String operation, String actor, String reason) {
        definitions.insertManagementAudit(UUID.randomUUID(), jobCode, runId, operation, actor, reason);
    }

    private void validateActorAndReason(String actor, String reason) {
        if (actor == null || actor.isBlank() || actor.length() > 100 || actor.chars().anyMatch(Character::isISOControl)
                || reason == null || reason.isBlank() || reason.length() > 400 || reason.chars().anyMatch(Character::isISOControl)) {
            throw problem("INVALID_AUDIT", "请提供有效操作者及 400 字以内的操作原因", 422);
        }
    }

    @Override
    public JobRunDO findRun(UUID runId) { return runs.selectById(runId); }

    private JobDefinitionDO lockDefinition(String jobCode) {
        JobDefinitionDO definition = definitions.lockDefinition(jobCode); // 同步任务配置表
        if (definition == null) throw problem("NOT_FOUND", "任务定义不存在", 404);
        return definition;
    }

    private JobRunDO newRun(String jobCode, String mode, String requestId, String reason) {
        JobRunDO run = new JobRunDO();
        run.setRunId(UUID.randomUUID()); run.setJobCode(jobCode); run.setMode(mode);
        run.setRequestId(requestId); run.setReason(reason); run.setTriggerType("XXL");
        return run;
    }

    private void insertRequest(String requestId, UUID runId, String jobCode, String mode, String fingerprint) {
        JobRequestKeyDO request = new JobRequestKeyDO();
        request.setRequestId(requestId); request.setRunId(runId); request.setJobCode(jobCode); request.setMode(mode);
        request.setRequestFingerprint(fingerprint);
        requests.insert(request);
    }

    private String fingerprint(String jobCode, String mode, String reason, String actor, UUID parent) {
        try {
            String input = String.join(String.valueOf((char) 0), jobCode, mode, reason, actor, String.valueOf(parent));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private IntegrationProblem problem(String code, String message, int status) { return new IntegrationProblem(code, message, false, status); }
    private String truncate(String message) { return message == null ? "同步失败" : message.substring(0, Math.min(500, message.length())); }
}
