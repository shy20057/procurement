<template>
  <div class="doc-page" v-loading="loading">
    <!-- 企业级单据操作条 -->
    <BillActionBar
      :title="tender ? tender.tenderTitle : '招标详情'"
      :bill-no="tender?.billNo"
      :status="tender?.status"
      @back="handleBack"
    />

    <div class="doc-content" v-if="tender">

      <!-- 单头基本信息 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">招标项目基本信息 · TENDER HEADER</span>
          <el-tag size="small" type="primary">{{ tender.tenderType === 'PUBLIC' ? '公开招标' : '邀请招标' }}</el-tag>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">招标标的名称</span>
            <span class="value strong">{{ tender.tenderTitle }}</span>
          </div>
          <div class="info-item">
            <span class="label">来源寻源立项单号</span>
            <span class="value mono">
              {{ tender.sourceProjectBillNo || '—' }}
            </span>
          </div>
          <div class="info-item">
            <span class="label">评标方法</span>
            <span class="value">
              <el-tag size="small" type="info">{{ tender.evaluationMethod === 'COMPREHENSIVE' ? '综合评分法' : '最低价法' }}</el-tag>
            </span>
          </div>
          <div class="info-item">
            <span class="label">公告发布日期</span>
            <span class="value mono">{{ tender.announceDate }}</span>
          </div>
          <div class="info-item">
            <span class="label">投标截止与开标时间</span>
            <span class="value mono">{{ tender.bidDeadline }} / {{ tender.openDate }}</span>
          </div>
          <div class="info-item">
            <span class="label">资格预审要求</span>
            <span class="value">{{ tender.needPrequalification ? '需进行资格预审' : '免预审' }}</span>
          </div>
        </div>
      </div>

      <!-- 分录工作台 Tabs: 投标供应商 vs 评标打分 -->
      <div class="doc-card">
        <el-tabs v-model="activeTab">
          <!-- 评标打分与综合排名 -->
          <el-tab-pane label="★ 评标打分与排名工作台 (EVALUATION)" name="eval">
            <div class="eval-header-tip">
              <el-icon><Trophy /></el-icon>
              <span>以下评标数据来自当前招标单。</span>
            </div>
            <el-table :data="tender.evaluations" stripe style="width:100%">
              <el-table-column prop="rank" label="排名" width="80" align="center">
                <template #default="{ row }">
                  <span class="rank-badge" :class="`rank-${row.rank}`">第 {{ row.rank }} 名</span>
                </template>
              </el-table-column>
              <el-table-column prop="supplierName" label="入围投标商" min-width="200">
                <template #default="{ row }">
                  <SupplierTag
                    :supplier-name="row.supplierName"
                    :supplier-code="row.supplierCode"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="techScore" label="技术得分" width="130" align="center">
                <template #default="{ row }">
                  <span class="mono">{{ row.techScore }} 分</span>
                </template>
              </el-table-column>
              <el-table-column prop="bizScore" label="商务得分" width="130" align="center">
                <template #default="{ row }">
                  <span class="mono">{{ row.bizScore }} 分</span>
                </template>
              </el-table-column>
              <el-table-column prop="totalScore" label="综合得分" width="120" align="center">
                <template #default="{ row }">
                  <span class="total-score mono">{{ row.totalScore }} 分</span>
                </template>
              </el-table-column>
              <el-table-column prop="judgeComment" label="评委综述结论" min-width="240" />
            </el-table>
          </el-tab-pane>

          <!-- 投标供应商与开标台 -->
          <el-tab-pane label="投标供应商分录 (BIDDERS)" name="bidders">
            <el-table :data="tender.bidders" stripe style="width:100%">
              <el-table-column prop="seq" label="行号" width="60" align="center" />
              <el-table-column prop="supplierCode" label="编码" width="130">
                <template #default="{ row }"><span class="mono">{{ row.supplierCode }}</span></template>
              </el-table-column>
              <el-table-column prop="supplierName" label="供应商名称" min-width="200" />
              <el-table-column prop="bidAmount" label="投标报价 (元)" width="150" align="right">
                <template #default="{ row }">
                  <span class="mono amount">¥ {{ (row.bidAmount || 0).toLocaleString() }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="submitTime" label="投标提交时间" width="160" />
              <el-table-column prop="prequalResult" label="资质预审" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.prequalResult === 'PASS' ? 'success' : row.prequalResult === 'FAIL' ? 'danger' : 'info'">
                    {{ row.prequalResult === 'PASS' ? '合格' : row.prequalResult === 'FAIL' ? '不合格' : '未评估' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="isShortlisted" label="入围开标" width="100" align="center">
                <template #default="{ row }">
                  <el-tag size="small" type="primary">{{ row.isShortlisted ? '已入围' : '淘汰' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Trophy } from '@element-plus/icons-vue'
import { stcApi } from '@/api/stc'
import type { TenderBill } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const tender = ref<TenderBill | null>(null)
const activeTab = ref('eval')

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getTenderDetail(id)
    tender.value = res.data
  } catch (err: any) {
    ElMessage.error(err.message || '加载招标详情失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/tenders')
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

.eval-header-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f1f3f5;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 12.5px;
  color: var(--ink);
  margin-bottom: 14px;
}

.rank-badge {
  font-family: var(--mono);
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 2px;
  font-size: 12px;
}
.rank-badge.rank-1 {
  background: #fff9db;
  color: #d9480f;
  border: 1px solid #ffe066;
}
.rank-badge.rank-2 {
  background: #f1f3f5;
  color: #495057;
}

.total-score {
  font-weight: 800;
  color: #2b8a3e;
  font-size: 15px;
}
.amount {
  font-weight: 700;
  color: var(--ink);
}
</style>
