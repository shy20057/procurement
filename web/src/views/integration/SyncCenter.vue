<template>
  <div class="sync-page">
    <header class="page-heading">
      <div><span class="eyebrow">INTEGRATION / SAP</span><h1>采购订单同步中心</h1><p>ERP 已发布订单写入 SRM，按页提交并保留同步进度。</p></div>
      <div class="heading-actions"><el-button @click="configureKey">管理密钥</el-button><el-button :loading="loading" @click="refresh()">刷新看板</el-button></div>
    </header>

    <el-alert v-if="loadFailed" class="connection-error" title="无法读取同步中心，请确认后端已启动且网关已加载集成路由。" type="error" :closable="false" show-icon />
    <el-alert v-else-if="overview && !orderJob" class="connection-error" title="当前后端没有返回采购订单任务" description="请确认连接的是已完成 Integration 迁移的数据库，并重启到新版后端。" type="warning" :closable="false" show-icon />

    <section class="scheduler-panel">
      <div><span class="eyebrow">DISTRIBUTED SCHEDULER</span><h2>XXL-JOB 调度中心</h2><p>同步、失败重试、对账和修复在 Admin 触发，SRM 保存检查点和运行记录。</p></div>
      <div class="scheduler-meta"><el-tag :type="overview?.xxlEnabled ? 'success' : 'info'">{{ overview?.xxlEnabled ? '执行器已配置' : '执行器未启用' }}</el-tag><span>AppName：<code>{{ overview?.xxlExecutorAppName || '—' }}</code></span><el-button :disabled="!adminUrl" @click="openScheduler">打开 XXL-JOB 控制台</el-button></div>
    </section>

    <section class="pipeline">
      <div><span class="step-number">01</span><strong>{{ overview?.mockEnabled ? '模拟 ERP' : 'ERP 发布接口' }}</strong><small>已发布订单头和完整明细</small></div><span class="flow-arrow">→</span>
      <div><span class="step-number">02</span><strong>固定窗口分页</strong><small>发布变更时间 + 源订单 ID</small></div><span class="flow-arrow">→</span>
      <div><span class="step-number">03</span><strong>批量幂等写入</strong><small>订单头、行和页游标同事务提交</small></div><span class="flow-arrow">→</span>
      <div><span class="step-number">04</span><strong>重试与对账</strong><small>XXL-JOB 重试 · 已提交页续跑</small></div>
    </section>
    <el-alert class="connection-error" title="增量上界直接取 ERP 当前时间" :description="'重叠回扫 ' + (overview?.overlapSeconds ?? 300) + ' 秒，窗口全部处理完成后推进水位。'" type="info" :closable="false" show-icon />

    <article v-if="orderJob" class="job-card">
      <div class="job-top">
        <div class="job-title"><el-icon><Document /></el-icon><div><h2>SAP 采购订单</h2><span class="mono">{{ orderJob.jobCode }}</span></div></div>
        <el-tag :type="orderJob.baselineComplete ? 'success' : 'info'" size="small">{{ orderJob.baselineComplete ? '基线已建立' : '待首次全量' }}</el-tag>
      </div>
      <dl>
        <div><dt>最近完整同步</dt><dd>{{ formatTime(orderJob.lastSuccessAt) }}</dd></div>
        <div><dt>建议调度计划</dt><dd><code>{{ orderJob.cronExpression }}</code> · {{ orderJob.zoneId }}</dd></div>
        <div><dt>Handler</dt><dd><code>{{ orderJob.handlerCode }}</code></dd></div>
        <div><dt>资源租约</dt><dd><code>{{ orderJob.resourceKey }}</code></dd></div>
      </dl>
      <div class="checkpoint checkpoint-grid">
        <span>检查点版本</span><code>v{{ orderJob.checkpointVersion }}</code>
        <span>完整水位</span><code>{{ orderCheckpoint?.completedWatermark || '基线尚未完成' }}</code>
        <span>活动窗口上界</span><code>{{ orderCheckpoint?.windowEndAt || '无活动窗口' }}</code>
        <span>最后提交的页游标</span><code>{{ purchaseOrderCursor(orderCheckpoint) }}</code>
      </div>
      <div class="job-bottom">
        <el-button type="primary" :disabled="!adminUrl" @click="openScheduler">前往 XXL-JOB Admin</el-button>
        <el-button v-if="orderJob.latestRunId" link type="primary" @click="selectedRun = orderJob.latestRunId"><el-tag :type="statusTone(orderJob.latestStatus)" size="small">{{ statusLabel(orderJob.latestStatus) }}</el-tag><span class="detail-link">查看最近运行</span></el-button>
      </div>
      <div class="job-footer">
        <span>业务执行开关 <el-switch :model-value="orderJob.enabled" :disabled="!overview?.managementEnabled" size="small" @change="toggleOrder(Boolean($event))" /></span>
        <small>Cron 启停在 XXL-JOB Admin 操作</small>
      </div>
    </article>

    <section v-if="orderJob" class="workspace">
      <el-tabs v-model="tab" @tab-change="loadTab">
        <el-tab-pane label="SRM 已同步订单" name="data">
          <el-alert v-if="!overview?.managementEnabled" title="订单查询需要后端启用集成管理" type="info" :closable="false" show-icon />
          <template v-else>
            <el-alert v-if="tableFailed" title="订单读取失败，请检查管理密钥，或确认后端已加载新版订单查询接口。" type="warning" :closable="false" show-icon />
            <div class="table-toolbar"><el-input v-model="keyword" placeholder="输入 ERP 或 SRM 订单号" clearable style="max-width:300px" @keyup.enter="searchOrders" @clear="searchOrders"><template #append><el-button @click="searchOrders">查询</el-button></template></el-input></div>
            <el-table :data="orders" v-loading="tableLoading" stripe>
              <el-table-column prop="sourceOrderNo" label="ERP 订单号" min-width="155" />
              <el-table-column prop="orderNo" label="SRM 订单号" min-width="145" />
              <el-table-column prop="title" label="订单标题" min-width="180" />
              <el-table-column label="发布状态" width="105"><template #default="{ row }">{{ publicationLabel(row.publicationStatus) }}</template></el-table-column>
              <el-table-column label="ERP 修改时间" min-width="170"><template #default="{ row }">{{ formatTime(row.sourceUpdatedAt) }}</template></el-table-column>
              <el-table-column label="SRM 同步时间" min-width="170"><template #default="{ row }">{{ formatTime(row.syncedAt) }}</template></el-table-column>
              <template #empty><el-empty :description="keyword ? '没有匹配的采购订单' : '尚无同步订单。发布源订单后，在 XXL-JOB Admin 执行采购订单同步。'" :image-size="85" /></template>
            </el-table>
            <el-pagination class="pagination" v-model:current-page="dataPage" :page-size="20" :total="dataTotal" layout="total, prev, pager, next" @current-change="loadTab" />
          </template>
        </el-tab-pane>
        <el-tab-pane label="同步运行记录" name="runs">
          <p class="table-hint">只显示采购订单任务。失败页和对账差异可在运行详情中查看。</p>
          <el-alert v-if="tableFailed" title="运行记录读取失败" type="warning" :closable="false" show-icon />
          <el-table :data="runs" v-loading="tableLoading" stripe>
            <el-table-column label="运行 ID" min-width="155"><template #default="{ row }"><el-button link type="primary" @click="selectedRun = row.runId">{{ row.runId.slice(0, 13) }}…</el-button></template></el-table-column>
            <el-table-column label="模式" min-width="125"><template #default="{ row }">{{ modeLabel(row.mode) }}</template></el-table-column>
            <el-table-column prop="xxlJobId" label="XXL 任务" width="100" />
            <el-table-column label="状态" width="115"><template #default="{ row }"><el-tag :type="statusTone(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
            <el-table-column label="拉取 / 应用 / 忽略" min-width="155"><template #default="{ row }">{{ row.fetchedCount }} / {{ row.appliedCount }} / {{ row.ignoredCount }}</template></el-table-column>
            <el-table-column prop="reason" label="原因" min-width="155" show-overflow-tooltip />
            <el-table-column label="创建时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
          </el-table>
          <el-pagination class="pagination" v-model:current-page="runPage" :page-size="20" :total="runTotal" layout="total, prev, pager, next" @current-change="loadTab" />
        </el-tab-pane>
        <el-tab-pane label="采购订单告警" name="alerts">
          <el-alert v-if="tableFailed" title="告警读取失败" type="warning" :closable="false" show-icon />
          <el-table :data="alerts" v-loading="tableLoading" stripe>
            <el-table-column prop="category" label="类别" width="165" /><el-table-column prop="summary" label="摘要" min-width="260" />
            <el-table-column label="状态" width="110"><template #default="{ row }">{{ row.status === 'RESOLVED' ? '已恢复' : row.status === 'OPEN' ? '待处理' : '已读未恢复' }}</template></el-table-column>
            <el-table-column prop="occurrenceCount" label="次数" width="75" />
            <el-table-column label="最近发现" min-width="170"><template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template></el-table-column>
            <el-table-column label="操作" min-width="140"><template #default="{ row }"><el-button v-if="row.runId" link type="primary" @click="selectedRun = row.runId">关联运行</el-button><el-button v-if="row.status === 'OPEN'" link :disabled="!overview?.managementEnabled" @click="acknowledge(row.id)">已读</el-button></template></el-table-column>
          </el-table>
          <el-pagination class="pagination" v-model:current-page="alertPage" :page-size="20" :total="alertTotal" layout="total, prev, pager, next" @current-change="loadTab" />
        </el-tab-pane>
      </el-tabs>
    </section>
    <RunDetailDrawer :run-id="selectedRun" :purchase-order-checkpoint="orderJob?.committedCursor" :admin-url="adminUrl" @close="selectedRun = null" @changed="refresh()" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi, type PurchaseOrderSummary } from '@/api/integration'
import { PURCHASE_ORDER_JOB, formatTime, modeLabel, statusLabel, statusTone } from '@/types/integration'
import type { Overview, SyncAlert, SyncRun } from '@/types/integration'
import { safeXxlAdminUrl } from '@/utils/xxlJobDisplay'
import { parsePurchaseOrderCheckpoint, purchaseOrderCursor } from './checkpointDisplay'
import RunDetailDrawer from './RunDetailDrawer.vue'

const orderJobCode = PURCHASE_ORDER_JOB
const overview = ref<Overview | null>(null)
const orderJob = computed(() => overview.value?.jobs.find(job => job.jobCode === orderJobCode))
const orderCheckpoint = computed(() => parsePurchaseOrderCheckpoint(orderJob.value?.committedCursor))
const adminUrl = computed(() => safeXxlAdminUrl(overview.value?.xxlAdminUrl))
const loading = ref(false)
const loadFailed = ref(false)
const tableLoading = ref(false)
const tableFailed = ref(false)
const tab = ref('data')
const keyword = ref('')
const orders = ref<PurchaseOrderSummary[]>([])
const runs = ref<SyncRun[]>([])
const alerts = ref<SyncAlert[]>([])
const dataPage = ref(1), runPage = ref(1), alertPage = ref(1)
const dataTotal = ref(0), runTotal = ref(0), alertTotal = ref(0)
const selectedRun = ref<string | null>(null)
let requestGeneration = 0
let disposed = false
let timer: ReturnType<typeof setInterval> | undefined

async function loadTab() {
  if (!orderJob.value) return
  const generation = ++requestGeneration
  tableFailed.value = false
  tableLoading.value = true
  const current = () => !disposed && generation === requestGeneration
  try {
    if (tab.value === 'data' && overview.value?.managementEnabled) {
      const result = await integrationApi.purchaseOrders(dataPage.value, keyword.value)
      if (current()) { orders.value = result.list; dataTotal.value = result.total }
    } else if (tab.value === 'runs') {
      const result = await integrationApi.runs(runPage.value, orderJobCode)
      if (current()) { runs.value = result.list; runTotal.value = result.total }
    } else if (tab.value === 'alerts') {
      const result = await integrationApi.alerts(alertPage.value, orderJobCode)
      if (current()) { alerts.value = result.list; alertTotal.value = result.total }
    }
  } catch {
    if (current()) tableFailed.value = true
  } finally {
    if (current()) tableLoading.value = false
  }
}

async function refresh(includeTable = true) {
  if (loading.value) return
  loading.value = true
  try {
    const previousJob = orderJob.value
    const data = await integrationApi.overview()
    if (disposed) return
    overview.value = data
    loadFailed.value = false
    if (includeTable || (tab.value !== 'data' && !tableLoading.value)
        || (!previousJob && orderJob.value)) await loadTab()
  } catch {
    if (!disposed) loadFailed.value = true
  } finally {
    if (!disposed) loading.value = false
  }
}

function searchOrders() { dataPage.value = 1; void loadTab() }
function openScheduler() { if (adminUrl.value) window.open(adminUrl.value, '_blank', 'noopener,noreferrer') }
function publicationLabel(value: PurchaseOrderSummary['publicationStatus']) {
  return ({ PUBLISHED: '已发布', WITHDRAWN: '已撤回', CANCELLED: '已取消' })[value] ?? value
}
async function toggleOrder(enabled: boolean) {
  try {
    const { value } = await ElMessageBox.prompt('请输入任务开关的操作原因。', enabled ? '启用同步' : '停用同步', {
      inputValidator: reason => (!!reason?.trim() && reason.trim().length <= 400) || '请填写 400 字以内的原因',
    })
    await integrationApi.enable(orderJobCode, enabled, value.trim())
    await refresh()
  } catch { /* Requests already show their error. */ }
}
async function configureKey() {
  try {
    const { value } = await ElMessageBox.prompt('密钥仅保存在当前浏览器会话。', '集成管理密钥', { inputType: 'password' })
    const key = value?.trim()
    if (key) sessionStorage.setItem('integration-admin-key', key)
    else sessionStorage.removeItem('integration-admin-key')
    ElMessage.success('管理密钥已更新')
    await refresh()
  } catch { /* Cancel leaves the previous key unchanged. */ }
}
async function acknowledge(id: number) {
  try { await integrationApi.acknowledge(id); await loadTab() } catch { /* Request error already shown. */ }
}

onMounted(() => {
  void refresh()
  timer = setInterval(() => { if (!document.hidden) void refresh(false) }, 5000)
})
onUnmounted(() => {
  disposed = true
  clearInterval(timer)
})
</script>

<style scoped>
.checkpoint.checkpoint-grid{display:grid;grid-template-columns:minmax(110px,auto) minmax(0,1fr);align-items:start}.state-tags{display:flex;gap:4px;flex-wrap:wrap}
.sync-page{padding:28px 30px 22px;max-width:1800px;margin:auto}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:24px}.eyebrow{font:11px var(--mono);letter-spacing:.13em;color:var(--whale)}h1{font-size:27px;margin:7px 0 5px;letter-spacing:-.7px}.page-heading p{color:var(--memo);font-size:13px;margin:0}.heading-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.live-label{font-size:12px;color:var(--memo);display:flex;align-items:center;gap:7px;margin-right:5px}.live-label i{width:7px;height:7px;border-radius:50%;background:#22a975}.live-label i.busy{background:var(--whale);box-shadow:0 0 0 4px #eef2ff}.connection-error{margin-bottom:20px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:20px}.metrics article{background:white;border:1px solid var(--rule);border-radius:12px;padding:20px 22px}.metrics article>span{font-size:12px;color:var(--memo)}.metrics strong{font:600 34px var(--mono);display:block;margin:8px 0;color:var(--ink)}.metrics small{font:12px var(--sans);color:var(--memo);margin-left:8px}.metrics p{font-size:11px;color:var(--memo);margin:0}.metrics .highlighted{background:var(--grad-card-blue)}.metrics .warning strong{color:#d97706}.scheduler-panel{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:18px 22px;margin-bottom:20px;border:1px solid #cad8f6;border-radius:12px;background:linear-gradient(135deg,#f8fbff,#eef4ff)}.scheduler-panel h2{font-size:17px;margin:5px 0}.scheduler-panel p{font-size:12px;color:var(--memo);margin:0}.scheduler-meta{display:flex;align-items:center;justify-content:flex-end;gap:14px;flex-wrap:wrap;font-size:12px}.scheduler-meta code{color:var(--whale)}.pipeline{background:var(--grad-mist);border:1px solid var(--rule);border-radius:12px;padding:21px 24px;display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:22px}.pipeline>div{display:grid;grid-template-columns:30px 1fr;gap:5px 9px}.step-number{grid-row:span 2;font:12px var(--mono);color:var(--whale);background:#e3ebff;border-radius:7px;width:29px;height:29px;display:grid;place-items:center}.pipeline strong{font-size:12px}.pipeline small{font-size:11px;color:var(--memo)}.flow-arrow{color:#a3b2d2;font-size:22px}.job-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:22px;min-height:80px}.job-card{border:1px solid var(--rule);background:white;border-radius:12px;overflow:hidden;padding:23px 23px 12px}.job-top{display:flex;align-items:center;justify-content:space-between;gap:12px}.job-title{display:flex;align-items:center;gap:12px}.job-title>.el-icon{width:43px;height:43px;background:var(--whale-light);border-radius:10px;font-size:21px;color:var(--whale)}.job-title h2{font-size:17px;margin:0 0 3px}.job-title .mono{font-size:10px;color:var(--memo)}dl{display:grid;gap:9px;margin:20px 0;font-size:12px}dl>div{display:flex;gap:22px}dt{color:var(--memo);min-width:82px}dd{margin:0;overflow-wrap:anywhere}.checkpoint{padding:10px 13px;border:1px dashed #d7e1f8;background:#f9fbff;border-radius:7px;display:flex;justify-content:space-between;gap:12px;font-size:11px;color:var(--memo);margin-bottom:18px}.checkpoint code{color:var(--whale);overflow-wrap:anywhere}.job-bottom{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.detail-link{font-size:11px;margin-left:7px}.job-footer{display:flex;justify-content:space-between;align-items:center;border-top:1px solid #edf1fa;margin-top:17px;padding-top:11px;color:var(--memo);font-size:11px;gap:10px;flex-wrap:wrap}.job-footer>span{display:flex;align-items:center;gap:10px}.workspace{background:white;border:1px solid var(--rule);border-radius:12px;padding:8px 22px 20px}.table-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:12px 0 20px;flex-wrap:wrap}.table-hint{font-size:12px;color:var(--memo);margin:8px 0 18px}.pagination{display:flex;justify-content:flex-end;margin-top:18px}.page-footnote{font-size:11px;color:#8290aa;text-align:center;margin-top:20px}.workspace :deep(.el-tabs__item){height:52px;font-size:13px}.workspace :deep(.el-table){font-size:12px}@media(max-width:1250px){.pipeline small{display:none}.pipeline>div{align-items:center}.step-number{grid-row:auto}.topbar-wordmark{display:none}}@media(max-width:1050px){.job-grid{grid-template-columns:1fr}.metrics{grid-template-columns:repeat(2,1fr)}.page-heading,.scheduler-panel{align-items:flex-start;flex-direction:column}.scheduler-meta{justify-content:flex-start}.pipeline{flex-wrap:wrap}.flow-arrow{display:none}.sync-page{padding:20px 16px}.pipeline>div{min-width:40%}}
</style>
