<template>
  <div class="page-content">
    <PageHeader
      :title="knowledgeBase?.name || t('documents.titleFallback')"
      :description="t('documents.description')"
    >
      <template #actions>
        <el-button @click="goBack">
          <el-icon style="margin-right: 4px"><ArrowLeft /></el-icon>
          {{ t('documents.back') }}
        </el-button>
        <el-button type="primary" @click="uploadDialogVisible = true">
          <el-icon style="margin-right: 4px"><UploadFilled /></el-icon>
          {{ t('documents.upload') }}
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card retrieval-test">
      <div class="retrieval-test__bar">
        <el-input
          v-model="retrievalQuery"
          :placeholder="t('documents.retrievalPlaceholder')"
          clearable
          @keyup.enter="handleRetrievalTest"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-input-number
          v-model="retrievalTopK"
          :min="1"
          :max="50"
          controls-position="right"
          class="retrieval-test__number"
        />
        <el-input-number
          v-model="retrievalScoreThreshold"
          :min="0"
          :max="1"
          :step="0.05"
          :precision="2"
          controls-position="right"
          class="retrieval-test__number"
        />
        <el-button type="primary" :loading="testingRetrieval" @click="handleRetrievalTest">
          {{ t('documents.retrievalTest') }}
        </el-button>
      </div>

      <div v-if="retrievalTraceId" class="retrieval-test__trace">
        {{ t('documents.trace') }}: <span class="mono-text">{{ retrievalTraceId }}</span>
      </div>

      <el-empty
        v-if="retrievalTested && !testingRetrieval && retrievalHits.length === 0"
        :description="t('documents.noHits')"
      />
      <div v-else-if="retrievalHits.length > 0" class="retrieval-hit-list">
        <div v-for="hit in retrievalHits" :key="hit.id" class="retrieval-hit">
          <div class="retrieval-hit__header">
            <span>#{{ hit.rank || '-' }} {{ hit.documentName || `${t('documents.document')} ${hit.documentId}` }}</span>
            <span class="mono-text">{{ t('documents.score') }} {{ formatScore(hit.finalScore ?? hit.score) }}</span>
          </div>
          <div class="retrieval-hit__meta">
            {{ t('documents.chunk') }} #{{ hit.chunkIndex + 1 }}
            <span v-if="hit.vectorScore !== undefined"> · vector {{ formatScore(hit.vectorScore) }}</span>
          </div>
          <div class="retrieval-hit__content">{{ hit.content }}</div>
        </div>
      </div>
    </div>

    <div class="hify-card task-panel">
      <div class="hify-card__header">
        <span class="hify-card__title">{{ t('documents.taskQueue') }}</span>
        <div class="task-panel__actions">
          <el-button size="small" :loading="loadingTasks" @click="loadTasks">{{ t('documents.refreshQueue') }}</el-button>
          <el-button size="small" type="primary" @click="rebuildDialogVisible = true">{{ t('documents.rebuildIndex') }}</el-button>
        </div>
      </div>
      <el-table :data="tasks" size="small" v-loading="loadingTasks">
        <el-table-column prop="taskType" :label="t('documents.task')" width="130" />
        <el-table-column prop="documentId" :label="t('documents.document')" width="90">
          <template #default="{ row }">{{ row.documentId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="taskStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processStage" :label="t('documents.stage')" width="120">
          <template #default="{ row }">{{ row.processStage || '-' }}</template>
        </el-table-column>
        <el-table-column prop="processProgress" :label="t('documents.progress')" width="160">
          <template #default="{ row }">
            <el-progress :percentage="safeProgress(row.processProgress)" :show-text="false" :stroke-width="5" />
          </template>
        </el-table-column>
        <el-table-column prop="progressMessage" :label="t('documents.note')" min-width="180" />
        <el-table-column prop="errorMessage" :label="t('documents.error')" min-width="160" />
      </el-table>
      <el-empty v-if="!loadingTasks && tasks.length === 0" :description="t('documents.emptyQueue')" :image-size="80" />
    </div>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :row-style="{ height: '56px' }"
        :empty-text="t('documents.empty')"
      >
        <template #name="{ row }">
          <span class="file-name">{{ row.name }}</span>
        </template>

        <template #fileType="{ row }">
          <el-tag size="small" type="info" class="file-type">{{ row.fileType }}</el-tag>
        </template>

        <template #fileSize="{ row }">
          <span class="mono-text">{{ formatFileSize(row.fileSize) }}</span>
        </template>

        <template #chunkCount="{ row }">
          <span v-if="row.chunkCount > 0" class="mono-text">{{ row.chunkCount }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #status="{ row }">
          <div class="status-cell">
            <el-tooltip
              v-if="(row.status === 'FAILED' || row.status === 'CANCELED') && row.errorMessage"
              :content="documentErrorText(row)"
              placement="top"
            >
              <el-tag size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else size="small" :type="statusTagType(row.status)">
              <el-icon v-if="row.status === 'PROCESSING'" class="is-loading status-icon">
                <Loading />
              </el-icon>
              {{ processLabel(row) }}
            </el-tag>
            <el-progress
              v-if="row.status === 'PROCESSING'"
              :percentage="safeProgress(row.processProgress)"
              :show-text="false"
              :stroke-width="4"
              class="status-progress"
            />
            <span v-if="row.status === 'PROCESSING' && row.processedChunkCount > 0" class="status-meta">
              {{ row.processedChunkCount }} chunks
            </span>
          </div>
        </template>

        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <template #actions="{ row }">
          <div class="document-actions">
            <el-button
              v-if="row.status === 'DONE'"
              class="document-action document-action--secondary"
              size="small"
              @click="handleViewChunks(row)"
            >
              {{ t('documents.viewChunks') }}
            </el-button>
            <el-button
              v-if="row.status === 'DONE'"
              class="document-action document-action--revectorize"
              size="small"
              @click="handleRevectorize(row)"
            >
              {{ t('documents.revectorize') }}
            </el-button>
            <el-button
              v-if="row.status === 'FAILED' || row.status === 'CANCELED'"
              size="small"
              type="primary"
              plain
              :disabled="row.status === 'FAILED' && row.retryable !== 1"
              @click="handleRetry(row)"
            >
              {{ t('documents.retry') }}
            </el-button>
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'PROCESSING'"
              size="small"
              type="warning"
              plain
              @click="handleCancel(row)"
            >
              {{ t('documents.cancelProcess') }}
            </el-button>
            <el-button
              size="small"
              class="document-action"
              type="danger"
              plain
              @click="handleDelete(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </div>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="uploadDialogVisible"
      :title="t('documents.uploadTitle')"
      width="520px"
      destroy-on-close
    >
      <el-upload
        drag
        :show-file-list="false"
        accept=".txt,.md,.pdf,.csv,text/plain,text/markdown,application/pdf,text/csv"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          {{ t('documents.uploadText') }}<em>{{ t('documents.clickUpload') }}</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">{{ t('documents.uploadTip') }}</div>
        </template>
      </el-upload>
    </el-dialog>

    <el-dialog
      v-model="chunkDialogVisible"
      :title="chunkDialogTitle"
      width="760px"
      destroy-on-close
      @closed="handleChunkDialogClosed"
    >
      <div v-loading="loadingChunks" class="chunk-list">
        <el-empty v-if="!loadingChunks && chunks.length === 0" :description="t('documents.emptyChunks')" />
        <div v-for="chunk in chunks" v-else :key="chunk.id" class="chunk-item">
          <div class="chunk-item__header">
            <span>{{ t('documents.chunk') }} #{{ chunk.chunkIndex + 1 }}</span>
            <el-button link type="primary" @click="toggleChunk(chunk.id)">
              {{ expandedChunkIds.has(chunk.id) ? t('documents.collapse') : t('documents.expandFull') }}
            </el-button>
          </div>
          <div class="chunk-item__content">
            {{ chunkText(chunk) }}
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="rebuildDialogVisible" :title="t('documents.rebuildTitle')" width="520px">
      <el-form label-width="120px">
        <el-form-item :label="t('documents.reason')">
          <el-input v-model="rebuildForm.reason" :placeholder="t('documents.reasonPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('documents.embeddingModel')">
          <el-input-number v-model="rebuildForm.embeddingModelConfigId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="Chunk Size">
          <el-input-number v-model="rebuildForm.chunkSize" :min="100" :max="8000" controls-position="right" />
        </el-form-item>
        <el-form-item label="Overlap">
          <el-input-number v-model="rebuildForm.chunkOverlap" :min="0" :max="2000" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rebuildDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="rebuilding" @click="handleRebuildIndex">{{ t('documents.submitRebuild') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Loading, Search, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, type UploadProps, type UploadRequestOptions } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn, type PageData } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  deleteDocument,
  cancelDocument,
  getDocument,
  getDocumentChunks,
  getDocumentList,
  getKnowledgeTasks,
  getKnowledgeBase,
  rebuildKnowledgeIndex,
  revectorizeDocument,
  retryDocument,
  testKnowledgeRetrieval,
  uploadKnowledgeDocument,
  type DocumentStatus,
  type KnowledgeBaseItem,
  type KnowledgeChunkItem,
  type KnowledgeDocumentItem,
  type KnowledgeSearchHit,
  type KnowledgeTaskItem,
} from '@/api/knowledge'

const MAX_FILE_SIZE = 200 * 1024 * 1024
const ALLOWED_EXTENSIONS = new Set(['txt', 'md', 'pdf', 'csv'])

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const { isNarrow } = useBreakpoint()
const knowledgeBaseId = computed(() => Number(route.params.id))

const knowledgeBase = ref<KnowledgeBaseItem | null>(null)
const tableRef = ref<{ refresh: () => void; load: () => void }>()
const retrievalQuery = ref('')
const retrievalTopK = ref(knowledgeBase.value?.topK || 5)
const retrievalScoreThreshold = ref(knowledgeBase.value?.scoreThreshold ?? 0.65)
const testingRetrieval = ref(false)
const retrievalTested = ref(false)
const retrievalHits = ref<KnowledgeSearchHit[]>([])
const retrievalTraceId = computed(() => retrievalHits.value[0]?.traceId || '')
const tasks = ref<KnowledgeTaskItem[]>([])
const loadingTasks = ref(false)
const rebuildDialogVisible = ref(false)
const rebuilding = ref(false)
const rebuildForm = reactive({
  reason: '',
  embeddingModelConfigId: undefined as number | undefined,
  chunkSize: undefined as number | undefined,
  chunkOverlap: undefined as number | undefined,
})

const columns = computed<HifyColumn[]>(() => [
  { label: t('documents.fileName'), slot: 'name', minWidth: '220' },
  { label: t('table.type'), slot: 'fileType', width: '90' },
  ...(!isNarrow.value ? [
    { label: t('documents.fileSize'), slot: 'fileSize', width: '110' } as HifyColumn,
  ] : []),
  { label: t('documents.chunkCount'), slot: 'chunkCount', width: '100', align: 'center' },
  { label: t('documents.processStatus'), slot: 'status', width: '120' },
  ...(!isNarrow.value ? [
    { label: t('documents.createdAt'), slot: 'createdAt', width: '120' } as HifyColumn,
  ] : []),
  { label: t('documents.actions'), slot: 'actions', width: isNarrow.value ? '170' : '320', align: 'right' },
])

function goBack() {
  router.push('/knowledge-bases')
}

async function loadKnowledgeBase() {
  knowledgeBase.value = await getKnowledgeBase(knowledgeBaseId.value)
  retrievalTopK.value = knowledgeBase.value.topK || 5
  retrievalScoreThreshold.value = knowledgeBase.value.scoreThreshold ?? 0.65
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    tasks.value = await getKnowledgeTasks(knowledgeBaseId.value)
    if (tasks.value.some(task => shouldPollTask(task.status))) {
      startTaskPolling()
    }
  } finally {
    loadingTasks.value = false
  }
}

async function fetchList(page: number, pageSize: number): Promise<PageData<KnowledgeDocumentItem>> {
  const result = await getDocumentList(knowledgeBaseId.value, page, pageSize)
  result.records
    .filter((item) => shouldPoll(item.status))
    .forEach((item) => startPolling(item.id))
  return result
}

function statusTagType(status: DocumentStatus) {
  if (status === 'DONE') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CANCELED') return 'info'
  if (status === 'PROCESSING') return 'primary'
  return 'info'
}

function statusLabel(status: DocumentStatus) {
  const labels: Record<DocumentStatus, string> = {
    PENDING: t('documents.status.pending'),
    PROCESSING: t('documents.status.processing'),
    DONE: t('documents.status.done'),
    FAILED: t('documents.status.failed'),
    CANCELED: t('documents.status.canceled'),
  }
  return labels[status] ?? status
}

function taskStatusType(status?: string) {
  if (status === 'DONE' || status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'PROCESSING' || status === 'RUNNING' || status === 'PENDING') return 'warning'
  return 'info'
}

function processLabel(row: KnowledgeDocumentItem) {
  if (row.status !== 'PROCESSING') return statusLabel(row.status)
  const stageLabels: Record<string, string> = {
    EXTRACTING: t('documents.processStage.extracting'),
    CHUNKING: t('documents.processStage.chunking'),
    EMBEDDING: t('documents.processStage.embedding'),
    SAVING: t('documents.processStage.saving'),
  }
  return stageLabels[row.processStage] ?? statusLabel(row.status)
}

function documentErrorText(row: KnowledgeDocumentItem) {
  const parts = [
    row.failedStage ? t('documents.statusStage', { stage: row.failedStage }) : '',
    row.errorCode ? t('documents.errorCode', { code: row.errorCode }) : '',
    t('documents.retryable', { value: row.retryable === 1 ? t('documents.yes') : t('documents.no') }),
    row.errorMessage ? t('documents.reasonText', { reason: row.errorMessage }) : '',
  ].filter(Boolean)
  return parts.join('\n')
}

function safeProgress(progress?: number) {
  if (progress === undefined || progress === null) return 0
  return Math.max(0, Math.min(100, progress))
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatScore(score?: number) {
  if (score === undefined || score === null) return '-'
  return score.toFixed(3)
}

async function handleRetrievalTest() {
  const query = retrievalQuery.value.trim()
  if (!query) {
    ElMessage.warning(t('documents.enterRetrievalQuestion'))
    return
  }
  testingRetrieval.value = true
  retrievalTested.value = true
  try {
    retrievalHits.value = await testKnowledgeRetrieval(knowledgeBaseId.value, {
      queryText: query,
      topK: retrievalTopK.value,
      scoreThreshold: retrievalScoreThreshold.value,
      retrievalMode: 'VECTOR',
      includeTrace: true,
    })
  } catch {
    retrievalHits.value = []
  } finally {
    testingRetrieval.value = false
  }
}

const uploadDialogVisible = ref(false)

const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const extension = rawFile.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED_EXTENSIONS.has(extension)) {
    ElMessage.error(t('documents.unsupportedFile'))
    return false
  }
  if (rawFile.size > MAX_FILE_SIZE) {
    ElMessage.error(t('documents.fileTooLarge'))
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  try {
    const documentId = await uploadKnowledgeDocument(knowledgeBaseId.value, options.file)
    notifySuccess(t('documents.messages.uploaded'))
    uploadDialogVisible.value = false
    tableRef.value?.refresh()
    startPolling(documentId)
    options.onSuccess?.(documentId)
  } catch (error) {
    options.onError?.(error as any)
  }
}

const pollingTimers = reactive(new Map<number, ReturnType<typeof setInterval>>())
const taskPollingTimer = ref<ReturnType<typeof setInterval> | null>(null)

function shouldPoll(status: DocumentStatus) {
  return status === 'PENDING' || status === 'PROCESSING'
}

function shouldPollTask(status?: string) {
  return status === 'PENDING' || status === 'PROCESSING' || status === 'RUNNING'
}

function startPolling(documentId: number) {
  if (pollingTimers.has(documentId)) return
  const timer = setInterval(async () => {
    try {
      const document = await getDocument(documentId)
      if (!shouldPoll(document.status)) {
        stopPolling(documentId)
        tableRef.value?.refresh()
      }
    } catch {
      stopPolling(documentId)
    }
  }, 5000)
  pollingTimers.set(documentId, timer)
}

function stopPolling(documentId: number) {
  const timer = pollingTimers.get(documentId)
  if (!timer) return
  clearInterval(timer)
  pollingTimers.delete(documentId)
}

function clearPolling() {
  pollingTimers.forEach((timer) => clearInterval(timer))
  pollingTimers.clear()
  if (taskPollingTimer.value) {
    clearInterval(taskPollingTimer.value)
    taskPollingTimer.value = null
  }
}

function startTaskPolling() {
  if (taskPollingTimer.value) return
  taskPollingTimer.value = setInterval(async () => {
    await loadTasks()
    if (!tasks.value.some(task => shouldPollTask(task.status)) && taskPollingTimer.value) {
      clearInterval(taskPollingTimer.value)
      taskPollingTimer.value = null
      tableRef.value?.refresh()
    }
  }, 5000)
}

const { confirm } = useConfirm()

async function handleDelete(row: KnowledgeDocumentItem) {
  const deleted = await confirm(
    t('documents.messages.deleteConfirm', { name: row.name }),
    () => deleteDocument(row.id),
    { successMsg: t('documents.messages.deleted') },
  )
  if (deleted) {
    stopPolling(row.id)
    tableRef.value?.refresh()
  }
}

async function handleRetry(row: KnowledgeDocumentItem) {
  const retried = await confirm(
    t('documents.messages.retryConfirm', { name: row.name }),
    () => retryDocument(row.id),
    {
      title: t('documents.messages.retryTitle'),
      confirmText: t('documents.messages.retryConfirmText'),
      successMsg: t('documents.messages.retrySubmitted'),
    },
  )
  if (retried) {
    startPolling(row.id)
    tableRef.value?.refresh()
  }
}

async function handleCancel(row: KnowledgeDocumentItem) {
  const canceled = await confirm(
    t('documents.messages.cancelConfirm', { name: row.name }),
    () => cancelDocument(row.id),
    {
      title: t('documents.messages.cancelTitle'),
      confirmText: t('documents.messages.cancelConfirmText'),
      successMsg: t('documents.messages.cancelRequested'),
    },
  )
  if (canceled) {
    startPolling(row.id)
    tableRef.value?.refresh()
  }
}

async function handleRevectorize(row: KnowledgeDocumentItem) {
  const submitted = await confirm(
    t('documents.messages.revectorizeConfirm', { name: row.name }),
    () => revectorizeDocument(row.id, { reason: 'manual revectorize from web' }),
    {
      title: t('documents.messages.revectorizeTitle'),
      confirmText: t('documents.messages.submit'),
      successMsg: t('documents.messages.revectorizeSubmitted'),
    },
  )
  if (submitted) {
    await loadTasks()
    startPolling(row.id)
  }
}

async function handleRebuildIndex() {
  rebuilding.value = true
  try {
    await rebuildKnowledgeIndex(knowledgeBaseId.value, {
      reason: rebuildForm.reason.trim() || undefined,
      embeddingModelConfigId: rebuildForm.embeddingModelConfigId,
      chunkSize: rebuildForm.chunkSize,
      chunkOverlap: rebuildForm.chunkOverlap,
    })
    rebuildDialogVisible.value = false
    notifySuccess(t('documents.messages.rebuildSubmitted'))
    await loadTasks()
    tableRef.value?.refresh()
  } finally {
    rebuilding.value = false
  }
}

const chunkDialogVisible = ref(false)
const loadingChunks = ref(false)
const chunks = ref<KnowledgeChunkItem[]>([])
const activeDocument = ref<KnowledgeDocumentItem | null>(null)
const expandedChunkIds = reactive(new Set<number>())
const chunkDialogTitle = computed(() => activeDocument.value
  ? t('documents.chunkDialogTitle', { name: activeDocument.value.name })
  : t('documents.viewChunksTitle'))

async function handleViewChunks(row: KnowledgeDocumentItem) {
  activeDocument.value = row
  chunkDialogVisible.value = true
  loadingChunks.value = true
  try {
    chunks.value = await getDocumentChunks(row.id)
  } finally {
    loadingChunks.value = false
  }
}

function toggleChunk(id: number) {
  if (expandedChunkIds.has(id)) {
    expandedChunkIds.delete(id)
    return
  }
  expandedChunkIds.add(id)
}

function chunkText(chunk: KnowledgeChunkItem) {
  if (expandedChunkIds.has(chunk.id) || chunk.content.length <= 200) {
    return chunk.content
  }
  return `${chunk.content.slice(0, 200)}...`
}

function handleChunkDialogClosed() {
  chunks.value = []
  activeDocument.value = null
  expandedChunkIds.clear()
}

onMounted(() => {
  loadKnowledgeBase()
  loadTasks()
})

onBeforeUnmount(() => {
  clearPolling()
})
</script>

<style scoped>
.file-name {
  font-weight: 600;
  color: var(--text-primary);
}

.file-type {
  text-transform: uppercase;
  font-family: var(--font-mono);
  font-size: 11px;
}

.mono-text {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.retrieval-test {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.retrieval-test__bar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 120px 120px auto;
  gap: 12px;
  align-items: center;
}

.retrieval-test__number {
  width: 120px;
}

.retrieval-test__trace {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.retrieval-hit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.retrieval-hit {
  padding: 12px 0;
  border-top: 1px solid var(--border-color-light);
}

.retrieval-hit__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
  color: var(--text-primary);
}

.retrieval-hit__meta {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

.retrieval-hit__content {
  margin-top: 8px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.task-panel__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

@media (max-width: 768px) {
  .retrieval-test__bar {
    grid-template-columns: 1fr;
  }

  .retrieval-test__number {
    width: 100%;
  }
}

.date-text {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

.no-data {
  color: var(--text-tertiary);
}

.status-icon {
  margin-right: 4px;
}

.status-cell {
  display: inline-flex;
  min-width: 92px;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
}

.status-progress {
  width: 92px;
}

.status-meta {
  font-size: 11px;
  color: var(--text-tertiary);
  line-height: 1;
}

.document-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 10px;
  min-width: 0;
}

.document-actions :deep(.el-button) {
  margin-left: 0;
}

.document-action {
  min-width: 76px;
  justify-content: center;
  font-weight: 600;
  white-space: nowrap;
}

.document-action--secondary {
  border-color: #d8deea;
  background: #fff;
  color: #34405a;
}

.document-action--secondary:hover,
.document-action--secondary:focus {
  border-color: #9fb3e8;
  background: #f5f8ff;
  color: #2f5fe8;
}

.document-action--revectorize {
  border-color: #2f66e8;
  background: #2f66e8;
  color: #fff;
  box-shadow: 0 4px 10px rgba(47, 102, 232, 0.18);
}

.document-action--revectorize:hover,
.document-action--revectorize:focus {
  border-color: #2456cf;
  background: #2456cf;
  color: #fff;
}

.chunk-list {
  min-height: 180px;
  max-height: 560px;
  overflow: auto;
}

.chunk-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color-light);
}

.chunk-item:first-child {
  padding-top: 0;
}

.chunk-item:last-child {
  border-bottom: none;
}

.chunk-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--text-primary);
}

.chunk-item__content {
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
