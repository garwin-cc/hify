import { del, get, post } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'
import { useAuthStore } from '@/stores/auth'

// ── Types ──────────────────────────────────────────────────────────────

export interface AgentOption {
  id: number
  name: string
  description: string
  enabled: number
}

export interface SessionMeta {
  id: number
  title: string
  agentId: number
  createdAt: string
  messageCount?: number
  lastMessageAt?: string
}

export interface ConversationMessage {
  id: number
  sessionId: number
  traceId?: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  status: string
  errorCode?: string
  errorMessage?: string
  partial?: number
  createdAt: string
}

export interface MessageFeedbackReq {
  userId?: number
  rating: 'LIKE' | 'DISLIKE'
  issueType?: string
  comment?: string
  correctedAnswer?: string
}

export interface MessageFeedbackResp {
  id: number
  messageId: number
  sessionId: number
  agentId: number
  projectId?: number
  traceId?: string
  userId: number
  rating: string
  issueType?: string
  comment?: string
  status?: string
  reviewStatus?: string
}

export interface ConversationTraceDetail {
  traceId: string
  status: string
  errorCode?: string
  errorMessage?: string
  startedAt?: string
  firstTokenAt?: string
  finishedAt?: string
  agent?: { id: number; name: string }
  model?: {
    modelConfigId?: number
    providerId?: number
    providerName?: string
    providerType?: string
    modelId?: string
  }
  workflow?: { triggered: boolean; workflowId?: number; workflowRunId?: number }
  rag?: {
    triggered: boolean
    hits: Array<{
      knowledgeBaseId: number
      knowledgeBaseName?: string
      documentId?: string
      documentName?: string
      chunkId?: number
      chunkIndex?: number
      score?: number
      contentPreview?: string
    }>
  }
  memory?: {
    enabled: boolean
    summaryUsed: boolean
    summaryVersion?: number
    summaryLatencyMs?: number
    summaryErrorMessage?: string
  }
  mcp?: {
    triggered: boolean
    toolCalls: Array<{
      toolName: string
      argumentKeys: string[]
      elapsedMs?: number
      success: boolean
      errorMessage?: string
    }>
  }
  llm?: {
    providerId?: number
    providerName?: string
    providerType?: string
    modelConfigId?: number
    modelId?: string
    inputTokens?: number
    outputTokens?: number
    firstTokenLatencyMs?: number
    totalLatencyMs?: number
    status?: string
    errorCode?: string
    errorMessage?: string
  }
}

export interface ConversationSummary {
  id: number
  sessionId: number
  agentId: number
  summary: string
  version: number
  sourceMessageStartId?: number
  sourceMessageEndId?: number
  sourceMessageCount: number
  status: string
  errorMessage?: string
  summarizedAt?: string
  updatedAt?: string
}

export interface TokenEvent {
  type: 'token'
  content: string
  traceId?: string
}

export interface DoneEvent {
  type: 'done'
  sessionId: number
  messageId: number
  finishReason: string
  inputTokens: number
  outputTokens: number
  traceId?: string
}

export interface WorkflowStartEvent {
  type: 'workflow_start'
  workflowRunId: number
  workflowId: number
  traceId?: string
}

export interface ErrorEvent {
  type: 'error'
  code: number
  message: string
  messageId?: number
  traceId?: string
}

export type SseEvent = TokenEvent | DoneEvent | WorkflowStartEvent | ErrorEvent

export interface StreamCallbacks {
  onToken: (token: string) => void
  onDone: (ev: DoneEvent) => void
  onWorkflowStart?: (ev: WorkflowStartEvent) => void
  onError: (msg: string, ev?: ErrorEvent) => void
}

// ── Agent API ──────────────────────────────────────────────────────────

export const getAgentOptions = (): Promise<PageData<AgentOption>> =>
  get('/v1/agents', { page: 1, pageSize: 100 })

export const getConversationSessions = (agentId: number): Promise<SessionMeta[]> =>
  get('/v1/conversations', { agentId })

export const getConversationMessages = (sessionId: number): Promise<ConversationMessage[]> =>
  get(`/v1/conversations/${sessionId}/messages`)

export const getConversationTrace = (messageId: number): Promise<ConversationTraceDetail> =>
  get(`/v1/conversations/messages/${messageId}/trace`)

export const submitMessageFeedback = (
  messageId: number,
  data: MessageFeedbackReq,
): Promise<MessageFeedbackResp> =>
  post(`/v1/conversations/messages/${messageId}/feedback`, data)

export const getConversationSummary = (sessionId: number): Promise<ConversationSummary | null> =>
  get(`/v1/conversations/${sessionId}/summary`)

export const clearConversationSummary = (sessionId: number): Promise<void> =>
  del(`/v1/conversations/${sessionId}/summary`)

export const deleteConversationSession = (sessionId: number): Promise<void> =>
  del(`/v1/conversations/${sessionId}`)

// ── Conversation SSE ───────────────────────────────────────────────────

/**
 * 发起流式对话请求，返回一个取消函数。
 *
 * 使用 fetch + ReadableStream 而非 EventSource，因为后端是 POST 接口。
 * SSE 行格式：`data: {"type":"token","content":"..."}\n\n`
 */
export function streamMessage(
  agentId: number,
  sessionId: number | null,
  content: string,
  callbacks: StreamCallbacks,
): () => void {
  const controller = new AbortController()
  const auth = useAuthStore()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (auth.token) {
    headers.Authorization = `Bearer ${auth.token}`
  }

  ;(async () => {
    let response: Response
    try {
      response = await fetch('/api/v1/conversations/stream', {
        method: 'POST',
        headers,
        body: JSON.stringify({ agentId, sessionId, content }),
        signal: controller.signal,
      })
    } catch (err: any) {
      if (err.name !== 'AbortError') callbacks.onError(err.message ?? '网络错误')
      return
    }

    if (!response.ok) {
      try {
        const data = await response.json()
        callbacks.onError(data.message ?? `HTTP ${response.status}`)
      } catch {
        callbacks.onError(`HTTP ${response.status}`)
      }
      return
    }

    const contentType = response.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) {
      try {
        const data = await response.json()
        callbacks.onError(data.message ?? '请求失败')
      } catch {
        callbacks.onError('请求失败')
      }
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let finished = false

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        // SSE 以 \n\n 分隔事件，以 \n 分隔行
        const parts = buffer.split('\n\n')
        buffer = parts.pop() ?? ''

        for (const part of parts) {
          for (const line of part.split('\n')) {
            if (!line.startsWith('data:')) continue
            const raw = line.slice(5).trim()
            if (!raw) continue
            try {
              const ev: SseEvent = JSON.parse(raw)
              if (ev.type === 'token') callbacks.onToken(ev.content)
              else if (ev.type === 'done') {
                finished = true
                callbacks.onDone(ev)
              }
              else if (ev.type === 'workflow_start') callbacks.onWorkflowStart?.(ev)
              else if (ev.type === 'error') {
                finished = true
                callbacks.onError(ev.message, ev)
              }
            } catch { /* ignore parse errors */ }
          }
        }
      }
      if (!finished && !controller.signal.aborted) {
        callbacks.onError('连接已结束但未收到完成事件')
      }
    } catch (err: any) {
      if (err.name !== 'AbortError') callbacks.onError(err.message ?? '连接中断')
    }
  })()

  return () => controller.abort()
}

// ── localStorage helpers ───────────────────────────────────────────────

const STORAGE_KEY = 'hify_sessions'

export function loadSessions(): SessionMeta[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
  } catch {
    return []
  }
}

export function saveSessions(sessions: SessionMeta[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
}
