export const PURCHASE_ORDER_JOB = 'SAP_PURCHASE_ORDER_DELTA'
export type RunMode = 'AUTO' | 'RECONCILE' | 'REPAIR'
export type RunStatus = 'PENDING' | 'RUNNING' | 'RETRY_WAIT' | 'CANCEL_REQUESTED' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface Page<T> { list: T[]; total: number; page: number; size: number }
export interface JobDefinition {
  jobCode: string; entityType: 'PURCHASE_ORDER'; name: string; enabled: boolean
  cronExpression: string; zoneId: string; handlerCode: string; resourceKey: string; baselineComplete: boolean
  committedCursor: string | null; lastSuccessAt: string | null
  latestRunId: string | null; latestStatus: RunStatus | null; checkpointVersion: number
}
export interface Overview {
  openAlertCount: number; activeRunCount: number
  jobs: JobDefinition[]; mockEnabled: boolean; managementEnabled: boolean
  schedulerProvider: 'XXL_JOB'; xxlEnabled: boolean; xxlAdminUrl: string | null; xxlExecutorAppName: string
  pageSize: number; overlapSeconds: number
}
export interface SyncRun {
  runId: string; jobCode: string; mode: RunMode; status: RunStatus; triggerType: string
  reason: string; attemptNo: number; phase: string | null; fetchedCount: number; appliedCount: number
  ignoredCount: number; errorCode: string | null; errorSummary: string | null
  createdAt: string; startedAt: string | null; finishedAt: string | null
  xxlJobId?: number | null; xxlLogKey?: string | null; xxlShardIndex?: number | null
  xxlShardTotal?: number | null; executorAddress?: string | null
  caughtUp: boolean; parentRunId: string | null; targetWatermark: string | null
}
export interface PageAudit {
  pageNo: number; phase: string; fromCursor: string; toCursor: string
  fetchedCount: number; appliedCount: number; ignoredCount: number; committedAt: string
}
export interface Failure { attemptNo: number; errorCode: string; errorSummary: string; occurredAt: string; fromCursor: string }
export interface Difference {
  id: string; businessKey: string; differenceType: string; sourceId: string | null
  sourceUpdatedAt: string | null; targetUpdatedAt: string | null; repaired: boolean
  verificationStatus: string; repairStatus: string; detail: string | null
}
export interface RunDetail extends SyncRun { pages: PageAudit[]; failures: Failure[]; differences: Difference[]; differenceCount: number }
export interface SyncAlert { id: number; jobCode: string; runId: string | null; category: string; summary: string; status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'; occurrenceCount: number; firstSeenAt: string; lastSeenAt: string }

export const isActiveRun = (status: string | null | undefined) => !!status && ['PENDING', 'RUNNING', 'RETRY_WAIT', 'CANCEL_REQUESTED'].includes(status)
export const statusLabel = (status: string | null | undefined) => ({ PENDING: '排队中', RUNNING: '运行中', RETRY_WAIT: '等待重试', CANCEL_REQUESTED: '取消中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[status || ''] || '尚未运行')
export const modeLabel = (mode: string) => ({ AUTO: '数据同步', RECONCILE: '只读对账', REPAIR: '指定订单修复' }[mode] || mode)
export const formatTime = (value: string | null | undefined) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
export const statusTone = (status: string | null | undefined): 'success' | 'danger' | 'warning' | 'info' | 'primary' => status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'RETRY_WAIT' ? 'warning' : status === 'RUNNING' ? 'primary' : 'info'
