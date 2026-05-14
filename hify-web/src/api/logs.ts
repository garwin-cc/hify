import { get } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'
import type { WorkflowRun } from '@/api/workflow'

export interface CursorPageData<T> {
  records: T[]
  nextCursorId?: number
  nextCursorTime?: string
  hasMore: boolean
}

export interface ConversationLog {
  traceId: string
  sessionId?: number
  userMessageId?: number
  assistantMessageId?: number
  userId?: number
  appId?: number
  apiKeyId?: number
  agentId?: number
  agentName?: string
  modelConfigId?: number
  modelId?: string
  workflowId?: number
  workflowRunId?: number
  ragTriggered?: boolean
  mcpTriggered?: boolean
  summaryUsed?: boolean
  summaryLatencyMs?: number
  summaryErrorMessage?: string
  status: string
  errorCode?: string
  errorMessage?: string
  startedAt?: string
  firstTokenAt?: string
  finishedAt?: string
}

export interface McpToolCallAudit {
  id: number
  traceId?: string
  projectId?: number
  agentId?: number
  userId?: number
  workflowId?: number
  workflowRunId?: number
  mcpServerId?: number
  toolName: string
  status: string
  argumentKeys?: string[]
  elapsedMs?: number
  success?: boolean
  errorSummary?: string
  createdAt: string
}

export interface LlmUsageStats {
  groupKey: string
  groupName: string
  callCount: number
  successCount: number
  failureCount: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  failureRate: number
  avgLatencyMs: number
}

export interface RagRetrievalTrace {
  id: number
  traceId: string
  sourceType?: string
  sourceId?: string
  agentId?: number
  queryText?: string
  knowledgeBaseIds?: number[]
  retrievalMode?: string
  topK?: number
  scoreThreshold?: number
  hitCount?: number
  selectedChunkIds?: number[]
  latencyMs?: number
  status?: string
  errorMessage?: string
  createdAt?: string
}

export const getConversationLogs = (params: Record<string, unknown>): Promise<CursorPageData<ConversationLog>> =>
  get('/v1/conversations/logs', params)

export const getWorkflowRunLogs = (
  page: number,
  size: number,
  params: Record<string, unknown> = {},
): Promise<PageData<WorkflowRun>> =>
  get('/v1/workflow-runs', { page, size, ...params })

export const getMcpToolAudits = (
  page: number,
  size: number,
  params: Record<string, unknown> = {},
): Promise<PageData<McpToolCallAudit>> =>
  get('/v1/mcp-tool-audits', { page, size, ...params })

export const getLlmUsageStats = (params: Record<string, unknown> = {}): Promise<LlmUsageStats[]> =>
  get('/v1/llm-usage', params)

export const getRagRetrievalTrace = (traceId: string): Promise<RagRetrievalTrace> =>
  get(`/v1/knowledge-bases/rag-traces/${traceId}`)
