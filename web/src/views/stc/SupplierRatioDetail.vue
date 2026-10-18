<template>
  <div class="doc-page" v-loading="loading">
    <BillActionBar
      :title="ratioDoc ? `${ratioDoc.categoryName} · SR 配额单` : '配额详情'"
      :bill-no="ratioDoc?.billNo"
      :status="ratioDoc?.status"
      @back="handleBack"
    />

    <div class="doc-content" v-if="ratioDoc">
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">配额单主属性 · SUPPLIER RATIO HEADER</span>
          <BillStatusBadge :status="ratioDoc.status" />
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">适用物料品类</span>
            <span class="value strong">{{ ratioDoc.categoryName }}</span>
          </div>
          <div class="info-item">
            <span class="label">品类统一编码</span>
            <span class="value mono">{{ ratioDoc.categoryCode }}</span>
          </div>
          <div class="info-item">
            <span class="label">配额执行周期</span>
            <span class="value">{{ ratioDoc.ratioCycle }}</span>
          </div>
        </div>
      </div>

      <!-- 分录工作区: 多供应商份额分配 -->
      <div class="doc-card">
        <div class="card-header">
          <div class="title-with-count">
            <span class="card-title">供应商份额分配明细 (RATIO ENTRIES)</span>
            <el-tag
              :type="totalRatio === 100 ? 'success' : 'danger'"
              size="small"
            >
              配额累计: {{ totalRatio }}% {{ totalRatio === 100 ? '(已满额闭环)' : '(需严格配平至100%)' }}
            </el-tag>
          </div>
        </div>

        <el-table :data="ratioDoc.ratios" stripe style="width:100%">
          <el-table-column prop="seq" label="序号" width="60" align="center" />
          <el-table-column prop="supplierCode" label="供应商编码" width="140">
            <template #default="{ row }"><span class="mono">{{ row.supplierCode }}</span></template>
          </el-table-column>
          <el-table-column prop="supplierName" label="供应商企业名称" min-width="200">
            <template #default="{ row }">
              <SupplierTag
                :supplier-name="row.supplierName"
                :supplier-code="row.supplierCode"
              />
            </template>
          </el-table-column>
          <el-table-column prop="perfGradeRef" label="SRM 综合绩效" width="140" align="center">
            <template #default="{ row }">
              <span v-if="row.perfGradeRef" class="grade-badge" :class="row.perfGradeRef">
                {{ row.perfGradeRef }} 级
              </span>
              <span v-else class="memo">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="ratioPercent" label="采购分配比例 (%)" width="180">
            <template #default="{ row }">
              <span class="mono">{{ row.ratioPercent }}</span>
              <span class="percent-sign">%</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="份额分配依据" min-width="220">
            <template #default="{ row }">
              {{ row.remark || '—' }}
            </template>
          </el-table-column>
        </el-table>

        <!-- 底部配平提示栏 -->
        <div class="ratio-summary-bar" :class="{ 'is-invalid': totalRatio !== 100 }">
          <span class="sum-text">全单配额校验：</span>
          <span class="sum-val mono">当前配比合计 <strong>{{ totalRatio }}%</strong></span>
          <span class="sum-hint" v-if="totalRatio !== 100">
            ⚠ 当前配额数据不满足 100% 的业务规则。
          </span>
          <span class="sum-hint valid" v-else>
            ✔ 当前配额合计为 100%。
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { stcApi } from '@/api/stc'
import type { SupplierRatioBill } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const ratioDoc = ref<SupplierRatioBill | null>(null)

const totalRatio = computed(() => {
  if (!ratioDoc.value?.ratios) return 0
  return ratioDoc.value.ratios.reduce((acc, curr) => acc + (Number(curr.ratioPercent) || 0), 0)
})

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getSupplierRatioDetail(id)
    ratioDoc.value = res.data
  } catch (err: any) {
    ElMessage.error(err.message || '加载配额单失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/supplier-ratios')
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
.title-with-count {
  display: flex;
  align-items: center;
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

.percent-sign {
  margin-left: 6px;
  font-weight: 600;
  color: var(--memo);
}

.grade-badge {
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 2px;
}
.grade-badge.A { background: #ebfbee; color: #2b8a3e; }
.grade-badge.B { background: #eef2ff; color: #4d6bfe; }
.grade-badge.C { background: #fff5f5; color: #e03131; }

.ratio-summary-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 10px 16px;
  background: #ebfbee;
  border: 1px solid #b2f2bb;
  border-radius: var(--radius-sm);
  font-size: 13px;
}
.ratio-summary-bar.is-invalid {
  background: #fff5f5;
  border-color: #ffc9c9;
}
.sum-text {
  font-weight: 600;
  color: var(--ink);
}
.sum-val strong {
  font-size: 15px;
}
.sum-hint {
  font-size: 12px;
  color: #e03131;
  font-weight: 500;
}
.sum-hint.valid {
  color: #2b8a3e;
}
</style>
