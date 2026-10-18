<template>
  <div class="page">
    <div class="page-header">
      <div class="page-title-group">
        <span class="page-eyebrow">D1 · 供应商管理</span>
        <h2 class="page-title">新建邀请单</h2>
      </div>
      <el-button @click="handleBack">
        <el-icon style="margin-right:4px"><ArrowLeft /></el-icon>返回
      </el-button>
    </div>

    <div class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <div class="form-section">
          <h3 class="form-section-title">企业信息</h3>
          <el-form-item label="受邀企业名称" prop="inviteeName">
            <el-input v-model="form.inviteeName" placeholder="请输入企业名称" />
          </el-form-item>
          <el-form-item label="统一信用代码">
            <el-input v-model="form.inviteeCreditCode" placeholder="请输入统一社会信用代码" />
          </el-form-item>
          <el-form-item label="联系人">
            <el-input v-model="form.inviteeContact" placeholder="请输入联系人姓名" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.inviteePhone" placeholder="请输入联系电话" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="form.inviteeEmail" placeholder="请输入邮箱地址" />
          </el-form-item>
        </div>

        <div class="form-section">
          <h3 class="form-section-title">邀请信息</h3>
          <el-form-item label="邀请人" prop="invitedBy">
            <el-input v-model="form.invitedBy" placeholder="请输入邀请人" />
          </el-form-item>
          <el-form-item label="有效期至" prop="expireDate">
            <el-date-picker v-model="form.expireDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:200px" />
          </el-form-item>
        </div>

        <div class="form-section">
          <h3 class="form-section-title">供应品类</h3>
          <div class="category-list">
            <div v-for="(cat, index) in form.categories" :key="index" class="category-row">
              <el-input v-model="cat.categoryCode" placeholder="品类编码" style="width:160px" />
              <el-input v-model="cat.categoryName" placeholder="品类名称" style="flex:1" />
              <el-input v-model="cat.remark" placeholder="备注" style="width:200px" />
              <el-button type="danger" link @click="removeCategory(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="default" @click="addCategory" style="margin-top:8px">
              <el-icon style="margin-right:4px"><Plus /></el-icon>添加品类
            </el-button>
          </div>
        </div>

        <div class="form-actions">
          <el-button type="primary" @click="handleSubmit" :loading="submitting">提交</el-button>
          <el-button @click="handleBack">取消</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue'
import { invitationApi } from '@/api/srm'
import type { CreateInviteRequest } from '@/types/srm'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)

const form = reactive<CreateInviteRequest>({
  inviteeName: '', inviteeCreditCode: '', inviteeContact: '',
  inviteePhone: '', inviteeEmail: '', invitedBy: '', expireDate: '', categories: [],
})

const rules = {
  inviteeName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  invitedBy: [{ required: true, message: '请输入邀请人', trigger: 'blur' }],
  expireDate: [{ required: true, message: '请选择有效期', trigger: 'change' }],
}

const addCategory = () => {
  form.categories!.push({ seq: form.categories!.length + 1, categoryCode: '', categoryName: '', remark: '' })
}

const removeCategory = (index: number) => { form.categories!.splice(index, 1) }

const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
    submitting.value = true
    await invitationApi.create(form)
    ElMessage.success('创建成功')
    router.push('/srm/invitations')
  } catch { /* validation or API error */ }
  finally { submitting.value = false }
}

const handleBack = () => { router.back() }
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--rule); }
.page-eyebrow { font-family: var(--mono); font-size: 11px; color: var(--memo); text-transform: uppercase; letter-spacing: 0.08em; display: block; margin-bottom: 4px; }
.page-title { font-family: var(--serif); font-size: 26px; font-weight: 400; color: var(--ink); margin: 0; letter-spacing: -0.3px; }
.form-card { background: var(--white); border: 1px solid var(--rule); border-radius: var(--radius-md); padding: 28px 32px; box-shadow: var(--shadow-sm); }
.form-section { margin-bottom: 28px; }
.form-section-title { font-family: var(--serif); font-size: 15px; color: var(--ink); margin: 0 0 16px; padding-bottom: 8px; border-bottom: 1px solid var(--rule); }
.form-section .el-input { max-width: 400px; }
.category-list { display: flex; flex-direction: column; gap: 8px; }
.category-row { display: flex; align-items: center; gap: 8px; }
.form-actions { display: flex; gap: 12px; padding-top: 8px; border-top: 1px solid var(--rule); }
</style>
