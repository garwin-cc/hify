<template>
  <div class="page-content">
    <PageHeader title="应用发布" description="把 Agent 发布成内部 Web App 或 API Endpoint，管理版本、访问地址和 API Key">
      <template #actions>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="primary" :disabled="!selectedAgentId" @click="publishDialogVisible = true">发布版本</el-button>
      </template>
    </PageHeader>

    <div class="publish-layout">
      <aside class="hify-card agent-list">
        <el-input v-model="agentKeyword" placeholder="搜索 Agent" clearable />
        <div class="agent-list__items">
          <button
            v-for="agent in filteredAgents"
            :key="agent.id"
            class="agent-item"
            :class="{ 'is-active': agent.id === selectedAgentId }"
            type="button"
            @click="selectAgent(agent.id)"
          >
            <span>{{ agent.name }}</span>
            <el-tag size="small" :type="agent.enabled === 1 ? 'success' : 'info'">
              {{ agent.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </button>
          <el-empty v-if="filteredAgents.length === 0" description="暂无 Agent" :image-size="80" />
        </div>
      </aside>

      <main class="publish-main">
        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">发布版本</span>
            <el-button size="small" :disabled="!selectedAgentId" @click="loadAgentDetail">同步当前配置</el-button>
          </div>
          <el-table :data="versions" size="small">
            <el-table-column prop="versionNo" label="版本" width="80">
              <template #default="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="versionTagType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="Agent" min-width="160" />
            <el-table-column prop="workflowId" label="Workflow" width="110">
              <template #default="{ row }">{{ row.workflowId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="160" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openAppDialog(row)">创建应用</el-button>
                <el-button link type="warning" @click="handleRollback(row.versionNo)">回滚</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && versions.length === 0" description="尚未发布版本" :image-size="80" />
        </section>

        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">应用入口</span>
            <el-button size="small" :disabled="!selectedVersionId" @click="openAppDialog()">新增应用</el-button>
          </div>
          <el-table :data="apps" size="small">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="endpointPath" label="访问地址" min-width="220">
              <template #default="{ row }">
                <span class="mono-text">{{ appUrl(row.endpointPath) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="能力" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.webEnabled === 1" size="small">Web</el-tag>
                <el-tag v-if="row.apiEnabled === 1" size="small" type="warning">API</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="selectApp(row)">API Key</el-button>
                <el-button link @click="copyExample(row)">示例</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && apps.length === 0" description="暂无应用入口" :image-size="80" />
        </section>

        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">API Key</span>
            <el-button size="small" :disabled="!selectedApp" @click="apiKeyDialogVisible = true">新增 Key</el-button>
          </div>
          <el-alert
            v-if="createdApiKey"
            type="warning"
            show-icon
            :closable="true"
            @close="createdApiKey = ''"
          >
            <template #title>
              新 Key 仅展示一次：<span class="mono-text">{{ createdApiKey }}</span>
            </template>
          </el-alert>
          <el-table :data="apiKeys" size="small">
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="keyPrefix" label="前缀" width="140" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="lastUsedAt" label="最近使用" width="180">
              <template #default="{ row }">{{ row.lastUsedAt || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="handleRevokeKey(row.id)">吊销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="selectedApp && apiKeys.length === 0" description="暂无 API Key" :image-size="80" />
          <el-empty v-if="!selectedApp" description="选择应用后查看 API Key" :image-size="80" />
        </section>
      </main>
    </div>

    <el-dialog v-model="publishDialogVisible" title="发布 Agent 版本" width="460px">
      <el-form label-width="90px">
        <el-form-item label="发布类型">
          <el-radio-group v-model="publishType">
            <el-radio-button label="TEST">测试版</el-radio-button>
            <el-radio-button label="PUBLISHED">正式版</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="publishDescription" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handlePublish">发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="appDialogVisible" title="创建应用入口" width="520px">
      <el-form :model="appForm" label-width="100px">
        <el-form-item label="版本" required>
          <el-select v-model="appForm.publishedVersionId" style="width: 100%">
            <el-option
              v-for="version in versions"
              :key="version.id"
              :label="`v${version.versionNo} · ${version.status}`"
              :value="version.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="appForm.name" />
        </el-form-item>
        <el-form-item label="Endpoint">
          <el-input v-model="appForm.endpointPath" placeholder="/internal/apps/customer-service" />
        </el-form-item>
        <el-form-item label="能力">
          <el-checkbox v-model="webEnabled">内部 Web App</el-checkbox>
          <el-checkbox v-model="apiEnabled">API Endpoint</el-checkbox>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="appForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateApp">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="apiKeyDialogVisible" title="新增 API Key" width="420px">
      <el-input v-model="apiKeyName" placeholder="例如：internal-bot-prod" />
      <template #footer>
        <el-button @click="apiKeyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateApiKey">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createAgentApiKey,
  createAgentApp,
  getAgentApiKeys,
  getAgentApps,
  getAgentDetail,
  getAgentList,
  getAgentVersions,
  publishAgentTestVersion,
  publishAgentVersion,
  revokeAgentApiKey,
  rollbackAgentVersion,
  type AgentApiKey,
  type AgentApp,
  type AgentListItem,
  type AgentVersion,
} from '@/api/agent'
import { notifySuccess } from '@/utils/notify'

const loading = ref(false)
const submitting = ref(false)
const agents = ref<AgentListItem[]>([])
const versions = ref<AgentVersion[]>([])
const apps = ref<AgentApp[]>([])
const apiKeys = ref<AgentApiKey[]>([])
const selectedAgentId = ref<number | null>(null)
const selectedApp = ref<AgentApp | null>(null)
const agentKeyword = ref('')

const publishDialogVisible = ref(false)
const publishType = ref<'TEST' | 'PUBLISHED'>('PUBLISHED')
const publishDescription = ref('')

const appDialogVisible = ref(false)
const appForm = reactive({
  publishedVersionId: 0,
  name: '',
  description: '',
  endpointPath: '',
})
const webEnabled = ref(true)
const apiEnabled = ref(true)

const apiKeyDialogVisible = ref(false)
const apiKeyName = ref('')
const createdApiKey = ref('')

const filteredAgents = computed(() => {
  const keyword = agentKeyword.value.trim().toLowerCase()
  if (!keyword) return agents.value
  return agents.value.filter(agent => agent.name.toLowerCase().includes(keyword))
})

const selectedVersionId = computed(() => versions.value[0]?.id ?? 0)

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const page = await getAgentList(1, 100)
    agents.value = page.records ?? []
    if (!selectedAgentId.value && agents.value.length > 0) {
      selectedAgentId.value = agents.value[0].id
    }
    await loadSelectedAgent()
  } finally {
    loading.value = false
  }
}

async function selectAgent(agentId: number) {
  selectedAgentId.value = agentId
  selectedApp.value = null
  apiKeys.value = []
  await loadSelectedAgent()
}

async function loadSelectedAgent() {
  if (!selectedAgentId.value) return
  const [versionRows, appRows] = await Promise.all([
    getAgentVersions(selectedAgentId.value),
    getAgentApps(selectedAgentId.value),
  ])
  versions.value = versionRows ?? []
  apps.value = appRows ?? []
}

async function loadAgentDetail() {
  if (!selectedAgentId.value) return
  await getAgentDetail(selectedAgentId.value)
  notifySuccess('已同步当前 Agent 配置')
}

function versionTagType(status: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'TEST') return 'warning'
  return 'info'
}

async function handlePublish() {
  if (!selectedAgentId.value) return
  submitting.value = true
  try {
    const req = { description: publishDescription.value.trim() || undefined }
    if (publishType.value === 'TEST') {
      await publishAgentTestVersion(selectedAgentId.value, req)
    } else {
      await publishAgentVersion(selectedAgentId.value, req)
    }
    publishDialogVisible.value = false
    publishDescription.value = ''
    await loadSelectedAgent()
    notifySuccess('版本已发布')
  } finally {
    submitting.value = false
  }
}

async function handleRollback(versionNo: number) {
  if (!selectedAgentId.value) return
  await ElMessageBox.confirm(`确定回滚到 v${versionNo}？当前草稿配置会被覆盖。`, '回滚版本', {
    type: 'warning',
    confirmButtonText: '回滚',
    cancelButtonText: '取消',
  })
  await rollbackAgentVersion(selectedAgentId.value, versionNo, 'frontend rollback')
  await loadSelectedAgent()
  notifySuccess('Agent 已回滚')
}

function openAppDialog(version?: AgentVersion) {
  appForm.publishedVersionId = version?.id ?? selectedVersionId.value
  appForm.name = version ? `${version.name} 内部应用` : ''
  appForm.description = ''
  appForm.endpointPath = ''
  webEnabled.value = true
  apiEnabled.value = true
  appDialogVisible.value = true
}

async function handleCreateApp() {
  if (!selectedAgentId.value || !appForm.publishedVersionId || !appForm.name.trim()) {
    ElMessage.warning('请选择版本并填写应用名称')
    return
  }
  submitting.value = true
  try {
    await createAgentApp(selectedAgentId.value, {
      publishedVersionId: appForm.publishedVersionId,
      name: appForm.name.trim(),
      description: appForm.description.trim() || undefined,
      endpointPath: appForm.endpointPath.trim() || undefined,
      webEnabled: webEnabled.value ? 1 : 0,
      apiEnabled: apiEnabled.value ? 1 : 0,
    })
    appDialogVisible.value = false
    await loadSelectedAgent()
    notifySuccess('应用入口已创建')
  } finally {
    submitting.value = false
  }
}

async function selectApp(app: AgentApp) {
  selectedApp.value = app
  createdApiKey.value = ''
  apiKeys.value = await getAgentApiKeys(app.id)
}

async function handleCreateApiKey() {
  if (!selectedApp.value || !apiKeyName.value.trim()) {
    ElMessage.warning('请输入 Key 名称')
    return
  }
  submitting.value = true
  try {
    const key = await createAgentApiKey(selectedApp.value.id, apiKeyName.value.trim())
    createdApiKey.value = key.apiKey
    apiKeyName.value = ''
    apiKeyDialogVisible.value = false
    apiKeys.value = await getAgentApiKeys(selectedApp.value.id)
    notifySuccess('API Key 已创建')
  } finally {
    submitting.value = false
  }
}

async function handleRevokeKey(keyId: number) {
  if (!selectedApp.value) return
  await revokeAgentApiKey(selectedApp.value.id, keyId)
  apiKeys.value = await getAgentApiKeys(selectedApp.value.id)
  notifySuccess('API Key 已吊销')
}

function appUrl(endpointPath: string) {
  return endpointPath?.startsWith('/') ? endpointPath : `/${endpointPath || '-'}`
}

function copyExample(app: AgentApp) {
  const sample = `curl -X POST ${appUrl(app.endpointPath)} \\
  -H "Authorization: Bearer <API_KEY>" \\
  -H "Content-Type: application/json" \\
  -d '{"input":"你好"}'`
  navigator.clipboard?.writeText(sample)
  ElMessage.success('调用示例已复制')
}
</script>

<style scoped>
.publish-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.agent-list {
  position: sticky;
  top: 16px;
}

.agent-list__items {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 12px;
}

.agent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  text-align: left;
}

.agent-item.is-active {
  background: var(--color-primary-50);
  border-color: var(--color-primary-200);
  color: var(--color-primary-700);
}

.publish-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.mono-text {
  font-family: var(--font-mono);
  word-break: break-all;
}

@media (max-width: 900px) {
  .publish-layout {
    grid-template-columns: 1fr;
  }
}
</style>
