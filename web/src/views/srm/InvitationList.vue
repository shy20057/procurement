<template>
  <div class="page">
    <!-- Page header -->
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D1 · 供应商管理</span>
        <h2 class="page-title">邀请单</h2>
      </div>
      <el-button type="primary" @click="handleCreate">
        <el-icon style="margin-right:6px"><Plus /></el-icon>新建邀请单
      </el-button>
    </div>

    <!-- Filter card -->
    <div class="filter-card">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:130px">
            <el-option label="待响应" value="PENDING" />
            <el-option label="已注册" value="REGISTERED" />
            <el-option label="已拒绝" value="DECLINED" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="企业名称">
          <el-input v-model="filter.inviteeName" placeholder="模糊搜索" clearable style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Table -->
    <div class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="billNo" label="单据编号" width="200">
          <template #default="{ row }">
            <span class="bill-no">{{ row.billNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="inviteeName" label="受邀企业名称" min-width="160" />
        <el-table-column prop="inviteeCreditCode" label="统一信用代码" width="160">
          <template #default="{ row }">
            <span class="mono">{{ row.inviteeCreditCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="invitedBy" label="邀请人" width="100" />
        <el-table-column prop="inviteDate" label="邀请日期" width="110" />
        <el-table-column prop="expireDate" label="有效期至" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status">{{ statusLabel[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="primary" @click="handlePushToReg(row)" v-if="row.status === 'PENDING'">下推注册</el-button>
            <el-button link type="danger" @click="handleDecline(row)" v-if="row.status === 'PENDING'">作废</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { invitationApi } from '@/api/srm'
import { unwrapList } from '@/utils/request'
import type { InviteResponse, InviteStatus } from '@/types/srm'

const router = useRouter()
const loading = ref(false)
const tableData = ref<InviteResponse[]>([])

const filter = reactive({ status: '' as InviteStatus | '', inviteeName: '' })

const statusLabel: Record<string, string> = {
  PENDING: '待响应', REGISTERED: '已注册', DECLINED: '已拒绝', EXPIRED: '已过期',
}

const loadData = async () => {
  loading.value = true
  try {
    const params: { status?: InviteStatus; inviteeName?: string } = {}
    if (filter.status) params.status = filter.status
    if (filter.inviteeName) params.inviteeName = filter.inviteeName
    const { data } = await invitationApi.list(params)
    tableData.value = unwrapList(data)
  } catch { ElMessage.error('加载数据失败') }
  finally { loading.value = false }
}

const handleReset = () => { filter.status = ''; filter.inviteeName = ''; loadData() }
const handleCreate = () => router.push('/srm/invitations/new')

const handleView = (row: InviteResponse) => {
  ElMessageBox.alert(
    `单号: ${row.billNo}\n企业: ${row.inviteeName}\n联系人: ${row.inviteeContact || '-'}\n电话: ${row.inviteePhone || '-'}\n邮箱: ${row.inviteeEmail || '-'}`,
    '邀请单详情'
  )
}

const handlePushToReg = async (row: InviteResponse) => {
  try {
    await ElMessageBox.confirm('确认下推生成注册单？', '提示')
    const { data } = await invitationApi.pushToReg(row.id)
    ElMessage.success('已下推生成注册单: ' + ((data as any)?.billNo ?? (data as any)?.data?.billNo ?? ''))
    loadData()
  } catch (err: any) { if (err !== 'cancel') ElMessage.error('操作失败') }
}

const handleDecline = async (row: InviteResponse) => {
  try {
    await ElMessageBox.confirm('确认作废此邀请单？', '提示')
    await invitationApi.decline(row.id)
    ElMessage.success('已作废')
    loadData()
  } catch (err: any) { if (err !== 'cancel') ElMessage.error('操作失败') }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--rule);
}
.page-eyebrow {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--memo);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  display: block;
  margin-bottom: 4px;
}
.page-title {
  font-family: var(--serif);
  font-size: 26px;
  font-weight: 400;
  color: var(--ink);
  margin: 0;
  letter-spacing: -0.3px;
}

.filter-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  box-shadow: var(--shadow-sm);
}
.filter-form { margin: 0; }

.table-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.bill-no { font-family: var(--mono); font-size: 13px; }
.mono { font-family: var(--mono); font-size: 12px; color: var(--memo); }

/* Status badges */
.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
}
.status-badge.PENDING    { background: #fef3c7; color: #92400e; }
.status-badge.REGISTERED { background: #d1fae5; color: #065f46; }
.status-badge.DECLINED   { background: #f3f4f6; color: #4b5563; }
.status-badge.EXPIRED    { background: #fee2e2; color: #991b1b; }
</style>
