<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D14 · STC 寻源到合同</span>
        <h2 class="page-title">基准价目表 (PR自动取价数据源)</h2>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="billNo" label="价目表编号" width="200">
          <template #default="{ row }">
            <span class="bill-no mono link" @click="handleView(row)">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="priceListName" label="价目表名称" min-width="220" />
        <el-table-column prop="supplierName" label="适用供应商" min-width="200">
          <template #default="{ row }">
            <SupplierTag :supplier-name="row.supplierName" :supplier-code="row.supplierCode" />
          </template>
        </el-table-column>
        <el-table-column prop="effectiveFrom" label="生效日期" width="120" />
        <el-table-column prop="effectiveTo" label="到期日期" width="120" />
        <el-table-column prop="status" label="价格状态" width="100">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看明细</el-button>
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
import type { PriceList } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<PriceList[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getPriceLists()
    tableData.value = res.data.list
  } finally {
    loading.value = false
  }
}

const handleView = (row: PriceList) => {
  router.push(`/stc/price-lists/${row.id}`)
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
