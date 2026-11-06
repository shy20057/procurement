import request from '@/utils/request'
import type { DataType, RunMode, Overview, Page, TargetRecord, SourceRecord, SyncRun, RunDetail, SyncAlert, CostCenter, ExchangeRate, SourceSaveRequest, SyncCheckpoint } from '@/types/integration'

interface Envelope<T> { code: string; message: string; data: T }
const adminHeaders = () => {
  const key = sessionStorage.getItem('integration-admin-key')
  return key ? { 'X-Integration-Admin-Key': key } : {}
}
const get = async <T>(url: string, params?: object) => (await request.get<Envelope<T>>(url, { params })).data.data
const post = async <T>(url: string, data: unknown, idempotencyKey?: string) => (await request.post<Envelope<T>>(url, data, {
  headers: { ...adminHeaders(), ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}) },
})).data.data
const base = '/api/integration'

export const integrationApi = {
  overview: () => get<Overview>(`${base}/overview`),
  checkpoint: (code: string) => get<SyncCheckpoint>(`${base}/jobs/${code}/checkpoint`),
  records: (type: DataType, page: number, keyword: string) => get<Page<TargetRecord>>(`${base}/${type === 'COST_CENTER' ? 'cost-centers' : 'exchange-rates'}`, { page, size: 20, keyword }),
  // ① 运行记录列表：前端在这里筛出 status=FAILED/CANCELLED 的行，拿到它的 runId（另一条来源是 overview 的 latestRunId）
  runs: (page: number) => get<Page<SyncRun>>(`${base}/job-runs`, { page, size: 20 }),
  // ② 运行详情：用①拿到的 runId 查单条，拿到 status；详情抽屉里的「失败补偿/重试」按钮靠它决定显不显示
  run: (id: string) => get<RunDetail>(`${base}/job-runs/${id}`),
  trigger: (code: string, mode: RunMode, reason: string, key: string) => post<SyncRun>(`${base}/jobs/${code}/runs`, { mode, reason }, key),
  // ③ 人工补偿重试：把②确认过是 FAILED 的那个 runId 原样带回，后端据此新建一条 RETRY 运行（parent_run_id 指向原 run）
  retry: (id: string, reason: string, key: string) => post<SyncRun>(`${base}/job-runs/${id}/retry`, { reason }, key),
  cancel: (id: string) => post<SyncRun>(`${base}/job-runs/${id}/cancel`, {}),
  enable: async (code: string, enabled: boolean) => (await request.patch<Envelope<unknown>>(`${base}/jobs/${code}`, { enabled }, { headers: adminHeaders() })).data.data,
  resetBaseline: (code: string, reason: string) => post<unknown>(`${base}/jobs/${code}/reset-incomplete-baseline`, { reason }),
  alerts: (page: number) => get<Page<SyncAlert>>(`${base}/alerts`, { page, size: 20 }),
  acknowledge: (id: number) => post<unknown>(`${base}/alerts/${id}/acknowledge`, {}),
  source: (type: DataType, page: number) => get<Page<SourceRecord>>(`${base}/mock-sap/${type}`, { page, size: 20 }),
  sourceFault: (type: DataType) => get<{ unavailable: boolean }>(`${base}/mock-sap/${type}/fault`),
  setSourceFault: (type: DataType, unavailable: boolean) => post<{ unavailable: boolean }>(`${base}/mock-sap/${type}/fault`, { enabled: unavailable }),
  saveCostCenter: (request: SourceSaveRequest<CostCenter>) => post<SourceRecord>(`${base}/mock-sap/cost-centers`, request),
  saveExchangeRate: (request: SourceSaveRequest<ExchangeRate>) => post<SourceRecord>(`${base}/mock-sap/exchange-rates`, request),
}
