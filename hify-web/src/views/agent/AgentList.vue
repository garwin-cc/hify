<template>
  <div class="page-content">
    <PageHeader
      title="Agent 管理"
      description="配置 AI Agent 的名称、系统提示词、绑定模型和工具"
    >
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          新建 Agent
        </el-button>
      </template>
    </PageHeader>

    <!-- 搜索栏 -->
    <div class="hify-card search-bar">
      <el-input
        v-model="filterName"
        placeholder="搜索 Agent 名称"
        clearable
        style="width: 240px"
        @change="tableRef?.load()"
        @clear="tableRef?.load()"
      />
      <el-select
        v-model="filterEnabled"
        placeholder="全部状态"
        clearable
        style="width: 120px"
        @change="tableRef?.load()"
      >
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
    </div>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :row-style="{ height: '56px' }"
        empty-text="暂无 Agent，点击「新建 Agent」开始配置"
      >
        <!-- 关联模型 -->
        <template #model="{ row }">
          <span v-if="row.modelName" class="model-name">{{ row.modelName }}</span>
          <span v-if="row.modelId" class="model-id">{{ row.modelId }}</span>
          <span v-if="!row.modelName" class="no-data">–</span>
        </template>

        <!-- 工具数量 -->
        <template #toolCount="{ row }">
          <el-tag v-if="row.toolCount > 0" size="small" type="info" round>
            {{ row.toolCount }}
          </el-tag>
          <span v-else class="no-data">–</span>
        </template>

        <!-- Temperature -->
        <template #temperature="{ row }">
          <span v-if="row.temperature != null" class="mono-text">{{ row.temperature }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #memory="{ row }">
          <el-tag :type="row.memoryEnabled === 1 ? 'success' : 'info'" size="small">
            {{ row.memoryEnabled === 1 ? '开启' : '关闭' }}
          </el-tag>
        </template>

        <!-- 状态 -->
        <template #enabled="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <!-- 创建时间 -->
        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <!-- 操作 -->
        <template #actions="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button
            size="small"
            :type="row.enabled === 1 ? 'warning' : 'success'"
            text
            :loading="togglingIds.has(row.id)"
            @click="handleToggle(row, row.enabled !== 1)"
          >{{ row.enabled === 1 ? '禁用' : '启用' }}</el-button>
          <el-button size="small" type="danger" text @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建 Agent' : '编辑 Agent'"
      width="700px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="150px"
        label-position="right"
        class="agent-form"
      >
        <el-tabs v-model="activeTab" class="agent-tabs">

          <!-- 基本配置 -->
          <el-tab-pane label="基本配置" name="basic">
            <el-form-item label="名称" prop="name">
              <el-input
                v-model="form.name"
                placeholder="Agent 名称"
                maxlength="100"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                placeholder="简短描述 Agent 的用途（可选）"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="绑定模型" prop="modelConfigId">
              <el-select
                v-model="form.modelConfigId"
                placeholder="请选择模型"
                style="width: 100%"
                :loading="loadingModels"
              >
                <el-option-group
                  v-for="group in modelGroups"
                  :key="group.providerId"
                  :label="group.providerName"
                >
                  <el-option
                    v-for="m in group.models"
                    :key="m.id"
                    :label="`${m.name}  (${m.modelId})`"
                    :value="m.id"
                  />
                </el-option-group>
              </el-select>
            </el-form-item>

            <el-form-item label="绑定知识库">
              <el-select
                v-model="form.knowledgeBaseIds"
                placeholder="请选择知识库（可选）"
                style="width: 100%"
                multiple
                clearable
                collapse-tags
                collapse-tags-tooltip
                :loading="loadingKnowledgeBases"
              >
                <el-option
                  v-for="kb in knowledgeBases"
                  :key="kb.id"
                  :label="kb.name"
                  :value="kb.id"
                  :disabled="kb.enabled !== 1"
                >
                  <div class="knowledge-option">
                    <span>{{ kb.name }}</span>
                    <span class="knowledge-option__meta">{{ kb.chunkCount || 0 }} 分块</span>
                  </div>
                </el-option>
              </el-select>
              <span class="form-hint-block">绑定后，对话会先检索知识库分块并注入上下文。</span>
            </el-form-item>

            <el-form-item label="绑定工作流">
              <el-select
                v-model="form.workflowId"
                placeholder="请选择工作流（可选）"
                style="width: 100%"
                clearable
                :loading="loadingWorkflows"
              >
                <el-option
                  v-for="workflow in workflows"
                  :key="workflow.id"
                  :label="workflow.name"
                  :value="workflow.id"
                  :disabled="workflow.enabled !== 1"
                >
                  <div class="workflow-option">
                    <span>{{ workflow.name }}</span>
                    <el-tag size="small" :type="workflow.enabled === 1 ? 'success' : 'info'">
                      {{ workflow.enabled === 1 ? 'PUBLISHED' : 'DRAFT' }}
                    </el-tag>
                  </div>
                </el-option>
              </el-select>
              <span class="form-hint-block">绑定后，对话会触发工作流执行；留空则直接按 Agent Prompt 调用模型。</span>
            </el-form-item>

            <el-form-item label="系统提示词" prop="systemPrompt">
              <el-input
                v-model="form.systemPrompt"
                type="textarea"
                :rows="6"
                placeholder="你是一个有帮助的助手..."
              />
            </el-form-item>

            <el-form-item label="Temperature">
              <el-slider
                v-model="form.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                show-input
                :show-input-controls="false"
                input-size="small"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="Max Tokens">
              <el-input-number
                v-model="form.maxTokens"
                :min="1"
                controls-position="right"
                placeholder="默认"
                style="width: 160px"
              />
              <span class="form-hint-inline">留空使用模型默认值</span>
            </el-form-item>

            <el-form-item label="最大上下文轮数">
              <el-input-number
                v-model="form.maxContextTurns"
                :min="1"
                :max="200"
                controls-position="right"
                placeholder="默认"
                style="width: 160px"
              />
              <span class="form-hint-inline">1 – 200，留空不限制</span>
            </el-form-item>

            <el-divider content-position="left">记忆</el-divider>

            <el-form-item label="Agent 记忆">
              <el-switch
                v-model="form.memoryEnabled"
                :active-value="1"
                :inactive-value="0"
                active-text="开启"
                inactive-text="关闭"
              />
              <span class="form-hint-block">开启后会在长会话中生成摘要，摘要失败不影响正常对话。</span>
            </el-form-item>

            <el-form-item label="摘要触发消息数">
              <el-input-number
                v-model="form.summaryTriggerMessageCount"
                :min="4"
                :max="500"
                controls-position="right"
                style="width: 160px"
                :disabled="form.memoryEnabled !== 1"
              />
            </el-form-item>

            <el-form-item label="摘要 Max Tokens">
              <el-input-number
                v-model="form.summaryMaxTokens"
                :min="100"
                :max="4000"
                controls-position="right"
                style="width: 160px"
                :disabled="form.memoryEnabled !== 1"
              />
            </el-form-item>
          </el-tab-pane>

          <!-- 工具绑定 -->
          <el-tab-pane label="工具绑定" name="tools">
            <div v-if="loadingMcp" class="tools-state">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载中...</span>
            </div>
            <div v-else-if="mcpTools.length === 0" class="tools-state tools-empty">
              暂无可用 MCP 工具，请先在「MCP 工具」页面接入工具
            </div>
            <el-checkbox-group v-else v-model="form.toolIds" :max="10" class="tools-grid">
              <el-checkbox
                v-for="tool in mcpTools"
                :key="tool.id"
                :label="tool.id"
                class="tool-checkbox"
              >
                <div class="tool-item">
                  <span class="tool-name">{{ tool.name }}</span>
                  <span class="tool-server">{{ tool.serverName }}</span>
                  <span v-if="tool.description" class="tool-desc">{{ tool.description }}</span>
                </div>
              </el-checkbox>
            </el-checkbox-group>
          </el-tab-pane>

        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Loading } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import { useProjectStore } from '@/stores/project'
import {
  getModelGroups,
  getAgentList,
  getAgentDetail,
  createAgent,
  updateAgent,
  deleteAgent,
  toggleAgentEnabled,
  type AgentListItem,
  type ModelGroup,
} from '@/api/agent'
import { getMcpToolOptions, type McpToolOption } from '@/api/mcp'
import { getKnowledgeBaseList, type KnowledgeBaseItem } from '@/api/knowledge'
import { getWorkflowList, type WorkflowListItem } from '@/api/workflow'

const projectStore = useProjectStore()

// ── 列配置 ────────────────────────────────────────────────────────────

const columns: HifyColumn[] = [
  { label: '名称',     prop: 'name',        minWidth: '160' },
  { label: '关联模型', slot: 'model',       minWidth: '180' },
  { label: '工具数量', slot: 'toolCount',   width: '90',  align: 'center' },
  { label: 'Temp.',   slot: 'temperature', width: '80',  align: 'center' },
  { label: '记忆',     slot: 'memory',      width: '80',  align: 'center' },
  { label: '状态',     slot: 'enabled',     width: '80',  align: 'center' },
  { label: '创建时间', slot: 'createdAt',   width: '110' },
  { label: '操作',     slot: 'actions',     width: '160', align: 'right' },
]

// ── 搜索过滤 ──────────────────────────────────────────────────────────

const filterName    = ref('')
const filterEnabled = ref<number | undefined>(undefined)

// ── 表格 ──────────────────────────────────────────────────────────────

const tableRef = ref<{ refresh: () => void; load: () => void }>()

function fetchList(page: number, pageSize: number) {
  return getAgentList(page, pageSize, {
    name:    filterName.value    || undefined,
    enabled: filterEnabled.value,
    projectId: projectStore.currentProjectId ?? undefined,
  })
}

// ── 启用 / 禁用 ───────────────────────────────────────────────────────

const togglingIds = reactive<Set<number>>(new Set())

async function handleToggle(row: AgentListItem, enabled: boolean) {
  togglingIds.add(row.id)
  try {
    await toggleAgentEnabled(row.id, enabled ? 1 : 0)
    tableRef.value?.refresh()
  } finally {
    togglingIds.delete(row.id)
  }
}

// ── 模型分组下拉 ──────────────────────────────────────────────────────

const modelGroups   = ref<ModelGroup[]>([])
const loadingModels = ref(false)

async function loadModelGroups() {
  loadingModels.value = true
  try {
    modelGroups.value = await getModelGroups()
  } finally {
    loadingModels.value = false
  }
}

// ── MCP 工具列表 ──────────────────────────────────────────────────────

const mcpTools = ref<McpToolOption[]>([])
const loadingMcp  = ref(false)

async function loadMcpServers() {
  loadingMcp.value = true
  try {
    mcpTools.value = await getMcpToolOptions(projectStore.currentProjectId)
  } finally {
    loadingMcp.value = false
  }
}

// ── 知识库下拉 ────────────────────────────────────────────────────────

const knowledgeBases = ref<KnowledgeBaseItem[]>([])
const loadingKnowledgeBases = ref(false)

async function loadKnowledgeBases() {
  loadingKnowledgeBases.value = true
  try {
    const result = await getKnowledgeBaseList(1, 100, undefined, projectStore.currentProjectId)
    knowledgeBases.value = result.records
  } finally {
    loadingKnowledgeBases.value = false
  }
}

// ── 工作流下拉 ────────────────────────────────────────────────────────

const workflows = ref<WorkflowListItem[]>([])
const loadingWorkflows = ref(false)

async function loadWorkflows() {
  loadingWorkflows.value = true
  try {
    const result = await getWorkflowList(1, 100, projectStore.currentProjectId)
    workflows.value = result.records
  } finally {
    loadingWorkflows.value = false
  }
}

onMounted(() => {
  loadModelGroups()
  loadMcpServers()
  loadKnowledgeBases()
  loadWorkflows()
})

// ── 表单 & 弹窗 ───────────────────────────────────────────────────────

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const activeTab = ref('basic')

const form = reactive({
  name:            '',
  description:     '',
  systemPrompt:    '',
  modelConfigId:   null as number | null,
  workflowId:      null as number | null,
  knowledgeBaseIds: [] as number[],
  temperature:     0.7,
  maxTokens:       undefined as number | undefined,
  maxContextTurns: undefined as number | undefined,
  memoryEnabled:   0,
  summaryTriggerMessageCount: 20,
  summaryMaxTokens: 800,
  toolIds:         [] as number[],
})

const rules: FormRules = {
  name:          [{ required: true, message: 'Agent 名称不能为空', trigger: 'blur' }],
  modelConfigId: [{ required: true, message: '请选择绑定模型',     trigger: 'change' }],
  systemPrompt:  [{ required: true, message: '系统提示词不能为空', trigger: 'blur' }],
}

function resetForm() {
  form.name            = ''
  form.description     = ''
  form.systemPrompt    = ''
  form.modelConfigId   = null
  form.workflowId      = null
  form.knowledgeBaseIds = []
  form.temperature     = 0.7
  form.maxTokens       = undefined
  form.maxContextTurns = undefined
  form.memoryEnabled   = 0
  form.summaryTriggerMessageCount = 20
  form.summaryMaxTokens = 800
  form.toolIds         = []
  activeTab.value      = 'basic'
}

function handleCreate() {
  resetForm()
  editingId.value = null
  dialogVisible.value = true
}

async function handleEdit(row: AgentListItem) {
  resetForm()
  editingId.value = row.id
  form.name          = row.name
  form.description   = row.description ?? ''
  form.modelConfigId = row.modelConfigId
  form.workflowId    = row.workflowId ?? null
  form.temperature   = row.temperature != null ? Number(row.temperature) : 0.7
  dialogVisible.value = true
  try {
    const detail = await getAgentDetail(row.id)
    form.systemPrompt    = detail.systemPrompt
    form.maxTokens       = detail.maxTokens    ?? undefined
    form.maxContextTurns = detail.maxContextTurns ?? undefined
    form.memoryEnabled   = detail.memoryEnabled ?? 0
    form.summaryTriggerMessageCount = detail.summaryTriggerMessageCount ?? 20
    form.summaryMaxTokens = detail.summaryMaxTokens ?? 800
    form.workflowId      = detail.workflowId ?? null
    form.knowledgeBaseIds = detail.knowledgeBaseIds ?? []
    form.toolIds         = detail.toolIds       ?? []
    if (detail.temperature != null) form.temperature = Number(detail.temperature)
  } catch {
    // keep defaults on fetch error
  }
}

function handleDialogClosed() {
  formRef.value?.clearValidate()
  submitting.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    activeTab.value = 'basic'
    return
  }

  submitting.value = true
  try {
    if (editingId.value !== null) {
      await updateAgent(editingId.value, {
        name:            form.name         || undefined,
        description:     form.description  ?? undefined,
        systemPrompt:    form.systemPrompt || undefined,
        modelConfigId:   form.modelConfigId ?? undefined,
        workflowId:      form.workflowId ?? null,
        bindWorkflow:    true,
        knowledgeBaseIds: form.knowledgeBaseIds,
        temperature:     form.temperature,
        maxTokens:       form.maxTokens    ?? null,
        maxContextTurns: form.maxContextTurns ?? null,
        memoryEnabled:   form.memoryEnabled,
        summaryTriggerMessageCount: form.summaryTriggerMessageCount,
        summaryMaxTokens: form.summaryMaxTokens,
        toolIds:         form.toolIds,
      })
      notifySuccess('Agent 已更新')
    } else {
      await createAgent({
        name:            form.name,
        description:     form.description   || undefined,
        systemPrompt:    form.systemPrompt,
        modelConfigId:   form.modelConfigId!,
        workflowId:      form.workflowId ?? undefined,
        knowledgeBaseIds: form.knowledgeBaseIds.length > 0 ? form.knowledgeBaseIds : undefined,
        temperature:     form.temperature,
        maxTokens:       form.maxTokens,
        maxContextTurns: form.maxContextTurns,
        memoryEnabled:   form.memoryEnabled,
        summaryTriggerMessageCount: form.summaryTriggerMessageCount,
        summaryMaxTokens: form.summaryMaxTokens,
        toolIds:         form.toolIds.length > 0 ? form.toolIds : undefined,
        projectId:       projectStore.currentProjectId ?? undefined,
      })
      notifySuccess('Agent 已创建')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // request interceptor has shown the error message
  } finally {
    submitting.value = false
  }
}

// ── 删除 ──────────────────────────────────────────────────────────────

const { confirm } = useConfirm()

async function handleDelete(row: AgentListItem) {
  const deleted = await confirm(
    `确定删除 Agent「${row.name}」？此操作无法恢复。`,
    () => deleteAgent(row.id),
  )
  if (deleted) tableRef.value?.refresh()
}
</script>

<style scoped>
.search-bar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
  padding: 12px 16px;
}

.model-name {
  display: block;
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.4;
}

.model-id {
  display: block;
  font-size: 11px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  line-height: 1.4;
}

.mono-text {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

.no-data {
  color: var(--text-tertiary);
}

.date-text {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

.form-hint-inline {
  margin-left: 10px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.form-hint-block {
  display: block;
  margin-top: 6px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  line-height: 1.4;
}

.knowledge-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.knowledge-option__meta {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.workflow-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

/* Tabs */
.agent-tabs {
  margin-top: -8px;
}

.agent-tabs :deep(.el-tabs__content) {
  padding-top: 16px;
}

.agent-form :deep(.el-form-item__label) {
  align-items: center;
  display: inline-flex;
  justify-content: flex-end;
  line-height: 1.3;
  min-height: 32px;
  white-space: nowrap;
  word-break: keep-all;
}

.agent-form :deep(.el-form-item__content) {
  min-width: 0;
}

/* Tool checkbox list */
.tools-state {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 24px 0;
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.tools-empty {
  justify-content: center;
}

.tools-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-checkbox {
  width: 100%;
  height: auto;
  min-height: 72px;
  margin-right: 0;
  padding: 10px 12px;
  align-items: flex-start;
  border: 1px solid var(--border-color-light);
  border-radius: 6px;
  background: var(--bg-surface);
  transition: background 0.15s, border-color 0.15s;
  white-space: normal;
}

.tool-checkbox:hover {
  background: var(--bg-secondary, #f5f7fa);
  border-color: var(--color-primary-200);
}

.tool-checkbox :deep(.el-checkbox__input) {
  margin-top: 4px;
}

.tool-checkbox :deep(.el-checkbox__label) {
  width: 100%;
  min-width: 0;
  padding-left: 12px;
  line-height: 1;
}

.tool-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.tool-name {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--text-primary);
  line-height: 1.35;
  word-break: break-word;
}

.tool-server {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  line-height: 1.3;
}

.tool-desc {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--text-xs);
  line-height: 1.45;
  word-break: break-word;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
