<template>
  <div class="page-content">
    <PageHeader title="日志中心" description="统一查看对话、Workflow、RAG、MCP 和 LLM 调用日志">
      <template #actions>
        <el-button :loading="loading" @click="refreshActive">刷新</el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="hify-card log-tabs" @tab-change="refreshActive">
      <el-tab-pane label="对话日志" name="conversation">
        <div class="filter-bar">
          <el-input v-model="conversationQuery.traceId" placeholder="traceId" clearable />
          <el-input v-model="conversationQuery.agentId" placeholder="agentId" clearable />
          <el-select v-model="conversationQuery.status" placeholder="状态" clearable>
            <el-option label="DONE" value="DONE" />
            <el-option label="ERROR" value="ERROR" />
            <el-option label="TIMEOUT" value="TIMEOUT" />
            <el-option label="CLIENT_DISCONNECTED" value="CLIENT_DISCONNECTED" />
          </el-select>
          <el-button type="primary" @click="loadConversationLogs(true)">查询</el-button>
        </div>
        <el-table :data="conversationLogs" size="small" v-loading="loading">
          <el-table-column prop="traceId" label="traceId" min-width="220" />
          <el-table-column prop="agentName" label="Agent" min-width="140" />
          <el-table-column prop="modelId" label="模型" min-width="140" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="链路" width="160">
            <template #default="{ row }">
              <el-tag v-if="row.ragTriggered" size="small">RAG</el-tag>
              <el-tag v-if="row.mcpTriggered" size="small" type="warning">MCP</el-tag>
              <el-tag v-if="row.workflowRunId" size="small" type="success">Workflow</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="startedAt" label="开始时间" width="180" />
          <el-table-column prop="errorMessage" label="错误" min-width="180" />
        </el-table>
        <div class="cursor-actions">
          <el-button :disabled="!conversationHasMore" @click="loadConversationLogs(false)">加载更多</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Workflow 运行" name="workflow">
        <div class="filter-bar">
          <el-input v-model="workflowQuery.traceId" placeholder="traceId" clearable />
          <el-input v-model="workflowQuery.workflowId" placeholder="workflowId" clearable />
          <el-select v-model="workflowQuery.status" placeholder="状态" clearable>
            <el-option label="RUNNING" value="RUNNING" />
            <el-option label="WAITING" value="WAITING" />
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
            <el-option label="TIMEOUT" value="TIMEOUT" />
          </el-select>
          <el-button type="primary" @click="workflowTableRef?.refresh()">查询</el-button>
        </div>
        <HifyTable
          ref="workflowTableRef"
          :columns="workflowColumns"
          :api="fetchWorkflowRuns"
          empty-text="暂无 Workflow 运行日志"
        >
          <template #status="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag></template>
          <template #traceId="{ row }"><span class="mono-text">{{ row.traceId || '-' }}</span></template>
          <template #elapsedMs="{ row }">{{ row.elapsedMs ?? '-' }}ms</template>
        </HifyTable>
      </el-tab-pane>

      <el-tab-pane label="RAG Trace" name="rag">
        <div class="filter-bar filter-bar--single">
          <el-input v-model="ragTraceId" placeholder="输入 traceId 精确查询 RAG 检索 trace" clearable />
          <el-button type="primary" :disabled="!ragTraceId.trim()" @click="loadRagTrace">查询</el-button>
        </div>
        <el-descriptions v-if="ragTrace" :column="2" border>
          <el-descriptions-item label="traceId">{{ ragTrace.traceId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ ragTrace.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ ragTrace.sourceType || '-' }} / {{ ragTrace.sourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检索模式">{{ ragTrace.retrievalMode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="命中数">{{ ragTrace.hitCount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ ragTrace.latencyMs ?? '-' }}ms</el-descriptions-item>
          <el-descriptions-item label="知识库">{{ ragTrace.knowledgeBaseIds?.join(', ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="选中分块">{{ ragTrace.selectedChunkIds?.join(', ') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="问题" :span="2">{{ ragTrace.queryText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误" :span="2">{{ ragTrace.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="输入 traceId 查询 RAG 检索详情" :image-size="80" />
      </el-tab-pane>

      <el-tab-pane label="MCP 调用" name="mcp">
        <div class="filter-bar">
          <el-input v-model="mcpQuery.traceId" placeholder="traceId" clearable />
          <el-input v-model="mcpQuery.toolName" placeholder="toolName" clearable />
          <el-select v-model="mcpQuery.status" placeholder="状态" clearable>
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
          <el-button type="primary" @click="mcpTableRef?.refresh()">查询</el-button>
        </div>
        <HifyTable ref="mcpTableRef" :columns="mcpColumns" :api="fetchMcpAudits" empty-text="暂无 MCP 调用审计">
          <template #success="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
          <template #argumentKeys="{ row }">{{ row.argumentKeys?.join(', ') || '-' }}</template>
          <template #elapsedMs="{ row }">{{ row.elapsedMs ?? '-' }}ms</template>
        </HifyTable>
      </el-tab-pane>

      <el-tab-pane label="LLM 调用" name="llm">
        <div class="filter-bar">
          <el-select v-model="llmGroupBy" placeholder="聚合维度">
            <el-option label="Provider" value="provider" />
            <el-option label="Model" value="model" />
            <el-option label="User" value="user" />
            <el-option label="App" value="app" />
          </el-select>
          <el-button type="primary" @click="loadLlmUsage">查询</el-button>
        </div>
        <el-table :data="llmUsage" size="small" v-loading="loading">
          <el-table-column prop="groupName" label="分组" min-width="160" />
          <el-table-column prop="callCount" label="调用" width="100" />
          <el-table-column prop="totalTokens" label="Tokens" width="120" />
          <el-table-column prop="avgLatencyMs" label="平均耗时" width="130">
            <template #default="{ row }">{{ Math.round(row.avgLatencyMs) }}ms</template>
          </el-table-column>
          <el-table-column prop="failureRate" label="失败率" width="120">
            <template #default="{ row }">{{ (row.failureRate * 100).toFixed(2) }}%</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn, type PageData } from '@/components/HifyTable.vue'
import {
  getConversationLogs,
  getLlmUsageStats,
  getMcpToolAudits,
  getRagRetrievalTrace,
  getWorkflowRunLogs,
  type ConversationLog,
  type LlmUsageStats,
  type RagRetrievalTrace,
} from '@/api/logs'
import type { WorkflowRun } from '@/api/workflow'

const activeTab = ref('conversation')
const loading = ref(false)

const conversationLogs = ref<ConversationLog[]>([])
const conversationHasMore = ref(false)
const conversationCursorId = ref<number | undefined>()
const conversationCursorTime = ref<string | undefined>()
const conversationQuery = reactive({ traceId: '', agentId: '', status: '' })

const workflowTableRef = ref<{ refresh: () => void }>()
const mcpTableRef = ref<{ refresh: () => void }>()
const workflowQuery = reactive({ traceId: '', workflowId: '', status: '' })
const mcpQuery = reactive({ traceId: '', toolName: '', status: '' })

const ragTraceId = ref('')
const ragTrace = ref<RagRetrievalTrace | null>(null)
const llmGroupBy = ref('provider')
const llmUsage = ref<LlmUsageStats[]>([])

const workflowColumns: HifyColumn[] = [
  { prop: 'id', label: 'Run ID', width: 90 },
  { prop: 'traceId', label: 'traceId', slot: 'traceId', minWidth: 220 },
  { prop: 'workflowId', label: 'Workflow', width: 110 },
  { prop: 'status', label: '状态', slot: 'status', width: 120 },
  { prop: 'currentNodeKey', label: '当前节点', width: 140 },
  { prop: 'elapsedMs', label: '耗时', slot: 'elapsedMs', width: 100 },
  { prop: 'createdAt', label: '创建时间', width: 180 },
]

const mcpColumns: HifyColumn[] = [
  { prop: 'traceId', label: 'traceId', minWidth: 220 },
  { prop: 'toolName', label: '工具', minWidth: 160 },
  { prop: 'success', label: '结果', slot: 'success', width: 100 },
  { prop: 'argumentKeys', label: '参数', slot: 'argumentKeys', minWidth: 180 },
  { prop: 'elapsedMs', label: '耗时', slot: 'elapsedMs', width: 100 },
  { prop: 'createdAt', label: '创建时间', width: 180 },
]

onMounted(() => loadConversationLogs(true))

async function refreshActive() {
  if (activeTab.value === 'conversation') await loadConversationLogs(true)
  if (activeTab.value === 'workflow') workflowTableRef.value?.refresh()
  if (activeTab.value === 'mcp') mcpTableRef.value?.refresh()
  if (activeTab.value === 'llm') await loadLlmUsage()
}

async function loadConversationLogs(reset: boolean) {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      traceId: conversationQuery.traceId || undefined,
      agentId: conversationQuery.agentId || undefined,
      status: conversationQuery.status || undefined,
      limit: 20,
      cursorId: reset ? undefined : conversationCursorId.value,
      cursorTime: reset ? undefined : conversationCursorTime.value,
    }
    const data = await getConversationLogs(params)
    conversationLogs.value = reset ? data.records : [...conversationLogs.value, ...data.records]
    conversationHasMore.value = data.hasMore
    conversationCursorId.value = data.nextCursorId
    conversationCursorTime.value = data.nextCursorTime
  } finally {
    loading.value = false
  }
}

function fetchWorkflowRuns(page: number, size: number): Promise<PageData<WorkflowRun>> {
  return getWorkflowRunLogs(page, size, {
    traceId: workflowQuery.traceId || undefined,
    workflowId: workflowQuery.workflowId || undefined,
    status: workflowQuery.status || undefined,
  })
}

function fetchMcpAudits(page: number, size: number) {
  return getMcpToolAudits(page, size, {
    traceId: mcpQuery.traceId || undefined,
    toolName: mcpQuery.toolName || undefined,
    status: mcpQuery.status || undefined,
  })
}

async function loadRagTrace() {
  loading.value = true
  try {
    ragTrace.value = await getRagRetrievalTrace(ragTraceId.value.trim())
  } finally {
    loading.value = false
  }
}

async function loadLlmUsage() {
  loading.value = true
  try {
    llmUsage.value = await getLlmUsageStats({ groupBy: llmGroupBy.value })
  } finally {
    loading.value = false
  }
}

function statusType(status?: string) {
  if (status === 'DONE' || status === 'SUCCESS') return 'success'
  if (status === 'RUNNING' || status === 'WAITING') return 'warning'
  if (status === 'ERROR' || status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'info'
}
</script>

<style scoped>
.log-tabs {
  padding-top: 8px;
}

.filter-bar {
  display: grid;
  grid-template-columns: repeat(3, minmax(140px, 1fr)) auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.filter-bar--single {
  grid-template-columns: minmax(240px, 1fr) auto;
}

.cursor-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.mono-text {
  font-family: var(--font-mono);
  word-break: break-all;
}

@media (max-width: 800px) {
  .filter-bar,
  .filter-bar--single {
    grid-template-columns: 1fr;
  }
}
</style>
