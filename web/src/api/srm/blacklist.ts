// D7 供应商黑名单单 API
import request from '@/utils/request'
import type { BlacklistResponse, CreateBlacklistRequest } from '@/types/srm'
import type { BillStatus, BlacklistTriggerSource } from '@/types/srm'
import type { RejectRequest } from '@/types/srm'

export const blacklistApi = {
  list: (params?: { status?: BillStatus; supplierCode?: string; triggerSource?: BlacklistTriggerSource }) => {
    return request.get<BlacklistResponse[]>('/api/srm/blacklists', { params })
  },

  get: (id: number) => {
    return request.get<BlacklistResponse>(`/api/srm/blacklists/${id}`)
  },

  create: (data: CreateBlacklistRequest) => {
    return request.post<BlacklistResponse>('/api/srm/blacklists', data)
  },

  submit: (id: number) => {
    return request.post(`/api/srm/blacklists/${id}/submit`)
  },

  approve: (id: number) => {
    return request.post<BlacklistResponse>(`/api/srm/blacklists/${id}/approve`)
  },

  reject: (id: number, data: RejectRequest) => {
    return request.post<BlacklistResponse>(`/api/srm/blacklists/${id}/reject`, data)
  },

  unapprove: (id: number) => {
    return request.post(`/api/srm/blacklists/${id}/unapprove`)
  },
}
