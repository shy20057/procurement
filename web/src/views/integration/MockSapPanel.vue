<template>
  <section>
    <el-alert title="开发模拟源 · 不是 SRM 本地数据" :description="`保存只修改源端。安全延迟为 ${settleDelaySeconds} 秒（默认约 2 分钟），重叠回扫 ${overlapSeconds} 秒；变化进入安全窗口后仍需执行同步。立即点击增量可能暂时读不到刚保存的数据。`" type="warning" :closable="false" show-icon />
    <div class="toolbar">
      <el-radio-group v-model="type" @change="changeType"><el-radio-button value="COST_CENTER">成本中心</el-radio-button><el-radio-button value="EXCHANGE_RATE">汇率</el-radio-button></el-radio-group>
      <div class="right"><span class="fault-label">临时模拟 503（进程重启恢复）</span><el-switch :model-value="unavailable" :loading="faultSaving" :disabled="!canManage" @change="toggleFault" /><el-button :loading="loading" @click="load">刷新源端</el-button><el-button type="primary" :disabled="!canManage" @click="edit(null)">新增源记录</el-button></div>
    </div>
    <el-alert v-if="unavailable" class="fault-alert" title="当前数据流的 SAP 提取接口会返回503；源记录列表仍可维护。演示结束后请关闭故障。" type="error" :closable="false" />
    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="businessKey" label="源业务键" min-width="230" show-overflow-tooltip />
      <el-table-column v-if="type === 'COST_CENTER'" label="名称" min-width="180"><template #default="{ row }">{{ row.costCenter?.name }}</template></el-table-column>
      <el-table-column v-else label="规范化汇率" min-width="170"><template #default="{ row }">1 {{ row.exchangeRate?.fromCurrency }} = {{ row.exchangeRate?.normalizedRate }} {{ row.exchangeRate?.toCurrency }}</template></el-table-column>
      <el-table-column prop="sourceId" label="源记录 ID" min-width="150" />
      <el-table-column label="独立业务状态" min-width="195"><template #default="{ row }"><div class="state-tags"><el-tag :type="(row.costCenter || row.exchangeRate)?.blocked ? 'warning' : 'info'" size="small">{{ (row.costCenter || row.exchangeRate)?.blocked ? '冻结' : '未冻结' }}</el-tag><el-tag :type="(row.costCenter || row.exchangeRate)?.enabled ? 'success' : 'warning'" size="small">{{ (row.costCenter || row.exchangeRate)?.enabled ? '启用' : '禁用' }}</el-tag><el-tag :type="(row.costCenter || row.exchangeRate)?.isDeleted ? 'danger' : 'info'" size="small">{{ (row.costCenter || row.exchangeRate)?.isDeleted ? '逻辑删除' : '未删除' }}</el-tag></div></template></el-table-column>
      <el-table-column prop="updatedAt" label="源修改时间（UTC · 微秒）" min-width="250" />
      <el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" :disabled="!canManage" @click="edit(row)">修改 / 状态</el-button></template></el-table-column>
    </el-table>
    <el-pagination class="pagination" v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="load" />

    <el-dialog v-model="dialogOpen" :title="`${editing ? '修改' : '新增'} SAP 模拟${type === 'COST_CENTER' ? '成本中心' : '汇率'}`" width="min(640px, 94vw)" :close-on-click-modal="false">
      <el-form label-position="top" class="form-grid">
        <template v-if="type === 'COST_CENTER'">
          <el-form-item label="控制范围"><el-input v-model="cc.controllingArea" :disabled="editing" maxlength="20" /></el-form-item>
          <el-form-item label="成本中心编码"><el-input v-model="cc.costCenterCode" :disabled="editing" maxlength="40" /></el-form-item>
          <el-form-item label="公司代码"><el-input v-model="cc.companyCode" maxlength="20" /></el-form-item>
          <el-form-item label="成本中心名称"><el-input v-model="cc.name" maxlength="200" /></el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="cc.validFrom" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="失效日期"><el-date-picker v-model="cc.validTo" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="冻结（禁止新业务引用）"><el-switch v-model="cc.blocked" /></el-form-item>
          <el-form-item label="业务启用"><el-switch v-model="cc.enabled" /></el-form-item>
          <el-form-item label="逻辑删除（保留历史记录）"><el-switch v-model="cc.isDeleted" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="汇率类型"><el-input v-model="fx.rateType" :disabled="editing" maxlength="20" /></el-form-item>
          <el-form-item label="生效日期"><el-date-picker v-model="fx.effectiveDate" :disabled="editing" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          <el-form-item label="原币（三位大写）"><el-input v-model="fx.fromCurrency" :disabled="editing" maxlength="3" /></el-form-item>
          <el-form-item label="目标币（三位大写）"><el-input v-model="fx.toCurrency" :disabled="editing" maxlength="3" /></el-form-item>
          <el-form-item label="原始汇率"><el-input v-model="fx.rawRate" inputmode="decimal" /></el-form-item>
          <el-form-item label="报价方式"><el-select v-model="fx.quotationMethod"><el-option label="直接报价 DIRECT" value="DIRECT" /><el-option label="间接报价 INDIRECT" value="INDIRECT" /></el-select></el-form-item>
          <el-form-item label="原币因子"><el-input v-model="fx.fromFactor" inputmode="decimal" /></el-form-item>
          <el-form-item label="目标币因子"><el-input v-model="fx.toFactor" inputmode="decimal" /></el-form-item>
          <el-form-item label="业务启用"><el-switch v-model="fx.enabled" /></el-form-item>
          <el-form-item label="逻辑删除"><el-switch v-model="fx.isDeleted" /></el-form-item>
        </template>
      </el-form>
      <p class="note">{{ editing ? `源 ID ${sourceId}，原始修改时间 ${expectedUpdatedAt}；保存时按原始微秒时间校验。业务键不可修改。` : 'ID 和修改时间由源端生成。' }}冻结、启用和逻辑删除相互独立。汇率规范化由后端计算。</p>
      <el-alert v-if="conflicted" title="源记录已被其他人修改，当前输入已保留。请先复制需要保留的内容，再点击“重新读取源记录”获取最新值后重新编辑；不会覆盖他人的修改。" type="warning" :closable="false" show-icon />
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button v-if="conflicted" :loading="reloading" @click="reloadEditing">重新读取源记录</el-button><el-button type="primary" :loading="saving" :disabled="conflicted" @click="save">保存源端变化</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi } from '@/api/integration'
import type { DataType, SourceRecord, CostCenter, ExchangeRate } from '@/types/integration'
import { sourceSaveConflict } from './sourceSaveConflict'

withDefaults(defineProps<{ canManage: boolean; settleDelaySeconds?: number; overlapSeconds?: number }>(), { settleDelaySeconds: 120, overlapSeconds: 300 })
const emit = defineEmits<{ changed: [] }>()
const type = ref<DataType>('COST_CENTER')
const records = ref<SourceRecord[]>([])
const page = ref(1), total = ref(0), loading = ref(false), unavailable = ref(false), faultSaving = ref(false)
const dialogOpen = ref(false), editing = ref(false), saving = ref(false), conflicted = ref(false), reloading = ref(false)
const sourceId = ref<string | null>(null), expectedUpdatedAt = ref<string | null>(null)
let editingPage = 1
const localDate = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const defaultCenter = (): CostCenter => ({ controllingArea: 'A100', costCenterCode: '', companyCode: '1000', name: '', validFrom: localDate(), validTo: '2099-12-31', blocked: false, enabled: true, isDeleted: false })
const defaultRate = (): ExchangeRate => ({ rateType: 'M', fromCurrency: 'USD', toCurrency: 'CNY', effectiveDate: localDate(), rawRate: '7.10', fromFactor: '1', toFactor: '1', quotationMethod: 'DIRECT', normalizedRate: null, blocked: false, enabled: true, isDeleted: false })
const cc = ref<CostCenter>(defaultCenter())
const fx = ref<ExchangeRate>(defaultRate())
let loadGeneration = 0
async function load() {
  const generation = ++loadGeneration
  loading.value = true
  try {
    const [data, fault] = await Promise.all([integrationApi.source(type.value, page.value), integrationApi.sourceFault(type.value)])
    if (generation === loadGeneration) { records.value = data.list; total.value = data.total; unavailable.value = fault.unavailable }
  } catch { /* Shared HTTP interceptor reports the error. */ }
  finally { if (generation === loadGeneration) loading.value = false }
}
function changeType() { page.value = 1; records.value = []; void load() }
function edit(record: SourceRecord | null) {
  editing.value = !!record; sourceId.value = record?.sourceId ?? null; expectedUpdatedAt.value = record?.updatedAt ?? null
  editingPage = page.value; conflicted.value = false
  cc.value = record?.costCenter ? { ...record.costCenter } : defaultCenter()
  fx.value = record?.exchangeRate ? { ...record.exchangeRate } : defaultRate()
  dialogOpen.value = true
}
async function save() {
  saving.value = true
  try {
    const original = { sourceId: sourceId.value, expectedUpdatedAt: expectedUpdatedAt.value }
    if (type.value === 'COST_CENTER') await integrationApi.saveCostCenter({ data: cc.value, ...original })
    else await integrationApi.saveExchangeRate({ data: { ...fx.value, normalizedRate: null }, ...original })
    dialogOpen.value = false; ElMessage.success('源端已保存；请等待安全延迟后执行同步，并在运行详情核对完整水位')
    await load(); emit('changed')
  } catch (error) {
    const conflict = sourceSaveConflict(error, sourceId.value)
    conflicted.value = conflict === 'stale-edit'
    if (conflict === 'stale-edit') {
      ElMessage.warning('并发编辑冲突：已保留输入，请重新读取源记录后编辑')
    } else if (conflict === 'duplicate-key') {
      ElMessage.warning('源业务键已存在，当前输入已保留；请修改业务键后重新保存，或读取已有记录进行编辑')
    }
    // Other errors retain their actual code/message from the shared HTTP interceptor.
  }
  finally { saving.value = false }
}
async function reloadEditing() {
  try {
    await ElMessageBox.confirm('重新读取会替换表单中的输入。请先复制需要保留的内容。', '刷新冲突记录', { type: 'warning' })
    reloading.value = true
    const result = await integrationApi.source(type.value, editingPage)
    const latest = result.list.find(record => record.sourceId === sourceId.value)
    if (latest) edit(latest)
    else ElMessage.warning('原记录已不在当前页，请关闭表单并刷新源端列表后重新选择')
  } catch { /* Keep the current input when cancelled or the reload fails. */ }
  finally { reloading.value = false }
}
async function toggleFault(value: string | number | boolean) {
  const enabled = Boolean(value)
  if (enabled) {
    try { await ElMessageBox.confirm('启用后提取接口返回503，可能产生自动重试与告警；开关仅保存在当前进程内存中，重启自动恢复。演示完成请关闭。', '模拟SAP故障', { type: 'warning' }) } catch { return }
  }
  faultSaving.value = true
  try { unavailable.value = (await integrationApi.setSourceFault(type.value, enabled)).unavailable; ElMessage.success(enabled ? '故障已启用' : 'SAP模拟接口已恢复'); emit('changed') } catch { /* Keep the confirmed server state. */ }
  finally { faultSaving.value = false }
}
onMounted(() => { void load() })
</script>

<style scoped>
.state-tags{display:flex;flex-wrap:wrap;gap:4px}
.toolbar,.right{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.toolbar{justify-content:space-between;margin:20px 0}.fault-label,.note{font-size:12px;color:var(--memo)}.fault-alert{margin-bottom:16px}.pagination{justify-content:flex-end;margin-top:18px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.form-grid .el-form-item{min-width:0}.form-grid .el-select{width:100%}.note{line-height:1.8}@media(max-width:650px){.form-grid{grid-template-columns:1fr}}
</style>
