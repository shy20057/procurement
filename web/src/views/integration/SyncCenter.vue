<template>
  <div class="sync-page">
    <header class="page-heading">
      <div><span class="eyebrow">INTEGRATION / SAP MASTER DATA</span><h1>主数据同步中心</h1><p>让外围系统的每一次变化，都有进度、记录与回执。</p></div>
      <div class="heading-actions"><span class="live-label"><i :class="{ busy: overview?.activeRunCount }" />{{ overview?.activeRunCount ? '任务运行中 · 每5秒刷新' : '每5秒刷新' }}</span><el-button @click="configureKey">管理密钥</el-button><el-button :loading="loading" @click="refresh">刷新看板</el-button></div>
    </header>

    <el-alert v-if="loadFailed" class="connection-error" title="无法读取同步数据，请确认后端已重启、数据库迁移已完成，且网关已加载新的集成路由。" type="error" :closable="false" show-icon />
    <div class="metrics">
      <article><span>SRM 成本中心</span><strong>{{ overview?.costCenterCount ?? '—' }}<small>条</small></strong><p>已落库的业务主数据</p></article>
      <article><span>有效期汇率记录</span><strong>{{ overview?.exchangeRateCount ?? '—' }}<small>条</small></strong><p>保留业务日期与源版本</p></article>
      <article :class="{ highlighted: overview?.activeRunCount }"><span>活动任务</span><strong>{{ overview?.activeRunCount ?? '—' }}<small>个</small></strong><p>包含排队、执行与等待重试</p></article>
      <article :class="{ warning: overview?.openAlertCount }"><span>未恢复告警</span><strong>{{ overview?.openAlertCount ?? '—' }}<small>条</small></strong><p>已读不等于故障恢复</p></article>
    </div>

    <section class="scheduler-panel">
      <div><span class="eyebrow">DISTRIBUTED SCHEDULER</span><h2>XXL-JOB 调度中心</h2><p>负责 Cron、路由、超时与失败重试；SRM 负责幂等、租约、检查点和业务补偿。</p></div>
      <div class="scheduler-meta"><el-tag :type="overview?.xxlEnabled ? 'success' : 'info'">{{ overview?.xxlEnabled ? '执行器已配置' : '执行器未启用' }}</el-tag><span>AppName：<code>{{ overview?.xxlExecutorAppName || '—' }}</code></span><el-button :disabled="!adminUrl" @click="openScheduler">打开 XXL-JOB 控制台</el-button></div>
    </section>

    <section class="pipeline"><div><span class="step-number">01</span><strong>{{ overview?.mockEnabled ? 'SAP 模拟源' : 'SAP 外围接口' }}</strong><small>权威数据 · 完整快照 / 变更日志</small></div><span class="flow-arrow">→</span><div><span class="step-number">02</span><strong>SRM 主动分页拉取</strong><small>{{ overview?.pageSize ?? '—' }} 条 / 页 · 保存成功检查点</small></div><span class="flow-arrow">→</span><div><span class="step-number">03</span><strong>批量幂等写入</strong><small>{{ overview?.writeBatchSize ?? '—' }} 条 / SQL · 业务键 + 源版本</small></div><span class="flow-arrow">→</span><div><span class="step-number">04</span><strong>对账与异常恢复</strong><small>页级回滚 · 持久化重试 · 差异报告</small></div></section>

    <div class="job-grid" v-loading="loading && !overview">
      <article v-for="job in overview?.jobs || []" :key="job.jobCode" class="job-card">
        <div class="job-top"><div class="job-title"><el-icon><component :is="job.entityType === 'COST_CENTER' ? OfficeBuilding : Money" /></el-icon><div><h2>{{ job.name }}</h2><span class="mono">{{ job.jobCode }}</span></div></div><el-tag :type="job.baselineComplete ? 'success' : 'info'" size="small">{{ job.baselineComplete ? '基线已建立' : '待首次全量' }}</el-tag></div>
        <dl><div><dt>最近完整同步</dt><dd>{{ formatTime(job.lastSuccessAt) }}</dd></div><div><dt>执行计划</dt><dd><code>{{ job.cronExpression }}</code> · {{ job.zoneId }} <span v-if="!overview?.xxlEnabled">（XXL执行器未启用）</span></dd></div><div><dt>Handler</dt><dd><code>{{ job.handlerCode }}</code></dd></div><div><dt>资源租约</dt><dd><code>{{ job.resourceKey }}</code></dd></div></dl>
        <div class="checkpoint"><span>已提交检查点 · v{{ job.checkpointVersion }}</span><code>{{ cursorLabel(job) }}</code></div>
        <div class="job-bottom"><el-button type="primary" :loading="triggering === `${job.jobCode}:AUTO`" :disabled="!overview?.managementEnabled || isActiveRun(job.latestStatus)" @click="trigger(job, 'AUTO')">{{ job.baselineComplete ? '拉取增量' : '首次全量同步' }}</el-button><el-dropdown @command="(mode: RunMode) => trigger(job, mode)"><el-button :disabled="!overview?.managementEnabled || isActiveRun(job.latestStatus)">对账与修复<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="RECONCILE">只读全量对账</el-dropdown-item><el-dropdown-item command="REPAIR">对账并修复差异</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button v-if="job.latestRunId" link type="primary" @click="selectedRun = job.latestRunId"><el-tag :type="statusTone(job.latestStatus)" size="small">{{ statusLabel(job.latestStatus) }}</el-tag><span class="detail-link">查看详情</span></el-button></div>
        <div class="job-footer"><span>业务执行开关 <el-switch :model-value="job.enabled" size="small" :disabled="!overview?.managementEnabled" @change="(value: string | number | boolean) => toggleJob(job, Boolean(value))" /></span><small>Cron 启停请在 XXL-JOB Admin 操作</small><el-button v-if="!job.baselineComplete && job.latestStatus === 'FAILED'" link type="warning" @click="resetBaseline(job)">重建未完成基线</el-button></div>
      </article>
    </div>

    <section class="workspace">
      <el-tabs v-model="tab" @tab-change="changeTab">
        <el-tab-pane label="SRM 本地主数据" name="data">
          <div class="table-toolbar"><el-radio-group v-model="dataType" @change="changeDataType"><el-radio-button value="COST_CENTER">成本中心</el-radio-button><el-radio-button value="EXCHANGE_RATE">汇率</el-radio-button></el-radio-group><el-input v-model="keyword" :placeholder="dataType === 'COST_CENTER' ? '搜索名称或成本中心编码' : '搜索币种，如 USD'" clearable style="max-width:280px" @keyup.enter="searchRecords" @clear="searchRecords"><template #append><el-button :icon="Search" @click="searchRecords" /></template></el-input></div>
          <el-table :data="records" v-loading="tableLoading" stripe>
            <template v-if="dataType === 'COST_CENTER'">
              <el-table-column prop="costCenterCode" label="成本中心编码" width="160" /><el-table-column prop="name" label="名称" min-width="170" /><el-table-column prop="controllingArea" label="控制范围" width="100" /><el-table-column prop="companyCode" label="公司" width="85" /><el-table-column label="有效期" min-width="200"><template #default="{ row }">{{ row.validFrom }} ~ {{ row.validTo }}</template></el-table-column>
            </template>
            <template v-else>
              <el-table-column prop="rateType" label="类型" width="70" /><el-table-column label="币种方向" width="140"><template #default="{ row }">{{ row.fromCurrency }} → {{ row.toCurrency }}</template></el-table-column><el-table-column prop="normalizedRate" label="规范化汇率（1单位原币）" min-width="190" /><el-table-column prop="effectiveDate" label="生效日" width="120" /><el-table-column prop="quotationMethod" label="报价方式" width="120" />
            </template>
            <el-table-column prop="sourceVersion" label="源版本" width="85" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.deleted ? 'danger' : row.blocked ? 'warning' : 'success'" size="small">{{ row.deleted ? '已停用' : row.blocked ? '已冻结' : '可用' }}</el-tag></template></el-table-column><el-table-column label="同步时间" width="185"><template #default="{ row }">{{ formatTime(row.syncedAt) }}</template></el-table-column>
            <template #empty><el-empty description="尚无同步数据，请先执行首次全量同步。" :image-size="90" /></template>
          </el-table>
          <el-pagination class="pagination" v-model:current-page="dataPage" :page-size="20" :total="dataTotal" layout="total, prev, pager, next" @current-change="loadRecords" />
        </el-tab-pane>
        <el-tab-pane label="同步运行记录" name="runs">
          <p class="table-hint">每次执行有独立运行ID；自动重试沿用原运行，人工补偿创建关联的新运行。</p>
          <el-table :data="runs" v-loading="tableLoading" stripe><el-table-column label="运行ID" min-width="165"><template #default="{ row }"><el-button link type="primary" @click="selectedRun = row.runId"><span class="mono">{{ row.runId.slice(0, 13) }}…</span></el-button></template></el-table-column><el-table-column label="数据流" min-width="135"><template #default="{ row }">{{ row.jobCode === 'SAP_COST_CENTER_DELTA' ? '成本中心' : '汇率' }} · {{ modeLabel(row.mode) }}</template></el-table-column><el-table-column label="触发" width="105"><template #default="{ row }"><el-tag :type="row.triggerType === 'XXL' ? 'primary' : 'info'" size="small">{{ row.triggerType }}</el-tag></template></el-table-column><el-table-column label="XXL任务" width="95"><template #default="{ row }">{{ row.xxlJobId ?? '—' }}</template></el-table-column><el-table-column label="状态" width="115"><template #default="{ row }"><el-tag :type="statusTone(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column prop="attemptNo" label="次数" width="65" /><el-table-column label="拉取 / 应用 / 忽略" width="160"><template #default="{ row }">{{ row.fetchedCount }} / {{ row.appliedCount }} / {{ row.ignoredCount }}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip /><el-table-column label="创建时间" width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column><el-table-column label="操作" width="85"><template #default="{ row }"><el-button link type="primary" @click="selectedRun = row.runId">详情</el-button></template></el-table-column></el-table>
          <el-pagination class="pagination" v-model:current-page="runPage" :page-size="20" :total="runTotal" layout="total, prev, pager, next" @current-change="loadRuns" />
        </el-tab-pane>
        <el-tab-pane :label="`告警待办${overview?.openAlertCount ? ` (${overview.openAlertCount})` : ''}`" name="alerts">
          <el-alert title="告警持久化保存在数据库；邮件通知尚未接入。已读仅代表确认收到，追赶成功后由系统恢复对应告警。" type="info" :closable="false" show-icon />
          <el-table :data="alerts" v-loading="tableLoading" stripe><el-table-column prop="category" label="类别" width="160" /><el-table-column prop="summary" label="摘要" min-width="290" /><el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.status === 'RESOLVED' ? 'success' : row.status === 'OPEN' ? 'danger' : 'warning'" size="small">{{ row.status === 'RESOLVED' ? '已恢复' : row.status === 'OPEN' ? '待处理' : '已读未恢复' }}</el-tag></template></el-table-column><el-table-column prop="occurrenceCount" label="累计次数" width="90" /><el-table-column label="最近发现" width="185"><template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template></el-table-column><el-table-column label="操作" width="150"><template #default="{ row }"><el-button v-if="row.runId" link type="primary" @click="selectedRun = row.runId">关联运行</el-button><el-button v-if="row.status === 'OPEN'" link :disabled="!overview?.managementEnabled" @click="acknowledge(row.id)">已读</el-button></template></el-table-column></el-table>
          <el-pagination class="pagination" v-model:current-page="alertPage" :page-size="20" :total="alertTotal" layout="total, prev, pager, next" @current-change="loadAlerts" />
        </el-tab-pane>
        <el-tab-pane v-if="overview?.mockEnabled" label="SAP 源端模拟（开发）" name="source"><MockSapPanel v-if="tab === 'source'" :can-manage="!!overview?.managementEnabled" @changed="refreshOverview" /></el-tab-pane>
      </el-tabs>
    </section>
    <footer class="page-footnote">{{ overview?.mockEnabled ? '当前为本地 SAP 模拟环境' : '当前使用配置的外围数据接口' }} · 当前实现 SOURCE_CURSOR 模式 · CDC / Canal 留待后续练习</footer>
    <RunDetailDrawer :run-id="selectedRun" :can-manage="!!overview?.managementEnabled" @close="selectedRun = null" @changed="refresh" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, OfficeBuilding, Money, ArrowDown } from '@element-plus/icons-vue'
import { integrationApi } from '@/api/integration'
import type { Overview, JobDefinition, DataType, RunMode, TargetRecord, SyncRun, SyncAlert } from '@/types/integration'
import { formatTime, statusLabel, statusTone, modeLabel, isActiveRun } from '@/types/integration'
import { safeXxlAdminUrl } from '@/utils/xxlJobDisplay'
import MockSapPanel from './MockSapPanel.vue'
import RunDetailDrawer from './RunDetailDrawer.vue'

const overview = ref<Overview | null>(null), loading = ref(false), loadFailed = ref(false), tableLoading = ref(false)
const tab = ref('data'), dataType = ref<DataType>('COST_CENTER'), keyword = ref('')
const records = ref<TargetRecord[]>([]), dataPage = ref(1), dataTotal = ref(0)
const runs = ref<SyncRun[]>([]), runPage = ref(1), runTotal = ref(0)
const alerts = ref<SyncAlert[]>([]), alertPage = ref(1), alertTotal = ref(0)
const selectedRun = ref<string | null>(null), triggering = ref<string | null>(null)
const requestKeys = new Map<string, string>()
const adminUrl = computed(() => safeXxlAdminUrl(overview.value?.xxlAdminUrl))
let timer: ReturnType<typeof setInterval> | undefined
let disposed = false
let dataGeneration = 0

function cursorLabel(job: JobDefinition) {
  if (!job.committedCursor) return '尚未建立检查点'
  try {
    const state = JSON.parse(job.committedCursor)
    return state.snapshotId ? `首次全量 · 已处理 ${state.snapshotPosition} 条` : state.sourceCursor || '等待源端游标'
  } catch { return job.committedCursor }
}
async function refreshOverview() { const data = await integrationApi.overview(); if (!disposed) overview.value = data }
async function loadRecords() {
  const generation = ++dataGeneration
  const result = await integrationApi.records(dataType.value, dataPage.value, keyword.value)
  if (!disposed && generation === dataGeneration) { records.value = result.list; dataTotal.value = result.total }
}
async function loadRuns() { const result = await integrationApi.runs(runPage.value); if (!disposed) { runs.value = result.list; runTotal.value = result.total } }
async function loadAlerts() { const result = await integrationApi.alerts(alertPage.value); if (!disposed) { alerts.value = result.list; alertTotal.value = result.total } }
async function loadTab() {
  tableLoading.value = true
  try { if (tab.value === 'data') await loadRecords(); else if (tab.value === 'runs') await loadRuns(); else if (tab.value === 'alerts') await loadAlerts() }
  finally { tableLoading.value = false }
}
async function refresh() {
  if (loading.value) return
  loading.value = true
  try { await Promise.all([refreshOverview(), loadTab()]); loadFailed.value = false }
  catch { loadFailed.value = true }
  finally { loading.value = false }
}
function changeTab() { void loadTab().catch(() => { loadFailed.value = true }) }
function changeDataType() { dataPage.value = 1; records.value = []; changeTab() }
function searchRecords() { dataPage.value = 1; changeTab() }
function openScheduler() { if (adminUrl.value) window.open(adminUrl.value, '_blank', 'noopener,noreferrer') }
async function trigger(job: JobDefinition, mode: RunMode) {
  if (triggering.value) return
  let reason = mode === 'AUTO' ? '同步中心手动触发' : '同步中心手动只读对账'
  if (mode === 'REPAIR') {
    try {
      const result = await ElMessageBox.prompt('将以源端快照补齐缺失或修复相同/较旧版本，不覆盖本地较新版本，也不删除源端缺失记录。请输入原因：', '对账并修复', { inputValidator: value => !!value?.trim() || '请填写原因', type: 'warning' })
      reason = result.value
    } catch { return }
  }
  const action = `${job.jobCode}:${mode}`
  triggering.value = action
  const key = requestKeys.get(action) || crypto.randomUUID()
  requestKeys.set(action, key)
  try {
    const run = await integrationApi.trigger(job.jobCode, mode, reason, key)
    requestKeys.delete(action); selectedRun.value = run.runId; ElMessage.success('任务已提交，可在详情中查看真实进度'); await refresh()
  } catch { /* Retain the same idempotency key when the request result is unknown. */ }
  finally { triggering.value = null }
}
async function toggleJob(job: JobDefinition, enabled: boolean) {
  try { await integrationApi.enable(job.jobCode, enabled); await refreshOverview(); ElMessage.success(enabled ? '业务执行已启用；Cron仍由XXL-JOB管理' : '业务执行已停用；当前运行不受影响') } catch { /* Keep confirmed server state. */ }
}
async function resetBaseline(job: JobDefinition) {
  try {
    const { value } = await ElMessageBox.prompt('仅重建未完成的源快照，不删除已落库记录。请先取消等待重试的任务，并填写原因。', '重建未完成基线', { inputValidator: value => !!value?.trim() || '请填写原因' })
    await integrationApi.resetBaseline(job.jobCode, value); await refresh(); ElMessage.success('未完成基线已重置，请执行首次全量同步')
  } catch { /* Dialog cancellation or backend rejection. */ }
}
async function acknowledge(id: number) { try { await integrationApi.acknowledge(id); await loadAlerts() } catch { /* Shared error notification. */ } }
async function configureKey() {
  try { const { value } = await ElMessageBox.prompt('仅当后端配置了 INTEGRATION_ADMIN_KEY 时需要填写。密钥只保存在当前浏览器会话；留空清除。', '集成管理密钥', { inputType: 'password' }); if (value) sessionStorage.setItem('integration-admin-key', value); else sessionStorage.removeItem('integration-admin-key'); ElMessage.success('会话配置已保存') } catch { /* Dialog cancellation. */ }
}
onMounted(() => { void refresh(); timer = setInterval(() => { if (!document.hidden && !loadFailed.value) void refresh() }, 5000) })
onUnmounted(() => { disposed = true; dataGeneration++; if (timer) clearInterval(timer) })
</script>

<style scoped>
.sync-page{padding:28px 30px 22px;max-width:1800px;margin:auto}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:24px}.eyebrow{font:11px var(--mono);letter-spacing:.13em;color:var(--whale)}h1{font-size:27px;margin:7px 0 5px;letter-spacing:-.7px}.page-heading p{color:var(--memo);font-size:13px;margin:0}.heading-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.live-label{font-size:12px;color:var(--memo);display:flex;align-items:center;gap:7px;margin-right:5px}.live-label i{width:7px;height:7px;border-radius:50%;background:#22a975}.live-label i.busy{background:var(--whale);box-shadow:0 0 0 4px #eef2ff}.connection-error{margin-bottom:20px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:20px}.metrics article{background:white;border:1px solid var(--rule);border-radius:12px;padding:20px 22px}.metrics article>span{font-size:12px;color:var(--memo)}.metrics strong{font:600 34px var(--mono);display:block;margin:8px 0;color:var(--ink)}.metrics small{font:12px var(--sans);color:var(--memo);margin-left:8px}.metrics p{font-size:11px;color:var(--memo);margin:0}.metrics .highlighted{background:var(--grad-card-blue)}.metrics .warning strong{color:#d97706}.scheduler-panel{display:flex;justify-content:space-between;align-items:center;gap:20px;padding:18px 22px;margin-bottom:20px;border:1px solid #cad8f6;border-radius:12px;background:linear-gradient(135deg,#f8fbff,#eef4ff)}.scheduler-panel h2{font-size:17px;margin:5px 0}.scheduler-panel p{font-size:12px;color:var(--memo);margin:0}.scheduler-meta{display:flex;align-items:center;justify-content:flex-end;gap:14px;flex-wrap:wrap;font-size:12px}.scheduler-meta code{color:var(--whale)}.pipeline{background:var(--grad-mist);border:1px solid var(--rule);border-radius:12px;padding:21px 24px;display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:22px}.pipeline>div{display:grid;grid-template-columns:30px 1fr;gap:5px 9px}.step-number{grid-row:span 2;font:12px var(--mono);color:var(--whale);background:#e3ebff;border-radius:7px;width:29px;height:29px;display:grid;place-items:center}.pipeline strong{font-size:12px}.pipeline small{font-size:11px;color:var(--memo)}.flow-arrow{color:#a3b2d2;font-size:22px}.job-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:22px;min-height:80px}.job-card{border:1px solid var(--rule);background:white;border-radius:12px;overflow:hidden;padding:23px 23px 12px}.job-top{display:flex;align-items:center;justify-content:space-between;gap:12px}.job-title{display:flex;align-items:center;gap:12px}.job-title>.el-icon{width:43px;height:43px;background:var(--whale-light);border-radius:10px;font-size:21px;color:var(--whale)}.job-title h2{font-size:17px;margin:0 0 3px}.job-title .mono{font-size:10px;color:var(--memo)}dl{display:grid;gap:9px;margin:20px 0;font-size:12px}dl>div{display:flex;gap:22px}dt{color:var(--memo);min-width:82px}dd{margin:0;overflow-wrap:anywhere}.checkpoint{padding:10px 13px;border:1px dashed #d7e1f8;background:#f9fbff;border-radius:7px;display:flex;justify-content:space-between;gap:12px;font-size:11px;color:var(--memo);margin-bottom:18px}.checkpoint code{color:var(--whale);overflow-wrap:anywhere}.job-bottom{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.detail-link{font-size:11px;margin-left:7px}.job-footer{display:flex;justify-content:space-between;align-items:center;border-top:1px solid #edf1fa;margin-top:17px;padding-top:11px;color:var(--memo);font-size:11px;gap:10px;flex-wrap:wrap}.job-footer>span{display:flex;align-items:center;gap:10px}.workspace{background:white;border:1px solid var(--rule);border-radius:12px;padding:8px 22px 20px}.table-toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:12px 0 20px;flex-wrap:wrap}.table-hint{font-size:12px;color:var(--memo);margin:8px 0 18px}.pagination{display:flex;justify-content:flex-end;margin-top:18px}.page-footnote{font-size:11px;color:#8290aa;text-align:center;margin-top:20px}.workspace :deep(.el-tabs__item){height:52px;font-size:13px}.workspace :deep(.el-table){font-size:12px}@media(max-width:1250px){.pipeline small{display:none}.pipeline>div{align-items:center}.step-number{grid-row:auto}.topbar-wordmark{display:none}}@media(max-width:1050px){.job-grid{grid-template-columns:1fr}.metrics{grid-template-columns:repeat(2,1fr)}.page-heading,.scheduler-panel{align-items:flex-start;flex-direction:column}.scheduler-meta{justify-content:flex-start}.pipeline{flex-wrap:wrap}.flow-arrow{display:none}.sync-page{padding:20px 16px}.pipeline>div{min-width:40%}}
</style>
