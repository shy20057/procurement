<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D4 · 供应商管理</span>
        <h2 class="page-title">档案详情 <span class="page-code">{{ archive?.supplierCode }}</span></h2>
      </div>
      <el-button @click="handleBack">
        <el-icon style="margin-right:4px"><ArrowLeft /></el-icon>返回
      </el-button>
    </div>

    <div v-loading="loading">
      <template v-if="archive">
        <!-- Base info card -->
        <div class="info-card">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">供应商编码</span>
              <span class="info-value mono">{{ archive.supplierCode }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">供应商名称</span>
              <span class="info-value">{{ archive.supplierName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">统一信用代码</span>
              <span class="info-value mono">{{ archive.unifiedCreditCode || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">供应商类型</span>
              <span class="info-value">{{ getTypeLabel(archive.supplierType) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">认证状态</span>
              <span class="status-badge" :class="archive.certStatus">{{ getCertStatusLabel(archive.certStatus) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">证照有效期至</span>
              <span class="info-value">{{ archive.certValidUntil || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">绩效等级</span>
              <span v-if="archive.perfGrade" class="grade-badge" :class="archive.perfGrade">{{ archive.perfGrade }}</span>
              <span v-else class="info-value">-</span>
            </div>
            <div class="info-item">
              <span class="info-label">绩效次数</span>
              <span class="info-value">{{ archive.perfEvalCount ?? '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">最近绩效日期</span>
              <span class="info-value">{{ archive.lastPerfDate || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">银行名称</span>
              <span class="info-value">{{ archive.bankName || '-' }}</span>
            </div>
            <div class="info-item full">
              <span class="info-label">银行账号</span>
              <span class="info-value mono">{{ archive.bankAccount || '-' }}</span>
            </div>
          </div>
        </div>

        <!-- Tabs -->
        <div class="tabs-card">
          <el-tabs>
            <el-tab-pane label="供应品类">
              <el-table :data="archive.categories ?? []" stripe style="width:100%">
                <el-table-column prop="seq" label="序号" width="60" />
                <el-table-column prop="categoryCode" label="品类编码" width="150">
                  <template #default="{ row }"><span class="mono">{{ row.categoryCode }}</span></template>
                </el-table-column>
                <el-table-column prop="categoryName" label="品类名称" min-width="160" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="证照">
              <el-table :data="archive.certs ?? []" stripe style="width:100%">
                <el-table-column prop="seq" label="序号" width="60" />
                <el-table-column prop="certType" label="证照类型" width="120">
                  <template #default="{ row }"><span class="mono">{{ row.certType }}</span></template>
                </el-table-column>
                <el-table-column prop="certNo" label="证照编号" width="160">
                  <template #default="{ row }"><span class="mono">{{ row.certNo }}</span></template>
                </el-table-column>
                <el-table-column prop="issueOrg" label="发证机构" min-width="140" />
                <el-table-column prop="validFrom" label="有效期从" width="110" />
                <el-table-column prop="validTo" label="有效期至" width="110" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="联系人">
              <el-table :data="archive.contacts" stripe style="width:100%">
                <el-table-column prop="seq" label="序号" width="60" />
                <el-table-column prop="contactName" label="姓名" width="100" />
                <el-table-column prop="position" label="职位" width="100" />
                <el-table-column prop="phone" label="电话" width="130">
                  <template #default="{ row }"><span class="mono">{{ row.phone }}</span></template>
                </el-table-column>
                <el-table-column prop="email" label="邮箱" min-width="180" />
                <el-table-column prop="isPrimary" label="主联系人" width="90" align="center">
                  <template #default="{ row }">
                    <span v-if="row.isPrimary" class="primary-tag">是</span>
                    <span v-else style="color:var(--memo)">否</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="关联单据">
              <el-table :data="relatedBills" stripe style="width:100%">
                <el-table-column prop="billType" label="单据类型" width="120" />
                <el-table-column prop="billNo" label="单据编号" width="200">
                  <template #default="{ row }"><span class="mono">{{ row.billNo }}</span></template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100" />
                <el-table-column prop="billDate" label="日期" width="110" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { archiveApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'
import type { ArchiveResponse, SupplierType, CertStatus, RelatedBillResponse } from '@/types/srm'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const archive = ref<ArchiveResponse | null>(null)
const relatedBills = ref<RelatedBillResponse[]>([])

const typeMap: Record<SupplierType, string> = { MANUFACTURER: '制造商', TRADER: '贸易商', SERVICE: '服务商' }
const certStatusMap: Record<CertStatus, string> = { POTENTIAL: '潜在', QUALIFIED: '合格', TEMP_QUALIFIED: '临时合格', FROZEN: '冻结', BLACKLIST: '黑名单' }
const getTypeLabel = (type?: SupplierType) => type ? typeMap[type] || type : '-'
const getCertStatusLabel = (status: CertStatus) => certStatusMap[status] || status

const loadData = async () => {
  loading.value = true
  try {
    const code = route.params.code as string
    const [archiveRes, billsRes] = await Promise.all([archiveApi.get(code), archiveApi.getRelatedBills(code)])
    archive.value = (archiveRes.data as any)?.data ?? archiveRes.data
    relatedBills.value = unwrapList(billsRes.data)
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const handleBack = () => { router.back() }

onMounted(() => { loadData() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--rule); }
.page-eyebrow { font-family: var(--mono); font-size: 11px; color: var(--memo); text-transform: uppercase; letter-spacing: 0.08em; display: block; margin-bottom: 4px; }
.page-title { font-family: var(--serif); font-size: 26px; font-weight: 400; color: var(--ink); margin: 0; letter-spacing: -0.3px; display: flex; align-items: baseline; gap: 12px; }
.page-code { font-family: var(--mono); font-size: 16px; color: var(--memo); }
.info-card { background: var(--white); border: 1px solid var(--rule); border-radius: var(--radius-md); padding: 24px 28px; box-shadow: var(--shadow-sm); }
.info-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px 40px; }
.info-item { display: flex; flex-direction: column; gap: 4px; }
.info-item.full { grid-column: 1 / -1; }
.info-label { font-size: 12px; color: var(--memo); text-transform: uppercase; letter-spacing: 0.05em; }
.info-value { font-size: 14px; color: var(--ink); }
.mono { font-family: var(--mono); font-size: 13px; }
.status-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 500; width: fit-content; }
.status-badge.POTENTIAL     { background: #f3f4f6; color: #4b5563; }
.status-badge.QUALIFIED     { background: #d1fae5; color: #065f46; }
.status-badge.TEMP_QUALIFIED{ background: #fef3c7; color: #92400e; }
.status-badge.FROZEN        { background: #fee2e2; color: #991b1b; }
.status-badge.BLACKLIST     { background: #991b1b; color: #fff; }
.grade-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 600; }
.grade-badge.A { background: #d1fae5; color: #065f46; }
.grade-badge.B { background: #dfe8ff; color: #4d6bfe; }
.grade-badge.C { background: #fef3c7; color: #92400e; }
.grade-badge.D { background: #fee2e2; color: #991b1b; }
.primary-tag { background: var(--signal-mute); color: var(--signal); font-family: var(--mono); font-size: 11px; padding: 1px 6px; border-radius: var(--radius-sm); }
.tabs-card { background: var(--white); border: 1px solid var(--rule); border-radius: var(--radius-md); overflow: hidden; box-shadow: var(--shadow-sm); padding: 0 4px; }
</style>
