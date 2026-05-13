<template>
  <div class="page-content">
    <PageHeader
      :title="knowledgeBase?.name || '文档管理'"
      description="上传文档后自动解析、分块、向量化，并通过状态列跟踪处理进度"
    >
      <template #actions>
        <el-button @click="goBack">
          <el-icon style="margin-right: 4px"><ArrowLeft /></el-icon>
          返回知识库
        </el-button>
        <el-button type="primary" @click="uploadDialogVisible = true">
          <el-icon style="margin-right: 4px"><UploadFilled /></el-icon>
          上传文档
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card retrieval-test">
      <div class="retrieval-test__bar">
        <el-input
          v-model="retrievalQuery"
          placeholder="输入问题测试知识库检索命中"
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
          测试检索
        </el-button>
      </div>

      <div v-if="retrievalTraceId" class="retrieval-test__trace">
        Trace: <span class="mono-text">{{ retrievalTraceId }}</span>
      </div>

      <el-empty
        v-if="retrievalTested && !testingRetrieval && retrievalHits.length === 0"
        description="未命中高相关分块"
      />
      <div v-else-if="retrievalHits.length > 0" class="retrieval-hit-list">
        <div v-for="hit in retrievalHits" :key="hit.id" class="retrieval-hit">
          <div class="retrieval-hit__header">
            <span>#{{ hit.rank || '-' }} {{ hit.documentName || `文档 ${hit.documentId}` }}</span>
            <span class="mono-text">score {{ formatScore(hit.finalScore ?? hit.score) }}</span>
          </div>
          <div class="retrieval-hit__meta">
            Chunk #{{ hit.chunkIndex + 1 }}
            <span v-if="hit.vectorScore !== undefined"> · vector {{ formatScore(hit.vectorScore) }}</span>
          </div>
          <div class="retrieval-hit__content">{{ hit.content }}</div>
        </div>
      </div>
    </div>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :row-style="{ height: '56px' }"
        empty-text="暂无文档，点击「上传文档」开始构建知识库"
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
          <el-button
            v-if="row.status === 'DONE'"
            size="small"
            @click="handleViewChunks(row)"
          >
            查看分块
          </el-button>
          <el-button
            v-if="row.status === 'FAILED' || row.status === 'CANCELED'"
            size="small"
            type="primary"
            text
            :disabled="row.status === 'FAILED' && row.retryable !== 1"
            @click="handleRetry(row)"
          >
            重试
          </el-button>
          <el-button
            v-if="row.status === 'PENDING' || row.status === 'PROCESSING'"
            size="small"
            type="warning"
            text
            @click="handleCancel(row)"
          >
            取消
          </el-button>
          <el-button
            size="small"
            type="danger"
            text
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文档"
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
          将 txt / md / pdf / csv 文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">仅支持 txt、md、pdf、csv，单个文件不超过 200MB。</div>
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
        <el-empty v-if="!loadingChunks && chunks.length === 0" description="暂无分块内容" />
        <div v-for="chunk in chunks" v-else :key="chunk.id" class="chunk-item">
          <div class="chunk-item__header">
            <span>Chunk #{{ chunk.chunkIndex + 1 }}</span>
            <el-button link type="primary" @click="toggleChunk(chunk.id)">
              {{ expandedChunkIds.has(chunk.id) ? '收起' : '展开全文' }}
            </el-button>
          </div>
          <div class="chunk-item__content">
            {{ chunkText(chunk) }}
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
  getKnowledgeBase,
  retryDocument,
  testKnowledgeRetrieval,
  uploadKnowledgeDocument,
  type DocumentStatus,
  type KnowledgeBaseItem,
  type KnowledgeChunkItem,
  type KnowledgeDocumentItem,
  type KnowledgeSearchHit,
} from '@/api/knowledge'

const MAX_FILE_SIZE = 200 * 1024 * 1024
const ALLOWED_EXTENSIONS = new Set(['txt', 'md', 'pdf', 'csv'])

const STATUS_LABEL: Record<DocumentStatus, string> = {
  PENDING: '等待处理',
  PROCESSING: '处理中',
  DONE: '完成',
  FAILED: '失败',
  CANCELED: '已取消',
}

const router = useRouter()
const route = useRoute()
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

const columns = computed<HifyColumn[]>(() => [
  { label: '文件名', slot: 'name', minWidth: '220' },
  { label: '类型', slot: 'fileType', width: '90' },
  ...(!isNarrow.value ? [
    { label: '文件大小', slot: 'fileSize', width: '110' } as HifyColumn,
  ] : []),
  { label: '分块数量', slot: 'chunkCount', width: '100', align: 'center' },
  { label: '处理状态', slot: 'status', width: '120' },
  ...(!isNarrow.value ? [
    { label: '创建时间', slot: 'createdAt', width: '120' } as HifyColumn,
  ] : []),
  { label: '操作', slot: 'actions', width: '170', align: 'right' },
])

function goBack() {
  router.push('/knowledge-bases')
}

async function loadKnowledgeBase() {
  knowledgeBase.value = await getKnowledgeBase(knowledgeBaseId.value)
  retrievalTopK.value = knowledgeBase.value.topK || 5
  retrievalScoreThreshold.value = knowledgeBase.value.scoreThreshold ?? 0.65
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
  return STATUS_LABEL[status] ?? status
}

function processLabel(row: KnowledgeDocumentItem) {
  if (row.status !== 'PROCESSING') return statusLabel(row.status)
  const stageLabels: Record<string, string> = {
    EXTRACTING: '解析中',
    CHUNKING: '分块中',
    EMBEDDING: '向量化中',
    SAVING: '写入中',
  }
  return stageLabels[row.processStage] ?? statusLabel(row.status)
}

function documentErrorText(row: KnowledgeDocumentItem) {
  const parts = [
    row.failedStage ? `失败阶段：${row.failedStage}` : '',
    row.errorCode ? `错误类型：${row.errorCode}` : '',
    `是否可重试：${row.retryable === 1 ? '是' : '否'}`,
    row.errorMessage ? `原因：${row.errorMessage}` : '',
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
    ElMessage.warning('请输入检索问题')
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
    ElMessage.error('仅支持 txt、md、pdf、csv 文件')
    return false
  }
  if (rawFile.size > MAX_FILE_SIZE) {
    ElMessage.error('文件大小不能超过 200MB')
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  try {
    const documentId = await uploadKnowledgeDocument(knowledgeBaseId.value, options.file)
    notifySuccess('文档已提交处理')
    uploadDialogVisible.value = false
    tableRef.value?.refresh()
    startPolling(documentId)
    options.onSuccess?.(documentId)
  } catch (error) {
    options.onError?.(error as any)
  }
}

const pollingTimers = reactive(new Map<number, ReturnType<typeof setInterval>>())

function shouldPoll(status: DocumentStatus) {
  return status === 'PENDING' || status === 'PROCESSING'
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
}

const { confirm } = useConfirm()

async function handleDelete(row: KnowledgeDocumentItem) {
  const deleted = await confirm(
    `确定删除文档「${row.name}」？对应分块会一起删除。`,
    () => deleteDocument(row.id),
    { successMsg: '文档已删除' },
  )
  if (deleted) {
    stopPolling(row.id)
    tableRef.value?.refresh()
  }
}

async function handleRetry(row: KnowledgeDocumentItem) {
  const retried = await confirm(
    `确定重试文档「${row.name}」？旧的半成品分块会先被清理。`,
    () => retryDocument(row.id),
    { successMsg: '文档已重新提交处理' },
  )
  if (retried) {
    startPolling(row.id)
    tableRef.value?.refresh()
  }
}

async function handleCancel(row: KnowledgeDocumentItem) {
  const canceled = await confirm(
    `确定取消文档「${row.name}」的处理任务？`,
    () => cancelDocument(row.id),
    { successMsg: '已请求取消文档处理' },
  )
  if (canceled) {
    startPolling(row.id)
    tableRef.value?.refresh()
  }
}

const chunkDialogVisible = ref(false)
const loadingChunks = ref(false)
const chunks = ref<KnowledgeChunkItem[]>([])
const activeDocument = ref<KnowledgeDocumentItem | null>(null)
const expandedChunkIds = reactive(new Set<number>())
const chunkDialogTitle = computed(() => activeDocument.value ? `分块：${activeDocument.value.name}` : '查看分块')

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
