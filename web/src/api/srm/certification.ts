// D3 供应商认证单 API
import request from '@/utils/request'
import type { CertResponse, CreateCertRequest } from '@/types/srm'
import type { BillStatus, CertConclusion } from '@/types/srm'
import type { RejectRequest } from '@/types/srm'

export const certificationApi = {
  list: (params?: { status?: BillStatus; supplierName?: string; conclusion?: CertConclusion }) => {
    return request.get<CertResponse[]>('/api/srm/certifications', { params })
  },

  get: (id: number) => {
    return request.get<CertResponse>(`/api/srm/certifications/${id}`)
  },

  create: (data: CreateCertRequest) => {
    return request.post<CertResponse>('/api/srm/certifications', data)
  },

  submit: (id: number) => {
    return request.post(`/api/srm/certifications/${id}/submit`)
  },

  approve: (id: number) => {
    return request.post<CertResponse>(`/api/srm/certifications/${id}/approve`)
  },

  reject: (id: number, data: RejectRequest) => {
    return request.post(`/api/srm/certifications/${id}/reject`, data)
  },

  unapprove: (id: number) => {
    return request.post(`/api/srm/certifications/${id}/unapprove`)
  },
}
