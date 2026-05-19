import { get } from '@/utils/request'

export interface OperationsSummary {
  conversationCount: number
  failedConversationCount: number
  conversationFailureRate: number
  totalTokens: number
  ragTriggeredCount: number
  ragHitCount: number
  ragHitRate: number
  workflowRunCount: number
  workflowSuccessCount: number
  workflowSuccessRate: number
  mcpCallCount: number
  mcpFailureCount: number
  mcpFailureRate: number
}

export interface AgentUsage {
  agentId: number
  agentName: string
  conversationCount: number
  failedConversationCount: number
  failureRate: number
  ragTriggeredCount: number
  mcpTriggeredCount: number
  totalTokens: number
}

export interface ModelUsage {
  providerId: number
  providerType: string
  modelConfigId: number
  modelId: string
  callCount: number
  failureCount: number
  failureRate: number
  inputTokens: number
  outputTokens: number
  totalTokens: number
  avgLatencyMs: number
}

export interface WorkflowUsage {
  workflowId: number
  workflowName: string
  runCount: number
  successCount: number
  failureCount: number
  successRate: number
  avgElapsedMs: number
}

export interface McpToolUsage {
  mcpServerId: number
  toolName: string
  callCount: number
  failureCount: number
  failureRate: number
  avgElapsedMs: number
  lastErrorSummary?: string
}

export interface ErrorUsage {
  sourceType: string
  errorCode: string
  errorMessage?: string
  count: number
  lastSeenAt?: string
}

export interface SlowLlmCall {
  traceId: string
  agentId?: number
  modelId?: string
  latencyMs: number
  totalTokens: number
  success?: boolean
  errorCode?: string
  createdAt?: string
}

export interface RiskConversation {
  traceId: string
  agentId?: number
  agentName?: string
  status?: string
  ragTriggered?: boolean
  ragHit?: boolean
  mcpTriggered?: boolean
  errorCode?: string
  errorMessage?: string
  startedAt?: string
}

export interface OperationsAnalyticsOverview {
  summary: OperationsSummary
  agents: AgentUsage[]
  models: ModelUsage[]
  workflows: WorkflowUsage[]
  mcpTools: McpToolUsage[]
  errors: ErrorUsage[]
  slowLlmCalls: SlowLlmCall[]
  riskConversations: RiskConversation[]
}

export interface OperationsAnalyticsQuery {
  projectId?: number
  from?: string
  to?: string
}

export const getOperationsAnalyticsOverview = (
  params: OperationsAnalyticsQuery = {},
): Promise<OperationsAnalyticsOverview> =>
  get('/v1/ops/analytics/overview', params)
