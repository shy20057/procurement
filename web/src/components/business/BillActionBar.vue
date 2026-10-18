<template>
  <div class="bill-action-bar">
    <div class="bar-left">
      <el-button v-if="showBack" text class="back-btn" @click="$emit('back')">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回列表</span>
      </el-button>

      <div class="title-group">
        <span class="bill-code mono" v-if="billNo">{{ billNo }}</span>
        <h2 class="bill-title">{{ title }}</h2>
        <BillStatusBadge v-if="status" :status="status" size="default" />
      </div>
    </div>

    <div class="bar-right">
      <!-- 基础操作 -->
      <slot name="custom-actions" />

      <el-button
        v-if="canSubmit"
        type="primary"
        :loading="loading"
        @click="$emit('submit')"
      >
        <el-icon><Upload /></el-icon>提交审核
      </el-button>

      <el-button
        v-if="canApprove"
        type="success"
        :loading="loading"
        @click="$emit('approve')"
      >
        <el-icon><Check /></el-icon>审核通过
      </el-button>

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowLeft,
  Upload,
  Check
} from '@element-plus/icons-vue'
import BillStatusBadge from './BillStatusBadge.vue'

interface Props {
  title: string
  billNo?: string
  status?: string
  loading?: boolean
  showBack?: boolean
  // 仅已接入后端的操作由页面显式启用
  allowSubmit?: boolean
  allowApprove?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showBack: true,
  loading: false
})

defineEmits<{
  (e: 'back'): void
  (e: 'submit'): void
  (e: 'approve'): void
}>()

// 仅在页面已接入真实后端操作时显示对应按钮。
const canSubmit = computed(() => props.allowSubmit === true)
const canApprove = computed(() => props.allowApprove === true)
</script>

<style scoped>
.bill-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: var(--white);
  border-bottom: 1px solid var(--rule);
  position: sticky;
  top: 0;
  z-index: 9;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.bar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  color: var(--memo);
  font-size: 13px;
  padding: 0 8px;
}
.back-btn:hover {
  color: var(--ink);
}

.title-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bill-code {
  font-size: 14px;
  color: var(--memo);
  background: var(--paper);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--rule);
}

.bill-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: -0.01em;
}

.bar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
