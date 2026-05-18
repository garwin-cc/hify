<template>
  <div class="page-content">
    <PageHeader
      :title="t('provider.title')"
      :description="t('provider.description')"
    >
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          {{ t('provider.addProvider') }}
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
        :empty-text="t('provider.empty')"
      >
        <!-- 类型 -->
        <template #type="{ row }">
          <el-tag size="small" :type="TYPE_TAG_TYPE[row.type] ?? 'info'" class="type-tag">
            {{ typeLabels[row.type] ?? row.type }}
          </el-tag>
        </template>

        <!-- Base URL -->
        <template #baseUrl="{ row }">
          <span class="url-text">{{ row.baseUrl || t('provider.defaultOfficial') }}</span>
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
                <div><span>{{ t('provider.health.lastCheck') }}</span><strong>{{ formatDateTime(row.lastCheckAt) }}</strong></div>
                <div><span>{{ t('provider.health.failCount') }}</span><strong>{{ row.failCount ?? 0 }}</strong></div>
                <div><span>{{ t('provider.health.alertStatus') }}</span><strong>{{ row.alertStatus || '-' }}</strong></div>
                <div v-if="row.errorMessage"><span>{{ t('provider.health.errorReason') }}</span><strong>{{ row.errorMessage }}</strong></div>
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
                {{ t('provider.modelCount', { count: row.modelCount }) }}
              </el-button>
            </template>
            <div class="model-list">
              <div class="model-list__header">
                <span>{{ t('provider.enabledModels') }}</span>
                <el-button link type="primary" size="small" @click="handleCreateModel(row)">
                  {{ t('provider.addModel') }}
                </el-button>
              </div>
              <div v-if="row.models.filter((m: ModelConfig) => m.enabled).length === 0" class="model-list__empty">
                {{ t('provider.noModels') }}
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
                    v-for="option in modelTypeOptions"
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
            {{ row.enabled ? t('provider.enabled') : t('provider.disabled') }}
          </el-tag>
        </template>

        <!-- 创建时间 -->
        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <!-- 操作 -->
        <template #actions="{ row }">
          <el-button size="small" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button
            size="small"
            :loading="testingIds.has(row.id)"
            style="margin-left: 4px"
            @click="handleTestConnection(row)"
          >
            {{ t('common.test') }}
          </el-button>
          <el-button
            size="small"
            type="danger"
            text
            style="margin-left: 4px"
            @click="handleDelete(row)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? t('provider.dialog.createProvider') : t('provider.dialog.editProvider')"
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
        <el-form-item :label="t('provider.dialog.name')" prop="name">
          <el-input v-model="form.name" :placeholder="t('provider.dialog.namePlaceholder')" />
        </el-form-item>

        <el-form-item :label="t('provider.dialog.type')" prop="type">
          <el-select
            v-model="form.type"
            style="width: 100%"
            :placeholder="t('provider.dialog.typePlaceholder')"
          >
            <el-option
              v-for="item in providerTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId !== null ? t('provider.dialog.apiKeyPlaceholderEdit') : t('provider.dialog.apiKeyPlaceholderCreate')"
          />
        </el-form-item>

        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" :placeholder="t('provider.dialog.baseUrlPlaceholder')" />
        </el-form-item>

        <el-form-item v-if="editingId !== null" :label="t('common.status')">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            :active-text="t('provider.enabled')"
            :inactive-text="t('provider.stopped')"
            inline-prompt
          />
          <div class="form-hint">{{ t('provider.dialog.statusHint') }}</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="modelDialogVisible"
      :title="t('provider.dialog.createModel')"
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
        <el-form-item :label="t('provider.dialog.provider')">
          <el-input :model-value="selectedProvider?.name || ''" disabled />
        </el-form-item>
        <el-form-item :label="t('provider.dialog.displayName')" prop="name">
          <el-input v-model="modelForm.name" :placeholder="t('provider.dialog.displayNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('provider.dialog.modelId')" prop="modelId">
          <el-input v-model="modelForm.modelId" :placeholder="t('provider.dialog.modelIdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('provider.dialog.modelUsage')" prop="modelType">
          <el-select v-model="modelForm.modelType" style="width: 100%">
            <el-option
              v-for="option in modelTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('provider.dialog.contextSize')">
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
        <el-button @click="modelDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="modelSubmitting" @click="handleSubmitModel">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t, locale } = useI18n()

const TYPE_LABEL_ZH: Record<string, string> = {
  OPENAI: 'OpenAI',
  ANTHROPIC: 'Anthropic',
  DEEPSEEK: 'DeepSeek',
  ALIBABA: '阿里百炼',
  OLLAMA: 'Ollama',
  OPENAI_COMPATIBLE: 'OpenAI 兼容',
}

const TYPE_LABEL_EN: Record<string, string> = {
  OPENAI: 'OpenAI',
  ANTHROPIC: 'Anthropic',
  DEEPSEEK: 'DeepSeek',
  ALIBABA: 'Alibaba Bailian',
  OLLAMA: 'Ollama',
  OPENAI_COMPATIBLE: 'OpenAI compatible',
}

const typeLabels = computed(() => locale.value === 'en-US' ? TYPE_LABEL_EN : TYPE_LABEL_ZH)

const providerTypeOptions = computed(() => PROVIDER_TYPES.map(item => ({
  value: item.value,
  label: typeLabels.value[item.value] ?? item.label,
})))

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

type ModelType = ModelConfig['modelType']

const modelTypeOptions = computed<Array<{ label: string; value: ModelType; successMessage: string }>>(() => [
  { label: t('provider.modelType.chat'), value: 'CHAT', successMessage: t('provider.modelType.chatSuccess') },
  { label: t('provider.modelType.embedding'), value: 'EMBEDDING', successMessage: t('provider.modelType.embeddingSuccess') },
  { label: t('provider.modelType.rerank'), value: 'RERANK', successMessage: t('provider.modelType.rerankSuccess') },
])

// ── 工具方法 ──────────────────────────────────────────────────────────

function healthTagType(status: string | null): 'success' | 'danger' | 'warning' | 'info' {
  return HEALTH_TAG_TYPE[status ?? ''] ?? 'info'
}

function healthLabel(status: string | null): string {
  const labels: Record<string, string> = {
    UP: t('provider.health.up'),
    DOWN: t('provider.health.down'),
    DEGRADED: t('provider.health.degraded'),
    UNKNOWN: t('provider.health.unknown'),
  }
  return labels[status ?? ''] ?? t('provider.health.unchecked')
}

function formatDateTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

// ── 列配置 ────────────────────────────────────────────────────────────

const { isNarrow } = useBreakpoint()

const columns = computed<HifyColumn[]>(() => [
  { label: t('table.name'),     prop: 'name',       minWidth: '150' },
  { label: t('table.type'),     slot: 'type',       width: '120'    },
  ...(!isNarrow.value ? [
    { label: 'Base URL', slot: 'baseUrl',  minWidth: '180' } as HifyColumn,
  ] : []),
  { label: t('table.health'), slot: 'health',     width: '130'    },
  { label: t('table.modelCount'),   slot: 'modelCount', width: '100', align: 'center' as const },
  { label: t('table.status'),     slot: 'enabled',    width: '90'     },
  ...(!isNarrow.value ? [
    { label: t('table.createdAt'), slot: 'createdAt', width: '110'   } as HifyColumn,
  ] : []),
  { label: t('table.actions'),     slot: 'actions',    width: '185', align: 'right' as const },
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
      const models = result.modelCount != null ? t('provider.connectivityModels', { count: result.modelCount }) : ''
      ElMessage.success(t('provider.connectivityOk', { latency: result.latencyMs, models }))
    } else {
      ElMessage.error(result.errorMessage || t('provider.connectivityFailed'))
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
    notifySuccess(modelTypeOptions.value.find(option => option.value === modelType)?.successMessage ?? t('provider.modelUsageUpdated'))
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
  name: [{ required: true, message: () => t('provider.validation.displayNameRequired'), trigger: 'blur' }],
  modelId: [{ required: true, message: () => t('provider.validation.modelIdRequired'), trigger: 'blur' }],
  modelType: [{ required: true, message: () => t('provider.validation.modelUsageRequired'), trigger: 'change' }],
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
    notifySuccess(t('provider.modelCreated'))
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
  name: [{ required: true, message: () => t('provider.validation.nameRequired'), trigger: 'blur' }],
  type: [{ required: true, message: () => t('provider.validation.typeRequired'), trigger: 'change' }],
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
      notifySuccess(t('provider.providerUpdated'))
    } else {
      await createProvider({
        name:    form.name,
        type:    form.type,
        apiKey:  form.apiKey  || undefined,
        baseUrl: form.baseUrl || undefined,
      })
      notifySuccess(t('provider.providerCreated'))
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
    t('provider.deleteConfirm', { name: row.name }),
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
