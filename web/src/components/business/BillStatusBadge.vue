<template>
  <span class="bill-status-badge" :class="[statusClass, size]">
    <span class="status-dot" v-if="showDot" />
    <el-icon v-if="iconComponent" class="status-icon"><component :is="iconComponent" /></el-icon>
    <span class="status-text">{{ labelText }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import {
  Edit,
  Timer,
  Check,
  Close,
  Lock,
  CircleClose,
  Warning,
  Finished
} from '@element-plus/icons-vue'

interface Props {
  status?: string
  label?: string
  size?: 'small' | 'default' | 'large'
  showDot?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  status: 'DRAFT',
  size: 'default',
  showDot: true
})

interface StatusConfig {
  label: string
  colorClass: string
  icon?: Component
}

const STATUS_MAP: Record<string, StatusConfig> = {
  // 通用单据状态
  DRAFT: { label: '草稿', colorClass: 'draft', icon: Edit },
  SUBMITTED: { label: '已提交', colorClass: 'info', icon: Timer },
  IN_REVIEW: { label: '审核中', colorClass: 'warning', icon: Timer },
  APPROVED: { label: '已审核', colorClass: 'success', icon: Check },
  REJECTED: { label: '已驳回', colorClass: 'danger', icon: Close },
  UNAPPROVED: { label: '已反审核', colorClass: 'draft', icon: Edit },
  CONVERTED: { label: '已下推', colorClass: 'neutral', icon: Finished },
  CLOSED: { label: '已关闭', colorClass: 'neutral', icon: Lock },
  CANCELLED: { label: '已作废', colorClass: 'neutral', icon: CircleClose },

  // 邀请单状态
  PENDING: { label: '待响应', colorClass: 'warning', icon: Timer },
  REGISTERED: { label: '已注册', colorClass: 'success', icon: Check },
  DECLINED: { label: '已拒绝', colorClass: 'danger', icon: Close },
  EXPIRED: { label: '已过期', colorClass: 'neutral', icon: Lock },

  // 认证与主数据状态
  POTENTIAL: { label: '潜在', colorClass: 'info' },
  QUALIFIED: { label: '正式合格', colorClass: 'success', icon: Check },
  TEMP_QUALIFIED: { label: '临时合格', colorClass: 'primary', icon: Check },
  FROZEN: { label: '已冻结', colorClass: 'warning', icon: Warning },
  BLACKLIST: { label: '黑名单', colorClass: 'danger', icon: CircleClose },

  // 招标状态
  ANNOUNCED: { label: '已发标', colorClass: 'primary' },
  BIDDING: { label: '投标中', colorClass: 'info', icon: Timer },
  OPENED: { label: '已开标', colorClass: 'warning' },
  EVALUATING: { label: '评标中', colorClass: 'warning', icon: Timer },
  AWARDED: { label: '已定标', colorClass: 'success', icon: Check },

  // 询价状态
  PUBLISHED: { label: '已发布', colorClass: 'primary' },
  QUOTING: { label: '报价中', colorClass: 'info', icon: Timer },
  COMPARING: { label: '比价中', colorClass: 'warning', icon: Timer },

  // 价目表 / BPA / SR
  EFFECTIVE: { label: '生效中', colorClass: 'success', icon: Check },
  EXHAUSTED: { label: '额度用尽', colorClass: 'danger', icon: Lock },
  SUPERSEDED: { label: '已被替代', colorClass: 'neutral' },
  TERMINATED: { label: '已终止', colorClass: 'danger', icon: CircleClose },

  // 绩效状态
  CONFIRMED: { label: '已确认', colorClass: 'success', icon: Check }
}

const currentConfig = computed<StatusConfig>(() => {
  const upper = (props.status || '').toUpperCase()
  return STATUS_MAP[upper] || { label: props.status || '-', colorClass: 'neutral' }
})

const labelText = computed(() => props.label || currentConfig.value.label)
const statusClass = computed(() => `status-${currentConfig.value.colorClass}`)
const iconComponent = computed(() => currentConfig.value.icon)
</script>

<style scoped>
.bill-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--sans);
  font-weight: 500;
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  line-height: 1.4;
  white-space: nowrap;
  letter-spacing: 0.02em;
}

.bill-status-badge.small {
  font-size: 11.5px;
  padding: 1px 6px;
}
.bill-status-badge.default {
  font-size: 12.5px;
  padding: 2px 8px;
}
.bill-status-badge.large {
  font-size: 14px;
  padding: 4px 12px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-icon {
  font-size: 12px;
}

/* 语义状态色彩定义 */
.status-draft {
  background: #f1f3f5;
  color: #495057;
  border: 1px solid #dee2e6;
}
.status-draft .status-dot { background: #868e96; }

.status-info {
  background: #e0f2fe;
  color: #0284c7;
  border: 1px solid #bae6fd;
}
.status-info .status-dot { background: #38bdf8; }

.status-primary {
  background: #eef2ff;
  color: #4d6bfe;
  border: 1px solid #cdd8ff;
}
.status-primary .status-dot { background: #4d6bfe; }

.status-warning {
  background: #fff9db;
  color: #f08c00;
  border: 1px solid #ffe066;
}
.status-warning .status-dot { background: #fab005; }

.status-success {
  background: #ebfbee;
  color: #2b8a3e;
  border: 1px solid #b2f2bb;
}
.status-success .status-dot { background: #40c057; }

.status-danger {
  background: #fff5f5;
  color: #e03131;
  border: 1px solid #ffc9c9;
}
.status-danger .status-dot { background: #fa5252; }

.status-neutral {
  background: #f8f9fa;
  color: #868e96;
  border: 1px solid #e9ecef;
}
.status-neutral .status-dot { background: #adb5bd; }
</style>
