<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D15 · STC 寻源到合同</span>
        <h2 class="page-title">框架协议 (BPA 一揽子额度协议)</h2>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="billNo" label="协议编号" width="200">
          <template #default="{ row }">
            <span class="bill-no mono link" @click="handleView(row)">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="agreementName" label="框架协议名称" min-width="220" />
        <el-table-column prop="supplierName" label="签约供应商" min-width="200">
          <template #default="{ row }">
            <SupplierTag :supplier-name="row.supplierName" :supplier-code="row.supplierCode" />
          </template>
        </el-table-column>
        <el-table-column prop="totalQuotaAmount" label="总协议额度" width="140" align="right">
          <template #default="{ row }">
            <span class="mono amount">¥ {{ (row.totalQuotaAmount || 0).toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="额度消耗进度" width="180">
          <template #default="{ row }">
            <div class="quota-bar">
              <el-progress
                :percentage="row.totalQuotaAmount > 0 ? Math.min(100, Math.round((row.usedQuotaAmount / row.totalQuotaAmount) * 100)) : 0"
                :status="row.usedQuotaAmount >= row.totalQuotaAmount ? 'exception' : ''"
              />
              <span class="quota-text mono">已用 ¥ {{ (row.usedQuotaAmount / 10000).toFixed(1) }}万</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="validTo" label="有效期至" width="120" />
        <el-table-column prop="status" label="协议状态" width="110">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">协议台账</el-button>
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
import type { BPABill } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<BPABill[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getBPAs()
    tableData.value = res.data.list
  } finally {
    loading.value = false
  }
}

const handleView = (row: BPABill) => {
  router.push(`/stc/bpa/${row.id}`)
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
.amount {
  font-weight: 700;
  color: var(--ink);
}

.quota-bar {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.quota-text {
  font-size: 11px;
  color: var(--memo);
}
</style>
