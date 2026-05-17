<template>
  <div class="page-content">
    <PageHeader
      title="模型提供商管理"
      description="接入 OpenAI / Claude / Gemini / Ollama 等 LLM 提供商，统一管理 API 密钥与接入地址"
    >
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          新增提供商
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :show-pagination="false"
        :row-style="{ height: '52px' }"
        empty-text="暂无提供商，点击「新增提供商」开始配置"
      >
        <!-- 类型 -->
        <template #type="{ row }">
          <el-tag size="small" :type="TYPE_TAG_TYPE[row.type] ?? 'info'" class="type-tag">
            {{ TYPE_LABEL[row.type] ?? row.type }}
          </el-tag>
        </template>

        <!-- Base URL -->
        <template #baseUrl="{ row }">
          <span class="url-text">{{ row.baseUrl || '（官方默认）' }}</span>
        </template>

        <!-- 健康状态 -->
        <template #health="{ row }">
          <div class="health-cell">
            <el-popover placement="bottom" :width="320" trigger="hover">
              <template #reference>
                <el-tag size="small" :type="healthTagType(row.healthStatus)" class="health-tag">
                  {{ healthLabel(row.healthStatus) }}
                </el-tag>
              </template>
              <div class="health-popover">
                <div><span>最近检测</span><strong>{{ formatDateTime(row.lastCheckAt) }}</strong></div>
                <div><span>连续失败</span><strong>{{ row.failCount ?? 0 }} 次</strong></div>
                <div><span>告警状态</span><strong>{{ row.alertStatus || '-' }}</strong></div>
                <div v-if="row.errorMessage"><span>失败原因</span><strong>{{ row.errorMessage }}</strong></div>
              </div>
            </el-popover>
            <span v-if="row.latencyMs !== null" class="latency-text">{{ row.latencyMs }}ms</span>
          </div>
        </template>

        <!-- 模型数 -->
        <template #modelCount="{ row }">
          <el-popover
            placement="bottom-start"
            :width="420"
            trigger="click"
            popper-class="model-list-popper"
          >
            <template #reference>
              <el-button link type="primary" size="small">
                {{ row.modelCount }} 个
              </el-button>
            </template>
            <div class="model-list">
              <div class="model-list__header">
                <span>已启用的模型</span>
                <el-button link type="primary" size="small" @click="handleCreateModel(row)">
                  新增模型
                </el-button>
              </div>
              <div v-if="row.models.filter((m: ModelConfig) => m.enabled).length === 0" class="model-list__empty">
                暂无模型，可手动新增
              </div>
              <div
                v-for="m in row.models.filter((m: ModelConfig) => m.enabled)"
                :key="m.id"
                class="model-item"
              >
                <div class="model-item__main">
                  <span class="model-item__name">{{ m.name }}</span>
                  <span class="model-item__id">{{ m.modelId }}</span>
                </div>
                <el-select
                  :model-value="m.modelType ?? 'CHAT'"
                  size="small"
                  class="model-type-select"
                  :loading="updatingModelTypeIds.has(m.id)"
                  @change="(value: ModelType) => handleUpdateModelType(m, value)"
                >
                  <el-option
                    v-for="option in MODEL_TYPE_OPTIONS"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </div>
            </div>
          </el-popover>
        </template>

        <!-- 状态 -->
        <template #enabled="{ row }">
          <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '禁用' }}
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
            :loading="testingIds.has(row.id)"
            style="margin-left: 4px"
            @click="handleTestConnection(row)"
          >
            测试
          </el-button>
          <el-button
            size="small"
            type="danger"
            text
            style="margin-left: 4px"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增提供商' : '编辑提供商'"
      width="520px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        label-position="right"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：OpenAI 官方、本地 Ollama" />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select
            v-model="form.type"
            style="width: 100%"
            placeholder="请选择提供商类型"
          >
            <el-option
              v-for="t in PROVIDER_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId !== null ? '留空则不修改' : 'Ollama 等本地部署可留空'"
          />
        </el-form-item>

        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com（留空使用官方默认）" />
        </el-form-item>

        <el-form-item v-if="editingId !== null" label="状态">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
          <div class="form-hint">停用后，该提供商不会再用于模型调用和健康检查。</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="modelDialogVisible"
      title="新增模型"
      width="520px"
      destroy-on-close
      @closed="handleModelDialogClosed"
    >
      <el-form
        ref="modelFormRef"
        :model="modelForm"
        :rules="modelRules"
        label-width="100px"
        label-position="right"
      >
        <el-form-item label="供应商">
          <el-input :model-value="selectedProvider?.name || ''" disabled />
        </el-form-item>
        <el-form-item label="显示名称" prop="name">
          <el-input v-model="modelForm.name" placeholder="如：text-embedding-v4" />
        </el-form-item>
        <el-form-item label="模型 ID" prop="modelId">
          <el-input v-model="modelForm.modelId" placeholder="如：text-embedding-v4" />
        </el-form-item>
        <el-form-item label="模型用途" prop="modelType">
          <el-select v-model="modelForm.modelType" style="width: 100%">
            <el-option
              v-for="option in MODEL_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="上下文长度">
          <el-input-number
            v-model="modelForm.contextSize"
            :min="1"
            :max="10000000"
            :step="1024"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="modelSubmitting" @click="handleSubmitModel">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  getProviderList,
  createProvider,
  createModelConfig,
  updateProvider,
  deleteProvider,
  testConnection,
  updateModelConfigType,
  PROVIDER_TYPES,
  type ProviderListItem,
  type ModelConfig,
} from '@/api/provider'

// ── 常量 ──────────────────────────────────────────────────────────────

const TYPE_LABEL: Record<string, string> = {
  OPENAI:            'OpenAI',
  ANTHROPIC:         'Anthropic',
  DEEPSEEK:          'DeepSeek',
  ALIBABA:           '阿里百炼',
  OLLAMA:            'Ollama',
  OPENAI_COMPATIBLE: 'OpenAI 兼容',
}

const TYPE_TAG_TYPE: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  OPENAI:            'primary',
  ANTHROPIC:         'warning',
  DEEPSEEK:          'success',
  ALIBABA:           'warning',
  OLLAMA:            'info',
  OPENAI_COMPATIBLE: 'danger',
}

const HEALTH_TAG_TYPE: Record<string, 'success' | 'danger' | 'warning' | 'info'> = {
  UP:       'success',
  DOWN:     'danger',
  DEGRADED: 'warning',
  UNKNOWN:  'info',
}

const HEALTH_LABEL: Record<string, string> = {
  UP:       '正常',
  DOWN:     '故障',
  DEGRADED: '降级',
  UNKNOWN:  '未知',
}

type ModelType = ModelConfig['modelType']

const MODEL_TYPE_OPTIONS: Array<{ label: string; value: ModelType; successMessage: string }> = [
  { label: '对话', value: 'CHAT', successMessage: '已标记为对话模型' },
  { label: '向量', value: 'EMBEDDING', successMessage: '已标记为向量模型' },
  { label: '重排', value: 'RERANK', successMessage: '已标记为重排模型' },
]

// ── 工具方法 ──────────────────────────────────────────────────────────

function healthTagType(status: string | null): 'success' | 'danger' | 'warning' | 'info' {
  return HEALTH_TAG_TYPE[status ?? ''] ?? 'info'
}

function healthLabel(status: string | null): string {
  return HEALTH_LABEL[status ?? ''] ?? '未检测'
}

function formatDateTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

// ── 列配置 ────────────────────────────────────────────────────────────

const { isNarrow } = useBreakpoint()

const columns = computed<HifyColumn[]>(() => [
  { label: '名称',     prop: 'name',       minWidth: '150' },
  { label: '类型',     slot: 'type',       width: '120'    },
  ...(!isNarrow.value ? [
    { label: 'Base URL', slot: 'baseUrl',  minWidth: '180' } as HifyColumn,
  ] : []),
  { label: '健康状态', slot: 'health',     width: '130'    },
  { label: '模型数',   slot: 'modelCount', width: '80', align: 'center' as const },
  { label: '状态',     slot: 'enabled',    width: '80'     },
  ...(!isNarrow.value ? [
    { label: '创建时间', slot: 'createdAt', width: '110'   } as HifyColumn,
  ] : []),
  { label: '操作',     slot: 'actions',    width: '185', align: 'right' as const },
])

// ── 表格 & API ────────────────────────────────────────────────────────

const tableRef = ref<{ refresh: () => void; load: () => void }>()

function fetchList(page: number, pageSize: number) {
  return getProviderList(page, pageSize)
}

// ── 测试连通性 ────────────────────────────────────────────────────────

const testingIds = reactive<Set<number>>(new Set())
const updatingModelTypeIds = reactive<Set<number>>(new Set())

async function handleTestConnection(row: ProviderListItem) {
  testingIds.add(row.id)
  try {
    const result = await testConnection(row.id)
    if (result.success) {
      const models = result.modelCount != null ? ` · ${result.modelCount} 个模型` : ''
      ElMessage.success(`连通正常 · 延迟 ${result.latencyMs}ms${models}`)
    } else {
      ElMessage.error(result.errorMessage || '连通性测试失败')
    }
    tableRef.value?.load()
  } finally {
    testingIds.delete(row.id)
  }
}

async function handleUpdateModelType(model: ModelConfig, modelType: ModelType) {
  if ((model.modelType ?? 'CHAT') === modelType) return

  updatingModelTypeIds.add(model.id)
  try {
    await updateModelConfigType(model.id, modelType)
    notifySuccess(MODEL_TYPE_OPTIONS.find(option => option.value === modelType)?.successMessage ?? '模型用途已更新')
    tableRef.value?.load()
  } catch {
    // request interceptor has shown the error message
  } finally {
    updatingModelTypeIds.delete(model.id)
  }
}

// ── 手动新增模型 ──────────────────────────────────────────────────────

const modelDialogVisible = ref(false)
const modelSubmitting = ref(false)
const modelFormRef = ref<FormInstance>()
const selectedProvider = ref<ProviderListItem | null>(null)

const modelForm = reactive({
  name: '',
  modelId: '',
  modelType: 'CHAT' as ModelType,
  contextSize: 8192,
})

const modelRules: FormRules = {
  name: [{ required: true, message: '显示名称不能为空', trigger: 'blur' }],
  modelId: [{ required: true, message: '模型 ID 不能为空', trigger: 'blur' }],
  modelType: [{ required: true, message: '请选择模型用途', trigger: 'change' }],
}

function handleCreateModel(row: ProviderListItem) {
  selectedProvider.value = row
  modelForm.name = ''
  modelForm.modelId = ''
  modelForm.modelType = 'CHAT'
  modelForm.contextSize = 8192
  modelDialogVisible.value = true
}

function handleModelDialogClosed() {
  modelFormRef.value?.clearValidate()
  modelSubmitting.value = false
}

async function handleSubmitModel() {
  if (!modelFormRef.value || !selectedProvider.value) return
  const valid = await modelFormRef.value.validate().catch(() => false)
  if (!valid) return

  modelSubmitting.value = true
  try {
    await createModelConfig({
      providerId: selectedProvider.value.id,
      name: modelForm.name.trim(),
      modelId: modelForm.modelId.trim(),
      modelType: modelForm.modelType,
      contextSize: modelForm.contextSize,
    })
    notifySuccess('模型已新增')
    modelDialogVisible.value = false
    tableRef.value?.load()
  } catch {
    // request interceptor has shown the error message
  } finally {
    modelSubmitting.value = false
  }
}

// ── 表单 & 弹窗 ───────────────────────────────────────────────────────

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  name:    '',
  type:    '',
  apiKey:  '',
  baseUrl: '',
  enabled: 1,
})

const rules: FormRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择提供商类型', trigger: 'change' }],
}

function handleCreate() {
  resetForm()
  editingId.value = null
  dialogVisible.value = true
}

function handleEdit(row: ProviderListItem) {
  editingId.value = row.id
  form.name = row.name
  form.type = row.type
  form.apiKey = ''
  form.baseUrl = row.baseUrl
  form.enabled = row.enabled ? 1 : 0
  dialogVisible.value = true
}

function resetForm() {
  form.name = ''
  form.type = ''
  form.apiKey = ''
  form.baseUrl = ''
  form.enabled = 1
}

function handleDialogClosed() {
  formRef.value?.clearValidate()
  submitting.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value !== null) {
      await updateProvider(editingId.value, {
        name:    form.name,
        type:    form.type,
        apiKey:  form.apiKey  || undefined,
        baseUrl: form.baseUrl,
        enabled: form.enabled,
      })
      notifySuccess('提供商已更新')
    } else {
      await createProvider({
        name:    form.name,
        type:    form.type,
        apiKey:  form.apiKey  || undefined,
        baseUrl: form.baseUrl || undefined,
      })
      notifySuccess('提供商已创建')
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

async function handleDelete(row: ProviderListItem) {
  const deleted = await confirm(
    `确定删除提供商「${row.name}」？关联的 Agent 配置将无法使用。`,
    () => deleteProvider(row.id),
  )
  if (deleted) tableRef.value?.refresh()
}
</script>

<style scoped>
.type-tag {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.url-text {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.date-text {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

.health-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.health-tag {
  flex-shrink: 0;
}

.health-popover {
  display: grid;
  gap: 8px;
  font-size: 13px;
}

.health-popover div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
}

.health-popover span {
  color: var(--el-text-color-secondary);
}

.health-popover strong {
  min-width: 0;
  color: var(--el-text-color-primary);
  font-weight: 500;
  word-break: break-word;
}

.latency-text {
  font-size: 11px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
}

.no-data {
  color: var(--text-tertiary);
}

.form-hint {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  margin-top: 4px;
  line-height: 1.4;
}

/* model popover (global because it's teleported) */
.model-list__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}

.model-list__empty {
  padding: 10px 0;
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

.model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  border-bottom: 1px solid var(--border-color-light);
}

.model-item:last-child {
  border-bottom: none;
}

.model-item__main {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.model-item__name {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--text-primary);
  flex-shrink: 0;
}

.model-item__id {
  font-size: 11px;
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-type-select {
  width: 90px;
  flex-shrink: 0;
}
</style>
