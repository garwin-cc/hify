<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">Hify</div>
      <div class="project-switcher">
        <el-select
          v-model="projectStore.currentProjectId"
          size="small"
          placeholder="选择项目"
          :loading="projectStore.loading"
          @change="handleProjectChange"
        >
          <el-option
            v-for="project in projectStore.projects"
            :key="project.id"
            :label="project.name"
            :value="project.id"
          />
        </el-select>
      </div>
      <el-menu :router="true" :default-active="route.path" class="menu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-main :class="['main', { 'main--chat': isChat }]">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Setting, Collection, Files, Connection, Tools, User } from '@element-plus/icons-vue'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const isChat = computed(() => route.path.startsWith('/conversation'))

onMounted(() => {
  projectStore.loadProjects().catch(() => {})
})

function handleProjectChange(projectId: number) {
  projectStore.setCurrentProject(projectId)
  router.go(0)
}

const menuItems = [
  { path: '/conversation', label: '对话',   icon: ChatDotRound },
  { path: '/agents',       label: 'Agent',  icon: Setting },
  { path: '/providers',    label: '模型',   icon: Connection },
  { path: '/knowledge-bases', label: '知识库',  icon: Collection },
  { path: '/workflows',    label: '工作流',  icon: Files },
  { path: '/mcp',         label: 'MCP 工具', icon: Tools },
  { path: '/projects',    label: '项目成员', icon: User },
]
</script>

<style scoped>
.layout { height: 100vh; }
.aside  { background: #001529; display: flex; flex-direction: column; }
.logo   { color: #fff; font-size: 20px; font-weight: 700; padding: 20px 24px; }
.project-switcher { padding: 0 12px 12px; }
.project-switcher :deep(.el-select) { width: 100%; }
.menu   { border-right: none; background: transparent; flex: 1; --el-menu-text-color: rgba(255,255,255,.65); --el-menu-active-color: #fff; --el-menu-hover-bg-color: rgba(255,255,255,.08); }
.main        { background: #f5f5f5; padding: 24px; overflow: auto; }
.main--chat  { padding: 0; background: var(--bg-surface); overflow: hidden; }
</style>
