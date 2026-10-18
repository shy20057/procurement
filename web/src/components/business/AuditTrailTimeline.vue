<template>
  <el-drawer
    v-model="visible"
    title="审批留痕与操作日志"
    size="420px"
    destroy-on-close
  >
    <div class="audit-container">
      <el-empty v-if="logList.length === 0" description="暂无真实操作日志" />
      <el-timeline v-else>
        <el-timeline-item
          v-for="(item, index) in logList"
          :key="index"
          :timestamp="item.timestamp"
          :type="getTimelineType(item.action)"
          placement="top"
        >
          <div class="audit-card">
            <div class="audit-header">
              <span class="audit-action">{{ item.action }}</span>
              <span class="audit-operator">{{ item.operator }}</span>
            </div>
            <div class="audit-comment" v-if="item.comment">
              <span class="quote">“</span>{{ item.comment }}<span class="quote">”</span>
            </div>
            <div class="audit-detail" v-if="item.details">
              {{ item.details }}
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'

export interface AuditLogItem {
  timestamp: string
  action: string
  operator: string
  comment?: string
  details?: string
}

const visible = ref(false)
const logList = ref<AuditLogItem[]>([])

const open = (logs: AuditLogItem[]) => {
  logList.value = logs ?? []
  visible.value = true
}

const getTimelineType = (action: string) => {
  if (action.includes('通过') || action.includes('确认') || action.includes('生效')) return 'success'
  if (action.includes('驳回') || action.includes('终止') || action.includes('作废')) return 'danger'
  if (action.includes('提交') || action.includes('审核中')) return 'primary'
  if (action.includes('反审核') || action.includes('修改')) return 'warning'
  return 'info'
}

defineExpose({
  open
})
</script>

<style scoped>
.audit-container {
  padding: 10px 4px;
}

.audit-card {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: var(--radius-sm);
  padding: 8px 12px;
  font-size: 13px;
}

.audit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.audit-action {
  font-weight: 600;
  color: var(--ink);
}

.audit-operator {
  color: var(--memo);
  font-size: 12px;
}

.audit-comment {
  color: #495057;
  font-style: italic;
  background: #fff;
  padding: 4px 8px;
  border-radius: 2px;
  border-left: 2px solid #adb5bd;
  margin-top: 4px;
  line-height: 1.4;
}

.audit-comment .quote {
  color: #ced4da;
  font-weight: bold;
}

.audit-detail {
  font-size: 11px;
  color: #868e96;
  margin-top: 4px;
}
</style>
