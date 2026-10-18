// D1 供应商邀请单 API
import request from '@/utils/request'
import type { InviteResponse, CreateInviteRequest, RegResponse } from '@/types/srm'
import type { InviteStatus } from '@/types/srm'

export const invitationApi = {
  list: (params?: { status?: InviteStatus; inviteeName?: string }) => {
    return request.get<InviteResponse[]>('/api/srm/invitations', { params })
  },

  get: (id: number) => {
    return request.get<InviteResponse>(`/api/srm/invitations/${id}`)
  },

  create: (data: CreateInviteRequest) => {
    return request.post<InviteResponse>('/api/srm/invitations', data)
  },

  decline: (id: number) => {
    return request.post(`/api/srm/invitations/${id}/decline`)
  },

  pushToReg: (id: number) => {
    return request.post<RegResponse>(`/api/srm/invitations/${id}/push-to-registration`)
  },
}
