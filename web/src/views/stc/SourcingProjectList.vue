<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D9 · STC 寻源到合同</span>
        <h2 class="page-title">寻源立项单</h2>
      </div>
    </div>

    <!-- 筛选过滤 -->
    <div class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="寻源方式">
          <el-select v-model="filter.method" placeholder="全部" clearable style="width:140px">
            <el-option label="招投标 (TENDER)" value="TENDER" />
            <el-option label="询价 (RFQ)" value="RFQ" />
            <el-option label="单一来源 (SINGLE)" value="SINGLE_SOURCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称/编号">
          <el-input v-model="filter.keyword" placeholder="模糊搜索" clearable style="width:200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="billNo" label="单据编号" width="200">
          <template #default="{ row }">
            <span class="bill-no mono link" @click="handleView(row)">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="寻源项目名称" min-width="220" />
        <el-table-column prop="categoryName" label="主要品类" width="140" />
        <el-table-column prop="sourcingMethod" label="寻源方式" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="getMethodTag(row.sourcingMethod)">{{ getMethodLabel(row.sourcingMethod) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="budgetAmount" label="预算金额 (元)" width="140" align="right">
          <template #default="{ row }">
            <span class="mono amount">¥ {{ formatMoney(row.budgetAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="initiator" label="发起人" width="140" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { stcApi } from '@/api/stc'
import type { SourcingProject, SourcingMethod } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<SourcingProject[]>([])

const filter = ref({
  method: '',
  status: '',
  keyword: ''
})

const getMethodLabel = (method: SourcingMethod) => {
  switch (method) {
    case 'TENDER': return '公开/邀请招标'
    case 'RFQ': return '线上询价 (RFQ)'
    case 'SINGLE_SOURCE': return '单一来源'
    case 'NEGOTIATION': return '竞争性谈判'
    default: return method
  }
}

const getMethodTag = (method: SourcingMethod) => {
  switch (method) {
    case 'TENDER': return 'primary'
    case 'RFQ': return 'success'
    case 'SINGLE_SOURCE': return 'warning'
    default: return 'info'
  }
}

const formatMoney = (val: number) => {
  return (val || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getProjects({
      status: filter.value.status || undefined,
      keyword: filter.value.keyword || undefined
    })
    tableData.value = filter.value.method
      ? res.data.list.filter((row: SourcingProject) => row.sourcingMethod === filter.value.method)
      : res.data.list
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filter.value = { method: '', status: '', keyword: '' }
  loadData()
}

const handleView = (row: SourcingProject) => {
  router.push(`/stc/sourcing-projects/${row.id}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page {
  padding: 20px 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
}
.page-eyebrow {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--signal);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.page-title {
  margin: 2px 0 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.filter-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 14px 18px 2px;
  margin-bottom: 16px;
}
.table-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 16px;
}

.bill-no.link {
  color: #4d6bfe;
  cursor: pointer;
  font-weight: 600;
}
.bill-no.link:hover {
  text-decoration: underline;
}
.amount {
  font-weight: 600;
  color: var(--ink);
}
</style>
