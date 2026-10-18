export type DataType = 'COST_CENTER' | 'EXCHANGE_RATE'
export type RunMode = 'AUTO' | 'RECONCILE' | 'REPAIR'
export type RunStatus = 'PENDING' | 'RUNNING' | 'RETRY_WAIT' | 'CANCEL_REQUESTED' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface Page<T> { list: T[]; total: number; page: number; size: number }
export interface JobDefinition {
  jobCode: string; entityType: DataType; name: string; enabled: boolean
  cronExpression: string; zoneId: string; handlerCode: string; resourceKey: string; baselineComplete: boolean
  committedCursor: string | null; lastSuccessAt: string | null
  latestRunId: string | null; latestStatus: RunStatus | null; checkpointVersion: number
}
export interface Overview {
  costCenterCount: number; exchangeRateCount: number; openAlertCount: number; activeRunCount: number
  jobs: JobDefinition[]; mockEnabled: boolean; managementEnabled: boolean
  schedulerProvider: 'XXL_JOB'; xxlEnabled: boolean; xxlAdminUrl: string | null; xxlExecutorAppName: string
  emailEnabled: boolean; pageSize: number; writeBatchSize: number
}
export interface CostCenter {
  controllingArea: string; costCenterCode: string; companyCode: string; name: string
  validFrom: string; validTo: string; blocked: boolean; deleted: boolean
}
export interface ExchangeRate {
  rateType: string; fromCurrency: string; toCurrency: string; effectiveDate: string
  rawRate: string | number; fromFactor: string | number; toFactor: string | number
  quotationMethod: 'DIRECT' | 'INDIRECT'; normalizedRate: string | number | null; deleted: boolean
}
export interface SourceRecord {
  entityType: DataType; businessKey: string; sourceVersion: number; changedAt: string
  costCenter: CostCenter | null; exchangeRate: ExchangeRate | null
}
export type TargetRecord = Partial<CostCenter & ExchangeRate> & {
  id: number; sourceVersion: number; sourceChangedAt: string; syncedAt: string
}
export interface SyncRun {
  runId: string; jobCode: string; mode: RunMode; status: RunStatus; triggerType: string
  reason: string; attemptNo: number; phase: string | null; fetchedCount: number; appliedCount: number
  ignoredCount: number; totalCount: number; errorCode: string | null; errorSummary: string | null
  createdAt: string; startedAt: string | null; finishedAt: string | null; nextRetryAt: string | null
  xxlJobId?: number | null; xxlLogKey?: string | null; xxlShardIndex?: number | null
  xxlShardTotal?: number | null; executorAddress?: string | null
}
export interface PageAudit {
  pageNo: number; phase: string; fromCursor: string; toCursor: string
  fetchedCount: number; appliedCount: number; ignoredCount: number; committedAt: string
}
export interface Failure { attemptNo: number; errorCode: string; errorSummary: string; occurredAt: string; fromCursor: string }
export interface Difference { businessKey: string; differenceType: string; sourceVersion: number | null; targetVersion: number | null; repaired: boolean }
export interface RunDetail extends SyncRun { pages: PageAudit[]; failures: Failure[]; differences: Difference[]; differenceCount: number }
export interface SyncAlert { id: number; jobCode: string; runId: string | null; category: string; summary: string; status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'; occurrenceCount: number; firstSeenAt: string; lastSeenAt: string }

export const isActiveRun = (status: string | null | undefined) => !!status && ['PENDING', 'RUNNING', 'RETRY_WAIT', 'CANCEL_REQUESTED'].includes(status)
export const statusLabel = (status: string | null | undefined) => ({ PENDING: '排队中', RUNNING: '运行中', RETRY_WAIT: '等待重试', CANCEL_REQUESTED: '取消中', SUCCEEDED: '已完成', FAILED: '失败', CANCELLED: '已取消' }[status || ''] || '尚未运行')
export const modeLabel = (mode: string) => ({ AUTO: '数据同步', RECONCILE: '只读对账', REPAIR: '对账并修复' }[mode] || mode)
export const formatTime = (value: string | null | undefined) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
export const statusTone = (status: string | null | undefined): 'success' | 'danger' | 'warning' | 'info' | 'primary' => status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'RETRY_WAIT' ? 'warning' : status === 'RUNNING' ? 'primary' : 'info'
