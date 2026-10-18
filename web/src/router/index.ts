import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      {
        path: '',
        redirect: '/workbench',
      },
      {
        path: 'workbench',
        name: 'GlobalWorkbench',
        component: () => import('@/views/workbench/GlobalWorkbench.vue'),
        meta: { title: '采购业务全景与作业驾驶舱' },
      },
      // ================= SRM 供应商全生命周期 =================
      {
        path: 'integration/sync',
        name: 'MasterDataSyncCenter',
        component: () => import('@/views/integration/SyncCenter.vue'),
        meta: { title: 'SAP 主数据同步中心' },
      },
      {
        path: 'srm',
        children: [
          {
            path: '',
            redirect: '/srm/invitations',
          },
          {
            path: 'invitations',
            name: 'InvitationList',
            component: () => import('@/views/srm/InvitationList.vue'),
            meta: { title: '供应商邀请单' },
          },
          {
            path: 'invitations/new',
            name: 'InvitationForm',
            component: () => import('@/views/srm/InvitationForm.vue'),
            meta: { title: '新建邀请单' },
          },
          {
            path: 'registrations',
            name: 'RegistrationList',
            component: () => import('@/views/srm/RegistrationList.vue'),
            meta: { title: '供应商注册申请单' },
          },
          {
            path: 'certifications',
            name: 'CertificationList',
            component: () => import('@/views/srm/CertificationList.vue'),
            meta: { title: '供应商认证单' },
          },
          {
            path: 'archives',
            name: 'ArchiveList',
            component: () => import('@/views/srm/ArchiveList.vue'),
            meta: { title: '供应商档案' },
          },
          {
            path: 'archives/:code',
            name: 'ArchiveDetail',
            component: () => import('@/views/srm/ArchiveDetail.vue'),
            meta: { title: '档案详情' },
          },
          {
            path: 'change-requests',
            name: 'ChangeList',
            component: () => import('@/views/srm/ChangeList.vue'),
            meta: { title: '供应商变更单' },
          },
          {
            path: 'freeze-requests',
            name: 'FreezeList',
            component: () => import('@/views/srm/FreezeList.vue'),
            meta: { title: '供应商冻结/解冻申请单' },
          },
          {
            path: 'blacklists',
            name: 'BlacklistList',
            component: () => import('@/views/srm/BlacklistList.vue'),
            meta: { title: '供应商黑名单单' },
          },
          {
            path: 'performance-evaluations',
            name: 'PerfList',
            component: () => import('@/views/srm/PerfList.vue'),
            meta: { title: '供应商绩效考核单' },
          },
        ],
      },
      // ================= STC 寻源到合同 =================
      {
        path: 'stc',
        children: [
          {
            path: '',
            redirect: '/stc/sourcing-projects',
          },
          {
            path: 'sourcing-projects',
            name: 'SourcingProjectList',
            component: () => import('@/views/stc/SourcingProjectList.vue'),
            meta: { title: 'D9 寻源立项单' },
          },
          {
            path: 'sourcing-projects/:id',
            name: 'SourcingProjectDetail',
            component: () => import('@/views/stc/SourcingProjectDetail.vue'),
            meta: { title: '寻源立项详情' },
          },
          {
            path: 'tenders',
            name: 'TenderList',
            component: () => import('@/views/stc/TenderList.vue'),
            meta: { title: 'D10 招标单' },
          },
          {
            path: 'tenders/:id',
            name: 'TenderDetail',
            component: () => import('@/views/stc/TenderDetail.vue'),
            meta: { title: '招标开评标工作台' },
          },
          {
            path: 'rfqs',
            name: 'RfqList',
            component: () => import('@/views/stc/RfqList.vue'),
            meta: { title: 'D11 询价单' },
          },
          {
            path: 'rfqs/:id',
            name: 'RfqDetail',
            component: () => import('@/views/stc/RfqDetail.vue'),
            meta: { title: '多轮横向比价决策矩阵' },
          },
          {
            path: 'awards',
            name: 'AwardList',
            component: () => import('@/views/stc/AwardList.vue'),
            meta: { title: 'D12 定标单' },
          },
          {
            path: 'awards/:id',
            name: 'AwardDetail',
            component: () => import('@/views/stc/AwardDetail.vue'),
            meta: { title: '定标决策与C3守门' },
          },
          {
            path: 'contracts',
            name: 'ContractList',
            component: () => import('@/views/stc/ContractList.vue'),
            meta: { title: 'D13 采购合同' },
          },
          {
            path: 'contracts/:id',
            name: 'ContractDetail',
            component: () => import('@/views/stc/ContractDetail.vue'),
            meta: { title: '采购合同详情' },
          },
          {
            path: 'price-lists',
            name: 'PriceListList',
            component: () => import('@/views/stc/PriceListList.vue'),
            meta: { title: 'D14 基准价目表' },
          },
          {
            path: 'price-lists/:id',
            name: 'PriceListDetail',
            component: () => import('@/views/stc/PriceListDetail.vue'),
            meta: { title: '价目表详情' },
          },
          {
            path: 'bpa',
            name: 'BpaList',
            component: () => import('@/views/stc/BpaList.vue'),
            meta: { title: 'D15 框架协议 (BPA)' },
          },
          {
            path: 'bpa/:id',
            name: 'BpaDetail',
            component: () => import('@/views/stc/BpaDetail.vue'),
            meta: { title: '框架协议额度池' },
          },
          {
            path: 'supplier-ratios',
            name: 'SupplierRatioList',
            component: () => import('@/views/stc/SupplierRatioList.vue'),
            meta: { title: 'D16 SR 配额单' },
          },
          {
            path: 'supplier-ratios/:id',
            name: 'SupplierRatioDetail',
            component: () => import('@/views/stc/SupplierRatioDetail.vue'),
            meta: { title: 'SR 配额单详情' },
          },
        ],
      },
      // 兼容原有路径
      {
        path: 'sourcing',
        redirect: '/stc/sourcing-projects',
      },
      // ================= PTP 采购执行与结算付款 =================
      {
        path: 'ptp',
        children: [
          { path: '', redirect: '/ptp/requisitions' },
          { path: 'requisitions', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: 'PR 采购需求管理' } },
          { path: 'orders', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: 'PO 采购订单协同' } },
          { path: 'receipts', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: 'GR 采购收货入库' } },
          { path: 'inspections', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: '采购验收与质量整改' } },
          { path: 'reconciliation', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: '3-Way 对账中心' } },
          { path: 'invoices', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: '发票签收与税务查验' } },
          { path: 'payment-requests', component: () => import('@/views/ptp/PtpPlaceholder.vue'), meta: { title: '请款申请与资金支付' } },
        ],
      },
      {
        path: 'procurement',
        redirect: '/ptp/requisitions',
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
