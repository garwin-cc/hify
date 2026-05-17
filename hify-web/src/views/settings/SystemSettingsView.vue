<template>
  <div class="page-content">
    <PageHeader title="系统设置" description="查看平台健康状态和关键配置入口">
      <template #actions>
        <el-button :loading="loading" @click="loadHealth">刷新健康检查</el-button>
      </template>
    </PageHeader>

    <div class="settings-grid">
      <section class="hify-card health-card">
        <div class="hify-card__header">
          <span class="hify-card__title">健康检查</span>
        </div>
        <div class="health-list">
          <div v-for="item in healthRows" :key="item.level" class="health-item">
            <span>{{ item.label }}</span>
            <el-tag size="small" :type="healthType(item.value)">{{ healthText(item.value) }}</el-tag>
          </div>
        </div>
        <el-input
          class="health-json"
          :model-value="JSON.stringify(health.deep, null, 2)"
          type="textarea"
          :rows="8"
          readonly
        />
      </section>

      <section class="hify-card">
        <div class="hify-card__header">
          <span class="hify-card__title">配置入口</span>
        </div>
        <div class="settings-links">
          <RouterLink v-for="item in settingLinks" :key="item.path" :to="item.path" class="settings-link">
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </RouterLink>
        </div>
      </section>
    </div>

    <section class="hify-card">
      <div class="hify-card__header">
        <span class="hify-card__title">初始化和后台 Job</span>
      </div>
      <el-table :data="initRows" size="small">
        <el-table-column prop="item" label="项目" width="180" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }"><el-tag size="small" type="info">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="240" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { getHealthStatus } from '@/api/system'

const loading = ref(false)
const health = reactive<Record<string, unknown>>({
  health: null,
  liveness: null,
  readiness: null,
  deep: null,
})

const healthRows = computed(() => [
  { level: 'health', label: '基础健康', value: health.health },
  { level: 'liveness', label: 'Liveness', value: health.liveness },
  { level: 'readiness', label: 'Readiness', value: health.readiness },
  { level: 'deep', label: 'Deep Health', value: health.deep },
])

const settingLinks = [
  { path: '/providers', title: '模型 Provider', description: '配置供应商、模型、健康检查和连接测试' },
  { path: '/users', title: '用户管理', description: '维护本地账号、角色和账号状态' },
  { path: '/audit', title: '身份源和权限', description: '配置 LDAP/OIDC/SAML，查看项目角色边界' },
  { path: '/analytics', title: '运营分析', description: '查看 Agent 使用、失败率、Token、RAG、Workflow 和 MCP 指标' },
  { path: '/logs', title: '日志中心', description: '查看对话、Workflow、MCP、RAG、LLM 调用日志' },
]

const initRows = [
  { item: '默认空间', status: '后端初始化', description: '由 hify-app Flyway 和初始化配置负责创建。' },
  { item: '默认管理员', status: '后端初始化', description: 'InitAdminRunner 负责本地管理员账号初始化。' },
  { item: 'Provider 健康检查', status: '后台 Job', description: '定时探测关键模型 Provider，Deep Health 会聚合状态。' },
  { item: '知识库任务恢复', status: '后台 Job', description: '服务启动后恢复 PENDING/PROCESSING 文档任务。' },
  { item: '日志清理', status: '后台 Job', description: '按保留策略清理过期运行日志、Session 和审计数据。' },
]

onMounted(loadHealth)

async function loadHealth() {
  loading.value = true
  try {
    const [base, liveness, readiness, deep] = await Promise.all([
      getHealthStatus('health').catch(errorValue),
      getHealthStatus('liveness').catch(errorValue),
      getHealthStatus('readiness').catch(errorValue),
      getHealthStatus('deep').catch(errorValue),
    ])
    health.health = base
    health.liveness = liveness
    health.readiness = readiness
    health.deep = deep
  } finally {
    loading.value = false
  }
}

function errorValue(error: unknown) {
  return { status: 'ERROR', error: error instanceof Error ? error.message : '请求失败' }
}

function healthText(value: unknown) {
  if (typeof value === 'string') return value
  if (value && typeof value === 'object' && 'status' in value) {
    return String((value as Record<string, unknown>).status)
  }
  return value ? 'OK' : 'UNKNOWN'
}

function healthType(value: unknown) {
  const text = healthText(value)
  if (text === 'UP' || text === 'OK') return 'success'
  if (text === 'ERROR' || text === 'DOWN') return 'danger'
  return 'info'
}
</script>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  margin-bottom: 16px;
}

.health-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.health-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
}

.health-json :deep(.el-textarea__inner) {
  font-family: var(--font-mono);
}

.settings-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.settings-link {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  color: var(--text-primary);
}

.settings-link:hover {
  border-color: var(--color-primary-300);
  background: var(--color-primary-50);
}

.settings-link span {
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

@media (max-width: 900px) {
  .settings-grid,
  .health-list {
    grid-template-columns: 1fr;
  }
}
</style>
