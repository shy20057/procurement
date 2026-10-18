<template>
  <div class="srm-shell">
    <!-- Topbar -->
    <header class="topbar">
      <div class="topbar-brand">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <rect x="2" y="2" width="7" height="7" rx="1" fill="var(--signal)"/>
          <rect x="11" y="2" width="7" height="7" rx="1" fill="var(--ink)"/>
          <rect x="2" y="11" width="7" height="7" rx="1" fill="var(--ink)"/>
          <rect x="11" y="11" width="7" height="7" rx="1" fill="var(--rule)"/>
        </svg>
        <span class="topbar-title">供应商管理系统</span>
      </div>
    </header>

    <div class="srm-body">
      <!-- Sidebar -->
      <aside class="sidebar">
        <nav class="snav">
          <div
            v-for="item in navItems"
            :key="item.path"
            class="snav-item"
            :class="{ active: isActive(item.path) }"
            @click="router.push(item.path)"
          >
            <span class="snav-code">{{ item.code }}</span>
            <span class="snav-label">{{ item.label }}</span>
          </div>
        </nav>
      </aside>

      <!-- Main content -->
      <main class="srm-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const navItems = [
  { code: 'D1', label: '邀请单', path: '/srm/invitations' },
  { code: 'D2', label: '注册单', path: '/srm/registrations' },
  { code: 'D3', label: '认证单', path: '/srm/certifications' },
  { code: 'D4', label: '档案', path: '/srm/archives' },
  { code: 'D5', label: '变更单', path: '/srm/change-requests' },
  { code: 'D6', label: '冻结/解冻', path: '/srm/freeze-requests' },
  { code: 'D7', label: '黑名单', path: '/srm/blacklists' },
  { code: 'D8', label: '绩效考核', path: '/srm/performance-evaluations' },
]

const isActive = (path: string) => route.path.startsWith(path)
</script>

<style scoped>
.srm-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

/* ── Topbar ── */
.topbar {
  height: 52px;
  background: var(--ink);
  color: var(--white);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 10;
}
.topbar-brand { display: flex; align-items: center; gap: 10px; }
.topbar-title {
  font-family: var(--serif);
  font-size: 16px;
  letter-spacing: 0.01em;
  color: var(--white);
}
.topbar-actions { display: flex; align-items: center; gap: 4px; }
.topbar-actions .el-button { color: rgba(255,255,255,0.7); }
.topbar-actions .el-button:hover { color: var(--white); }

/* ── Body ── */
.srm-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* ── Sidebar ── */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--white);
  border-right: 1px solid var(--rule);
  overflow-y: auto;
}
.snav { padding: 12px 0; }
.snav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.15s, border-color 0.15s;
  user-select: none;
}
.snav-item:hover { background: var(--paper); }
.snav-item.active {
  border-left-color: var(--signal);
  background: var(--signal-mute);
}
.snav-code {
  font-family: var(--mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--memo);
  min-width: 22px;
}
.snav-item.active .snav-code { color: var(--signal); }
.snav-label {
  font-size: 14px;
  color: var(--ink);
}
.snav-item.active .snav-label { font-weight: 600; }

/* ── Main ── */
.srm-main {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
  background: var(--paper);
}
</style>
