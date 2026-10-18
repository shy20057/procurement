// D8 供应商绩效考核单 API
import request from '@/utils/request'
import type { PerfResponse, CreatePerfRequest, FreezeResponse, BlacklistResponse, CreateFreezeRequest, CreateBlacklistRequest } from '@/types/srm'
import type { PerfStatus, PerfGrade } from '@/types/srm'
import type { RejectRequest } from '@/types/srm'

export const perfApi = {
  list: (params?: { status?: PerfStatus; supplierCode?: string; period?: string; grade?: PerfGrade }) => {
    return request.get<PerfResponse[]>('/api/srm/performance-evaluations', { params })
  },

  get: (id: number) => {
    return request.get<PerfResponse>(`/api/srm/performance-evaluations/${id}`)
  },

  create: (data: CreatePerfRequest) => {
    return request.post<PerfResponse>('/api/srm/performance-evaluations', data)
  },

  submit: (id: number) => {
    return request.post(`/api/srm/performance-evaluations/${id}/submit`)
  },

  confirm: (id: number) => {
    return request.post<PerfResponse>(`/api/srm/performance-evaluations/${id}/confirm`)
  },

  approve: (id: number) => {
    return request.post<PerfResponse>(`/api/srm/performance-evaluations/${id}/approve`)
  },

  reject: (id: number, data: RejectRequest) => {
    return request.post<PerfResponse>(`/api/srm/performance-evaluations/${id}/reject`, data)
  },

  pushToFreeze: (id: number, data: CreateFreezeRequest) => {
    return request.post<FreezeResponse>(`/api/srm/performance-evaluations/${id}/push-to-freeze`, data)
  },

  pushToBlacklist: (id: number, data: CreateBlacklistRequest) => {
    return request.post<BlacklistResponse>(`/api/srm/performance-evaluations/${id}/push-to-blacklist`, data)
  },
}
