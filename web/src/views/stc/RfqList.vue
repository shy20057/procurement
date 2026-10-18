<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D11 · STC 寻源到合同</span>
        <h2 class="page-title">询价单 (多轮询价比价台)</h2>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="billNo" label="单据编号" width="200">
          <template #default="{ row }">
            <span class="bill-no mono link" @click="handleView(row)">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rfqTitle" label="询价主题" min-width="220" />
        <el-table-column prop="sourceProjectBillNo" label="来源立项单号" width="200">
          <template #default="{ row }">
            <span class="mono memo">{{ row.sourceProjectBillNo || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentRound" label="当前轮次" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="primary">第 {{ row.currentRound }} / {{ row.maxRounds }} 轮</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quoteDeadline" label="报价截止时间" width="160" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">比价看板</el-button>
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
import type { RFQBill } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<RFQBill[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getRFQs()
    tableData.value = res.data.list
  } finally {
    loading.value = false
  }
}

const handleView = (row: RFQBill) => {
  router.push(`/stc/rfqs/${row.id}`)
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
