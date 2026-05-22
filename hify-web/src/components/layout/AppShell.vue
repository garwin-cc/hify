<template>
  <el-config-provider :locale="elementLocale">
    <div class="app-layout">
      <aside class="sidebar" :class="{ 'is-collapsed': isCollapsed }">
        <div class="sidebar-logo">
          <div class="sidebar-logo__mark">H</div>
          <Transition name="sidebar-fade">
            <div v-if="!isCollapsed" class="sidebar-logo__info">
              <span class="sidebar-logo__brand">Hify</span>
              <span class="sidebar-logo__tagline">AI Agent Platform</span>
            </div>
          </Transition>
        </div>

        <nav class="sidebar-nav">
          <template v-for="group in navGroups" :key="group.label">
            <div v-if="!isCollapsed" class="sidebar-group">{{ group.label }}</div>
            <template v-for="item in group.items" :key="item.path">
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
          </template>
        </nav>

        <div class="sidebar-bottom">
          <Transition name="sidebar-fade">
            <span v-if="!isCollapsed" class="sidebar-version">v 0.1.0</span>
          </Transition>
          <button
            class="sidebar-toggle"
            :title="isCollapsed ? t('nav.expandSidebar') : t('nav.collapseSidebar')"
            @click="isCollapsed = !isCollapsed"
          >
            <el-icon>
              <ArrowLeft v-if="!isCollapsed" />
              <ArrowRight v-else />
            </el-icon>
          </button>
        </div>
      </aside>

      <div class="main-wrapper">
        <TopBar />
        <main class="main-content">
          <RouterView />
        </main>
      </div>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import {
  ArrowLeft,
  ArrowRight,
  ChatDotRound,
  Cpu,
  DataLine,
  Grid,
  Lock,
  Medal,
  Setting,
  Share,
  Tickets,
  Tools,
  TrendCharts,
  User,
} from '@element-plus/icons-vue'
import TopBar from '@/components/layout/TopBar.vue'
import { useAuthStore } from '@/stores/auth'
import { useBreakpoint } from '@/composables/useBreakpoint'

const route = useRoute()
const auth = useAuthStore()
const { t, locale } = useI18n()
const { isNarrow } = useBreakpoint()

const elementLocale = computed(() => locale.value === 'en-US' ? en : zhCn)

const userCollapsed = ref(false)

const isCollapsed = computed({
  get: () => isNarrow.value || userCollapsed.value,
  set: (value: boolean) => { userCollapsed.value = value },
})

interface NavItem {
  path: string
  label: string
  icon: unknown
}

const navGroups = computed(() => {
  const groups: Array<{ label: string; items: NavItem[] }> = [
    {
      label: t('nav.run'),
      items: [
        { path: '/conversation', label: t('nav.conversation'), icon: ChatDotRound },
        { path: '/apps', label: t('nav.apps'), icon: Grid },
      ],
    },
    {
      label: t('nav.orchestration'),
      items: [
        { path: '/agents', label: t('nav.agents'), icon: User },
        { path: '/workflows', label: t('nav.workflows'), icon: Share },
        { path: '/knowledge', label: t('nav.knowledge'), icon: Cpu },
        auth.isAdmin ? { path: '/mcp', label: t('nav.tools'), icon: Tools } : null,
      ].filter(Boolean) as NavItem[],
    },
    {
      label: t('nav.governance'),
      items: [
        auth.isAdmin ? { path: '/projects', label: t('nav.projects'), icon: User } : null,
        auth.isAdmin ? { path: '/analytics', label: t('nav.analytics'), icon: TrendCharts } : null,
        auth.isAdmin ? { path: '/quality', label: t('nav.quality'), icon: Medal } : null,
        auth.isAdmin ? { path: '/logs', label: t('nav.logs'), icon: Tickets } : null,
        auth.isAdmin ? { path: '/audit', label: t('nav.audit'), icon: Lock } : null,
        auth.isAdmin ? { path: '/settings', label: t('nav.settings'), icon: Setting } : null,
        auth.isAdmin ? { path: '/providers', label: t('nav.providers'), icon: DataLine } : null,
        auth.isAdmin ? { path: '/users', label: t('nav.users'), icon: User } : null,
      ].filter(Boolean) as NavItem[],
    },
  ]
  return groups.filter(group => group.items.length > 0)
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background-color: var(--bg-app);
}

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

.sidebar-logo__info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow: hidden;
  white-space: nowrap;
}

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

.sidebar-logo__tagline {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.30);
  letter-spacing: 0.04em;
  line-height: 1;
}

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

.sidebar-group {
  padding: 10px 10px 4px;
  color: rgba(255, 255, 255, 0.28);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

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
  border-left: 3px solid transparent;
  transition:
    background-color 120ms ease,
    color 120ms ease;
}

.sidebar-item:hover {
  background-color: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.85);
}

.sidebar-item.is-active {
  background-color: rgba(255, 255, 255, 0.09);
  color: #ffffff;
  border-left-color: var(--color-primary-400);
}

.sidebar.is-collapsed .sidebar-item {
  justify-content: center;
  padding: 8px;
  border-left-color: transparent;
}

.sidebar.is-collapsed .sidebar-item.is-active {
  border-left-color: transparent;
  background-color: rgba(76, 110, 245, 0.15);
}

.sidebar-item__icon {
  font-size: 16px;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.40);
  transition: color 120ms ease;
}

.sidebar-item:hover .sidebar-item__icon { color: rgba(255, 255, 255, 0.80); }
.sidebar-item.is-active .sidebar-item__icon { color: var(--color-primary-400); }

.sidebar-item__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}

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

.sidebar-fade-enter-active { transition: opacity 150ms ease 60ms; }
.sidebar-fade-leave-active { transition: opacity 80ms ease; }
.sidebar-fade-enter-from,
.sidebar-fade-leave-to { opacity: 0; }

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
