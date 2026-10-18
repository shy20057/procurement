// D6 供应商冻结/解冻单 API
import request from '@/utils/request'
import type { FreezeResponse, CreateFreezeRequest } from '@/types/srm'
import type { BillStatus, FreezeApplyType } from '@/types/srm'
import type { RejectRequest } from '@/types/srm'

export const freezeApi = {
  list: (params?: { status?: BillStatus; supplierCode?: string; applyType?: FreezeApplyType }) => {
    return request.get<FreezeResponse[]>('/api/srm/freeze-requests', { params })
  },

  get: (id: number) => {
    return request.get<FreezeResponse>(`/api/srm/freeze-requests/${id}`)
  },

  create: (data: CreateFreezeRequest) => {
    return request.post<FreezeResponse>('/api/srm/freeze-requests', data)
  },

  submit: (id: number) => {
    return request.post(`/api/srm/freeze-requests/${id}/submit`)
  },

  approve: (id: number) => {
    return request.post<FreezeResponse>(`/api/srm/freeze-requests/${id}/approve`)
  },

  reject: (id: number, data: RejectRequest) => {
    return request.post(`/api/srm/freeze-requests/${id}/reject`, data)
  },

  unapprove: (id: number) => {
    return request.post(`/api/srm/freeze-requests/${id}/unapprove`)
  },
}
