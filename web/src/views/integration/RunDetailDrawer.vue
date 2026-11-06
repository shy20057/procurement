<template>
  <el-drawer :model-value="!!runId" title="同步运行详情" size="min(900px, 94vw)" @close="emit('close')">
    <div v-loading="loading">
      <el-alert v-if="loadFailed" title="运行详情加载失败，请检查后端连接后重试。" type="error" :closable="false" show-icon />
      <template v-if="detail">
        <div class="run-heading"><div><span class="eyebrow">{{ modeLabel(detail.mode) }}</span><h3>{{ jobName(detail.jobCode) }}</h3></div><el-tag :type="statusTone(detail.status)">{{ statusLabel(detail.status) }}</el-tag></div>
        <div class="progress-panel">
          <div class="progress-title"><strong>{{ detail.phase === 'BASELINE' ? '首次全量基线' : detail.mode === 'REPAIR' ? '差异修复' : detail.mode === 'RECONCILE' ? '全量对账' : detail.phase === 'COMPLETED' ? '处理完成' : '增量处理' }}</strong><span>已拉取 {{ detail.fetchedCount }} 条 · 已应用 {{ detail.appliedCount }} 条 · 忽略 {{ detail.ignoredCount }} 条</span></div>
          <div class="page-progress"><strong>成功页 {{ detail.pages.length }} 页<span v-if="detail.pages.length >= 100">（仅展示最近 100 页）</span></strong><el-tag :type="detail.caughtUp ? 'success' : 'info'">{{ detail.mode === 'AUTO' ? detail.caughtUp ? '已完成目标窗口' : '尚未追赶至目标水位' : '独立对账，不推进 AUTO 水位' }}</el-tag></div>
          <small>当前状态表没有固定总行数，不计算精确完成百分比。每页业务数据和检查点一起提交；完整水位仅在窗口末页提交后推进。{{ detail.status === 'SUCCEEDED' && !detail.fetchedCount ? '本次没有读取到新变化。' : '' }}</small>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="运行ID" :span="2"><span class="mono id">{{ detail.runId }}</span></el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ detail.triggerType }}</el-descriptions-item>
          <el-descriptions-item label="执行次数">{{ detail.attemptNo }}</el-descriptions-item>
          <el-descriptions-item label="目标完整水位" :span="2"><span class="mono">{{ detail.targetWatermark || '—' }}</span></el-descriptions-item>
          <el-descriptions-item v-if="!purchaseOrderRun" label="本次窗口 ID" :span="2"><span class="mono">{{ detail.windowId || '—' }}</span></el-descriptions-item>
          <el-descriptions-item label="当前完整水位 W" :span="2"><span class="mono">{{ purchaseOrderRun ? orderCheckpoint?.completedWatermark || '基线尚未完成' : checkpoint ? checkpoint.completedWatermark || '基线尚未完成' : '检查点暂不可用' }}</span></el-descriptions-item>
          <el-descriptions-item v-if="purchaseOrderRun" label="当前活动窗口上界" :span="2"><span class="mono">{{ orderCheckpoint?.windowEndAt || '无活动窗口' }}</span></el-descriptions-item>
          <el-descriptions-item v-else label="当前活动窗口 [S, E)" :span="2"><span class="mono">{{ checkpoint?.activeWindowId ? `[${checkpoint.windowStartAt}, ${checkpoint.windowEndAt})` : '无活动窗口' }}</span></el-descriptions-item>
          <el-descriptions-item label="当前页位置" :span="2"><span class="mono">{{ purchaseOrderRun ? purchaseOrderCursor(orderCheckpoint) : checkpointCursor(checkpoint) }}</span></el-descriptions-item>
          <el-descriptions-item v-if="detail.xxlJobId != null" label="XXL任务ID">{{ detail.xxlJobId }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.xxlLogKey" label="XXL日志键"><span class="mono">{{ shortXxlLogKey(detail.xxlLogKey) }}</span></el-descriptions-item>
          <el-descriptions-item v-if="detail.executorAddress" label="执行器节点" :span="2"><span class="mono">{{ detail.executorAddress }}</span></el-descriptions-item>
          <el-descriptions-item v-if="detail.xxlShardIndex != null && detail.xxlShardTotal != null" label="分片参数">{{ detail.xxlShardIndex }}/{{ detail.xxlShardTotal }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(detail.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(detail.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作原因" :span="2">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.nextRetryAt && detail.status === 'RETRY_WAIT'" label="下次重试" :span="2">{{ formatTime(detail.nextRetryAt) }}</el-descriptions-item>
        </el-descriptions>
        <p class="hint">“当前”检查点属于该任务的最新 AUTO 进度；历史运行自身的扫描范围与页位置见下方扫描上下文及成功页。对账、修复使用独立扫描，不改变 AUTO 完整水位。</p>
        <el-alert v-if="detail.errorSummary" class="error-box" :title="`${detail.errorCode}：${detail.errorSummary}`" type="error" :closable="false" show-icon />
        <div class="actions">
          <el-button :loading="loading" @click="refresh">刷新详情</el-button>
          <el-button v-if="purchaseOrderRun" :disabled="!adminUrl" @click="openScheduler">前往 XXL-JOB Admin</el-button>
          <template v-else>
            <el-button v-if="canManage && ['FAILED', 'CANCELLED'].includes(detail.status)" type="primary" @click="retry">失败补偿 / 重试</el-button>
            <el-button v-if="canManage && isActiveRun(detail.status) && detail.status !== 'CANCEL_REQUESTED'" type="danger" plain @click="cancel">取消运行</el-button>
          </template>
        </div>
        <el-tabs>
          <el-tab-pane label="成功页与检查点">
            <p class="hint">显示最近100页；增量游标为原始（修改时间，源 ID），基线按固定源 ID 上界扫描，页游标不等于完整水位。</p>
            <el-table :data="detail.pages" size="small" border>
              <el-table-column prop="pageNo" label="页" width="55" /><el-table-column prop="phase" label="阶段" width="105" />
              <el-table-column prop="fetchedCount" label="拉取" width="65" /><el-table-column prop="appliedCount" label="应用" width="65" />
              <el-table-column prop="ignoredCount" label="忽略" width="65" /><el-table-column prop="fromCursor" label="页前游标" min-width="250" show-overflow-tooltip /><el-table-column prop="toCursor" label="页后游标" min-width="250" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`错误记录（${detail.failures.length}）`"><el-table :data="detail.failures" size="small" border><el-table-column prop="attemptNo" label="次数" width="60" /><el-table-column prop="errorCode" label="错误码" width="180" /><el-table-column prop="errorSummary" label="错误摘要" min-width="220" /><el-table-column label="时间" width="170"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane :label="`对账差异（${detail.differenceCount}）`"><p class="hint">显示前100条；多页扫描是观察结果，差异需点查复核。“源端缺失”仅报告，不自动删除历史业务数据；较新的目标不自动覆盖。</p><el-table :data="detail.differences" size="small" border><el-table-column prop="businessKey" label="业务键" min-width="200" /><el-table-column prop="sourceId" label="源 ID" min-width="150" /><el-table-column prop="differenceType" label="差异类型" width="190" /><el-table-column prop="sourceUpdatedAt" label="源修改时间（UTC）" min-width="245" /><el-table-column prop="targetUpdatedAt" label="目标源修改时间（UTC）" min-width="245" /><el-table-column prop="verificationStatus" label="复核状态" min-width="140" /><el-table-column prop="repairStatus" label="修复状态" min-width="140" /><el-table-column label="修复结果" width="85"><template #default="{ row }"><el-tag :type="row.repaired ? 'success' : 'info'" size="small">{{ row.repaired ? '已修复' : '未修复' }}</el-tag></template></el-table-column><el-table-column prop="detail" label="差异说明" min-width="240" show-overflow-tooltip /></el-table></el-tab-pane>
          <el-tab-pane label="扫描上下文"><p class="hint">运行持久化的扫描范围和位置；所有源 ID 与微秒时间按服务端原样显示。</p><pre class="scan-context">{{ prettyJson(detail.scanContext) }}</pre></el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi } from '@/api/integration'
import type { RunDetail, SyncCheckpoint } from '@/types/integration'
import { formatTime, isActiveRun, statusLabel, statusTone, modeLabel, isPurchaseOrderJob, syncJobLabel } from '@/types/integration'
import { shortXxlLogKey } from '@/utils/xxlJobDisplay'
import { checkpointCursor, prettyJson, parsePurchaseOrderCheckpoint, purchaseOrderCursor } from './checkpointDisplay'

const props = defineProps<{ runId: string | null; canManage: boolean; purchaseOrderCheckpoint?: string | null; adminUrl?: string | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const detail = ref<RunDetail | null>(null)
const checkpoint = ref<SyncCheckpoint | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
let generation = 0
let timer: ReturnType<typeof setInterval> | undefined
const jobName = (code: string) => `SAP ${syncJobLabel(code)}同步`
const purchaseOrderRun = computed(() => !!detail.value && isPurchaseOrderJob(detail.value.jobCode))
const orderCheckpoint = computed(() => parsePurchaseOrderCheckpoint(props.purchaseOrderCheckpoint))
function openScheduler() {
  if (props.adminUrl) window.open(props.adminUrl, '_blank', 'noopener,noreferrer')
}
async function refresh() {
  const id = props.runId
  if (!id || loading.value) return
  const requestGeneration = generation
  loading.value = true
  try {
    const result = await integrationApi.run(id)
    if (generation === requestGeneration && props.runId === id) { detail.value = result; loadFailed.value = false }
    if (isPurchaseOrderJob(result.jobCode)) return
    try {
      const state = await integrationApi.checkpoint(result.jobCode)
      if (generation === requestGeneration && props.runId === id) checkpoint.value = state
    } catch { if (generation === requestGeneration && props.runId === id) checkpoint.value = null }
  } catch { if (generation === requestGeneration) loadFailed.value = true }
  finally { if (generation === requestGeneration) loading.value = false }
}
async function retry() {
  if (!detail.value || purchaseOrderRun.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入重试原因；数据同步恢复最后成功页和原窗口，对账/修复补偿重新全扫。', '人工补偿', { inputValidator: value => !!value?.trim() || '请填写原因' })
    const run = await integrationApi.retry(detail.value.runId, value, crypto.randomUUID())
    ElMessage.success('已创建补偿任务'); emit('changed'); emit('close')
    void run
  } catch { /* Cancellation is normal; API failures already have a request-level message. */ }
}
async function cancel() {
  if (!detail.value || purchaseOrderRun.value) return
  try { await ElMessageBox.confirm('当前页会在安全边界结束，已成功数据不会撤回。', '取消运行', { type: 'warning' }); await integrationApi.cancel(detail.value.runId); await refresh(); emit('changed') } catch { /* Dialog cancellation. */ }
}
watch(() => props.runId, () => { generation++; loading.value = false; loadFailed.value = false; detail.value = null; checkpoint.value = null; void refresh() }, { immediate: true })
watch(() => detail.value?.status, (status, previous) => { if (previous && isActiveRun(previous) && status && !isActiveRun(status)) emit('changed') })
onMounted(() => { timer = setInterval(() => { if (props.runId && !loadFailed.value && !document.hidden && (!detail.value || isActiveRun(detail.value.status))) void refresh() }, 2000) })
onUnmounted(() => { generation++; if (timer) clearInterval(timer) })
</script>

<style scoped>
.page-progress{display:flex;justify-content:space-between;align-items:center;gap:12px;margin:14px 0;flex-wrap:wrap}.scan-context{white-space:pre-wrap;overflow-wrap:anywhere;background:var(--whale-light);padding:16px;border-radius:8px;font-size:12px}.mono{overflow-wrap:anywhere}
.run-heading,.progress-title,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.run-heading{margin-bottom:18px}.run-heading h3{margin:4px 0;font-size:20px}.eyebrow{font-size:12px;color:var(--whale)}.progress-panel{background:var(--whale-light);padding:18px;border-radius:10px;margin-bottom:20px}.progress-title{align-items:flex-start;margin-bottom:12px;flex-wrap:wrap}.progress-title span,small,.hint{font-size:12px;color:var(--memo)}.actions{justify-content:flex-start;margin:20px 0;flex-wrap:wrap}.error-box{margin-top:16px}.id{word-break:break-all;font-size:12px}
</style>
