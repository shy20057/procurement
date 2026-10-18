<template>
  <div class="shell">
    <!-- Topbar -->
    <header class="topbar">
      <div class="topbar-brand" @click="router.push('/workbench')">
        <span class="brand-mark">
          <svg width="20" height="20" viewBox="0 0 18 18" fill="none">
            <rect x="1" y="1" width="7" height="7" rx="1.5" fill="#fff"/>
            <rect x="10" y="1" width="7" height="7" rx="1.5" fill="#fff" fill-opacity="0.75"/>
            <rect x="1" y="10" width="7" height="7" rx="1.5" fill="#fff" fill-opacity="0.75"/>
            <rect x="10" y="10" width="7" height="7" rx="1.5" fill="#fff" fill-opacity="0.4"/>
          </svg>
        </span>
        <span class="topbar-wordmark">PROCUREMENT · S2P</span>
        <span class="topbar-divider" />
        <span class="topbar-module">{{ currentModule.label }}</span>
      </div>

      <!-- 顶层主业务域切换 -->
      <nav class="topbar-nav">
        <div
          v-for="mod in modules"
          :key="mod.key"
          class="top-nav-tab"
          :class="{ active: mod.key === currentModuleKey }"
          @click="switchModule(mod)"
        >
          <el-icon class="nav-icon"><component :is="mod.icon" /></el-icon>
          <span>{{ mod.label }}</span>
        </div>
      </nav>

    </header>

    <div class="body">
      <!-- Sidebar -->
      <aside class="sidebar">
        <div class="sidebar-sub" v-if="currentModule.children?.length">
          <div class="sub-header">
            <span class="group-title">{{ currentModule.group }}</span>
            <span class="count-tag">{{ currentModule.children.length }} 类单据</span>
          </div>
          <div
            v-for="item in currentModule.children"
            :key="item.path"
            class="sub-item"
            :class="{ active: isActive(item.path) }"
            @click="router.push(item.path)"
          >
            <span v-if="item.code" class="sub-code mono">{{ item.code }}</span>
            <span class="sub-label">{{ item.label }}</span>
            <span v-if="item.tag" class="sub-tag" :class="item.tagClass">{{ item.tag }}</span>
          </div>
        </div>
      </aside>

      <!-- Main Content -->
      <main class="main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  OfficeBuilding,
  Search,
  ShoppingCart,
  Monitor,
  Connection
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

interface NavItem {
  code?: string
  label: string
  path: string
  tag?: string
  tagClass?: string
}

interface Module {
  key: string
  label: string
  group: string
  icon: Component
  children: NavItem[]
}

const modules: Module[] = [
  {
    key: 'workbench',
    label: '工作台',
    group: '全局驾驶舱',
    icon: Monitor,
    children: [
      { label: '采购作业与态势大屏', path: '/workbench' }
    ]
  },
  {
    key: 'srm',
    label: 'SRM 供应商',
    group: 'SRM 供应商全生命周期',
    icon: OfficeBuilding,
    children: [
      { code: 'D1', label: '供应商邀请单', path: '/srm/invitations' },
      { code: 'D2', label: '供应商注册申请单', path: '/srm/registrations' },
      { code: 'D3', label: '供应商认证单', path: '/srm/certifications' },
      { code: 'D4', label: '供应商档案库 (主数据)', path: '/srm/archives', tag: '主档案', tagClass: 'tag-master' },
      { code: 'D5', label: '供应商变更单', path: '/srm/change-requests' },
      { code: 'D6', label: '冻结/解冻申请单', path: '/srm/freeze-requests' },
      { code: 'D7', label: '供应商黑名单单', path: '/srm/blacklists' },
      { code: 'D8', label: '绩效考核单', path: '/srm/performance-evaluations' }
    ]
  },
  {
    key: 'stc',
    label: 'STC 寻源到合同',
    group: 'STC 寻源比价与定价',
    icon: Search,
    children: [
      { code: 'D9', label: '寻源立项单', path: '/stc/sourcing-projects' },
      { code: 'D10', label: '招标单 (开评标)', path: '/stc/tenders' },
      { code: 'D11', label: '询价单 (多轮比价)', path: '/stc/rfqs', tag: '比价矩阵', tagClass: 'tag-hot' },
      { code: 'D12', label: '定标单 (C3风控)', path: '/stc/awards', tag: '守门硬阻断', tagClass: 'tag-warn' },
      { code: 'D13', label: '采购合同', path: '/stc/contracts' },
      { code: 'D14', label: '基准价目表', path: '/stc/price-lists' },
      { code: 'D15', label: '框架协议 (BPA)', path: '/stc/bpa' },
      { code: 'D16', label: 'SR 配额单', path: '/stc/supplier-ratios' }
    ]
  },
  {
    key: 'integration',
    label: '外围系统集成',
    group: '集成管理',
    icon: Connection,
    children: [
      { code: 'INT', label: '数据同步中心', path: '/integration/sync', tag: '增量同步', tagClass: 'tag-master' }
    ]
  },
  {
    key: 'ptp',
    label: 'PTP 采购与结算',
    group: 'PTP 履约交付与付款',
    icon: ShoppingCart,
    children: [
      { code: 'P1', label: 'PR 采购需求申请', path: '/ptp/requisitions' },
      { code: 'P2', label: 'PO 协同采购订单', path: '/ptp/orders' },
      { code: 'P3', label: 'GR 采购收货入库', path: '/ptp/receipts' },
      { code: 'P4', label: '质量验收与整改', path: '/ptp/inspections' },
      { code: 'P5', label: '3-Way 对账中心', path: '/ptp/reconciliation', tag: '三单匹配', tagClass: 'tag-master' },
      { code: 'P6', label: '发票签收 (BTS查验)', path: '/ptp/invoices' },
      { code: 'P7', label: '请款与资金付款', path: '/ptp/payment-requests' }
    ]
  }
]

const currentModuleKey = computed(() => {
  const path = route.path
  if (path.startsWith('/workbench')) return 'workbench'
  if (path.startsWith('/srm')) return 'srm'
  if (path.startsWith('/stc') || path.startsWith('/sourcing')) return 'stc'
  if (path.startsWith('/integration')) return 'integration'
  if (path.startsWith('/ptp') || path.startsWith('/procurement')) return 'ptp'
  return 'workbench'
})

const currentModule = computed(() =>
  modules.find(m => m.key === currentModuleKey.value) || modules[0]
)

const switchModule = (mod: Module) => {
  if (mod.children && mod.children.length > 0) {
    router.push(mod.children[0].path)
  }
}

const isActive = (path: string) => {
  if (path === '/workbench') return route.path === '/workbench'
  return route.path.startsWith(path)
}
</script>

<style scoped>
.shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--paper);
}

/* ── Topbar · 蓝白雾气渐变（DeepSeek 官网风） ── */
.topbar {
  height: 56px;
  background: var(--grad-mist);
  color: var(--ink);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 10;
  border-bottom: 1px solid rgba(77, 107, 254, 0.14);
  box-shadow: 0 2px 14px rgba(77, 107, 254, 0.08);
  position: relative;
  overflow: hidden;
  backdrop-filter: blur(10px);
}
/* 顶部鲸蓝高光带 */
.topbar::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent 0%, #38bdf8 35%, #4d6bfe 65%, transparent 100%);
  opacity: 0.75;
}
/* 右上装饰光晕 */
.topbar::after {
  content: '';
  position: absolute;
  top: -70px; right: 8%;
  width: 340px; height: 180px;
  background: radial-gradient(ellipse at center, rgba(77, 107, 254, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  z-index: 1;
}
.brand-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--grad-whale);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 10px rgba(77, 107, 254, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.25);
  flex-shrink: 0;
}
.topbar-wordmark {
  font-family: var(--mono);
  font-size: 14px;
  font-weight: 700;
  background: var(--grad-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.08em;
}
.topbar-divider {
  width: 1px;
  height: 18px;
  background: rgba(22, 34, 74, 0.15);
  margin: 0 6px;
}
.topbar-module {
  font-size: 13px;
  color: var(--memo);
  font-weight: 500;
}

/* 顶层模块导航 */
.topbar-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  z-index: 1;
}
.top-nav-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-radius: 999px;
  color: #3c4c74;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  border: 1px solid transparent;
}
.top-nav-tab:hover {
  background: rgba(77, 107, 254, 0.08);
  color: var(--whale);
}
.top-nav-tab.active {
  background: var(--grad-whale);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(77, 107, 254, 0.38), inset 0 1px 0 rgba(255, 255, 255, 0.22);
}
.nav-icon {
  font-size: 15px;
}
.badge-dot {
  background: linear-gradient(135deg, #ff6b6b, #f03e3e);
  color: #fff;
  font-size: 10px;
  font-family: var(--mono);
  font-weight: 700;
  padding: 0 5px;
  border-radius: 10px;
  height: 16px;
  line-height: 16px;
}

/* Topbar Actions */
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 1;
}
.icon-btn {
  color: var(--memo);
  font-size: 16px;
}
.icon-btn:hover {
  color: var(--whale);
}
.user-profile {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-avatar {
  background: var(--grad-whale);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 0 0 2px rgba(77, 107, 254, 0.25);
}
.user-meta {
  display: flex;
  flex-direction: column;
}
.user-name {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ink);
}
.user-role {
  font-size: 11px;
  color: var(--memo);
}

/* ── Body ── */
.body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* ── Sidebar · 浅鲸蓝 ── */
.sidebar {
  width: 232px;
  background: linear-gradient(180deg, #ffffff 0%, #f2f6ff 100%);
  border-right: 1px solid var(--rule);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;
}
.sidebar-sub {
  padding: 12px 10px;
}
.sub-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px 10px;
  border-bottom: 1px dashed var(--rule);
  margin-bottom: 8px;
}
.group-title {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--whale);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.count-tag {
  font-size: 11px;
  font-family: var(--mono);
  color: var(--memo);
  background: var(--whale-light);
  padding: 1px 6px;
  border-radius: 8px;
}

.sub-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: #3c4c74;
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 3px;
  transition: all 0.18s ease;
  position: relative;
}
.sub-item:hover {
  background: var(--whale-light);
  color: var(--whale);
}
.sub-item.active {
  background: var(--grad-whale);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 3px 10px rgba(77, 107, 254, 0.32);
}
.sub-code {
  font-size: 11px;
  color: var(--whale);
  background: var(--whale-light);
  padding: 1px 4px;
  border-radius: 3px;
  flex-shrink: 0;
}
.sub-item.active .sub-code {
  background: rgba(255, 255, 255, 0.22);
  color: #ffffff;
}
.sub-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sub-tag {
  font-size: 10.5px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 600;
  white-space: nowrap;
}
.sub-tag.tag-master { background: #e0f2fe; color: #0284c7; }
.sub-tag.tag-hot { background: #dcfce7; color: #16a34a; }
.sub-tag.tag-warn { background: #fef3c7; color: #d97706; }
.sub-item.active .sub-tag { background: rgba(255,255,255,0.22); color: #fff; }

/* ── Main Area ── */
.main {
  flex: 1;
  overflow-y: auto;
  background:
    radial-gradient(1200px 300px at 80% -80px, rgba(77, 107, 254, 0.06), transparent 60%),
    var(--paper);
}
</style>
