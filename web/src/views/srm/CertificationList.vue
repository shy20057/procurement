<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D3 · 供应商管理</span>
        <h2 class="page-title">认证单</h2>
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
        <el-form-item label="供应商名称">
          <el-input v-model="filter.supplierName" placeholder="模糊搜索" clearable style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="filter.status = ''; filter.supplierName = ''; loadData()">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billNo" label="单据编号" width="200">
          <template #default="{ row }"><span class="bill-no">{{ row.billNo }}</span></template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商名称" min-width="160" />
        <el-table-column prop="certType" label="认证类型" width="100">
          <template #default="{ row }"><span class="mono">{{ getCertTypeLabel(row.certType) }}</span></template>
        </el-table-column>
        <el-table-column prop="conclusion" label="认证结论" width="110">
          <template #default="{ row }">
            <span v-if="row.conclusion" class="status-badge" :class="row.conclusion">{{ getConclusionLabel(row.conclusion) }}</span>
            <span v-else style="color:var(--memo)">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="responsiblePerson" label="负责人" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status">{{ statusLabel[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
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
import { certificationApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'
import type { CertResponse, BillStatus, CertConclusion, CertType } from '@/types/srm'

const loading = ref(false)
const tableData = ref<CertResponse[]>([])
const filter = reactive({ status: '' as BillStatus | '', supplierName: '' })

const statusLabel: Record<string, string> = {
  DRAFT: '草稿', SUBMITTED: '已提交', IN_REVIEW: '审核中', APPROVED: '已审核', REJECTED: '已驳回',
}
const certTypeMap: Record<CertType, string> = { FIRST: '初次认证', RENEWAL: '复审', CHANGE: '变更' }
const conclusionMap: Record<CertConclusion, string> = { QUALIFIED: '合格', TEMP_QUALIFIED: '临时合格', UNQUALIFIED: '不合格' }

const getCertTypeLabel = (type: CertType) => certTypeMap[type] || type
const getConclusionLabel = (c: CertConclusion) => conclusionMap[c] || c

const loadData = async () => {
  loading.value = true
  try {
    const params: { status?: BillStatus; supplierName?: string } = {}
    if (filter.status) params.status = filter.status
    if (filter.supplierName) params.supplierName = filter.supplierName
    const { data } = await certificationApi.list(params)
    tableData.value = unwrapList(data)
  } catch { ElMessage.error('加载数据失败') }
  finally { loading.value = false }
}

const handleView = (row: CertResponse) => {
  ElMessageBox.alert(
    `单号: ${row.billNo}\n供应商: ${row.supplierName}\n认证类型: ${getCertTypeLabel(row.certType)}\n结论: ${row.conclusion ? getConclusionLabel(row.conclusion) : '-'}\n有效期至: ${row.validUntil || '-'}`,
    '认证单详情'
  )
}

const handleApprove = async (row: CertResponse) => {
  try {
    await ElMessageBox.confirm('确认审核通过？', '提示')
    await certificationApi.approve(row.id)
    ElMessage.success('审核通过')
    loadData()
  } catch (err: any) { if (err !== 'cancel') ElMessage.error('操作失败') }
}

const handleReject = async (row: CertResponse) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { confirmButtonText: '确定', cancelButtonText: '取消' })
    await certificationApi.reject(row.id, { reason: value })
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
.status-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 500; }
.status-badge.DRAFT     { background: #f3f4f6; color: #4b5563; }
.status-badge.SUBMITTED { background: #fef3c7; color: #92400e; }
.status-badge.IN_REVIEW { background: #dfe8ff; color: #4d6bfe; }
.status-badge.APPROVED  { background: #d1fae5; color: #065f46; }
.status-badge.REJECTED  { background: #fee2e2; color: #991b1b; }
.status-badge.QUALIFIED      { background: #d1fae5; color: #065f46; }
.status-badge.TEMP_QUALIFIED { background: #fef3c7; color: #92400e; }
.status-badge.UNQUALIFIED    { background: #fee2e2; color: #991b1b; }
</style>
