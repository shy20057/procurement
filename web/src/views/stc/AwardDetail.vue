<template>
  <div class="doc-page" v-loading="loading">
    <!-- 企业级单据操作条 -->
    <BillActionBar
      :title="award ? `${award.awardSupplierName} · 定标单` : '定标详情'"
      :bill-no="award?.billNo"
      :status="award?.status"
      :allow-approve="award?.status === 'SUBMITTED' || award?.status === 'IN_REVIEW'"
      @back="handleBack"
      @approve="handleApprove"
    />

    <div class="doc-content" v-if="award">

      <!-- 风控警示横幅 (针对被冻结或黑名单供应商高亮展现) -->
      <el-alert
        v-if="isRiskSupplier"
        type="error"
        :closable="false"
        show-icon
        class="risk-alert"
      >
        <template #title>
          <span class="alert-title">【C3 风控强一致性警示】当前中标供应商已处于冻结/黑名单状态！</span>
        </template>
        <div class="alert-desc">
          定标单返回的供应商【{{ award.awardSupplierName }}】状态为【{{ award.awardSupplierCertStatus }}】。
          系统已依据《07-SRM与STC模块协作说明》第3章 C3 规则激活<strong>强阻断保护</strong>，在此状态解除前禁止审核定标！
        </div>
      </el-alert>

      <!-- 单头基本信息卡片 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">定标决策基本信息 · AWARD PROFILE</span>
          <span class="mono-badge">来源: {{ award.sourceBillType }} / {{ award.sourceBillNo }}</span>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">拟中标供应商</span>
            <div class="value">
              <SupplierTag
                :supplier-name="award.awardSupplierName"
                :supplier-code="award.awardSupplierCode"
                :cert-status="award.awardSupplierCertStatus"
              />
            </div>
          </div>
          <div class="info-item">
            <span class="label">中标总金额</span>
            <span class="value mono amount">¥ {{ (award.totalAwardAmount || 0).toLocaleString() }} {{ award.currency }}</span>
          </div>
          <div class="info-item">
            <span class="label">定标来源单据</span>
            <span class="value mono">
              {{ award.sourceBillNo || '—' }}
            </span>
          </div>
          <div class="info-item full">
            <span class="label">定标决策结论与说明</span>
            <span class="value">{{ award.awardConclusion }}</span>
          </div>
        </div>
      </div>

      <!-- 分录工作区: 中标明细行 -->
      <div class="doc-card">
        <div class="card-header">
          <div class="title-with-count">
            <span class="card-title">中标标的物料明细分录 (AWARD ITEMS)</span>
            <el-tag size="small" type="info">共 {{ award.items.length }} 项标的</el-tag>
          </div>
          <div class="total-summary mono">
            中标标的总计: <strong>¥ {{ (award.totalAwardAmount || 0).toLocaleString() }}</strong>
          </div>
        </div>

        <el-table :data="award.items" stripe style="width:100%">
          <el-table-column prop="seq" label="行号" width="60" align="center" />
          <el-table-column prop="itemCode" label="物料编码" width="130">
            <template #default="{ row }"><span class="mono">{{ row.itemCode }}</span></template>
          </el-table-column>
          <el-table-column prop="itemName" label="物料品名" min-width="180" />
          <el-table-column prop="spec" label="规格型号" width="140">
            <template #default="{ row }"><span class="mono">{{ row.spec || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="qty" label="中标数量" width="110" align="center">
            <template #default="{ row }"><span class="mono">{{ row.qty }} {{ row.unit }}</span></template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="中标单价" width="130" align="right">
            <template #default="{ row }"><span class="mono">¥ {{ row.unitPrice.toLocaleString() }}</span></template>
          </el-table-column>
          <el-table-column prop="totalAmount" label="总金额" width="150" align="right">
            <template #default="{ row }"><span class="mono amount">¥ {{ row.totalAmount.toLocaleString() }}</span></template>
          </el-table-column>
        </el-table>
      </div>

    </div>

    <!-- C3 阻断弹窗 -->
    <el-dialog
      v-model="c3DialogVisible"
      title="【C3 风控硬性阻断】定标审核已拦截"
      width="540px"
      :show-close="false"
      class="c3-dialog"
    >
      <div class="c3-content">
        <el-result
          icon="error"
          title="合规阻断：无法完成定标审批"
          :sub-title="c3ErrorMsg"
        >
          <template #extra>
            <div class="c3-actions">
              <el-button @click="c3DialogVisible = false">已知晓关闭</el-button>
              <el-button type="primary" @click="router.push('/srm/freeze-requests')">
                前往 SRM 查看冻结记录与整改
              </el-button>
            </div>
          </template>
        </el-result>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { stcApi } from '@/api/stc'
import type { AwardBill } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const award = ref<AwardBill | null>(null)

const c3DialogVisible = ref(false)
const c3ErrorMsg = ref('')

const isRiskSupplier = computed(() => {
  const status = award.value?.awardSupplierCertStatus
  return status === 'FROZEN' || status === 'BLACKLIST'
})

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getAwardDetail(id)
    award.value = res.data
  } catch (err: any) {
    ElMessage.error(err.message || '加载定标详情失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/awards')
}

// C3 强风控阻断拦截
const handleApprove = async () => {
  if (!award.value) return
  try {
    const res = await stcApi.approveAward(award.value.id)
    award.value = res.data
    ElMessage.success('【C3 校验通过】定标审核通过')
  } catch (err: any) {
    // 捕获 C3 硬阻断异常
    c3ErrorMsg.value = err.message
    c3DialogVisible.value = true
  }
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

.risk-alert {
  margin-bottom: 16px;
  border-radius: var(--radius-sm);
}
.alert-title {
  font-size: 14px;
  font-weight: 700;
}
.alert-desc {
  font-size: 13px;
  margin-top: 4px;
  line-height: 1.5;
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

.total-summary {
  font-size: 13px;
  color: #495057;
}
.total-summary strong {
  color: #4d6bfe;
  font-size: 15px;
}

/* 下推履约卡片 */
.downstream-panel {
  border-left: 4px solid #2b8a3e;
}
.sub-hint {
  font-size: 12px;
  color: #868e96;
}
.dispatch-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 6px;
}
.dispatch-card {
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fdfdfd;
  cursor: pointer;
  transition: all 0.2s ease;
}
.dispatch-card:hover {
  border-color: #2b8a3e;
  background: #f4fce3;
  transform: translateY(-2px);
}
.dispatch-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #ebfbee;
  color: #2b8a3e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.dispatch-info {
  flex: 1;
}
.dispatch-title {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink);
}
.dispatch-desc {
  font-size: 11.5px;
  color: #868e96;
  margin-top: 2px;
  line-height: 1.4;
}

.c3-dialog :deep(.el-result__subtitle) {
  text-align: left;
  line-height: 1.6;
  color: #c92a2a;
  background: #fff5f5;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  border: 1px solid #ffc9c9;
}
.c3-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
