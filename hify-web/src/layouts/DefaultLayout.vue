<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">Hify</div>
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
import { useRoute } from 'vue-router'
import { ChatDotRound, Setting, Collection, Files, Connection, Tools } from '@element-plus/icons-vue'

const route = useRoute()
const isChat = computed(() => route.path.startsWith('/conversation'))

const menuItems = [
  { path: '/conversation', label: '对话',   icon: ChatDotRound },
  { path: '/agent',        label: 'Agent',  icon: Setting },
  { path: '/model',        label: '模型',   icon: Connection },
  { path: '/knowledge-bases', label: '知识库',  icon: Collection },
  { path: '/workflow',    label: '工作流',  icon: Files },
  { path: '/mcp',         label: 'MCP 工具', icon: Tools },
]
</script>

<style scoped>
.layout { height: 100vh; }
.aside  { background: #001529; display: flex; flex-direction: column; }
.logo   { color: #fff; font-size: 20px; font-weight: 700; padding: 20px 24px; }
.menu   { border-right: none; background: transparent; flex: 1; --el-menu-text-color: rgba(255,255,255,.65); --el-menu-active-color: #fff; --el-menu-hover-bg-color: rgba(255,255,255,.08); }
.main        { background: #f5f5f5; padding: 24px; overflow: auto; }
.main--chat  { padding: 0; background: var(--bg-surface); overflow: hidden; }
</style>
