<template>
  <div class="doc-page" v-loading="loading">
    <!-- 企业级单据操作条 -->
    <BillActionBar
      :title="rfq ? rfq.rfqTitle : '询价详情'"
      :bill-no="rfq?.billNo"
      :status="rfq?.status"
      @back="handleBack"
    />

    <div class="doc-content" v-if="rfq">

      <!-- 单头基本卡片 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">询价基础参数 · RFQ PROFILE</span>
          <span class="mono-badge">轮次进度：第 {{ rfq.currentRound }} / {{ rfq.maxRounds }} 轮</span>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">询价主题</span>
            <span class="value strong">{{ rfq.rfqTitle }}</span>
          </div>
          <div class="info-item">
            <span class="label">来源寻源立项单号</span>
            <span class="value mono">
              {{ rfq.sourceProjectBillNo || "—" }}
            </span>
          </div>
          <div class="info-item">
            <span class="label">本轮报价截止时间</span>
            <span class="value mono">{{ rfq.quoteDeadline }}</span>
          </div>
        </div>
      </div>

      <!-- 实时报价矩阵，仅展示当前询价单返回的报价 -->
      <div class="doc-card matrix-card">
        <div class="card-header matrix-header">
          <div class="title-left">
            <el-icon class="matrix-icon"><Histogram /></el-icon>
            <span class="card-title highlight">多轮横向比价矩阵</span>
          </div>
          <div class="round-tabs" v-if="availableRounds.length">
            <span class="tab-label">查看比价轮次:</span>
            <el-radio-group v-model="selectedRound" size="small">
              <el-radio-button v-for="round in availableRounds" :key="round" :value="round">
                第 {{ round }} 轮
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <el-empty v-if="roundQuotes.length === 0" description="暂无报价数据" />
        <div v-else class="matrix-table-wrapper">
          <table class="matrix-table">
            <thead>
              <tr>
                <th class="col-item">采购标的物料</th>
                <th class="col-qty">采购数量</th>
                <th v-for="supplier in roundSuppliers" :key="supplier.code" class="col-sup">
                  <div class="sup-head"><span class="sup-name">{{ supplier.name }}</span></div>
                  <div class="sup-code mono">{{ supplier.code }}</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in roundItems" :key="item.itemCode">
                <td>
                  <div class="item-title">{{ item.itemName }}</div>
                  <div class="item-meta mono">编码: {{ item.itemCode }}</div>
                </td>
                <td class="align-center mono">{{ item.qty }} {{ item.unit }}</td>
                <td v-for="supplier in roundSuppliers" :key="supplier.code" class="align-right"
                    :class="{ 'best-price-cell': isLowestQuote(supplier.code, item.itemCode) }">
                  <template v-if="getQuote(supplier.code, item.itemCode)">
                    <span class="star" v-if="isLowestQuote(supplier.code, item.itemCode)">★ 本轮最低单价</span>
                    <span class="price mono">¥ {{ getQuote(supplier.code, item.itemCode)?.quoteUnitPrice?.toLocaleString() }}</span>
                    <div class="sub-amount mono">合计: ¥ {{ getQuote(supplier.code, item.itemCode)?.quoteAmount?.toLocaleString() }}</div>
                    <div class="delivery-tag" v-if="getQuote(supplier.code, item.itemCode)?.deliveryDays != null">
                      承诺交期: {{ getQuote(supplier.code, item.itemCode)?.deliveryDays }} 天
                    </div>
                  </template>
                  <span v-else>—</span>
                </td>
              </tr>
              <tr class="summary-row">
                <td colspan="2"><span class="sum-title">本轮报价金额汇总</span></td>
                <td v-for="supplier in roundSuppliers" :key="supplier.code" class="align-right">
                  <div class="total-price mono">¥ {{ getTotalAmount(supplier.code) }}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 分录明细 Tab: 原始轮次报价台账 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">全部轮次报价明细留痕 (AUDIT QUOTE LOGS)</span>
        </div>
        <el-table :data="rfq.quotes" stripe style="width:100%" max-height="300">
          <el-table-column prop="roundNo" label="轮次" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small">第 {{ row.roundNo }} 轮</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="报价供应商" min-width="180" />
          <el-table-column prop="itemName" label="物料品名" width="160" />
          <el-table-column prop="qty" label="采购数量" width="100" align="center" />
          <el-table-column prop="quoteUnitPrice" label="单价" width="120" align="right">
            <template #default="{ row }"><span class="mono">¥ {{ row.quoteUnitPrice }}</span></template>
          </el-table-column>
          <el-table-column prop="quoteAmount" label="总金额" width="130" align="right">
            <template #default="{ row }"><span class="mono">¥ {{ row.quoteAmount.toLocaleString() }}</span></template>
          </el-table-column>
          <el-table-column prop="deliveryDays" label="承诺交期" width="100" align="center">
            <template #default="{ row }">{{ row.deliveryDays }} 天</template>
          </el-table-column>
          <el-table-column prop="quoteDate" label="报价时间" width="110" />
          <el-table-column prop="remark" label="报价说明" min-width="140" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Histogram } from '@element-plus/icons-vue'
import { stcApi } from '@/api/stc'
import type { RFQBill, RFQQuoteEntry } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const rfq = ref<RFQBill | null>(null)
const selectedRound = ref<number>(1)

const availableRounds = computed(() =>
  [...new Set((rfq.value?.quotes ?? []).map(quote => quote.roundNo))].sort((a, b) => a - b)
)
const roundQuotes = computed(() =>
  (rfq.value?.quotes ?? []).filter(quote => quote.roundNo === selectedRound.value)
)
const roundSuppliers = computed(() => {
  const suppliers = new Map<string, string>()
  roundQuotes.value.forEach(quote => suppliers.set(quote.supplierCode, quote.supplierName))
  return [...suppliers].map(([code, name]) => ({ code, name }))
})
const roundItems = computed(() => {
  const items = new Map<string, RFQQuoteEntry>()
  roundQuotes.value.forEach(quote => {
    if (!items.has(quote.itemCode)) items.set(quote.itemCode, quote)
  })
  return [...items.values()]
})
const getQuote = (supplierCode: string, itemCode: string) =>
  roundQuotes.value.find(quote => quote.supplierCode === supplierCode && quote.itemCode === itemCode)
const isLowestQuote = (supplierCode: string, itemCode: string) => {
  const quote = getQuote(supplierCode, itemCode)
  if (!quote) return false
  const prices = roundQuotes.value.filter(item => item.itemCode === itemCode).map(item => item.quoteUnitPrice)
  return quote.quoteUnitPrice === Math.min(...prices)
}
const getTotalAmount = (supplierCode: string) =>
  roundQuotes.value.filter(quote => quote.supplierCode === supplierCode)
    .reduce((total, quote) => total + Number(quote.quoteAmount || 0), 0)
    .toLocaleString('zh-CN', { minimumFractionDigits: 2 })

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getRfqDetail(id)
    rfq.value = res.data
    selectedRound.value = availableRounds.value.at(-1) ?? 1
  } catch (err: any) {
    ElMessage.error(err.message || '加载详情失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/rfqs')
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
.card-title.highlight {
  color: #4d6bfe;
  font-size: 14px;
}
.mono-badge {
  font-family: var(--mono);
  font-size: 12px;
  background: #f1f3f5;
  color: #495057;
  padding: 2px 8px;
  border-radius: 2px;
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

/* 比价矩阵专有样式 */
.matrix-card {
  border: 2px solid #4d6bfe;
  box-shadow: 0 4px 16px rgba(59, 91, 219, 0.08);
}
.matrix-header {
  background: #eef2ff;
  margin: -16px -20px 16px -20px;
  padding: 12px 20px;
  border-bottom: 1px solid #bac8ff;
}
.title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.matrix-icon {
  font-size: 18px;
  color: #4d6bfe;
}

.round-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tab-label {
  font-size: 12px;
  color: #495057;
  font-weight: 600;
}

.matrix-table-wrapper {
  overflow-x: auto;
}
.matrix-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.matrix-table th, .matrix-table td {
  border: 1px solid #dee2e6;
  padding: 12px 14px;
}
.matrix-table th {
  background: #f8f9fa;
  font-weight: 600;
  color: #495057;
}

.col-sup.is-best {
  background: #ebfbee;
  border-top: 3px solid #2b8a3e;
}
.sup-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.sup-name {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink);
}
.sup-code {
  font-size: 11px;
  color: #868e96;
  margin-top: 2px;
}

.item-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--ink);
}
.item-meta {
  font-size: 11.5px;
  color: #868e96;
  margin-top: 2px;
}

.align-center { text-align: center; }
.align-right { text-align: right; }

.best-price-cell {
  background: #f4fce3;
  color: #2b8a3e;
}
.best-price-cell .price {
  font-size: 15px;
  font-weight: 800;
  color: #2b8a3e;
}
.price {
  font-size: 14px;
  font-weight: 600;
}
.star {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  color: #2b8a3e;
  background: #d3f9d8;
  padding: 1px 4px;
  border-radius: 2px;
  margin-right: 6px;
}
.sub-amount {
  font-size: 11px;
  color: #868e96;
  margin-top: 2px;
}
.benchmark-cell {
  background: #f8f9fa;
  color: #868e96;
}

/* 汇总行 */
.summary-row td {
  background: #f8f9fa;
  border-top: 2px solid #adb5bd;
}
.sum-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}
.sum-desc {
  font-size: 11.5px;
  color: #868e96;
  margin-top: 2px;
}
.total-price {
  font-size: 16px;
  font-weight: 800;
  color: var(--ink);
}
.total-price.best-total {
  font-size: 18px;
  color: #2b8a3e;
}
.diff-tag {
  font-size: 11px;
  font-weight: 600;
  margin-top: 2px;
}
.diff-tag.positive { color: #2b8a3e; }
.diff-tag.neutral { color: #868e96; }
.delivery-tag {
  font-size: 11.5px;
  color: #495057;
  margin-top: 2px;
}

/* 决策行 */
.action-row td {
  background: #fff;
  padding: 16px 14px;
}
.decision-label {
  font-size: 14px;
  font-weight: 700;
  color: #4d6bfe;
}
.rec-note {
  font-size: 11px;
  color: #2b8a3e;
  font-weight: 600;
  margin-top: 4px;
}
</style>
