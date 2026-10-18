// D5 供应商变更单 API
import request from '@/utils/request'
import type { ChangeResponse, CreateChangeRequest } from '@/types/srm'
import type { BillStatus, ChangeType } from '@/types/srm'
import type { RejectRequest } from '@/types/srm'

export const changeApi = {
  list: (params?: { status?: BillStatus; supplierCode?: string; changeType?: ChangeType }) => {
    return request.get<ChangeResponse[]>('/api/srm/change-requests', { params })
  },

  get: (id: number) => {
    return request.get<ChangeResponse>(`/api/srm/change-requests/${id}`)
  },

  create: (data: CreateChangeRequest) => {
    return request.post<ChangeResponse>('/api/srm/change-requests', data)
  },

  submit: (id: number) => {
    return request.post(`/api/srm/change-requests/${id}/submit`)
  },

  approve: (id: number) => {
    return request.post<ChangeResponse>(`/api/srm/change-requests/${id}/approve`)
  },

  reject: (id: number, data: RejectRequest) => {
    return request.post(`/api/srm/change-requests/${id}/reject`, data)
  },

  unapprove: (id: number) => {
    return request.post(`/api/srm/change-requests/${id}/unapprove`)
  },
}
