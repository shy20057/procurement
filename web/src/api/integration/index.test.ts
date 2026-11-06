import { afterEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { integrationApi } from './index'
import type { CostCenter } from '@/types/integration'

vi.mock('@/utils/request', () => ({ default: { post: vi.fn(), get: vi.fn(), patch: vi.fn() } }))
const data: CostCenter = {
  controllingArea: 'A100', costCenterCode: 'CC001', companyCode: '1000', name: '采购部',
  validFrom: '2026-01-01', validTo: '2099-12-31', blocked: true, enabled: false, isDeleted: false,
}

afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals() })

describe('source maintenance HTTP contract', () => {
  it('sends the original large source ID, exact timestamp and independent states when editing', async () => {
    vi.stubGlobal('sessionStorage', { getItem: () => null })
    vi.mocked(request.post).mockResolvedValue({ data: { data: {} } })
    await integrationApi.saveCostCenter({ data, sourceId: '9007199254740993', expectedUpdatedAt: '2026-11-05T01:02:03.123456Z' })
    expect(request.post).toHaveBeenCalledWith('/api/integration/mock-sap/cost-centers', {
      data, sourceId: '9007199254740993', expectedUpdatedAt: '2026-11-05T01:02:03.123456Z',
    }, { headers: {} })
  })

  it('leaves creation identity and modification time to the source server', async () => {
    vi.stubGlobal('sessionStorage', { getItem: () => null })
    vi.mocked(request.post).mockResolvedValue({ data: { data: {} } })
    await integrationApi.saveCostCenter({ data, sourceId: null, expectedUpdatedAt: null })
    expect(request.post).toHaveBeenCalledWith('/api/integration/mock-sap/cost-centers', {
      data, sourceId: null, expectedUpdatedAt: null,
    }, { headers: {} })
  })
})
