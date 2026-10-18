<template>
  <div class="lifecycle-stepper-card">
    <div class="stepper-header" v-if="title">
      <span class="stepper-title">{{ title }}</span>
      <span class="stepper-tip" v-if="subtitle">{{ subtitle }}</span>
    </div>
    <div class="steps-track">
      <div
        v-for="(step, index) in steps"
        :key="step.key"
        class="step-node"
        :class="[getStepState(index), { 'is-current': index === activeIndex }]"
      >
        <div class="step-connector" v-if="index > 0" :class="{ 'is-active': index <= activeIndex }" />
        <div class="step-indicator">
          <el-icon v-if="index < activeIndex" class="check-icon"><Check /></el-icon>
          <span v-else class="step-num mono">{{ (index + 1).toString().padStart(2, '0') }}</span>
        </div>
        <div class="step-meta">
          <div class="step-label">{{ step.label }}</div>
          <div class="step-sub" v-if="step.desc">{{ step.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'

export interface StepItem {
  key: string
  label: string
  desc?: string
}

interface Props {
  title?: string
  subtitle?: string
  steps: StepItem[]
  currentKey: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '业务流转进程',
  subtitle: '端到端 S2P 协作链路'
})

const activeIndex = computed(() => {
  const idx = props.steps.findIndex(s => s.key.toLowerCase() === props.currentKey.toLowerCase())
  return idx !== -1 ? idx : 0
})

const getStepState = (index: number) => {
  if (index < activeIndex.value) return 'completed'
  if (index === activeIndex.value) return 'active'
  return 'pending'
}
</script>

<style scoped>
.lifecycle-stepper-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 12px 18px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}

.stepper-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px dashed var(--rule);
}
.stepper-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.02em;
}
.stepper-tip {
  font-size: 12px;
  color: var(--memo);
}

.steps-track {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  position: relative;
}

.step-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  flex: 1;
  text-align: center;
}

.step-connector {
  position: absolute;
  top: 14px;
  right: 50%;
  width: 100%;
  height: 2px;
  background: #e9ecef;
  z-index: 1;
  transition: background 0.3s;
}
.step-connector.is-active {
  background: #2b8a3e;
}

.step-indicator {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f8f9fa;
  border: 2px solid #ced4da;
  color: #868e96;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  position: relative;
  z-index: 2;
  transition: all 0.2s ease;
}

.step-node.completed .step-indicator {
  background: #ebfbee;
  border-color: #40c057;
  color: #2b8a3e;
}
.step-node.completed .check-icon {
  font-size: 14px;
  stroke-width: 2;
}

.step-node.active .step-indicator {
  background: #1a1d29;
  border-color: #1a1d29;
  color: #ffffff;
  box-shadow: 0 0 0 4px rgba(26, 29, 41, 0.12);
}

.step-meta {
  margin-top: 6px;
}
.step-label {
  font-size: 12.5px;
  font-weight: 600;
  color: #495057;
}
.step-node.active .step-label {
  color: var(--ink);
  font-weight: 700;
}
.step-sub {
  font-size: 11px;
  color: #868e96;
  margin-top: 2px;
}
</style>
