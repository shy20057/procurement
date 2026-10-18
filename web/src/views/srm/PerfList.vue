<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D8 · 供应商管理</span>
        <h2 class="page-title">绩效考核</h2>
      </div>
    </div>

    <div class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已完成" value="CONFIRMED" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商编码">
          <el-input v-model="filter.supplierCode" placeholder="模糊搜索" clearable style="width:160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="filter.status = ''; filter.supplierCode = ''; loadData()">重置</el-button>
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
        <el-table-column prop="supplierName" label="供应商名称" min-width="140" />
        <el-table-column prop="evalPeriod" label="考核周期" width="110">
          <template #default="{ row }"><span class="mono">{{ row.evalPeriod }}</span></template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80">
          <template #default="{ row }">
            <span v-if="row.totalScore !== undefined && row.totalScore !== null" class="score" :class="getScoreClass(row.totalScore)">{{ row.totalScore }}</span>
            <span v-else style="color:var(--memo)">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="等级" width="80">
          <template #default="{ row }">
            <span v-if="row.grade" class="grade-badge" :class="row.grade">{{ row.grade }}</span>
            <span v-else style="color:var(--memo)">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="evaluator" label="评价人" width="90" />
        <el-table-column prop="evalDate" label="评价日期" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status">{{ getStatusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="success" @click="handleConfirm(row)" v-if="row.status === 'SUBMITTED'">确认</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { perfApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'
import type { PerfResponse, PerfStatus, PerfGrade } from '@/types/srm'

const loading = ref(false)
const tableData = ref<PerfResponse[]>([])
const filter = reactive({ status: '' as PerfStatus | '', supplierCode: '' })

const statusLabel: Record<PerfStatus, string> = { DRAFT: '草稿', SUBMITTED: '已提交', CONFIRMED: '已完成' }
const gradeMap: Record<PerfGrade, string> = { A: 'A级', B: 'B级', C: 'C级', D: 'D级' }
const getStatusLabel = (status: PerfStatus) => statusLabel[status]
const getGradeLabel = (g?: PerfGrade) => g ? gradeMap[g] || g : '-'
const getScoreClass = (score: number) => score >= 90 ? 'score-a' : score >= 80 ? 'score-b' : score >= 70 ? 'score-c' : 'score-d'

const loadData = async () => {
  loading.value = true
  try {
    const params: { status?: PerfStatus; supplierCode?: string } = {}
    if (filter.status) params.status = filter.status
    if (filter.supplierCode) params.supplierCode = filter.supplierCode
    const { data } = await perfApi.list(params)
    tableData.value = unwrapList(data)
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const handleView = (row: PerfResponse) => {
  ElMessageBox.alert(
    `单号: ${row.billNo}\n供应商: ${row.supplierCode}\n总分: ${row.totalScore ?? '-'}\n等级: ${getGradeLabel(row.grade)}\n评价人: ${row.evaluator}\n评价日期: ${row.evalDate}`,
    '绩效考核详情'
  )
}

const handleConfirm = async (row: PerfResponse) => {
  try {
    await ElMessageBox.confirm('确认完成绩效考核？', '提示')
    await perfApi.confirm(row.id)
    ElMessage.success('已确认')
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
.score { font-family: var(--mono); font-size: 13px; font-weight: 600; }
.score-a { color: #065f46; }
.score-b { color: #4d6bfe; }
.score-c { color: #92400e; }
.score-d { color: #991b1b; }
.grade-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 600; }
.grade-badge.A { background: #d1fae5; color: #065f46; }
.grade-badge.B { background: #dfe8ff; color: #4d6bfe; }
.grade-badge.C { background: #fef3c7; color: #92400e; }
.grade-badge.D { background: #fee2e2; color: #991b1b; }
.status-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 500; }
.status-badge.DRAFT     { background: #f3f4f6; color: #4b5563; }
.status-badge.SUBMITTED { background: #fef3c7; color: #92400e; }
.status-badge.CONFIRMED { background: #d1fae5; color: #065f46; }
</style>
