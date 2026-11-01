package com.example.procurement.integration.dao.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * 一次同步运行的持久化对象；状态流转规则位于 SyncJobServiceImpl。
 *
 * 状态机：PENDING → RUNNING → SUCCEEDED / RETRY_WAIT → RUNNING / FAILED
 * 取消：PENDING|RETRY_WAIT → CANCELLED，RUNNING → CANCEL_REQUESTED → CANCELLED
 * 活跃（仍占用资源）：PENDING / RUNNING / RETRY_WAIT / CANCEL_REQUESTED
 */
@Data
public class JobRunDO {
    /** 主键，服务端生成的 UUID */
    private UUID runId;
    /** 属于哪个同步任务，外键指向 job_definition.job_code */
    private String jobCode;
    /** 运行模式：AUTO 增量 / RECONCILE 只读对账 / REPAIR 对账并修复 */
    private String mode;
    /** 触发来源：MANUAL 人工接口 / XXL 调度中心 / RETRY 人工补偿 */
    private String triggerType;
    /** 人工补偿时指向被重试的那条运行；自动重试沿用原运行，为 null */
    private UUID parentRunId;
    /** 幂等键：人工触发用 Idempotency-Key 头，XXL 用 xxl:jobCode:jobId:logKey:shardIndex */
    private String requestId;
    /** 触发原因（必填，≤500 字）：人工触发填原因，XXL 填固定文案 */
    private String reason;
    /** 运行状态：PENDING/RUNNING/SUCCEEDED/RETRY_WAIT/FAILED/CANCEL_REQUESTED/CANCELLED */
    private String status;
    /** 已尝试次数，领取任务时 +1；退避重试与失联恢复的判据 */
    private Integer attemptNo;
    /** 退避重试的最早可执行时间，仅 RETRY_WAIT 状态下有效 */
    private Instant nextRetryAt;
    /** 当前执行者标识（本地调度器节点 id），与 job_lease.owner_id 一致 */
    private String ownerId;
    /** 栅栏令牌，抢租约时 +1；令牌已变说明执行权易主，旧实例禁止继续写 */
    private Long fenceToken;
    /** 执行阶段：BASELINE 建基线 / DELTA 拉增量 / RECONCILE 对账 / COMPLETED 完成 */
    private String phase;
    /** 已从源端取到的记录数 */
    private Long fetchedCount;
    /** 实际写入本地主数据的记录数 */
    private Long appliedCount;
    /** 因源版本过旧或重复而跳过的记录数 */
    private Long ignoredCount;
    /** 本轮源端记录总量，用于前端算进度百分比 */
    private Long totalCount;
    /** 失败错误码，如 SAP_NETWORK_ERROR / JOB_BUSY / SOURCE_VERSION_CONFLICT */
    private String errorCode;
    /** 失败摘要，≤500 字 */
    private String errorSummary;
    /** 开始执行时间；等待执行时为 null */
    private Instant startedAt;
    /** 结束时间；CANCEL_REQUESTED 阶段尚未真正结束，为 null */
    private Instant finishedAt;
    /** 记录创建时间，即提交时刻 */
    private Instant createdAt;
    /** XXL 调度中心的任务 id */
    private Long xxlJobId;
    /** XXL 调度日志键（64 位 hex），与 jobId、shardIndex 共同构成调度身份 */
    private String xxlLogKey;
    /** XXL 分片序号 */
    private Integer xxlShardIndex;
    /** XXL 分片总数 */
    private Integer xxlShardTotal;
    /** 实际执行这次调用的执行器地址 */
    private String executorAddress;
}
