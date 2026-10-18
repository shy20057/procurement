<template>
  <div class="workbench-page">
    <!-- Header -->
    <div class="workbench-header">
      <div class="header-left">
        <span class="eyebrow">Enterprise S2P Cockpit</span>
        <h1 class="title">采购业务全景与作业驾驶舱</h1>
        <p class="subtitle">SRM 供应商治理与 STC 寻源比价数据总览；PTP 采购结算功能规划中</p>
      </div>
    </div>

    <!-- 关键指标看板 -->
    <div class="kpi-grid" v-loading="dashboardLoading">
      <div class="kpi-card" @click="router.push('/stc/sourcing-projects')">
        <div class="kpi-icon blue"><el-icon><Search /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">寻源立项单</div>
          <div class="kpi-value">{{ projectCount ?? '—' }} <span class="unit">个</span></div>
          <div class="kpi-sub">{{ dashboardLoading ? '加载中' : projectCount === null ? '数据暂不可用' : '来自寻源立项接口' }}</div>
        </div>
      </div>

      <div class="kpi-card" @click="router.push('/stc/awards')">
        <div class="kpi-icon orange"><el-icon><Bell /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">待审核定标单</div>
          <div class="kpi-value">{{ pendingAwardCount ?? '—' }} <span class="unit">单</span></div>
          <div class="kpi-sub">{{ dashboardLoading ? '加载中' : pendingAwardCount === null ? '数据暂不可用' : '包含已提交及审核中' }}</div>
        </div>
      </div>

      <div class="kpi-card" @click="router.push('/srm/archives')">
        <div class="kpi-icon red"><el-icon><Warning /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">临期证照预警</div>
          <div class="kpi-value">{{ expiringSupplierCount ?? '—' }} <span class="unit">家</span></div>
          <div class="kpi-sub">{{ dashboardLoading ? '加载中' : expiringSupplierCount === null ? '数据暂不可用' : '30 天内证照到期的供应商' }}</div>
        </div>
      </div>

      <div class="kpi-card" @click="router.push('/stc/bpa')">
        <div class="kpi-icon green"><el-icon><PieChart /></el-icon></div>
        <div class="kpi-content">
          <div class="kpi-label">BPA 框架协议</div>
          <div class="kpi-value">{{ bpaCount ?? '—' }} <span class="unit">份</span></div>
          <div class="kpi-sub">{{ dashboardLoading ? '加载中' : bpaCount === null ? '数据暂不可用' : '来自框架协议接口' }}</div>
        </div>
      </div>
    </div>

    <!-- 端到端 S2P 交互式业务全景图谱 -->
    <div class="workflow-map-card">
      <div class="card-header">
        <div class="card-title">
          <el-icon><Connection /></el-icon>
          <span>端到端采购业务全景流转蓝图</span>
        </div>
        <span class="card-tip">点击已接入的业务节点可查看对应单据</span>
      </div>

      <div class="flow-track">
        <!-- SRM 阶段 -->
        <div class="phase-group srm">
          <div class="phase-badge">01 · SRM 供应商治理</div>
          <div class="nodes-container">
            <div class="node-chip" @click="router.push('/srm/invitations')">
              <span class="code">D1</span> 邀请单
            </div>
            <div class="node-arrow">→</div>
            <div class="node-chip" @click="router.push('/srm/registrations')">
              <span class="code">D2</span> 注册单
            </div>
            <div class="node-arrow">→</div>
            <div class="node-chip" @click="router.push('/srm/certifications')">
              <span class="code">D3</span> 认证单
            </div>
            <div class="node-arrow">⇒</div>
            <div class="node-chip master" @click="router.push('/srm/archives')">
              <span class="code">D4</span> 供应商档案库
            </div>
          </div>
        </div>

        <div class="cross-divider">
          <span class="line" />
          <span class="label">合格主数据输出</span>
          <span class="line" />
        </div>

        <!-- STC 阶段 -->
        <div class="phase-group stc">
          <div class="phase-badge">02 · STC 寻源到合同</div>
          <div class="nodes-container">
            <div class="node-chip" @click="router.push('/stc/sourcing-projects')">
              <span class="code">D9</span> 寻源立项
            </div>
            <div class="node-arrow">→</div>
            <div class="sub-branches">
              <div class="node-chip" @click="router.push('/stc/tenders')">
                <span class="code">D10</span> 招投标单
              </div>
              <div class="node-chip highlight" @click="router.push('/stc/rfqs')">
                <span class="code">D11</span> 询价比价台
              </div>
            </div>
            <div class="node-arrow">→</div>
            <div class="node-chip master-warn" @click="router.push('/stc/awards')">
              <span class="code">D12</span> 定标单 (C3守门)
            </div>
            <div class="node-arrow">⇒</div>
            <div class="sub-branches-horizontal">
              <div class="node-chip mini" @click="router.push('/stc/contracts')"><span class="code">D13</span> 合同</div>
              <div class="node-chip mini" @click="router.push('/stc/price-lists')"><span class="code">D14</span> 价目表</div>
              <div class="node-chip mini" @click="router.push('/stc/bpa')"><span class="code">D15</span> BPA</div>
              <div class="node-chip mini" @click="router.push('/stc/supplier-ratios')"><span class="code">D16</span> SR配额</div>
            </div>
          </div>
        </div>

        <div class="cross-divider">
          <span class="line" />
          <span class="label">履约依据下达</span>
          <span class="line" />
        </div>

        <!-- PTP 阶段 -->
        <div class="phase-group ptp">
          <div class="phase-badge">03 & 04 · 采购执行与结算（规划中）</div>
          <div class="nodes-container">
            <div class="node-chip placeholder">
              PR 需求申请
            </div>
            <div class="node-arrow">→</div>
            <div class="node-chip placeholder">
              PO 协同订单
            </div>
            <div class="node-arrow">→</div>
            <div class="node-chip placeholder">
              GR 收货/验收
            </div>
            <div class="node-arrow">→</div>
            <div class="node-chip placeholder match">
              3-Way 匹配对账
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部双面板：待办流转与风控预警 -->
    <div class="dashboard-split">
      <!-- 待办任务流 -->
      <div class="panel-card" v-loading="dashboardLoading">
        <div class="panel-header">
          <span class="panel-title">待审核定标单</span>
          <el-button text size="small" @click="router.push('/stc/awards')">查看全部 →</el-button>
        </div>
        <el-alert v-if="!dashboardLoading && pendingAwardCount === null" title="定标单数据暂不可用" type="warning" :closable="false" />
        <el-table v-else :data="pendingAwards" stripe style="width:100%">
          <el-table-column prop="billNo" label="单据编号" width="180">
            <template #default="{ row }">
              <span class="mono bill-link" @click="router.push(`/stc/awards/${row.id}`)">{{ row.billNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="awardSupplierName" label="拟中标供应商" min-width="180" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <BillStatusBadge :status="row.status" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stc/awards/${row.id}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 供应商风控与预警雷达 -->
      <div class="panel-card" v-loading="dashboardLoading">
        <div class="panel-header">
          <span class="panel-title">供应商风控与资质预警</span>
          <el-button text size="small" @click="router.push('/srm/archives')">主数据档案库 →</el-button>
        </div>
        <el-alert v-if="riskLoadFailed" title="部分风控数据获取失败，列表可能不完整" type="warning" :closable="false" />
        <el-table :data="riskSuppliers" stripe style="width:100%">
          <el-table-column prop="supplierCode" label="编码" width="120">
            <template #default="{ row }">
              <span class="mono">{{ row.supplierCode }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="供应商名称" min-width="160" />
          <el-table-column prop="certStatus" label="当前状态" width="100">
            <template #default="{ row }">
              <SupplierTag :supplier-name="row.supplierName" :cert-status="row.certStatus" :show-code="false" />
            </template>
          </el-table-column>
          <el-table-column prop="warningDesc" label="风险原因 / 临期说明" min-width="160" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Bell, Warning, PieChart, Connection } from '@element-plus/icons-vue'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'
import { stcApi } from '@/api/stc'
import type { AwardBill } from '@/types/stc'
import { archiveApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'

const router = useRouter()

const dashboardLoading = ref(true)
const projectCount = ref<number | null>(null)
const pendingAwardCount = ref<number | null>(null)
const expiringSupplierCount = ref<number | null>(null)
const bpaCount = ref<number | null>(null)
const pendingAwards = ref<AwardBill[]>([])
const riskSuppliers = ref<any[]>([])
const riskLoadFailed = ref(false)

const loadDashboard = async () => {
  const [projects, awards, bpas, archivesResult, certs] = await Promise.allSettled([
    stcApi.getProjects(), stcApi.getAwards(), stcApi.getBPAs(),
    archiveApi.list(), archiveApi.getExpiringCerts(30)
  ])

  if (projects.status === 'fulfilled') projectCount.value = projects.value.data.total
  if (awards.status === 'fulfilled') {
    pendingAwards.value = (awards.value.data.list as AwardBill[])
      .filter(a => a.status === 'SUBMITTED' || a.status === 'IN_REVIEW')
    pendingAwardCount.value = pendingAwards.value.length
  }
  if (bpas.status === 'fulfilled') bpaCount.value = bpas.value.data.total

  riskLoadFailed.value = archivesResult.status === 'rejected' || certs.status === 'rejected'
  const archiveStatuses = new Map<string, string>()
  if (archivesResult.status === 'fulfilled') {
    const archives = unwrapList(archivesResult.value.data)
    archives.forEach((a: any) => archiveStatuses.set(a.supplierCode, a.certStatus))
    const risky = archives
      .filter((a: any) => a.certStatus === 'FROZEN' || a.certStatus === 'BLACKLIST')
      .map((a: any) => ({
        supplierCode: a.supplierCode,
        supplierName: a.supplierName,
        certStatus: a.certStatus,
        warningDesc: a.certStatus === 'FROZEN' ? '供应商已冻结，禁止发起新交易' : '供应商已列入黑名单，全面封禁交易'
      }))
    riskSuppliers.value = risky
  }
  if (certs.status === 'fulfilled') {
    const expiringCerts = unwrapList(certs.value.data)
    expiringSupplierCount.value = new Set(expiringCerts.map((c: any) => c.supplierCode)).size
    const expiring = expiringCerts.map((c: any) => ({
      supplierCode: c.supplierCode,
      supplierName: c.supplierName,
      certStatus: archiveStatuses.get(c.supplierCode),
      warningDesc: `${c.certType ?? '资质证照'} 临期预警（${c.validTo ?? ''} 到期）`
    }))
    riskSuppliers.value = [...riskSuppliers.value, ...expiring]
  }
  dashboardLoading.value = false
}

onMounted(loadDashboard)
</script>

<style scoped>
.workbench-page {
  padding: 20px 24px 40px;
  max-width: 1440px;
  margin: 0 auto;
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}
.eyebrow {
  font-family: var(--mono);
  font-size: 12px;
  font-weight: 600;
  background: var(--grad-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.title {
  margin: 4px 0 6px;
  font-size: 24px;
  font-weight: 800;
  color: var(--ink);
  letter-spacing: -0.01em;
}
.subtitle {
  margin: 0;
  font-size: 13.5px;
  color: var(--memo);
}
.system-status {
  display: flex;
  align-items: center;
  gap: 8px;
}
.status-dot.green {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #40c057;
  display: inline-block;
}

/* KPI Grid */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.kpi-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 16px;
  cursor: pointer;
  transition: all 0.22s ease;
  box-shadow: var(--shadow-sm);
  position: relative;
  overflow: hidden;
}
.kpi-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--grad-whale);
  opacity: 0;
  transition: opacity 0.22s ease;
}
.kpi-card:hover {
  transform: translateY(-3px);
  border-color: var(--whale-mute);
  box-shadow: var(--shadow-md);
}
.kpi-card:hover::before {
  opacity: 1;
}
.kpi-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
  color: #fff;
  box-shadow: 0 4px 10px rgba(11, 30, 75, 0.16);
}
.kpi-icon.blue   { background: var(--grad-whale); }
.kpi-icon.orange { background: linear-gradient(135deg, #f59e0b, #f97316); }
.kpi-icon.red    { background: linear-gradient(135deg, #f87171, #ef4444); }
.kpi-icon.green  { background: linear-gradient(135deg, #34d399, #10b981); }

.kpi-label { font-size: 13px; color: var(--memo); }
.kpi-value { font-size: 24px; font-weight: 800; color: var(--ink); margin: 2px 0; }
.kpi-value .unit { font-size: 13px; font-weight: normal; color: var(--memo); }
.kpi-sub { font-size: 11.5px; }
.kpi-sub.positive { color: #2b8a3e; }
.kpi-sub.warning { color: #f08c00; font-weight: 500; }
.kpi-sub.danger { color: #e03131; font-weight: 500; }
.kpi-sub.neutral { color: var(--memo); }

/* Workflow Blueprint · 蓝白鲸鱼渐变（DeepSeek 官网风） */
.workflow-map-card {
  background: var(--grad-card-blue);
  border: 1px solid rgba(77, 107, 254, 0.16);
  color: var(--ink);
  border-radius: var(--radius-md);
  padding: 20px 24px;
  margin-bottom: 24px;
  box-shadow: 0 8px 24px rgba(77, 107, 254, 0.1);
  position: relative;
  overflow: hidden;
}
.workflow-map-card::before {
  content: '';
  position: absolute;
  top: -120px; right: -60px;
  width: 420px; height: 320px;
  background: radial-gradient(ellipse at center, rgba(56, 189, 248, 0.14) 0%, transparent 65%);
  pointer-events: none;
}
.workflow-map-card::after {
  content: '';
  position: absolute;
  bottom: -140px; left: 10%;
  width: 360px; height: 280px;
  background: radial-gradient(ellipse at center, rgba(77, 107, 254, 0.1) 0%, transparent 65%);
  pointer-events: none;
}
.workflow-map-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  border-bottom: 1px solid rgba(77, 107, 254, 0.14);
  padding-bottom: 12px;
  position: relative;
  z-index: 1;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--ink);
}
.card-title .el-icon { color: var(--whale); }
.card-tip {
  font-size: 12px;
  color: var(--memo);
}

.flow-track {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  overflow-x: auto;
  padding: 10px 0;
  position: relative;
  z-index: 1;
}
.phase-group {
  border: 1px solid rgba(77, 107, 254, 0.2);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(4px);
  box-shadow: 0 2px 8px rgba(77, 107, 254, 0.06);
}
.phase-badge {
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 700;
  margin-bottom: 10px;
  letter-spacing: 0.05em;
}
.phase-group.srm .phase-badge { color: #0284c7; }
.phase-group.stc .phase-badge { color: #059669; }
.phase-group.ptp .phase-badge { color: #7c3aed; }

.nodes-container {
  display: flex;
  align-items: center;
  gap: 8px;
}
.node-chip {
  background: #ffffff;
  border: 1px solid #cdd8ff;
  color: #3c4c74;
  font-size: 12px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s ease;
  box-shadow: 0 1px 3px rgba(77, 107, 254, 0.08);
}
.node-chip:hover {
  background: var(--whale-light);
  border-color: var(--whale);
  color: var(--whale);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(77, 107, 254, 0.2);
}
.node-chip .code {
  font-family: var(--mono);
  font-weight: 700;
  color: var(--whale);
  margin-right: 4px;
}
.node-chip.master {
  background: var(--grad-whale);
  border-color: transparent;
  color: #fff;
  font-weight: 600;
  box-shadow: 0 3px 12px rgba(77, 107, 254, 0.4);
}
.node-chip.master .code { color: rgba(255,255,255,0.85); }
.node-chip.master-warn {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  border-color: transparent;
  color: #fff;
}
.node-chip.master-warn .code { color: rgba(255,255,255,0.85); }
.node-chip.highlight {
  border-color: #34d399;
}
.node-chip.mini {
  padding: 3px 6px;
  font-size: 11px;
}
.node-chip.placeholder {
  cursor: default;
  color: #9aa8cc;
  border-style: dashed;
  background: rgba(255,255,255,0.5);
  box-shadow: none;
}
.node-chip.placeholder.match {
  color: #8b5cf6;
  border-color: #c4b5fd;
}

.node-arrow {
  color: #8aa0d0;
  font-size: 13px;
  font-family: var(--mono);
}
.sub-branches {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.sub-branches-horizontal {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
}

.cross-divider {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 4px;
}
.cross-divider .line {
  width: 1px;
  height: 20px;
  background: rgba(77, 107, 254, 0.25);
}
.cross-divider .label {
  font-size: 10px;
  font-family: var(--mono);
  color: var(--memo);
  white-space: nowrap;
  margin: 4px 0;
}

/* Dashboard Split */
.dashboard-split {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
}
.panel-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  padding: 18px 20px;
  box-shadow: var(--shadow-sm);
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--rule);
  padding-bottom: 10px;
}
.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
}
.bill-link {
  color: var(--whale);
  cursor: pointer;
  font-weight: 600;
}
.bill-link:hover {
  text-decoration: underline;
}
</style>
