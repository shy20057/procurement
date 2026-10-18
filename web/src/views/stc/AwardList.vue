<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D12 · STC 寻源到合同</span>
        <h2 class="page-title">定标单 (定标决策与C3守门)</h2>
      </div>
      <div class="header-tip">
        <el-tag type="warning" effect="plain">
          <el-icon><WarningFilled /></el-icon>
          集成 C3 强一致性风控守门机制 · 阻断冻结/黑名单供应商审批
        </el-tag>
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
        <el-table-column prop="sourceBillType" label="定标来源" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.sourceBillType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceBillNo" label="来源单号" width="200">
          <template #default="{ row }">
            <span class="mono memo">{{ row.sourceBillNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="awardSupplierName" label="拟中标供应商" min-width="200">
          <template #default="{ row }">
            <SupplierTag
              :supplier-name="row.awardSupplierName"
              :supplier-code="row.awardSupplierCode"
              :cert-status="row.awardSupplierCertStatus"
            />
          </template>
        </el-table-column>
        <el-table-column prop="totalAwardAmount" label="中标金额 (元)" width="150" align="right">
          <template #default="{ row }">
            <span class="mono amount">¥ {{ (row.totalAwardAmount || 0).toLocaleString() }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <BillStatusBadge :status="row.status" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">评审详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { WarningFilled } from '@element-plus/icons-vue'
import { stcApi } from '@/api/stc'
import type { AwardBill } from '@/types/stc'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<AwardBill[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res = await stcApi.getAwards()
    tableData.value = res.data.list
  } finally {
    loading.value = false
  }
}

const handleView = (row: AwardBill) => {
  router.push(`/stc/awards/${row.id}`)
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
</style>
