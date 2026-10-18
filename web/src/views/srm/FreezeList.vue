<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D6 · 供应商管理</span>
        <h2 class="page-title">冻结/解冻申请单</h2>
      </div>
    </div>

    <div class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="审核中" value="IN_REVIEW" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请类型">
          <el-select v-model="filter.applyType" placeholder="全部" clearable style="width:120px">
            <el-option label="冻结" value="FREEZE" />
            <el-option label="解冻" value="UNFREEZE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="filter.status = ''; filter.applyType = ''; loadData()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billNo" label="单据编号" width="200">
          <template #default="{ row }"><span class="bill-no">{{ row.billNo }}</span></template>
        </el-table-column>
        <el-table-column prop="supplierCode" label="供应商编码" width="140">
          <template #default="{ row }"><span class="mono">{{ row.supplierCode }}</span></template>
        </el-table-column>
        <el-table-column prop="applyType" label="申请类型" width="90">
          <template #default="{ row }">
            <span class="type-badge" :class="row.applyType">{{ row.applyType === 'FREEZE' ? '冻结' : '解冻' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerSource" label="触发来源" width="110">
          <template #default="{ row }"><span class="mono">{{ row.triggerSource }}</span></template>
        </el-table-column>
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status">{{ statusLabel[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <template v-if="row.status === 'IN_REVIEW'">
              <el-button link type="success" @click="handleApprove(row)">通过</el-button>
              <el-button link type="danger" @click="handleReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { freezeApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'
import type { FreezeResponse, BillStatus, FreezeApplyType } from '@/types/srm'

const loading = ref(false)
const tableData = ref<FreezeResponse[]>([])
const filter = reactive({ status: '' as BillStatus | '', applyType: '' as FreezeApplyType | '' })

const statusLabel: Record<string, string> = {
  DRAFT: '草稿', SUBMITTED: '已提交', IN_REVIEW: '审核中', APPROVED: '已审核', REJECTED: '已驳回',
}

const loadData = async () => {
  loading.value = true
  try {
    const params: { status?: BillStatus; applyType?: FreezeApplyType } = {}
    if (filter.status) params.status = filter.status
    if (filter.applyType) params.applyType = filter.applyType
    const { data } = await freezeApi.list(params)
    tableData.value = unwrapList(data)
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const handleView = (row: FreezeResponse) => {
  ElMessageBox.alert(
    `单号: ${row.billNo}\n供应商: ${row.supplierCode}\n类型: ${row.applyType === 'FREEZE' ? '冻结' : '解冻'}\n原因: ${row.reason}\n状态快照: ${row.preStatusSnapshot || '-'}`,
    '冻结/解冻单详情'
  )
}

const handleApprove = async (row: FreezeResponse) => {
  try {
    await ElMessageBox.confirm('确认审核通过？', '提示')
    await freezeApi.approve(row.id)
    ElMessage.success('审核通过')
    loadData()
  } catch (err: any) { if (err !== 'cancel') ElMessage.error('操作失败') }
}

const handleReject = async (row: FreezeResponse) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { confirmButtonText: '确定', cancelButtonText: '取消' })
    await freezeApi.reject(row.id, { reason: value })
    ElMessage.success('已驳回')
    loadData()
  } catch (err: any) { if (err !== 'cancel') ElMessage.error('操作失败') }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--rule); }
.page-eyebrow { font-family: var(--mono); font-size: 11px; color: var(--memo); text-transform: uppercase; letter-spacing: 0.08em; display: block; margin-bottom: 4px; }
.page-title { font-family: var(--serif); font-size: 26px; font-weight: 400; color: var(--ink); margin: 0; letter-spacing: -0.3px; }
.filter-card { background: var(--white); border: 1px solid var(--rule); border-radius: var(--radius-md); padding: 16px 20px; box-shadow: var(--shadow-sm); }
.filter-form { margin: 0; }
.table-card { background: var(--white); border: 1px solid var(--rule); border-radius: var(--radius-md); overflow: hidden; box-shadow: var(--shadow-sm); }
.bill-no { font-family: var(--mono); font-size: 13px; }
.mono { font-family: var(--mono); font-size: 12px; color: var(--memo); }
.type-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 500; }
.type-badge.FREEZE  { background: #fee2e2; color: #991b1b; }
.type-badge.UNFREEZE{ background: #d1fae5; color: #065f46; }
.status-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 500; }
.status-badge.DRAFT     { background: #f3f4f6; color: #4b5563; }
.status-badge.SUBMITTED { background: #fef3c7; color: #92400e; }
.status-badge.IN_REVIEW { background: #dfe8ff; color: #4d6bfe; }
.status-badge.APPROVED  { background: #d1fae5; color: #065f46; }
.status-badge.REJECTED  { background: #fee2e2; color: #991b1b; }
</style>
