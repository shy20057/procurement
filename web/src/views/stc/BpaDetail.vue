<template>
  <div class="doc-page" v-loading="loading">
    <BillActionBar
      :title="bpa ? bpa.agreementName : '框架协议详情'"
      :bill-no="bpa?.billNo"
      :status="bpa?.status"
      @back="handleBack"
    />

    <div class="doc-content" v-if="bpa">
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">框架协议总额度与有效期 · BPA HEADER</span>
          <BillStatusBadge :status="bpa.status" />
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">框架协议名称</span>
            <span class="value strong">{{ bpa.agreementName }}</span>
          </div>
          <div class="info-item">
            <span class="label">签约供应商</span>
            <div class="value">
              <SupplierTag
                :supplier-name="bpa.supplierName"
                :supplier-code="bpa.supplierCode"
              />
            </div>
          </div>
          <div class="info-item">
            <span class="label">协议总额度池</span>
            <span class="value mono amount">¥ {{ (bpa.totalQuotaAmount || 0).toLocaleString() }} {{ bpa.currency }}</span>
          </div>
          <div class="info-item">
            <span class="label">已使用额度</span>
            <span class="value mono text-warning">¥ {{ (bpa.usedQuotaAmount || 0).toLocaleString() }}</span>
          </div>
          <div class="info-item">
            <span class="label">剩余可用额度</span>
            <span class="value mono text-success">¥ {{ ((bpa.totalQuotaAmount - bpa.usedQuotaAmount) || 0).toLocaleString() }}</span>
          </div>
          <div class="info-item">
            <span class="label">协议有效期限</span>
            <span class="value mono">{{ bpa.validFrom }} 至 {{ bpa.validTo }}</span>
          </div>
          <div class="info-item full">
            <span class="label">额度消耗进度池</span>
            <el-progress
              :text-inside="true"
              :stroke-width="20"
              :percentage="bpa.totalQuotaAmount > 0 ? Math.min(100, Math.round((bpa.usedQuotaAmount / bpa.totalQuotaAmount) * 100)) : 0"
              status="success"
            />
          </div>
        </div>
      </div>

      <!-- 适用品类明细分录 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">适用品类与最高数量约束 (CATEGORIES & LIMITS)</span>
        </div>
        <el-table :data="bpa.categories" stripe style="width:100%">
          <el-table-column prop="seq" label="行号" width="60" align="center" />
          <el-table-column prop="categoryCode" label="品类编码" width="130">
            <template #default="{ row }"><span class="mono">{{ row.categoryCode }}</span></template>
          </el-table-column>
          <el-table-column prop="categoryName" label="品类名称" min-width="160" />
          <el-table-column prop="unitPrice" label="协议固化单价" width="140" align="right">
            <template #default="{ row }">
              <span class="mono">¥ {{ (row.unitPrice || 0).toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="maxQty" label="上限数量" width="120" align="center">
            <template #default="{ row }"><span class="mono">{{ row.maxQty || '无限制' }}</span></template>
          </el-table-column>
          <el-table-column prop="remark" label="下单说明" min-width="220" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { stcApi } from '@/api/stc'
import type { BPABill } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const bpa = ref<BPABill | null>(null)

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getBpaDetail(id)
    bpa.value = res.data
  } catch (err: any) {
    ElMessage.error(err.message || '加载框架协议失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/bpa')
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.doc-page {
  padding-bottom: 40px;
}
.doc-content {
  padding: 16px 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.doc-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--rule);
}
.card-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--memo);
  letter-spacing: 0.05em;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px 24px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.info-item.full {
  grid-column: 1 / -1;
}

.info-item .label {
  font-size: 12px;
  color: var(--memo);
}
.info-item .value {
  font-size: 13.5px;
  color: var(--ink);
}
.info-item .value.strong {
  font-weight: 700;
  font-size: 14.5px;
}
.amount {
  font-weight: 700;
  color: #4d6bfe;
}
.text-warning {
  color: #f08c00;
  font-weight: 700;
}
.text-success {
  color: #2b8a3e;
  font-weight: 700;
}
</style>
