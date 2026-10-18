// D2 供应商注册申请单 API
import request from '@/utils/request'
import type { RegResponse, CreateRegRequest, CertResponse } from '@/types/srm'
import type { BillStatus, SourceType } from '@/types/srm'

export const registrationApi = {
  list: (params?: { status?: BillStatus; supplierName?: string; sourceType?: SourceType }) => {
    return request.get<RegResponse[]>('/api/srm/registrations', { params })
  },

  get: (id: number) => {
    return request.get<RegResponse>(`/api/srm/registrations/${id}`)
  },

  create: (data: CreateRegRequest) => {
    return request.post<RegResponse>('/api/srm/registrations', data)
  },

  submit: (id: number) => {
    return request.post(`/api/srm/registrations/${id}/submit`)
  },

  pushToCert: (id: number) => {
    return request.post<CertResponse>(`/api/srm/registrations/${id}/push-to-certification`)
  },
}
