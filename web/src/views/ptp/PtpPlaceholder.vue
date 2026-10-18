<template>
  <div class="ptp-placeholder-page">
    <div class="header-card">
      <div class="eyebrow">Phase 04 · Purchase-to-Pay</div>
      <h2 class="title">{{ pageTitle }}</h2>
      <p class="desc">
        PTP 采购到付款功能尚未接入后端。以下仅展示规划中的业务流程。
      </p>
    </div>

    <!-- 3-Way Matching 三单匹配概念交互卡片 -->
    <div class="concept-card">
      <div class="concept-header">
        <span class="badge">核心特性预备</span>
        <span class="concept-title">3-Way Matching（订单 · 收货验收 · 税务发票）三单智能匹配中心</span>
      </div>
      <div class="concept-grid">
        <div class="flow-step">
          <div class="step-num">01</div>
          <div class="step-title">采购订单 (PO)</div>
          <div class="step-desc">承接 STC 价目表与 BPA 协议条款，锁定采购单价与交期</div>
        </div>
        <div class="flow-arrow">➔</div>
        <div class="flow-step">
          <div class="step-num">02</div>
          <div class="step-title">实物收货验收 (GR)</div>
          <div class="step-desc">按订单校验容差，合格入库触发 SAP 记账，不合格整改回流 SRM 绩效</div>
        </div>
        <div class="flow-arrow">➔</div>
        <div class="flow-step">
          <div class="step-num">03</div>
          <div class="step-title">发票签收 (BTS)</div>
          <div class="step-desc">对接 BTS 税务系统查验发票真伪，校验税率与进项抵扣额</div>
        </div>
        <div class="flow-arrow">➔</div>
        <div class="flow-step finish">
          <div class="step-num">04</div>
          <div class="step-title">智能对账与付款</div>
          <div class="step-desc">三单对齐平账，核销应付挂账，推送 TMS 资金排期支付</div>
        </div>
      </div>
    </div>

    <div class="status-box">
      <el-result
        icon="info"
        title="PTP 功能规划中"
        sub-title="当前页面没有采购执行或结算业务数据。"
      >
        <template #extra>
          <el-button type="primary" @click="router.push('/stc/awards')">
            查看 STC 定标与履约依据 →
          </el-button>
          <el-button @click="router.push('/workbench')">
            返回全局驾驶舱
          </el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const pageTitle = computed(() => {
  const path = route.path
  if (path.includes('requisition')) return '采购需求申请管理 (PR)'
  if (path.includes('order')) return '采购订单与协同中心 (PO)'
  if (path.includes('receipt')) return '采购收货与库存管理 (GR)'
  if (path.includes('inspection')) return '质量验收与不合格整改'
  if (path.includes('reconciliation')) return '财务对账中心 (3-Way Matching)'
  if (path.includes('invoice')) return '发票签收与税务查验工作台'
  if (path.includes('payment')) return '请款申请与资金支付 (TMS联动)'
  return '采购执行与结算中心'
})
</script>

<style scoped>
.ptp-placeholder-page {
  padding: 20px 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.header-card {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 20px 24px;
  margin-bottom: 20px;
}
.eyebrow {
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 600;
  color: var(--whale);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.title {
  margin: 4px 0 8px;
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
}
.desc {
  margin: 0;
  font-size: 13.5px;
  color: var(--memo);
  line-height: 1.5;
}

.concept-card {
  background: var(--grad-card-blue);
  border: 1px solid rgba(77, 107, 254, 0.16);
  color: var(--ink);
  border-radius: var(--radius-md);
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 8px 24px rgba(77, 107, 254, 0.1);
}
.concept-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(77, 107, 254, 0.14);
}
.badge {
  background: var(--grad-whale);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 700;
}
.concept-title {
  font-size: 16px;
  font-weight: 700;
}

.concept-grid {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.flow-step {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #cdd8ff;
  border-radius: var(--radius-sm);
  padding: 16px;
  flex: 1;
  text-align: center;
}
.flow-step.finish {
  background: var(--whale-light);
  border-color: var(--whale);
}
.step-num {
  font-family: var(--mono);
  font-size: 20px;
  font-weight: 800;
  color: var(--whale);
  margin-bottom: 6px;
}
.step-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 6px;
}
.step-desc {
  font-size: 12px;
  color: var(--memo);
  line-height: 1.4;
}
.flow-arrow {
  font-size: 20px;
  color: #8aa0d0;
  padding: 0 10px;
}

.status-box {
  background: var(--white);
  border: 1px solid var(--rule);
  border-radius: var(--radius-sm);
  padding: 30px;
}
</style>
