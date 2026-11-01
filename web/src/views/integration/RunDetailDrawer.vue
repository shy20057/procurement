<template>
  <el-drawer :model-value="!!runId" title="同步运行详情" size="min(900px, 94vw)" @close="emit('close')">
    <div v-loading="loading">
      <el-alert v-if="loadFailed" title="运行详情加载失败，请检查后端连接后重试。" type="error" :closable="false" show-icon />
      <template v-if="detail">
        <div class="run-heading"><div><span class="eyebrow">{{ modeLabel(detail.mode) }}</span><h3>{{ jobName(detail.jobCode) }}</h3></div><el-tag :type="statusTone(detail.status)">{{ statusLabel(detail.status) }}</el-tag></div>
        <div class="progress-panel">
          <div class="progress-title"><strong>{{ detail.phase === 'BASELINE' ? '首次全量基线' : detail.phase === 'RECONCILE' ? '全量对账' : detail.phase === 'COMPLETED' ? '处理完成' : '增量处理' }}</strong><span>已拉取 {{ detail.fetchedCount }} 条 · 已应用 {{ detail.appliedCount }} 条 · 忽略 {{ detail.ignoredCount }} 条</span></div>
          <el-progress :percentage="percentage" :status="detail.status === 'FAILED' ? 'exception' : detail.status === 'SUCCEEDED' ? 'success' : undefined" :indeterminate="isActiveRun(detail.status) && !detail.totalCount" />
          <small>每页提交数据与检查点；失败只回滚当前页。{{ detail.totalCount ? `本次已知数据量 ${detail.totalCount} 条` : detail.status === 'SUCCEEDED' ? detail.fetchedCount ? `本次已拉取 ${detail.fetchedCount} 条` : '本次没有新增变化' : '等待源端确认数据范围' }}</small>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="运行ID" :span="2"><span class="mono id">{{ detail.runId }}</span></el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ detail.triggerType }}</el-descriptions-item>
          <el-descriptions-item label="执行次数">{{ detail.attemptNo }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.xxlJobId != null" label="XXL任务ID">{{ detail.xxlJobId }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.xxlLogKey" label="XXL日志键"><span class="mono">{{ shortXxlLogKey(detail.xxlLogKey) }}</span></el-descriptions-item>
          <el-descriptions-item v-if="detail.executorAddress" label="执行器节点" :span="2"><span class="mono">{{ detail.executorAddress }}</span></el-descriptions-item>
          <el-descriptions-item v-if="detail.xxlShardIndex != null && detail.xxlShardTotal != null" label="分片参数">{{ detail.xxlShardIndex }}/{{ detail.xxlShardTotal }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(detail.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(detail.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作原因" :span="2">{{ detail.reason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.nextRetryAt && detail.status === 'RETRY_WAIT'" label="下次重试" :span="2">{{ formatTime(detail.nextRetryAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="detail.errorSummary" class="error-box" :title="`${detail.errorCode}：${detail.errorSummary}`" type="error" :closable="false" show-icon />
        <div class="actions"><el-button :loading="loading" @click="refresh">刷新详情</el-button><el-button v-if="canManage && ['FAILED', 'CANCELLED'].includes(detail.status)" type="primary" @click="retry">失败补偿 / 重试</el-button><el-button v-if="canManage && isActiveRun(detail.status) && detail.status !== 'CANCEL_REQUESTED'" type="danger" plain @click="cancel">取消运行</el-button></div>
        <el-tabs>
          <el-tab-pane label="成功页与检查点">
            <p class="hint">显示最近100页；游标是源端位置，不是成本中心业务ID。</p>
            <el-table :data="detail.pages" size="small" border>
              <el-table-column prop="pageNo" label="页" width="55" /><el-table-column prop="phase" label="阶段" width="105" />
              <el-table-column prop="fetchedCount" label="拉取" width="65" /><el-table-column prop="appliedCount" label="应用" width="65" />
              <el-table-column prop="ignoredCount" label="忽略" width="65" /><el-table-column prop="toCursor" label="成功检查点" min-width="250" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`错误记录（${detail.failures.length}）`"><el-table :data="detail.failures" size="small" border><el-table-column prop="attemptNo" label="次数" width="60" /><el-table-column prop="errorCode" label="错误码" width="180" /><el-table-column prop="errorSummary" label="错误摘要" min-width="220" /><el-table-column label="时间" width="170"><template #default="{ row }">{{ formatTime(row.occurredAt) }}</template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane :label="`对账差异（${detail.differenceCount}）`"><p class="hint">显示前100条；“源端缺失”仅报告，不自动删除历史主数据。</p><el-table :data="detail.differences" size="small" border><el-table-column prop="businessKey" label="业务键" min-width="200" /><el-table-column prop="differenceType" label="差异类型" width="190" /><el-table-column prop="sourceVersion" label="源版本" width="80" /><el-table-column prop="targetVersion" label="本地版本" width="90" /><el-table-column label="修复" width="80"><template #default="{ row }"><el-tag :type="row.repaired ? 'success' : 'info'" size="small">{{ row.repaired ? '已修复' : '未修复' }}</el-tag></template></el-table-column></el-table></el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi } from '@/api/integration'
import type { RunDetail } from '@/types/integration'
import { formatTime, isActiveRun, statusLabel, statusTone, modeLabel } from '@/types/integration'
import { shortXxlLogKey } from '@/utils/xxlJobDisplay'

const props = defineProps<{ runId: string | null; canManage: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const detail = ref<RunDetail | null>(null)
const loading = ref(false)
const loadFailed = ref(false)
let generation = 0
let timer: ReturnType<typeof setInterval> | undefined
const jobName = (code: string) => code === 'SAP_COST_CENTER_DELTA' ? 'SAP 成本中心同步' : 'SAP 汇率同步'
const percentage = computed(() => !detail.value ? 0 : detail.value.status === 'SUCCEEDED' ? 100 : detail.value.totalCount ? Math.min(99, Math.floor(detail.value.fetchedCount / detail.value.totalCount * 100)) : 0)
async function refresh() {
  const id = props.runId
  if (!id || loading.value) return
  const requestGeneration = generation
  loading.value = true
  try {
    const result = await integrationApi.run(id)
    if (generation === requestGeneration && props.runId === id) { detail.value = result; loadFailed.value = false }
  } catch { if (generation === requestGeneration) loadFailed.value = true }
  finally { if (generation === requestGeneration) loading.value = false }
}
async function retry() {
  if (!detail.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入重试原因；系统从最后成功页继续。', '人工补偿', { inputValidator: value => !!value?.trim() || '请填写原因' })
    const run = await integrationApi.retry(detail.value.runId, value, crypto.randomUUID())
    ElMessage.success('已创建补偿任务'); emit('changed'); emit('close')
    void run
  } catch { /* Cancellation is normal; API failures already have a request-level message. */ }
}
async function cancel() {
  if (!detail.value) return
  try { await ElMessageBox.confirm('当前页会在安全边界结束，已成功数据不会撤回。', '取消运行', { type: 'warning' }); await integrationApi.cancel(detail.value.runId); await refresh(); emit('changed') } catch { /* Dialog cancellation. */ }
}
watch(() => props.runId, () => { generation++; loading.value = false; loadFailed.value = false; detail.value = null; void refresh() }, { immediate: true })
watch(() => detail.value?.status, (status, previous) => { if (previous && isActiveRun(previous) && status && !isActiveRun(status)) emit('changed') })
onMounted(() => { timer = setInterval(() => { if (props.runId && !loadFailed.value && !document.hidden && (!detail.value || isActiveRun(detail.value.status))) void refresh() }, 2000) })
onUnmounted(() => { generation++; if (timer) clearInterval(timer) })
</script>

<style scoped>
.run-heading,.progress-title,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.run-heading{margin-bottom:18px}.run-heading h3{margin:4px 0;font-size:20px}.eyebrow{font-size:12px;color:var(--whale)}.progress-panel{background:var(--whale-light);padding:18px;border-radius:10px;margin-bottom:20px}.progress-title{align-items:flex-start;margin-bottom:12px;flex-wrap:wrap}.progress-title span,small,.hint{font-size:12px;color:var(--memo)}.actions{justify-content:flex-start;margin:20px 0;flex-wrap:wrap}.error-box{margin-top:16px}.id{word-break:break-all;font-size:12px}
</style>
