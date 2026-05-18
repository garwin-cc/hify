<template>
  <div class="page-content">
    <PageHeader
      :title="t('knowledge.title')"
      :description="t('knowledge.description')"
    >
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          {{ t('knowledge.create') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card search-bar">
      <el-input
        v-model="filterName"
        :placeholder="t('knowledge.searchPlaceholder')"
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
        :empty-text="t('knowledge.empty')"
      >
        <template #name="{ row }">
          <el-button link type="primary" class="name-link" @click="goDocuments(row)">
            {{ row.name }}
          </el-button>
        </template>

        <template #description="{ row }">
          <span v-if="row.description" class="description-text">{{ row.description }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #enabled="{ row }">
          <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
            {{ row.enabled === 1 ? t('knowledge.enabled') : t('knowledge.disabled') }}
          </el-tag>
        </template>

        <template #documentCount="{ row }">
          <span v-if="row.documentCount > 0" class="count-text">{{ row.documentCount }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <template #actions="{ row }">
          <el-button size="small" @click="handleEdit(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" @click="openRetrievalTest(row)">{{ t('knowledge.retrievalTest') }}</el-button>
          <el-button size="small" type="danger" text @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? t('knowledge.createTitle') : t('knowledge.editTitle')"
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
        <el-form-item :label="t('knowledge.name')" prop="name">
          <el-input
            v-model="form.name"
            :placeholder="t('knowledge.namePlaceholder')"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item :label="t('knowledge.descriptionLabel')">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            :placeholder="t('knowledge.descriptionPlaceholder')"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item :label="t('knowledge.embeddingModel')" prop="embeddingModelConfigId">
          <el-select
            v-model="form.embeddingModelConfigId"
            :placeholder="t('knowledge.embeddingModelPlaceholder')"
            filterable
            :loading="loadingEmbeddingModels"
            :disabled="editingId !== null && editingDocumentCount > 0"
            style="width: 100%"
          >
            <el-option
              v-for="model in embeddingModels"
              :key="model.id"
              :label="modelOptionLabel(model)"
              :value="model.id"
            />
          </el-select>
          <div v-if="editingId !== null && editingDocumentCount > 0" class="form-hint">
            {{ t('knowledge.lockedEmbeddingHint') }}
          </div>
          <div v-else-if="embeddingModels.length === 0" class="form-hint">
            {{ t('knowledge.embeddingModelHint') }}
          </div>
        </el-form-item>

        <el-divider content-position="left">{{ t('knowledge.retrievalConfig') }}</el-divider>

        <el-form-item :label="t('knowledge.retrievalMode')">
          <el-segmented
            v-model="form.retrievalMode"
            :options="[
              { label: t('knowledge.vector'), value: 'VECTOR' },
              { label: t('knowledge.fulltext'), value: 'FULLTEXT' },
              { label: t('knowledge.hybrid'), value: 'HYBRID' },
            ]"
          />
        </el-form-item>

        <el-form-item v-if="form.retrievalMode === 'HYBRID'" :label="t('knowledge.hybridWeight')">
          <el-slider v-model="form.hybridAlpha" :min="0" :max="1" :step="0.05" style="width: 280px" />
          <div class="form-hint">{{ t('knowledge.hybridHint') }}</div>
        </el-form-item>

        <el-form-item label="TopK">
          <el-input-number v-model="form.topK" :min="1" :max="50" controls-position="right" />
        </el-form-item>

        <el-form-item :label="t('knowledge.candidateTopK')">
          <el-input-number v-model="form.candidateTopK" :min="1" :max="100" controls-position="right" />
          <div class="form-hint">{{ t('knowledge.candidateHint') }}</div>
        </el-form-item>

        <el-form-item :label="t('knowledge.scoreThreshold')">
          <el-input-number
            v-model="form.scoreThreshold"
            :min="0"
            :max="1"
            :step="0.05"
            :precision="2"
            controls-position="right"
          />
        </el-form-item>

        <el-form-item :label="t('knowledge.chunkSize')">
          <el-input-number v-model="form.chunkSize" :min="128" :max="4000" controls-position="right" />
        </el-form-item>

        <el-form-item :label="t('knowledge.chunkOverlap')">
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="1000" controls-position="right" />
          <div v-if="editingId !== null && editingDocumentCount > 0" class="form-hint">
            {{ t('knowledge.chunkConfigHint') }}
          </div>
        </el-form-item>

        <el-form-item :label="t('knowledge.contextBudget')">
          <el-input-number v-model="form.maxContextTokens" :min="512" :max="16000" controls-position="right" />
        </el-form-item>

        <el-divider content-position="left">{{ t('knowledge.rerankAndFilter') }}</el-divider>

        <el-form-item :label="t('knowledge.enableRerank')">
          <el-switch v-model="form.rerankEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item v-if="form.rerankEnabled === 1" :label="t('knowledge.rerankModel')">
          <el-select
            v-model="form.rerankModelConfigId"
            :placeholder="t('knowledge.rerankModelPlaceholder')"
            filterable
            :loading="loadingRerankModels"
            style="width: 100%"
          >
            <el-option
              v-for="model in rerankModels"
              :key="model.id"
              :label="modelOptionLabel(model)"
              :value="model.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="form.rerankEnabled === 1" :label="t('knowledge.rerankTopN')">
          <el-input-number v-model="form.rerankTopN" :min="1" :max="100" controls-position="right" />
        </el-form-item>

        <el-form-item :label="t('knowledge.defaultFilter')">
          <el-switch v-model="form.metadataFilterEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <template v-if="form.metadataFilterEnabled === 1">
          <el-form-item :label="t('knowledge.department')">
            <el-input v-model="form.defaultDepartment" :placeholder="t('knowledge.departmentPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('knowledge.documentType')">
            <el-input v-model="form.defaultDocumentType" :placeholder="t('knowledge.documentTypePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('knowledge.tags')">
            <el-input v-model="form.defaultTagsText" :placeholder="t('knowledge.tagsPlaceholder')" />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="retrievalDialogVisible" :title="t('knowledge.retrievalDialogTitle')" width="760px">
      <el-form label-width="90px">
        <el-form-item :label="t('knowledge.query')">
          <el-input v-model="retrievalForm.queryText" :placeholder="t('knowledge.queryPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('knowledge.department')">
          <el-input v-model="retrievalForm.department" :placeholder="t('knowledge.optionalPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('knowledge.tags')">
          <el-input v-model="retrievalForm.tagsText" :placeholder="t('knowledge.tagsPlaceholder')" />
        </el-form-item>
      </el-form>
      <el-table :data="retrievalHits" v-loading="retrievalLoading" max-height="360" :empty-text="t('knowledge.retrievalEmpty')">
        <el-table-column prop="rank" label="#" width="56" />
        <el-table-column prop="documentName" :label="t('knowledge.document')" min-width="130" />
        <el-table-column prop="content" :label="t('knowledge.content')" min-width="260" show-overflow-tooltip />
        <el-table-column :label="t('knowledge.score')" width="180">
          <template #default="{ row }">
            <div class="score-stack">
              <span>Final {{ formatScore(row.finalScore ?? row.score) }}</span>
              <span v-if="row.vectorScore != null">Vector {{ formatScore(row.vectorScore) }}</span>
              <span v-if="row.keywordScore != null">Keyword {{ formatScore(row.keywordScore) }}</span>
              <span v-if="row.rerankScore != null">Rerank {{ formatScore(row.rerankScore) }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="retrievalDialogVisible = false">{{ t('knowledge.close') }}</el-button>
        <el-button type="primary" :loading="retrievalLoading" @click="runRetrievalTest">{{ t('knowledge.runTest') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useProjectStore } from '@/stores/project'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBaseList,
  updateKnowledgeBase,
  updateKnowledgeRetrievalConfig,
  testKnowledgeRetrieval,
  type KnowledgeBaseItem,
  type KnowledgeSearchHit,
} from '@/api/knowledge'
import { getEnabledModelConfigs, type ModelConfig } from '@/api/provider'

const router = useRouter()
const { t } = useI18n()
const { isNarrow } = useBreakpoint()
const projectStore = useProjectStore()

const columns = computed<HifyColumn[]>(() => [
  { label: t('table.name'), slot: 'name', minWidth: '180' },
  ...(!isNarrow.value ? [
    { label: t('table.description'), slot: 'description', minWidth: '260' } as HifyColumn,
  ] : []),
  { label: t('table.status'), slot: 'enabled', width: '90' },
  { label: t('table.documentCount'), slot: 'documentCount', width: '110', align: 'center' },
  ...(!isNarrow.value ? [
    { label: t('table.createdAt'), slot: 'createdAt', width: '120' } as HifyColumn,
  ] : []),
  { label: t('table.actions'), slot: 'actions', width: '150', align: 'right' },
])

const tableRef = ref<{ refresh: () => void; load: () => void }>()
const filterName = ref('')

function fetchList(page: number, pageSize: number) {
  return getKnowledgeBaseList(page, pageSize, filterName.value.trim(), projectStore.currentProjectId)
}

function goDocuments(row: KnowledgeBaseItem) {
  router.push(`/knowledge-bases/${row.id}/documents`)
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const selectedKnowledgeBase = ref<KnowledgeBaseItem | null>(null)

const form = reactive({
  name: '',
  description: '',
  embeddingModelConfigId: null as number | null,
  retrievalMode: 'VECTOR',
  hybridAlpha: 0.7,
  topK: 5,
  candidateTopK: 20,
  scoreThreshold: 0.65,
  chunkSize: 512,
  chunkOverlap: 64,
  maxContextTokens: 3000,
  rerankEnabled: 0,
  rerankModelConfigId: null as number | null,
  rerankTopN: 20,
  metadataFilterEnabled: 0,
  defaultDepartment: '',
  defaultDocumentType: '',
  defaultTagsText: '',
})
const editingDocumentCount = ref(0)
const loadingEmbeddingModels = ref(false)
const loadingRerankModels = ref(false)
const embeddingModels = ref<ModelConfig[]>([])
const rerankModels = ref<ModelConfig[]>([])

const rules: FormRules = {
  name: [
    { required: true, message: () => t('knowledge.validation.nameRequired'), trigger: 'blur' },
    { min: 1, max: 100, message: () => t('knowledge.validation.nameLength'), trigger: 'blur' },
  ],
  embeddingModelConfigId: [
    { required: true, message: () => t('knowledge.validation.embeddingRequired'), trigger: 'change' },
  ],
}

onMounted(() => {
  loadEmbeddingModels()
  loadRerankModels()
})

async function loadEmbeddingModels() {
  loadingEmbeddingModels.value = true
  try {
    embeddingModels.value = await getEnabledModelConfigs('EMBEDDING')
  } catch {
    embeddingModels.value = []
  } finally {
    loadingEmbeddingModels.value = false
  }
}

async function loadRerankModels() {
  loadingRerankModels.value = true
  try {
    rerankModels.value = await getEnabledModelConfigs('RERANK')
  } catch {
    rerankModels.value = []
  } finally {
    loadingRerankModels.value = false
  }
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.embeddingModelConfigId = embeddingModels.value[0]?.id ?? null
  form.retrievalMode = 'VECTOR'
  form.hybridAlpha = 0.7
  form.topK = 5
  form.candidateTopK = 20
  form.scoreThreshold = 0.65
  form.chunkSize = 512
  form.chunkOverlap = 64
  form.maxContextTokens = 3000
  form.rerankEnabled = 0
  form.rerankModelConfigId = null
  form.rerankTopN = 20
  form.metadataFilterEnabled = 0
  form.defaultDepartment = ''
  form.defaultDocumentType = ''
  form.defaultTagsText = ''
  editingDocumentCount.value = 0
}

function handleCreate() {
  resetForm()
  editingId.value = null
  dialogVisible.value = true
}

function handleEdit(row: KnowledgeBaseItem) {
  editingId.value = row.id
  selectedKnowledgeBase.value = row
  form.name = row.name
  form.description = row.description || ''
  form.embeddingModelConfigId = row.embeddingModelConfigId
  form.retrievalMode = row.retrievalMode || 'VECTOR'
  form.hybridAlpha = row.hybridAlpha ?? 0.7
  form.topK = row.topK || 5
  form.candidateTopK = row.candidateTopK || 20
  form.scoreThreshold = row.scoreThreshold ?? 0.65
  form.chunkSize = row.chunkSize || 512
  form.chunkOverlap = row.chunkOverlap ?? 64
  form.maxContextTokens = row.maxContextTokens || 3000
  form.rerankEnabled = row.rerankEnabled || 0
  form.rerankModelConfigId = row.rerankModelConfigId ?? null
  form.rerankTopN = row.rerankTopN || 20
  form.metadataFilterEnabled = row.metadataFilterEnabled || 0
  const metadataFilter = parseMetadataFilter(row.defaultMetadataFilterJson)
  form.defaultDepartment = String(metadataFilter.department || '')
  form.defaultDocumentType = String(metadataFilter.documentType || '')
  form.defaultTagsText = Array.isArray(metadataFilter.tags) ? metadataFilter.tags.join(',') : ''
  editingDocumentCount.value = row.documentCount
  dialogVisible.value = true
}

function handleDialogClosed() {
  formRef.value?.clearValidate()
  submitting.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.embeddingModelConfigId === null) return
  if (form.candidateTopK < form.topK) {
    form.candidateTopK = form.topK
  }
  if (form.chunkOverlap >= form.chunkSize) {
    form.chunkOverlap = Math.max(0, form.chunkSize - 1)
  }

  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      embeddingModelConfigId: form.embeddingModelConfigId,
      projectId: projectStore.currentProjectId ?? undefined,
    }
    const retrievalPayload = {
      retrievalMode: form.retrievalMode,
      hybridAlpha: form.hybridAlpha,
      topK: form.topK,
      candidateTopK: form.candidateTopK,
      scoreThreshold: form.scoreThreshold,
      chunkSize: form.chunkSize,
      chunkOverlap: form.chunkOverlap,
      maxContextTokens: form.maxContextTokens,
      rerankEnabled: form.rerankEnabled,
      rerankModelConfigId: form.rerankEnabled === 1 ? form.rerankModelConfigId : null,
      rerankTopN: form.rerankTopN,
      metadataFilterEnabled: form.metadataFilterEnabled,
      defaultMetadataFilter: buildDefaultMetadataFilter(),
    }
    if (editingId.value === null) {
      const created = await createKnowledgeBase(payload)
      await updateKnowledgeRetrievalConfig(created.id, retrievalPayload)
      notifySuccess(t('knowledge.messages.created'))
    } else {
      await updateKnowledgeBase(editingId.value, payload)
      await updateKnowledgeRetrievalConfig(editingId.value, retrievalPayload)
      notifySuccess(t('knowledge.messages.updated'))
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // request interceptor has shown the error message
  } finally {
    submitting.value = false
  }
}

const retrievalDialogVisible = ref(false)
const retrievalLoading = ref(false)
const retrievalHits = ref<KnowledgeSearchHit[]>([])
const retrievalForm = reactive({
  queryText: '',
  department: '',
  tagsText: '',
})

function openRetrievalTest(row: KnowledgeBaseItem) {
  selectedKnowledgeBase.value = row
  retrievalDialogVisible.value = true
  retrievalHits.value = []
}

async function runRetrievalTest() {
  if (!selectedKnowledgeBase.value || !retrievalForm.queryText.trim()) return
  retrievalLoading.value = true
  try {
    retrievalHits.value = await testKnowledgeRetrieval(selectedKnowledgeBase.value.id, {
      queryText: retrievalForm.queryText.trim(),
      department: retrievalForm.department.trim() || undefined,
      tags: splitTags(retrievalForm.tagsText),
      includeTrace: true,
    })
  } finally {
    retrievalLoading.value = false
  }
}

function buildDefaultMetadataFilter() {
  return {
    department: form.defaultDepartment.trim() || undefined,
    documentType: form.defaultDocumentType.trim() || undefined,
    tags: splitTags(form.defaultTagsText),
  }
}

function parseMetadataFilter(value?: string) {
  if (!value) return {} as Record<string, any>
  try {
    return JSON.parse(value)
  } catch {
    return {} as Record<string, any>
  }
}

function splitTags(value: string) {
  return value.split(',').map(item => item.trim()).filter(Boolean)
}

function modelOptionLabel(model: ModelConfig) {
  return `${model.name} (${model.modelId})`
}

function formatScore(value?: number) {
  return value == null ? '-' : value.toFixed(4)
}

const { confirm } = useConfirm()

async function handleDelete(row: KnowledgeBaseItem) {
  const deleted = await confirm(
    t('knowledge.messages.deleteConfirm', { name: row.name }),
    () => deleteKnowledgeBase(row.id),
    { successMsg: t('knowledge.messages.deleted') },
  )
  if (deleted) tableRef.value?.refresh()
}
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.name-link {
  padding: 0;
  font-weight: 600;
}

.description-text {
  display: inline-block;
  max-width: 520px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.count-text {
  font-family: var(--font-mono);
  color: var(--text-primary);
}

.date-text {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

.no-data {
  color: var(--text-tertiary);
}

.form-hint {
  margin-top: 6px;
  font-size: var(--text-xs);
  line-height: 1.4;
  color: var(--text-tertiary);
}

.score-stack {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}
</style>
