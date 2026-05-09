<template>
  <header class="topbar">
    <!-- 左：面包屑 -->
    <el-breadcrumb class="topbar-breadcrumb" separator="/">
      <el-breadcrumb-item :to="{ path: '/' }">Hify</el-breadcrumb-item>
      <el-breadcrumb-item v-if="currentCrumb">
        {{ currentCrumb }}
      </el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 右：用户信息 -->
    <div class="topbar-user">
      <span class="topbar-user__name">Admin</span>
      <div class="topbar-user__avatar">A</div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const crumbMap: Record<string, string> = {
  '/providers':    '模型管理',
  '/agents':       'Agent 管理',
  '/conversation': '对话',
  '/knowledge':    '知识库',
  '/workflows':    '工作流',
  '/mcp':          'MCP 工具',
}

const currentCrumb = computed(() => {
  const matched = Object.keys(crumbMap)
    .find(prefix => route.path === prefix || route.path.startsWith(prefix + '/'))
  return matched ? crumbMap[matched] : null
})
</script>

<style scoped>
.topbar {
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--page-padding);
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color-light);
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
}

/* 面包屑 */
.topbar-breadcrumb {
  font-size: var(--text-sm);
}
:deep(.el-breadcrumb__inner a),
:deep(.el-breadcrumb__inner.is-link) {
  color: var(--text-tertiary) !important;
  font-weight: var(--font-normal);
  transition: color var(--transition-fast);
}
:deep(.el-breadcrumb__inner a:hover) {
  color: var(--color-primary) !important;
}
:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--text-primary) !important;
  font-weight: var(--font-medium);
}
:deep(.el-breadcrumb__separator) {
  color: var(--text-disabled) !important;
}

/* 用户区 */
.topbar-user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-md);
  transition: background-color var(--transition-fast);
}
.topbar-user:hover {
  background-color: var(--bg-subtle);
}

.topbar-user__name {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: var(--font-medium);
}

.topbar-user__avatar {
  width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, var(--color-primary-500) 0%, var(--color-cyan-500) 100%);
  color: white;
  font-size: 12px;
  font-weight: var(--font-semibold);
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0;
}
</style>
