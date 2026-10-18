// STC 寻源到合同模块 API 服务（真实后端接口）
import request, { unwrapList } from '@/utils/request'

/**
 * 将后端返回体统一适配为列表分页结构 { list, total }。
 * 兼容 Result 包装（{ code, data }）与直接分页对象（{ list, total }）。
 */
const toPage = (body: any) => {
  const payload = body?.data ?? body
  const list = unwrapList(payload?.list !== undefined ? payload : body)
  return { data: { list, total: payload?.total ?? list.length } }
}

/**
 * 将后端返回体统一适配为单实体结构，解掉 Result 包装。
 */
const toEntity = (body: any) => ({ data: body?.data ?? body })

export const stcApi = {
  // D9 寻源立项单
  getProjects: (params?: any) =>
    request.get('/api/stc/sourcing-projects', { params }).then(res => toPage(res.data)),
  getProjectDetail: (id: number) =>
    request.get(`/api/stc/sourcing-projects/${id}`).then(res => toEntity(res.data)),
  createProject: (data: any) =>
    request.post('/api/stc/sourcing-projects', data).then(res => toEntity(res.data)),
  submitProject: (id: number) =>
    request.post(`/api/stc/sourcing-projects/${id}/submit`).then(res => toEntity(res.data)),
  approveProject: (id: number) =>
    request.post(`/api/stc/sourcing-projects/${id}/approve`).then(res => toEntity(res.data)),

  // D10 招标单（开评标）
  getTenders: (params?: any) =>
    request.get('/api/stc/tenders', { params }).then(res => toPage(res.data)),
  getTenderDetail: (id: number) =>
    request.get(`/api/stc/tenders/${id}`).then(res => toEntity(res.data)),

  // D11 询价单（多轮比价）
  getRFQs: (params?: any) =>
    request.get('/api/stc/rfqs', { params }).then(res => toPage(res.data)),
  getRfqDetail: (id: number) =>
    request.get(`/api/stc/rfqs/${id}`).then(res => toEntity(res.data)),

  // D12 定标单（C3 风控守门，阻断校验由后端执行）
  getAwards: (params?: any) =>
    request.get('/api/stc/awards', { params }).then(res => toPage(res.data)),
  getAwardDetail: (id: number) =>
    request.get(`/api/stc/awards/${id}`).then(res => toEntity(res.data)),
  approveAward: (id: number) =>
    request.post(`/api/stc/awards/${id}/approve`).then(res => toEntity(res.data)),

  // D13 采购合同
  getContracts: (params?: any) =>
    request.get('/api/stc/contracts', { params }).then(res => toPage(res.data)),
  getContractDetail: (id: number) =>
    request.get(`/api/stc/contracts/${id}`).then(res => toEntity(res.data)),

  // D14 基准价目表
  getPriceLists: (params?: any) =>
    request.get('/api/stc/price-lists', { params }).then(res => toPage(res.data)),
  getPriceListDetail: (id: number) =>
    request.get(`/api/stc/price-lists/${id}`).then(res => toEntity(res.data)),

  // D15 框架协议（BPA）
  getBPAs: (params?: any) =>
    request.get('/api/stc/bpa', { params }).then(res => toPage(res.data)),
  getBpaDetail: (id: number) =>
    request.get(`/api/stc/bpa/${id}`).then(res => toEntity(res.data)),

  // D16 SR 配额单
  getSupplierRatios: (params?: any) =>
    request.get('/api/stc/supplier-ratios', { params }).then(res => toPage(res.data)),
  getSupplierRatioDetail: (id: number) =>
    request.get(`/api/stc/supplier-ratios/${id}`).then(res => toEntity(res.data)),
}
