<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D10 · STC 寻源到合同</span>
        <h2 class="page-title">招标单 (发标·投标·开评标)</h2>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="billNo" label="单据编号" width="200">
          <template #default="{ row }">
            <span class="bill-no mono link" @click="handleView(row)">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tenderTitle" label="招标标的名称" min-width="220" />
        <el-table-column prop="tenderType" label="招标类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.tenderType === 'PUBLIC' ? '公开招标' : '邀请招标' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evaluationMethod" label="评标方法" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.evaluationMethod === 'COMPREHENSIVE' ? '综合评分法' : '最低价法' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openDate" label="开标日期" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">开评标台</el-button>
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
import type { TenderBill } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<TenderBill[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getTenders()
    tableData.value = res.data.list
  } finally {
    loading.value = false
  }
}

const handleView = (row: TenderBill) => {
  router.push(`/stc/tenders/${row.id}`)
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
</style>
