<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D16 · STC 寻源到合同</span>
        <h2 class="page-title">SR 配额单 (多供应商份额分配)</h2>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="billNo" label="配额单编号" width="200">
          <template #default="{ row }">
            <span class="bill-no mono link" @click="handleView(row)">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分配物料品类" min-width="180">
          <template #default="{ row }">
            {{ row.categoryName }} <span class="mono memo">({{ row.categoryCode }})</span>
          </template>
        </el-table-column>
        <el-table-column prop="ratioCycle" label="配额周期" width="130" align="center" />
        <el-table-column label="多供应商份额分布" min-width="260">
          <template #default="{ row }">
            <div class="ratio-dist-pills">
              <span
                v-for="r in row.ratios"
                :key="r.supplierCode"
                class="ratio-pill"
              >
                {{ r.supplierName }}: <strong>{{ r.ratioPercent }}%</strong>
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">配额详情</el-button>
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
import type { SupplierRatioBill } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<SupplierRatioBill[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getSupplierRatios()
    tableData.value = res.data.list
  } finally {
    loading.value = false
  }
}

const handleView = (row: SupplierRatioBill) => {
  router.push(`/stc/supplier-ratios/${row.id}`)
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

.ratio-dist-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.ratio-pill {
  background: #f1f3f5;
  border: 1px solid #dee2e6;
  border-radius: 2px;
  padding: 2px 8px;
  font-size: 12px;
}
.ratio-pill strong {
  color: #2b8a3e;
}
</style>
