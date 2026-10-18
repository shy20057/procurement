<template>
  <section>
    <el-alert title="开发模拟源 · 不是 SRM 本地数据" description="在这里新增、修改或停用 SAP 数据，再点击上方“拉取增量”，观察本地版本和检查点变化。源数据修改不会直接写入 SRM 表。" type="warning" :closable="false" show-icon />
    <div class="toolbar">
      <el-radio-group v-model="type" @change="changeType"><el-radio-button value="COST_CENTER">成本中心</el-radio-button><el-radio-button value="EXCHANGE_RATE">汇率</el-radio-button></el-radio-group>
      <div class="right"><span class="fault-label">模拟接口不可用</span><el-switch :model-value="unavailable" :loading="faultSaving" :disabled="!canManage" @change="toggleFault" /><el-button :loading="loading" @click="load">刷新源端</el-button><el-button type="primary" :disabled="!canManage" @click="edit(null)">新增源记录</el-button></div>
    </div>
    <el-alert v-if="unavailable" class="fault-alert" title="当前数据流的 SAP 提取接口会返回503；源记录列表仍可维护。演示结束后请关闭故障。" type="error" :closable="false" />
    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="businessKey" label="源业务键" min-width="230" show-overflow-tooltip />
      <el-table-column v-if="type === 'COST_CENTER'" label="名称" min-width="180"><template #default="{ row }">{{ row.costCenter?.name }}</template></el-table-column>
      <el-table-column v-else label="规范化汇率" min-width="170"><template #default="{ row }">1 {{ row.exchangeRate?.fromCurrency }} = {{ row.exchangeRate?.normalizedRate }} {{ row.exchangeRate?.toCurrency }}</template></el-table-column>
      <el-table-column prop="sourceVersion" label="源版本" width="95" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.costCenter?.deleted || row.exchangeRate?.deleted ? 'danger' : row.costCenter?.blocked ? 'warning' : 'success'" size="small">{{ row.costCenter?.deleted || row.exchangeRate?.deleted ? '已停用' : row.costCenter?.blocked ? '已冻结' : '可用' }}</el-tag></template></el-table-column>
      <el-table-column label="变更时间" width="185"><template #default="{ row }">{{ formatTime(row.changedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" :disabled="!canManage" @click="edit(row)">修改 / 停用</el-button></template></el-table-column>
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
          <el-form-item label="停用（保留历史记录）"><el-switch v-model="cc.deleted" /></el-form-item>
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
          <el-form-item label="停用"><el-switch v-model="fx.deleted" /></el-form-item>
        </template>
      </el-form>
      <p class="note">{{ editing ? `当前版本 ${expectedVersion}，保存时进行并发版本校验。` : '保存后产生一条源端变更，等待SRM主动同步。' }}汇率规范化由后端使用 BigDecimal 计算。</p>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存源端变化</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { integrationApi } from '@/api/integration'
import type { DataType, SourceRecord, CostCenter, ExchangeRate } from '@/types/integration'
import { formatTime } from '@/types/integration'

defineProps<{ canManage: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const type = ref<DataType>('COST_CENTER')
const records = ref<SourceRecord[]>([])
const page = ref(1), total = ref(0), loading = ref(false), unavailable = ref(false), faultSaving = ref(false)
const dialogOpen = ref(false), editing = ref(false), saving = ref(false), expectedVersion = ref(0)
const localDate = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const defaultCenter = (): CostCenter => ({ controllingArea: 'A100', costCenterCode: '', companyCode: '1000', name: '', validFrom: localDate(), validTo: '2099-12-31', blocked: false, deleted: false })
const defaultRate = (): ExchangeRate => ({ rateType: 'M', fromCurrency: 'USD', toCurrency: 'CNY', effectiveDate: localDate(), rawRate: '7.10', fromFactor: '1', toFactor: '1', quotationMethod: 'DIRECT', normalizedRate: null, deleted: false })
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
  editing.value = !!record; expectedVersion.value = record?.sourceVersion || 0
  cc.value = record?.costCenter ? { ...record.costCenter } : defaultCenter()
  fx.value = record?.exchangeRate ? { ...record.exchangeRate } : defaultRate()
  dialogOpen.value = true
}
async function save() {
  saving.value = true
  try {
    if (type.value === 'COST_CENTER') await integrationApi.saveCostCenter(cc.value, expectedVersion.value)
    else await integrationApi.saveExchangeRate({ ...fx.value, normalizedRate: null }, expectedVersion.value)
    dialogOpen.value = false; ElMessage.success('SAP源端已保存，点击同步任务即可拉取变化')
    await load(); emit('changed')
  } catch { /* Keep the form open for correction or refreshing a conflicting version. */ }
  finally { saving.value = false }
}
async function toggleFault(value: string | number | boolean) {
  const enabled = Boolean(value)
  if (enabled) {
    try { await ElMessageBox.confirm('启用后提取接口返回503，可能产生自动重试与告警；演示完成请恢复。', '模拟SAP故障', { type: 'warning' }) } catch { return }
  }
  faultSaving.value = true
  try { unavailable.value = (await integrationApi.setSourceFault(type.value, enabled)).unavailable; ElMessage.success(enabled ? '故障已启用' : 'SAP模拟接口已恢复'); emit('changed') } catch { /* Keep the confirmed server state. */ }
  finally { faultSaving.value = false }
}
onMounted(() => { void load() })
</script>

<style scoped>
.toolbar,.right{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.toolbar{justify-content:space-between;margin:20px 0}.fault-label,.note{font-size:12px;color:var(--memo)}.fault-alert{margin-bottom:16px}.pagination{justify-content:flex-end;margin-top:18px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.form-grid .el-form-item{min-width:0}.form-grid .el-select{width:100%}.note{line-height:1.8}@media(max-width:650px){.form-grid{grid-template-columns:1fr}}
</style>
