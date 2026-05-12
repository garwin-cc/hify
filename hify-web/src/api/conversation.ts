import { del, get } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'

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
  role: 'user' | 'assistant' | 'tool'
  content: string
  status: string
  createdAt: string
}

export interface TokenEvent {
  type: 'token'
  content: string
}

export interface DoneEvent {
  type: 'done'
  sessionId: number
  messageId: number
  finishReason: string
  inputTokens: number
  outputTokens: number
}

export interface WorkflowStartEvent {
  type: 'workflow_start'
  workflowRunId: number
  workflowId: number
}

export interface ErrorEvent {
  type: 'error'
  code: number
  message: string
}

export type SseEvent = TokenEvent | DoneEvent | WorkflowStartEvent | ErrorEvent

export interface StreamCallbacks {
  onToken: (token: string) => void
  onDone: (ev: DoneEvent) => void
  onWorkflowStart?: (ev: WorkflowStartEvent) => void
  onError: (msg: string) => void
}

// ── Agent API ──────────────────────────────────────────────────────────

export const getAgentOptions = (): Promise<PageData<AgentOption>> =>
  get('/v1/agents', { page: 1, pageSize: 100 })

export const getConversationSessions = (agentId: number): Promise<SessionMeta[]> =>
  get('/v1/conversations', { agentId })

export const getConversationMessages = (sessionId: number): Promise<ConversationMessage[]> =>
  get(`/v1/conversations/${sessionId}/messages`)

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

  ;(async () => {
    let response: Response
    try {
      response = await fetch('/api/v1/conversations/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

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
              else if (ev.type === 'done') callbacks.onDone(ev)
              else if (ev.type === 'workflow_start') callbacks.onWorkflowStart?.(ev)
              else if (ev.type === 'error') callbacks.onError(ev.message)
            } catch { /* ignore parse errors */ }
          }
        }
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
