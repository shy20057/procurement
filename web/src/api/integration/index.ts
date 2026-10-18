import request from '@/utils/request'
import type { DataType, RunMode, Overview, Page, TargetRecord, SourceRecord, SyncRun, RunDetail, SyncAlert, CostCenter, ExchangeRate } from '@/types/integration'

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
  records: (type: DataType, page: number, keyword: string) => get<Page<TargetRecord>>(`${base}/${type === 'COST_CENTER' ? 'cost-centers' : 'exchange-rates'}`, { page, size: 20, keyword }),
  runs: (page: number) => get<Page<SyncRun>>(`${base}/job-runs`, { page, size: 20 }),
  run: (id: string) => get<RunDetail>(`${base}/job-runs/${id}`),
  trigger: (code: string, mode: RunMode, reason: string, key: string) => post<SyncRun>(`${base}/jobs/${code}/runs`, { mode, reason }, key),
  retry: (id: string, reason: string, key: string) => post<SyncRun>(`${base}/job-runs/${id}/retry`, { reason }, key),
  cancel: (id: string) => post<SyncRun>(`${base}/job-runs/${id}/cancel`, {}),
  enable: async (code: string, enabled: boolean) => (await request.patch<Envelope<unknown>>(`${base}/jobs/${code}`, { enabled }, { headers: adminHeaders() })).data.data,
  resetBaseline: (code: string, reason: string) => post<unknown>(`${base}/jobs/${code}/reset-incomplete-baseline`, { reason }),
  alerts: (page: number) => get<Page<SyncAlert>>(`${base}/alerts`, { page, size: 20 }),
  acknowledge: (id: number) => post<unknown>(`${base}/alerts/${id}/acknowledge`, {}),
  source: (type: DataType, page: number) => get<Page<SourceRecord>>(`${base}/mock-sap/${type}`, { page, size: 20 }),
  sourceFault: (type: DataType) => get<{ unavailable: boolean }>(`${base}/mock-sap/${type}/fault`),
  setSourceFault: (type: DataType, unavailable: boolean) => post<{ unavailable: boolean }>(`${base}/mock-sap/${type}/fault`, { enabled: unavailable }),
  saveCostCenter: (data: CostCenter, expectedVersion: number) => post<SourceRecord>(`${base}/mock-sap/cost-centers`, { data, expectedVersion }),
  saveExchangeRate: (data: ExchangeRate, expectedVersion: number) => post<SourceRecord>(`${base}/mock-sap/exchange-rates`, { data, expectedVersion }),
}
