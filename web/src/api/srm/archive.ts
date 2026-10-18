// D4 供应商档案 API
import request from '@/utils/request'
import type { ArchiveResponse, UpdateArchiveRequest, ExpiringCertResponse, RelatedBillResponse } from '@/types/srm'

export const archiveApi = {
  list: () => {
    return request.get<ArchiveResponse[]>('/api/srm/archives')
  },

  get: (supplierCode: string) => {
    return request.get<ArchiveResponse>(`/api/srm/archives/${supplierCode}`)
  },

  update: (supplierCode: string, data: UpdateArchiveRequest) => {
    return request.put(`/api/srm/archives/${supplierCode}`, data)
  },

  getRelatedBills: (supplierCode: string, billType?: string) => {
    return request.get<RelatedBillResponse[]>(`/api/srm/archives/${supplierCode}/related-bills`, {
      params: { billType },
    })
  },

  getExpiringCerts: (days: number = 30) => {
    return request.get<ExpiringCertResponse[]>('/api/srm/archives/expiring-certs', {
      params: { days },
    })
  },
}
