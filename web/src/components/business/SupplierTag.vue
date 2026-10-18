<template>
  <div class="supplier-tag-wrapper">
    <el-popover
      v-if="showPopover && supplier"
      placement="top"
      :width="300"
      trigger="hover"
    >
      <template #reference>
        <span class="supplier-tag" :class="[riskLevel, { 'is-clickable': clickable }]" @click="handleClick">
          <el-icon v-if="isRisk" class="risk-icon"><WarningFilled /></el-icon>
          <span class="supplier-name">{{ displayName }}</span>
          <span v-if="showCode && supplierCode" class="supplier-code mono">({{ supplierCode }})</span>
          <span v-if="statusLabel" class="status-indicator" :class="statusClass">{{ statusLabel }}</span>
        </span>
      </template>

      <div class="supplier-popover-content">
        <div class="popover-title">
          <span class="name">{{ supplierName }}</span>
          <el-tag size="small" :type="tagType">{{ statusLabel }}</el-tag>
        </div>
        <div class="popover-meta">
          <div class="meta-row">
            <span class="label">供应商编码:</span>
            <span class="value mono">{{ supplierCode }}</span>
          </div>
          <div class="meta-row" v-if="unifiedCreditCode">
            <span class="label">信用代码:</span>
            <span class="value mono">{{ unifiedCreditCode }}</span>
          </div>
          <div class="meta-row" v-if="perfGrade">
            <span class="label">绩效评级:</span>
            <span class="value grade-badge" :class="perfGrade">{{ perfGrade }} 级</span>
          </div>
          <div class="meta-row warning-text" v-if="isRisk">
            <span class="label">风控警示:</span>
            <span class="value">{{ riskReason || '当前状态受限，严禁作为中标商定标！' }}</span>
          </div>
        </div>
      </div>
    </el-popover>

    <!-- 没有 Popover 时的普通展示 -->
    <span v-else class="supplier-tag" :class="[riskLevel, { 'is-clickable': clickable }]" @click="handleClick">
      <el-icon v-if="isRisk" class="risk-icon"><WarningFilled /></el-icon>
      <span class="supplier-name">{{ displayName }}</span>
      <span v-if="showCode && supplierCode" class="supplier-code mono">({{ supplierCode }})</span>
      <span v-if="statusLabel" class="status-indicator" :class="statusClass">{{ statusLabel }}</span>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

interface SupplierLike {
  supplierCode?: string
  supplierName?: string
  certStatus?: string
  status?: string
  unifiedCreditCode?: string
  perfGrade?: string
  riskReason?: string
}

interface Props {
  supplier?: SupplierLike
  supplierCode?: string
  supplierName?: string
  certStatus?: string
  unifiedCreditCode?: string
  perfGrade?: string
  riskReason?: string
  showCode?: boolean
  showPopover?: boolean
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showCode: true,
  showPopover: true,
  clickable: false
})

const emit = defineEmits<{
  (e: 'click', code?: string): void
}>()

const supplierCode = computed(() => props.supplier?.supplierCode || props.supplierCode || '')
const supplierName = computed(() => props.supplier?.supplierName || props.supplierName || '')
const certStatus = computed(() => {
  const s = props.supplier?.certStatus || props.supplier?.status || props.certStatus || ''
  return s.toUpperCase()
})
const unifiedCreditCode = computed(() => props.supplier?.unifiedCreditCode || props.unifiedCreditCode || '')
const perfGrade = computed(() => props.supplier?.perfGrade || props.perfGrade || '')
const riskReason = computed(() => props.supplier?.riskReason || props.riskReason || '')

const displayName = computed(() => supplierName.value || supplierCode.value || '未知供应商')

const isRisk = computed(() => certStatus.value === 'FROZEN' || certStatus.value === 'BLACKLIST')

const riskLevel = computed(() => {
  if (certStatus.value === 'BLACKLIST') return 'risk-blacklist'
  if (certStatus.value === 'FROZEN') return 'risk-frozen'
  if (certStatus.value === 'TEMP_QUALIFIED') return 'risk-temp'
  if (certStatus.value === 'QUALIFIED') return 'risk-qualified'
  return 'risk-normal'
})

const statusLabel = computed(() => {
  switch (certStatus.value) {
    case 'QUALIFIED': return '合格'
    case 'TEMP_QUALIFIED': return '临时合格'
    case 'FROZEN': return '已冻结'
    case 'BLACKLIST': return '黑名单'
    case 'POTENTIAL': return '潜在'
    default: return ''
  }
})

const statusClass = computed(() => {
  switch (certStatus.value) {
    case 'QUALIFIED': return 'status-success'
    case 'TEMP_QUALIFIED': return 'status-primary'
    case 'FROZEN': return 'status-warning'
    case 'BLACKLIST': return 'status-danger'
    default: return 'status-info'
  }
})

const tagType = computed(() => {
  switch (certStatus.value) {
    case 'QUALIFIED': return 'success'
    case 'TEMP_QUALIFIED': return 'primary'
    case 'FROZEN': return 'warning'
    case 'BLACKLIST': return 'danger'
    default: return 'info'
  }
})

const handleClick = () => {
  if (props.clickable) {
    emit('click', supplierCode.value)
  }
}
</script>

<style scoped>
.supplier-tag-wrapper {
  display: inline-flex;
  vertical-align: middle;
}

.supplier-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  font-size: 13px;
  color: var(--ink);
  transition: all 0.15s ease;
}

.supplier-tag.is-clickable {
  cursor: pointer;
}
.supplier-tag.is-clickable:hover {
  border-color: #4d6bfe;
  background: #eef2ff;
}

.supplier-name {
  font-weight: 500;
}

.supplier-code {
  color: #868e96;
  font-size: 12px;
}

.risk-icon {
  font-size: 14px;
}

.status-indicator {
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 2px;
  font-weight: 600;
}

/* 风险等级样式 */
.risk-blacklist {
  background: #fff5f5;
  border-color: #ffc9c9;
  color: #c92a2a;
}
.risk-blacklist .risk-icon { color: #fa5252; }
.risk-blacklist .status-indicator { background: #ffe3e3; color: #e03131; }

.risk-frozen {
  background: #fff9db;
  border-color: #ffe066;
  color: #e67700;
}
.risk-frozen .risk-icon { color: #fcc419; }
.risk-frozen .status-indicator { background: #fff3bf; color: #d9480f; }

.risk-temp .status-indicator { background: #dfe8ff; color: #4d6bfe; }
.risk-qualified .status-indicator { background: #d3f9d8; color: #2b8a3e; }

/* Popover Content */
.supplier-popover-content {
  font-size: 13px;
  line-height: 1.6;
}
.popover-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #f1f3f5;
  margin-bottom: 8px;
}
.popover-title .name {
  font-weight: 600;
  font-size: 14px;
}
.popover-meta .meta-row {
  display: flex;
  margin-bottom: 4px;
  gap: 8px;
}
.popover-meta .meta-row .label {
  color: #868e96;
  width: 80px;
  flex-shrink: 0;
}
.popover-meta .meta-row .value {
  color: var(--ink);
}
.popover-meta .warning-text {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #ffa8a8;
  color: #e03131;
  font-weight: 500;
}
.grade-badge {
  font-weight: 700;
  padding: 0 4px;
  border-radius: 2px;
}
.grade-badge.A { background: #ebfbee; color: #2b8a3e; }
.grade-badge.B { background: #eef2ff; color: #4d6bfe; }
.grade-badge.C { background: #fff5f5; color: #e03131; }
</style>
