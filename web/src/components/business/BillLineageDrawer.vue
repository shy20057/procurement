<template>
  <el-drawer
    v-model="visible"
    title="单据血缘与全链路流转追溯"
    size="460px"
    destroy-on-close
  >
    <div class="lineage-container">
      <div class="lineage-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>展示当前单据的 BOTP 上游来源单据与下游衍生单据，点击编号可穿透跳转。</span>
      </div>

      <!-- 上游来源 -->
      <div class="lineage-section">
        <div class="section-title">
          <span class="badge upstream">上游来源</span>
          <span>前置单据 (Source Documents)</span>
        </div>
        <div v-if="upstreamDocs && upstreamDocs.length > 0" class="docs-list">
          <div
            v-for="doc in upstreamDocs"
            :key="doc.billNo"
            class="doc-node-card"
            @click="navigateTo(doc)"
          >
            <div class="node-header">
              <span class="type-name">{{ doc.docType }}</span>
              <BillStatusBadge v-if="doc.status" :status="doc.status" size="small" />
            </div>
            <div class="node-body">
              <span class="bill-no mono">{{ doc.billNo }}</span>
              <span class="node-title">{{ doc.title || '-' }}</span>
            </div>
            <div class="node-footer" v-if="doc.date">
              <span class="date">{{ doc.date }}</span>
              <span class="jump-action">穿透查看 →</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint">无前置来源单据（此为源头单据）</div>
      </div>

      <!-- 当前单据 -->
      <div class="lineage-divider">
        <div class="flow-arrow">▼ 下推衍生</div>
      </div>

      <div class="lineage-section current">
        <div class="section-title">
          <span class="badge current">当前单据</span>
          <span class="type-name">{{ currentDoc.docType }}</span>
        </div>
        <div class="doc-node-card is-current">
          <div class="node-header">
            <span class="bill-no mono highlight">{{ currentDoc.billNo }}</span>
            <BillStatusBadge v-if="currentDoc.status" :status="currentDoc.status" size="small" />
          </div>
          <div class="node-title current-title">{{ currentDoc.title || '-' }}</div>
        </div>
      </div>

      <div class="lineage-divider">
        <div class="flow-arrow">▼ 下推生成</div>
      </div>

      <!-- 下游衍生 -->
      <div class="lineage-section">
        <div class="section-title">
          <span class="badge downstream">下游衍生</span>
          <span>目标单据 (Downstream Documents)</span>
        </div>
        <div v-if="downstreamDocs && downstreamDocs.length > 0" class="docs-list">
          <div
            v-for="doc in downstreamDocs"
            :key="doc.billNo"
            class="doc-node-card"
            @click="navigateTo(doc)"
          >
            <div class="node-header">
              <span class="type-name">{{ doc.docType }}</span>
              <BillStatusBadge v-if="doc.status" :status="doc.status" size="small" />
            </div>
            <div class="node-body">
              <span class="bill-no mono">{{ doc.billNo }}</span>
              <span class="node-title">{{ doc.title || '-' }}</span>
            </div>
            <div class="node-footer" v-if="doc.date">
              <span class="date">{{ doc.date }}</span>
              <span class="jump-action">穿透查看 →</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint">暂无下游衍生单据（待下推生成）</div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import BillStatusBadge from './BillStatusBadge.vue'

export interface LineageDocNode {
  billNo: string
  docType: string
  title?: string
  status?: string
  date?: string
  path?: string
}

const router = useRouter()
const visible = ref(false)

const currentDoc = ref<LineageDocNode>({
  billNo: '',
  docType: '',
  status: undefined
})

const upstreamDocs = ref<LineageDocNode[]>([])
const downstreamDocs = ref<LineageDocNode[]>([])

const open = (current: LineageDocNode, up: LineageDocNode[] = [], down: LineageDocNode[] = []) => {
  currentDoc.value = current
  upstreamDocs.value = up
  downstreamDocs.value = down
  visible.value = true
}

const navigateTo = (doc: LineageDocNode) => {
  if (doc.path) {
    visible.value = false
    router.push(doc.path)
  }
}

defineExpose({
  open
})
</script>

<style scoped>
.lineage-container {
  padding: 0 4px;
}

.lineage-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f1f3f5;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--memo);
  margin-bottom: 18px;
}

.lineage-section {
  background: #fafafa;
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 12px 14px;
}

.lineage-section.current {
  background: #f8f9fa;
  border-color: #ced4da;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
}

.badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 2px;
  font-weight: 700;
}
.badge.upstream { background: #eef2ff; color: #4d6bfe; }
.badge.current { background: #1a1d29; color: #fff; }
.badge.downstream { background: #ebfbee; color: #2b8a3e; }

.docs-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.doc-node-card {
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.doc-node-card:hover {
  border-color: #4d6bfe;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}

.doc-node-card.is-current {
  cursor: default;
  border-left: 3px solid #1a1d29;
  box-shadow: none;
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.type-name {
  font-size: 12px;
  font-weight: 600;
  color: #495057;
}

.node-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.bill-no {
  font-size: 13px;
  color: #4d6bfe;
  font-weight: 500;
}
.bill-no.highlight {
  color: var(--ink);
  font-size: 14px;
  font-weight: 700;
}

.node-title {
  font-size: 12px;
  color: #868e96;
}
.current-title {
  font-size: 13px;
  color: var(--ink);
  margin-top: 4px;
}

.node-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #f1f3f5;
  font-size: 11px;
  color: #adb5bd;
}
.jump-action {
  color: #4d6bfe;
  font-weight: 500;
}

.empty-hint {
  font-size: 12px;
  color: #adb5bd;
  text-align: center;
  padding: 14px 0;
}

.lineage-divider {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}
.flow-arrow {
  font-size: 11px;
  color: #868e96;
  font-family: var(--mono);
}
</style>
