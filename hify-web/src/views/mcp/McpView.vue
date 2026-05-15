<template>
  <div class="page-content">
    <PageHeader
      title="MCP 工具"
      description="接入外部 MCP Server，测试连通性后同步工具列表，供 Agent 绑定调用"
    >
      <template v-if="auth.isAdmin" #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          新增 MCP Server
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card search-bar">
      <el-input
        v-model="filterName"
        placeholder="搜索 Server 名称"
        clearable
        style="width: 280px"
        @keyup.enter="tableRef?.refresh()"
        @change="tableRef?.refresh()"
        @clear="tableRef?.refresh()"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :row-style="{ height: '56px' }"
        empty-text="暂无 MCP Server，点击「新增 MCP Server」开始接入工具"
      >
        <template #name="{ row }">
          <el-button link type="primary" class="name-link" @click="handleViewTools(row)">
            {{ row.name }}
          </el-button>
        </template>

        <template #description="{ row }">
          <span v-if="row.description" class="description-text">{{ row.description }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #endpoint="{ row }">
          <span class="endpoint-text">{{ row.endpoint }}</span>
        </template>

        <template #toolCount="{ row }">
          <el-button
            v-if="(row.toolCount ?? 0) > 0"
            link
            type="primary"
            size="small"
            @click="handleViewTools(row)"
          >
            {{ row.toolCount }} 个
          </el-button>
          <span v-else class="no-data">–</span>
        </template>

        <template #enabled="{ row }">
          <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <template #actions="{ row }">
          <template v-if="auth.isAdmin">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              size="small"
              :loading="testingIds.has(row.id)"
              @click="handleTest(row)"
            >
              测试
            </el-button>
            <el-button size="small" type="danger" text @click="handleDelete(row)">删除</el-button>
          </template>
          <el-tag v-else size="small" type="info">只读</el-tag>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增 MCP Server' : '编辑 MCP Server'"
      width="560px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        label-position="right"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入 MCP Server 名称" maxlength="100" show-word-limit />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="描述工具用途（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="Endpoint" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="如：http://127.0.0.1:3001/sse" />
        </el-form-item>

        <el-form-item label="状态">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="toolsDrawerVisible"
      :title="selectedServer ? `${selectedServer.name} 的工具列表` : '工具列表'"
      size="520px"
    >
      <div v-if="loadingTools" class="tools-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在加载工具列表
      </div>

      <el-empty
        v-else-if="selectedTools.length === 0"
        description="暂无工具，请先点击测试同步 tools/list"
      />

      <div v-else class="tool-list">
        <div v-for="tool in selectedTools" :key="tool.id" class="tool-card">
          <div class="tool-card__header">
            <span class="tool-card__name">{{ tool.name }}</span>
            <el-tag size="small" type="info">MCP Tool</el-tag>
          </div>
          <div class="tool-card__description">
            {{ tool.description || '暂无描述' }}
          </div>
          <el-collapse>
            <el-collapse-item title="Input Schema" :name="tool.id">
              <pre class="schema-pre">{{ formatSchema(tool.inputSchema) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Loading, Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useAuthStore } from '@/stores/auth'
import { notifySuccess } from '@/utils/notify'
import {
  createMcpServer,
  deleteMcpServer,
  getMcpServerDetail,
  getMcpServerPage,
  testMcpServer,
  updateMcpServer,
  type McpServer,
  type McpTool,
  type SaveMcpServerRequest,
} from '@/api/mcp'

const tableRef = ref<{ refresh: () => void }>()
const formRef = ref<FormInstance>()
const { confirm } = useConfirm()
const auth = useAuthStore()

const filterName = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const testingIds = ref<Set<number>>(new Set())
const toolsDrawerVisible = ref(false)
const loadingTools = ref(false)
const selectedServer = ref<McpServer | null>(null)
const selectedTools = ref<McpTool[]>([])

const form = reactive<SaveMcpServerRequest>({
  name: '',
  description: '',
  endpoint: '',
  enabled: 1,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入 MCP Server 名称', trigger: 'blur' }],
  endpoint: [{ required: true, message: '请输入 Endpoint', trigger: 'blur' }],
}

const columns: HifyColumn[] = [
  { label: '名称', slot: 'name', minWidth: 180 },
  { label: '描述', slot: 'description', minWidth: 220 },
  { label: 'Endpoint', slot: 'endpoint', minWidth: 280 },
  { label: '工具数', slot: 'toolCount', width: 110, align: 'center' },
  { label: '状态', slot: 'enabled', width: 100 },
  { label: '创建时间', slot: 'createdAt', width: 140 },
  { label: '操作', slot: 'actions', width: 210, align: 'right', fixed: 'right' },
]

function fetchList(page: number, pageSize: number) {
  return getMcpServerPage(page, pageSize, {
    name: filterName.value.trim() || undefined,
  })
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.endpoint = ''
  form.enabled = 1
}

function handleCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: McpServer) {
  editingId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.endpoint = row.endpoint
  form.enabled = row.enabled ?? 1
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    form.endpoint = form.endpoint.replace(/\s+/g, '')
    if (editingId.value === null) {
      await createMcpServer(form)
      notifySuccess('MCP Server 创建成功')
    } else {
      await updateMcpServer(editingId.value, form)
      notifySuccess('MCP Server 更新成功')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}

function handleDialogClosed() {
  formRef.value?.clearValidate()
  resetForm()
  editingId.value = null
}

async function handleDelete(row: McpServer) {
  const deleted = await confirm(
    `确定删除 MCP Server「${row.name}」？如果已有 Agent 绑定其工具，后端会拒绝删除。`,
    () => deleteMcpServer(row.id),
    { successMsg: 'MCP Server 已删除' },
  )
  if (deleted) {
    tableRef.value?.refresh()
  }
}

async function handleTest(row: McpServer) {
  testingIds.value = new Set(testingIds.value).add(row.id)
  try {
    const result = await testMcpServer(row.id)
    if (result.success) {
      notifySuccess(`连通性测试成功，已同步 ${result.tools?.length ?? 0} 个工具`)
      tableRef.value?.refresh()
      if (toolsDrawerVisible.value && selectedServer.value?.id === row.id) {
        await loadTools(row)
      }
      return
    }
    ElMessage.error(result.message || 'MCP Server 连通性测试失败')
  } finally {
    const next = new Set(testingIds.value)
    next.delete(row.id)
    testingIds.value = next
  }
}

async function handleViewTools(row: McpServer) {
  selectedServer.value = row
  toolsDrawerVisible.value = true
  await loadTools(row)
}

async function loadTools(row: McpServer) {
  loadingTools.value = true
  try {
    const detail = await getMcpServerDetail(row.id)
    selectedServer.value = detail.server
    selectedTools.value = detail.tools || []
  } finally {
    loadingTools.value = false
  }
}

function formatSchema(schema: Record<string, unknown>) {
  return JSON.stringify(schema || {}, null, 2)
}
</script>

<style scoped>
.search-bar {
  padding: var(--space-4);
}

.name-link {
  padding: 0;
  font-weight: var(--font-medium);
}

.description-text,
.endpoint-text {
  color: var(--text-secondary);
}

.endpoint-text {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.no-data {
  color: var(--text-disabled);
}

.date-text {
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.tools-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 160px;
  color: var(--text-secondary);
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.tool-card {
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  background: var(--bg-surface);
}

.tool-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.tool-card__name {
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.tool-card__description {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text-secondary);
  line-height: 1.6;
}

.schema-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-secondary);
}
</style>
