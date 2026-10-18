<template>
  <div class="doc-page" v-loading="loading">
    <BillActionBar
      :title="priceList ? priceList.priceListName : '价目表详情'"
      :bill-no="priceList?.billNo"
      :status="priceList?.status"
      @back="handleBack"
    />

    <div class="doc-content" v-if="priceList">
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">价目表基本属性 · PRICE LIST HEADER</span>
          <BillStatusBadge :status="priceList.status" size="small" />
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">价目表名称</span>
            <span class="value strong">{{ priceList.priceListName }}</span>
          </div>
          <div class="info-item">
            <span class="label">适用供应商</span>
            <div class="value">
              <SupplierTag
                :supplier-name="priceList.supplierName"
                :supplier-code="priceList.supplierCode"
              />
            </div>
          </div>
          <div class="info-item">
            <span class="label">价格有效期</span>
            <span class="value mono">{{ priceList.effectiveFrom }} 至 {{ priceList.effectiveTo }}</span>
          </div>
          <div class="info-item">
            <span class="label">来源定标单号</span>
            <span class="value mono">
              {{ priceList.sourceAwardBillNo || '—' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 分录工作区: 物料有效期阶梯价格 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">价格分录明细行 (PRICING ITEMS)</span>
        </div>
        <el-table :data="priceList.items" stripe style="width:100%">
          <el-table-column prop="seq" label="行号" width="60" align="center" />
          <el-table-column prop="categoryCode" label="品类编码" width="130">
            <template #default="{ row }"><span class="mono">{{ row.categoryCode }}</span></template>
          </el-table-column>
          <el-table-column prop="itemCode" label="物料编码" width="130">
            <template #default="{ row }"><span class="mono">{{ row.itemCode }}</span></template>
          </el-table-column>
          <el-table-column prop="itemName" label="物料名称" min-width="180" />
          <el-table-column prop="unitPrice" label="含税基准单价" width="140" align="right">
            <template #default="{ row }">
              <span class="mono amount">¥ {{ row.unitPrice.toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="tieredPriceNote" label="阶梯价规则说明" min-width="200">
            <template #default="{ row }">
              <el-tag size="small" type="warning" v-if="row.tieredPriceNote">{{ row.tieredPriceNote }}</el-tag>
              <span v-else class="memo">无阶梯差异</span>
            </template>
          </el-table-column>
          <el-table-column prop="regionalDiffNote" label="区域价差" min-width="160">
            <template #default="{ row }">{{ row.regionalDiffNote || '全国统一价' }}</template>
          </el-table-column>
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
import type { PriceList } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const priceList = ref<PriceList | null>(null)

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getPriceListDetail(id)
    priceList.value = res.data
  } catch (err: any) {
    ElMessage.error(err.message || '加载价目表失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/price-lists')
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
.info-item .value.link {
  color: #4d6bfe;
  cursor: pointer;
}
.info-item .value.link:hover {
  text-decoration: underline;
}
.amount {
  font-weight: 700;
  color: #4d6bfe;
}
</style>
