<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D4 · 供应商管理</span>
        <h2 class="page-title">供应商档案</h2>
      </div>
      <el-button @click="loadExpiringCerts">
        <el-icon style="margin-right:6px"><Warning /></el-icon>证照到期预警
      </el-button>
    </div>

    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="supplierCode" label="供应商编码" width="140">
          <template #default="{ row }"><span class="bill-no">{{ row.supplierCode }}</span></template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商名称" min-width="160" />
        <el-table-column prop="unifiedCreditCode" label="统一信用代码" width="160">
          <template #default="{ row }"><span class="mono">{{ row.unifiedCreditCode }}</span></template>
        </el-table-column>
        <el-table-column prop="supplierType" label="类型" width="90">
          <template #default="{ row }"><span class="type-tag">{{ getTypeLabel(row.supplierType) }}</span></template>
        </el-table-column>
        <el-table-column prop="certStatus" label="认证状态" width="110">
          <template #default="{ row }">
            <span class="status-badge" :class="row.certStatus">{{ getCertStatusLabel(row.certStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="certValidUntil" label="证照有效期至" width="120" />
        <el-table-column prop="perfGrade" label="绩效等级" width="90">
          <template #default="{ row }">
            <span v-if="row.perfGrade" class="grade-badge" :class="row.perfGrade">{{ row.perfGrade }}</span>
            <span v-else style="color:var(--memo)">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="perfEvalCount" label="绩效次数" width="80" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="expiringDialogVisible" title="证照到期预警" width="640px">
      <el-table :data="expiringCerts" stripe>
        <el-table-column prop="supplierCode" label="供应商编码" width="140">
          <template #default="{ row }"><span class="bill-no">{{ row.supplierCode }}</span></template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商名称" min-width="160" />
        <el-table-column prop="validTo" label="到期日期" width="120" />
      </el-table>
      <template #footer>
        <el-button @click="expiringDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import { archiveApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'
import type { ArchiveResponse, SupplierType, CertStatus, ExpiringCertResponse } from '@/types/srm'

const router = useRouter()
const loading = ref(false)
const tableData = ref<ArchiveResponse[]>([])
const expiringDialogVisible = ref(false)
const expiringCerts = ref<ExpiringCertResponse[]>([])

const typeMap: Record<SupplierType, string> = { MANUFACTURER: '制造商', TRADER: '贸易商', SERVICE: '服务商' }
const certStatusMap: Record<CertStatus, string> = { POTENTIAL: '潜在', QUALIFIED: '合格', TEMP_QUALIFIED: '临时合格', FROZEN: '冻结', BLACKLIST: '黑名单' }

const getTypeLabel = (type?: SupplierType) => type ? typeMap[type] || type : '-'
const getCertStatusLabel = (status: CertStatus) => certStatusMap[status] || status

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await archiveApi.list()
    tableData.value = unwrapList(data)
  } catch { ElMessage.error('加载数据失败') }
  finally { loading.value = false }
}

const loadExpiringCerts = async () => {
  try {
    const { data } = await archiveApi.getExpiringCerts(30)
    expiringCerts.value = unwrapList(data)
    expiringDialogVisible.value = true
  } catch { ElMessage.error('加载失败') }
}

const handleDetail = (row: ArchiveResponse) => { router.push(`/srm/archives/${row.supplierCode}`) }

onMounted(() => { loadData() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--rule); }
.page-eyebrow { font-family: var(--mono); font-size: 11px; color: var(--memo); text-transform: uppercase; letter-spacing: 0.08em; display: block; margin-bottom: 4px; }
.page-title { font-family: var(--serif); font-size: 26px; font-weight: 400; color: var(--ink); margin: 0; letter-spacing: -0.3px; }
.table-card { background: var(--white); border: 1px solid var(--rule); border-radius: var(--radius-md); overflow: hidden; box-shadow: var(--shadow-sm); }
.bill-no { font-family: var(--mono); font-size: 13px; }
.mono { font-family: var(--mono); font-size: 12px; color: var(--memo); }
.type-tag { font-size: 12px; color: var(--memo); }
.status-badge { display: inline-block; padding: 2px 8px; border-radius: var(--radius-sm); font-family: var(--mono); font-size: 11px; font-weight: 500; }
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
</style>
