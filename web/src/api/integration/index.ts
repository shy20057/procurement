import request from '@/utils/request'
import type { Overview, Page, RunDetail, SyncAlert, SyncRun } from '@/types/integration'

interface Envelope<T> { code: string; message: string; data: T }
export interface PurchaseOrderSummary {
  orderNo: string
  sourceOrderNo: string
  title: string
  publicationStatus: 'PUBLISHED' | 'WITHDRAWN' | 'CANCELLED'
  sourceUpdatedAt: string
  syncedAt: string
}
const adminHeaders = () => {
  const key = sessionStorage.getItem('integration-admin-key')
  return key ? { 'X-Integration-Admin-Key': key } : {}
}
const get = async <T>(url: string, params?: object) => (await request.get<Envelope<T>>(url, { params })).data.data
const post = async <T>(url: string, data: unknown) => (await request.post<Envelope<T>>(url, data, {
  headers: adminHeaders(),
})).data.data
const base = '/api/integration'

export const integrationApi = {
  overview: () => get<Overview>(`${base}/overview`),
  purchaseOrders: async (page: number, keyword: string) => (await request.get<Envelope<Page<PurchaseOrderSummary>>>(
    `${base}/purchase-orders`, { params: { page, size: 20, keyword }, headers: adminHeaders() },
  )).data.data,
  runs: (page: number, jobCode?: string) => get<Page<SyncRun>>(`${base}/job-runs`, { page, size: 20, ...(jobCode ? { jobCode } : {}) }),
  run: (id: string) => get<RunDetail>(`${base}/job-runs/${id}`),
  enable: async (code: string, enabled: boolean, reason: string) => (await request.patch<Envelope<unknown>>(`${base}/jobs/${code}`, { enabled, reason }, { headers: adminHeaders() })).data.data,
  alerts: (page: number, jobCode?: string) => get<Page<SyncAlert>>(`${base}/alerts`, { page, size: 20, ...(jobCode ? { jobCode } : {}) }),
  acknowledge: (id: number) => post<unknown>(`${base}/alerts/${id}/acknowledge`, {}),
}
