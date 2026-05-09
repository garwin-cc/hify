<template>
  <div class="app-layout">

    <!-- ── 深色侧边栏 ─────────────────────────────────────────────────── -->
    <aside class="sidebar" :class="{ 'is-collapsed': isCollapsed }">

      <!-- Logo 区 -->
      <div class="sidebar-logo">
        <div class="sidebar-logo__mark">H</div>
        <Transition name="sidebar-fade">
          <div v-if="!isCollapsed" class="sidebar-logo__info">
            <span class="sidebar-logo__brand">Hify</span>
            <span class="sidebar-logo__tagline">AI Agent Platform</span>
          </div>
        </Transition>
      </div>

      <!-- 菜单区 -->
      <nav class="sidebar-nav">
        <template v-for="item in navItems" :key="item.path">
          <el-tooltip
            v-if="isCollapsed"
            :content="item.label"
            placement="right"
            :show-after="300"
          >
            <span class="sidebar-tooltip-anchor">
              <RouterLink
                :to="item.path"
                class="sidebar-item"
                :class="{ 'is-active': route.path.startsWith(item.path) }"
              >
                <el-icon class="sidebar-item__icon">
                  <component :is="item.icon" />
                </el-icon>
              </RouterLink>
            </span>
          </el-tooltip>

          <RouterLink
            v-else
            :to="item.path"
            class="sidebar-item"
            :class="{ 'is-active': route.path.startsWith(item.path) }"
          >
            <el-icon class="sidebar-item__icon">
              <component :is="item.icon" />
            </el-icon>
            <Transition name="sidebar-fade">
              <span class="sidebar-item__label">
                {{ item.label }}
              </span>
            </Transition>
          </RouterLink>
        </template>
      </nav>

      <!-- 底部：版本号 + 折叠按钮 -->
      <div class="sidebar-bottom">
        <Transition name="sidebar-fade">
          <span v-if="!isCollapsed" class="sidebar-version">v 0.1.0</span>
        </Transition>
        <button
          class="sidebar-toggle"
          :title="isCollapsed ? '展开侧边栏' : '折叠侧边栏'"
          @click="isCollapsed = !isCollapsed"
        >
          <el-icon>
            <ArrowLeft v-if="!isCollapsed" />
            <ArrowRight v-else />
          </el-icon>
        </button>
      </div>

    </aside>

    <!-- ── 浅色主内容区 ──────────────────────────────────────────────── -->
    <div class="main-wrapper">
      <TopBar />
      <main class="main-content">
        <RouterView />
      </main>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Setting,
  User,
  ChatDotRound,
  Cpu,
  Share,
  Tools,
  ArrowLeft,
  ArrowRight,
} from '@element-plus/icons-vue'
import TopBar from '@/components/layout/TopBar.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'

const route = useRoute()
const { isNarrow } = useBreakpoint()

// 用户手动偏好（宽屏下有效）
const userCollapsed = ref(false)

// 窄屏强制折叠；宽屏时跟随用户偏好
const isCollapsed = computed({
  get: () => isNarrow.value || userCollapsed.value,
  set: (v: boolean) => { userCollapsed.value = v },
})

const navItems = [
  { path: '/providers',    label: '模型管理',  icon: Setting     },
  { path: '/agents',       label: 'Agent 管理', icon: User        },
  { path: '/conversation', label: '对话',       icon: ChatDotRound},
  { path: '/knowledge',    label: '知识库',     icon: Cpu         },
  { path: '/workflows',    label: '工作流',     icon: Share       },
  { path: '/mcp',          label: 'MCP 工具',   icon: Tools       },
]
</script>

<style scoped>
/* ── 整体布局 ──────────────────────────────────────────────────────────── */
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--bg-app);
}

/* ══════════════════════════════════════════════════════════════════════════
   侧边栏
══════════════════════════════════════════════════════════════════════════ */
.sidebar {
  width: 220px;
  min-width: 220px;
  background-color: var(--color-bg-dark);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
  transition: width 280ms cubic-bezier(0.16, 1, 0.3, 1),
              min-width 280ms cubic-bezier(0.16, 1, 0.3, 1);
}
.sidebar.is-collapsed {
  width: 56px;
  min-width: 56px;
}

/* ── Logo 区 ──────────────────────────────────────────────────────────── */
.sidebar-logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
  overflow: hidden;
}

/* 品牌标志方块 */
.sidebar-logo__mark {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-cyan-500) 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 0 12px rgba(76, 110, 245, 0.45),
              0 0 24px rgba(6, 182, 212, 0.20);
}

/* 品牌文字区（展开时显示） */
.sidebar-logo__info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
  white-space: nowrap;
}

/* "Hify" — 主色渐变文字 */
.sidebar-logo__brand {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.06em;
  background: linear-gradient(135deg, #829AF8 0%, #22D3EE 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.2;
}

/* "AI Agent Platform" */
.sidebar-logo__tagline {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.30);
  letter-spacing: 0.04em;
  line-height: 1;
}

/* ── 菜单区 ───────────────────────────────────────────────────────────── */
.sidebar-nav {
  flex: 1;
  padding: 10px 8px;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.sidebar-nav::-webkit-scrollbar { width: 0; }

.sidebar-tooltip-anchor {
  display: block;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-md);
  color: rgba(255, 255, 255, 0.55);
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  position: relative;
  border-left: 3px solid transparent;   /* 占位，保证 active 时不跳动 */
  transition:
    background-color 120ms ease,
    color 120ms ease;
}
.sidebar-item:hover {
  background-color: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.85);
}

/* 选中态：左侧主色竖线 + 微亮背景 */
.sidebar-item.is-active {
  background-color: rgba(255, 255, 255, 0.09);
  color: #ffffff;
  border-left-color: var(--color-primary-400);
}

/* 折叠时 item 居中 */
.sidebar.is-collapsed .sidebar-item {
  justify-content: center;
  padding: 8px;
  border-left-color: transparent;
}
.sidebar.is-collapsed .sidebar-item.is-active {
  border-left-color: transparent;
  /* 折叠态改用顶部细线或背景区分 */
  background-color: rgba(76, 110, 245, 0.15);
}

/* 菜单图标 */
.sidebar-item__icon {
  font-size: 16px;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.40);
  transition: color 120ms ease;
}
.sidebar-item:hover .sidebar-item__icon { color: rgba(255, 255, 255, 0.80); }
.sidebar-item.is-active .sidebar-item__icon { color: var(--color-primary-400); }

/* 菜单文字 */
.sidebar-item__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── 底部区域 ─────────────────────────────────────────────────────────── */
.sidebar-bottom {
  padding: 10px 8px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  gap: 8px;
}
.sidebar.is-collapsed .sidebar-bottom {
  justify-content: center;
}

.sidebar-version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.20);
  font-family: var(--font-mono);
  white-space: nowrap;
  overflow: hidden;
}

/* 折叠 / 展开按钮 */
.sidebar-toggle {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.35);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition:
    background-color 150ms ease,
    color 150ms ease,
    border-color 150ms ease;
}
.sidebar-toggle:hover {
  background-color: rgba(255, 255, 255, 0.10);
  color: rgba(255, 255, 255, 0.75);
  border-color: rgba(255, 255, 255, 0.16);
}
.sidebar-toggle .el-icon { font-size: 12px; }

/* ── 折叠过渡动效（文字淡出） ─────────────────────────────────────────── */
.sidebar-fade-enter-active { transition: opacity 150ms ease 60ms; }
.sidebar-fade-leave-active { transition: opacity 80ms ease; }
.sidebar-fade-enter-from,
.sidebar-fade-leave-to    { opacity: 0; }

/* ══════════════════════════════════════════════════════════════════════════
   主内容区
══════════════════════════════════════════════════════════════════════════ */
.main-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  background-color: var(--color-bg-secondary);
}
</style>
