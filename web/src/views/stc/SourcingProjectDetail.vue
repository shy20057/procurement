<template>
  <div class="doc-page" v-loading="loading">
    <!-- 企业级单据操作条 -->
    <BillActionBar
      :title="project ? project.projectName : '寻源立项详情'"
      :bill-no="project?.billNo"
      :status="project?.status"
      :allow-submit="project?.status === 'DRAFT'"
      :allow-approve="project?.status === 'SUBMITTED'"
      @back="handleBack"
      @submit="handleSubmit"
      @approve="handleApprove"
    />

    <div class="doc-content" v-if="project">

      <!-- 单头基本信息卡 -->
      <div class="doc-card">
        <div class="card-header">
          <span class="card-title">单头基本信息 · PROJECT HEADER</span>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="label">寻源项目名称</span>
            <span class="value strong">{{ project.projectName }}</span>
          </div>
          <div class="info-item">
            <span class="label">寻源方式</span>
            <span class="value">
              <el-tag :type="getMethodTag(project.sourcingMethod)">{{ getMethodLabel(project.sourcingMethod) }}</el-tag>
            </span>
          </div>
          <div class="info-item">
            <span class="label">主要品类</span>
            <span class="value">{{ project.categoryName }} <span class="mono memo">({{ project.categoryCode }})</span></span>
          </div>
          <div class="info-item">
            <span class="label">预算金额</span>
            <span class="value mono amount">¥ {{ (project.budgetAmount || 0).toLocaleString() }} {{ project.currency }}</span>
          </div>
          <div class="info-item">
            <span class="label">计划执行周期</span>
            <span class="value mono">{{ project.planStartDate }} 至 {{ project.planEndDate }}</span>
          </div>
          <div class="info-item">
            <span class="label">项目发起人</span>
            <span class="value">{{ project.initiator }}</span>
          </div>
          <div class="info-item full" v-if="project.sourcingMethod === 'SINGLE_SOURCE'">
            <span class="label warning-label">单一来源特批理由</span>
            <span class="value warning-box">{{ project.singleSourceReason || '未填写' }}</span>
          </div>
        </div>
      </div>

      <!-- 分录工作区: 候选供应商分录 -->
      <div class="doc-card">
        <div class="card-header">
          <div class="title-with-count">
            <span class="card-title">候选供应商圈选分录 (CANDIDATES)</span>
            <el-tag size="small" type="info">共 {{ project.candidates.length }} 家已匹配</el-tag>
          </div>
        </div>

        <el-table :data="project.candidates" stripe style="width:100%">
          <el-table-column prop="seq" label="行号" width="60" align="center" />
          <el-table-column prop="supplierCode" label="供应商编码" width="140">
            <template #default="{ row }">
              <span class="mono">{{ row.supplierCode }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="企业名称" min-width="200">
            <template #default="{ row }">
              <SupplierTag
                :supplier-name="row.supplierName"
                :supplier-code="row.supplierCode"
                :cert-status="row.certStatus"
                :perf-grade="row.perfGrade"
              />
            </template>
          </el-table-column>
          <el-table-column prop="matchResult" label="品类匹配结果" width="120">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.matchResult || '暂无结果' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="certStatus" label="资质认证" width="110">
            <template #default="{ row }">
              <BillStatusBadge v-if="row.certStatus" :status="row.certStatus" size="small" />
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="perfGrade" label="绩效评级" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.perfGrade" class="grade-badge" :class="row.perfGrade">{{ row.perfGrade }}</span>
              <span v-else class="memo">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="选定理由/备注" min-width="150" />
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
import type { SourcingProject, SourcingMethod } from '@/types/stc'
import BillActionBar from '@/components/business/BillActionBar.vue'
import BillStatusBadge from '@/components/business/BillStatusBadge.vue'
import SupplierTag from '@/components/business/SupplierTag.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const project = ref<SourcingProject | null>(null)

const setProject = (data: SourcingProject) => {
  project.value = {
    ...data,
    candidates: Array.isArray(data.candidates) ? data.candidates : []
  }
}

const getMethodLabel = (method?: SourcingMethod) => {
  switch (method) {
    case 'TENDER': return '公开/邀请招标'
    case 'RFQ': return '线上询价 (RFQ)'
    case 'SINGLE_SOURCE': return '单一来源'
    default: return method || '-'
  }
}

const getMethodTag = (method?: SourcingMethod) => {
  switch (method) {
    case 'TENDER': return 'primary'
    case 'RFQ': return 'success'
    case 'SINGLE_SOURCE': return 'warning'
    default: return 'info'
  }
}

const loadDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await stcApi.getProjectDetail(id)
    setProject(res.data)
  } catch (err: any) {
    ElMessage.error(err.message || '加载详情失败')
  } finally {
    loading.value = false
  }
}

const handleBack = () => {
  router.push('/stc/sourcing-projects')
}

const handleSubmit = async () => {
  if (!project.value) return
  const res = await stcApi.submitProject(project.value.id)
  setProject(res.data)
  ElMessage.success('已提交立项审批')
}

const handleApprove = async () => {
  if (!project.value) return
  const res = await stcApi.approveProject(project.value.id)
  setProject(res.data)
  ElMessage.success('立项审核通过')
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

.warning-label {
  color: #d9480f !important;
  font-weight: 600;
}
.warning-box {
  background: #fff9db;
  border: 1px solid #ffe066;
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  color: #d9480f;
  line-height: 1.5;
}

.grade-badge {
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 2px;
}
.grade-badge.A { background: #ebfbee; color: #2b8a3e; }
.grade-badge.B { background: #eef2ff; color: #4d6bfe; }
.grade-badge.C { background: #fff5f5; color: #e03131; }
</style>
