<template>
  <div class="doc-page" v-loading="loading">
    <BillActionBar
      :title="contract ? contract.contractName : '采购合同详情'"
      :bill-no="contract?.billNo"
      :status="contract?.status"
      @back="handleBack"
    />

    <div class="doc-content" v-if="contract">

      <!-- 单头卡片 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">合同主属性与法务合规 · CONTRACT HEADER</span>
          <div class="tags-group">
            <el-tag size="small" :type="contract.legalReviewStatus === 'APPROVED' ? 'success' : 'info'">
              法务：{{ contract.legalReviewStatus === 'APPROVED' ? '审核已通过' : contract.legalReviewStatus === 'IN_REVIEW' ? '审核中' : '待审核' }}
            </el-tag>
            <el-tag size="small" :type="contract.sealStatus === 'SEALED' ? 'success' : 'info'">{{ contract.sealStatus === 'SEALED' ? '已电子盖章' : '待盖章' }}</el-tag>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">合同名称</span>
            <span class="value strong">{{ contract.contractName }}</span>
          </div>
          <div class="info-item">
            <span class="label">签约供应商</span>
            <div class="value">
              <SupplierTag
                :supplier-name="contract.supplierName"
                :supplier-code="contract.supplierCode"
              />
            </div>
          </div>
          <div class="info-item">
            <span class="label">合同总金额</span>
            <span class="value mono amount">¥ {{ (contract.contractAmount || 0).toLocaleString() }} {{ contract.currency }}</span>
          </div>
          <div class="info-item">
            <span class="label">合同履约有效期</span>
            <span class="value mono">{{ contract.validFrom }} 至 {{ contract.validTo }}</span>
          </div>
          <div class="info-item">
            <span class="label">企业方签约代表</span>
            <span class="value">{{ contract.signatory }}</span>
          </div>
          <div class="info-item">
            <span class="label">来源定标单号</span>
            <span class="value mono">
              {{ contract.sourceAwardBillNo || '—' }}
            </span>
          </div>
        </div>
      </div>

      <!-- 分录工作区: 条款与标的清单 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">合同条款与标的分录 (CLAUSES & ITEMS)</span>
        </div>
        <el-table :data="contract.clauses" stripe style="width:100%">
          <el-table-column prop="seq" label="序号" width="60" align="center" />
          <el-table-column prop="clauseType" label="条款类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.clauseType === 'TARGET' ? 'primary' : 'info'">
                {{ row.clauseType === 'TARGET' ? '标的清单' : row.clauseType === 'PAYMENT' ? '付款条款' : row.clauseType === 'WARRANTY' ? '质保条款' : '其他条款' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="itemName" label="标的品名 / 条款主题" min-width="220">
            <template #default="{ row }">
              <strong>{{ row.itemName || '-' }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="qty" label="约定数量" width="100" align="center">
            <template #default="{ row }"><span class="mono">{{ row.qty || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="含税单价" width="120" align="right">
            <template #default="{ row }">
              <span class="mono">{{ row.unitPrice ? `¥ ${row.unitPrice.toLocaleString()}` : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="条款约定明细" min-width="260" />
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
import type { PurchaseContract } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const contract = ref<PurchaseContract | null>(null)

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getContractDetail(id)
    contract.value = res.data
  } catch (err: any) {
    ElMessage.error(err.message || '加载合同详情失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/contracts')
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
.tags-group {
  display: flex;
  gap: 8px;
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
