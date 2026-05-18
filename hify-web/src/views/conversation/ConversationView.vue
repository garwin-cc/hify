<template>
  <div class="chat-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="sidebar-kicker">Agent</div>
        <el-select
          v-model="selectedAgentId"
          :placeholder="t('conversation.selectAgent')"
          size="small"
          style="width: 100%"
          @change="onAgentChange"
        >
          <el-option
            v-for="a in agents"
            :key="a.id"
            :label="a.name"
            :value="a.id"
          />
        </el-select>
        <div class="sidebar-actions">
          <el-button size="small" type="primary" :icon="Plus" @click="newSession">{{ t('conversation.new') }}</el-button>
          <el-dropdown trigger="click">
            <el-button size="small" :icon="MoreFilled" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :disabled="!currentSessionId || isStreaming" @click="clearCurrentSummary">
                  {{ t('conversation.clearSummary') }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="session-list">
        <div
          v-for="s in sessionList"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="switchSession(s)"
        >
          <el-icon><ChatDotRound /></el-icon>
          <div class="session-main">
            <span class="session-title">{{ s.title }}</span>
            <span class="session-meta">{{ formatSessionTime(s.createdAt) }}</span>
          </div>
          <el-button
            class="session-delete"
            text
            circle
            size="small"
            :icon="DeleteIcon"
            :disabled="isStreaming"
            @click.stop="deleteSession(s)"
          />
        </div>
        <div v-if="sessionList.length === 0" class="session-empty">
          <span>{{ t('conversation.emptySessions') }}</span>
          <el-button link type="primary" size="small" @click="newSession">{{ t('conversation.newSession') }}</el-button>
        </div>
      </div>
    </aside>

    <div class="chat-panel">
      <header class="chat-header">
        <div>
          <div class="chat-title">{{ currentSessionTitle }}</div>
          <div class="chat-subtitle">{{ selectedAgentName }}</div>
        </div>
        <el-tag v-if="isStreaming" size="small" type="primary" effect="plain">{{ t('conversation.generating') }}</el-tag>
      </header>

      <div ref="messagesEl" class="messages">
        <div v-if="messages.length === 0" class="messages-placeholder">
          <div class="placeholder-title">{{ t('conversation.placeholderTitle') }}</div>
          <div class="placeholder-desc">{{ t('conversation.placeholderDesc') }}</div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message-row"
          :class="msg.role"
        >
          <div v-if="msg.role === 'assistant'" class="message-avatar">H</div>
          <div class="bubble" :class="[msg.role, { 'bubble--error': msg.error }]">
            <div v-if="msg.role === 'assistant'" class="assistant-meta">
              <span>{{ assistantStatus(msg) }}</span>
              <el-button
                v-if="msg.id"
                class="trace-button"
                text
                size="small"
                :icon="InfoFilled"
                @click="openTrace(msg)"
              />
            </div>
            <span v-if="msg.role === 'assistant' && msg.waiting" class="typing-dots">
              <span /><span /><span />
            </span>
            <span v-else-if="msg.error" class="error-text">{{ msg.content }}</span>
            <span
              v-else-if="msg.role === 'assistant'"
              class="md-body"
              v-html="renderContent(msg)"
            />
            <span v-else>{{ msg.content }}</span>
          </div>
        </div>
      </div>

      <div class="input-bar">
        <div class="input-card">
          <div class="input-agent">{{ selectedAgentName }}</div>
          <div class="composer-row">
            <el-input
              v-model="inputText"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 5 }"
              :disabled="isStreaming"
              :placeholder="t('conversation.askAgent', { name: selectedAgentName })"
              resize="none"
              class="composer-input"
              @keydown="onKeydown"
            />
            <el-button
              class="send-button"
              type="primary"
              :icon="isStreaming ? undefined : Promotion"
              :loading="isStreaming"
              :disabled="isStreaming || !selectedAgentId || !inputText.trim()"
              circle
              @click="send"
            />
          </div>
          <div class="composer-hint">{{ t('conversation.composerHint') }}</div>
        </div>
      </div>
    </div>

    <el-drawer v-model="traceDrawerVisible" :title="t('conversation.traceTitle')" size="520px">
      <div v-if="traceLoading" class="trace-empty">{{ t('conversation.loadingTrace') }}</div>
      <div v-else-if="traceDetail" class="trace-panel">
        <el-tabs class="trace-tabs">
          <el-tab-pane :label="t('conversation.trace.overview')">
            <section class="trace-section">
              <dl>
                <dt>traceId</dt><dd>{{ traceDetail.traceId }}</dd>
                <dt>{{ t('conversation.trace.status') }}</dt><dd>{{ traceDetail.status }}</dd>
                <dt>Agent</dt><dd>{{ traceDetail.agent?.name || '-' }}</dd>
                <dt>{{ t('conversation.trace.model') }}</dt><dd>{{ traceDetail.model?.providerName || '-' }} / {{ traceDetail.model?.modelId || '-' }}</dd>
                <dt>{{ t('conversation.trace.error') }}</dt><dd>{{ traceDetail.errorMessage || '-' }}</dd>
              </dl>
            </section>
          </el-tab-pane>

          <el-tab-pane label="RAG">
            <section class="trace-section">
              <div v-if="!traceDetail.rag?.triggered" class="trace-empty">{{ t('conversation.trace.notTriggered') }}</div>
              <div v-else-if="!traceDetail.rag?.hits.length" class="trace-empty">{{ t('conversation.trace.noRagHits') }}</div>
              <div v-for="hit in traceDetail.rag?.hits ?? []" :key="`${hit.documentId}-${hit.chunkIndex}`" class="trace-item">
                <div class="trace-item-title">{{ hit.documentName || hit.documentId || '-' }}</div>
                <div class="trace-meta">chunk #{{ hit.chunkIndex ?? '-' }} · score {{ formatScore(hit.score) }}</div>
                <p>{{ hit.contentPreview }}</p>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane label="MCP">
            <section class="trace-section">
              <div v-if="!traceDetail.mcp?.triggered" class="trace-empty">{{ t('conversation.trace.notTriggered') }}</div>
              <div v-for="tool in traceDetail.mcp?.toolCalls ?? []" :key="tool.toolName" class="trace-item">
                <div class="trace-item-title">{{ tool.toolName }}</div>
                <div class="trace-meta">
                  {{ tool.success ? t('conversation.trace.yes') : t('conversation.trace.no') }} · {{ tool.elapsedMs ?? '-' }}ms · {{ tool.argumentKeys?.join(', ') || '-' }}
                </div>
                <p v-if="tool.errorMessage">{{ tool.errorMessage }}</p>
              </div>
            </section>
          </el-tab-pane>

          <el-tab-pane label="LLM">
            <section class="trace-section">
              <dl>
                <dt>Provider</dt><dd>{{ traceDetail.llm?.providerName || '-' }}</dd>
                <dt>modelId</dt><dd>{{ traceDetail.llm?.modelId || '-' }}</dd>
                <dt>{{ t('conversation.trace.firstToken') }}</dt><dd>{{ traceDetail.llm?.firstTokenLatencyMs ?? '-' }}ms</dd>
                <dt>{{ t('conversation.trace.totalLatency') }}</dt><dd>{{ traceDetail.llm?.totalLatencyMs ?? '-' }}ms</dd>
                <dt>{{ t('conversation.trace.tokens') }}</dt><dd>{{ traceDetail.llm?.inputTokens ?? '-' }} / {{ traceDetail.llm?.outputTokens ?? '-' }}</dd>
                <dt>{{ t('conversation.trace.error') }}</dt><dd>{{ traceDetail.llm?.errorMessage || '-' }}</dd>
              </dl>
            </section>
          </el-tab-pane>

          <el-tab-pane label="Workflow">
            <section class="trace-section">
              <dl>
                <dt>{{ t('conversation.trace.triggered') }}</dt><dd>{{ traceDetail.workflow?.triggered ? t('conversation.trace.yes') : t('conversation.trace.no') }}</dd>
                <dt>workflowId</dt><dd>{{ traceDetail.workflow?.workflowId ?? '-' }}</dd>
                <dt>runId</dt><dd>{{ traceDetail.workflow?.workflowRunId ?? '-' }}</dd>
              </dl>
            </section>
          </el-tab-pane>

          <el-tab-pane :label="t('conversation.trace.memory')">
            <section class="trace-section">
              <dl>
                <dt>{{ t('conversation.trace.enabled') }}</dt><dd>{{ traceDetail.memory?.enabled ? t('conversation.trace.yes') : t('conversation.trace.no') }}</dd>
                <dt>{{ t('conversation.trace.summaryUsed') }}</dt><dd>{{ traceDetail.memory?.summaryUsed ? t('conversation.trace.yes') : t('conversation.trace.no') }}</dd>
                <dt>{{ t('conversation.trace.summaryVersion') }}</dt><dd>{{ traceDetail.memory?.summaryVersion ?? '-' }}</dd>
                <dt>{{ t('conversation.trace.summaryLatency') }}</dt><dd>{{ traceDetail.memory?.summaryLatencyMs ?? '-' }}ms</dd>
                <dt>{{ t('conversation.trace.summaryError') }}</dt><dd>{{ traceDetail.memory?.summaryErrorMessage || '-' }}</dd>
              </dl>
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChatDotRound, Delete as DeleteIcon, InfoFilled, MoreFilled, Plus, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import {
  clearConversationSummary,
  deleteConversationSession,
  getAgentOptions,
  getConversationMessages,
  getConversationTrace,
  getConversationSessions,
  streamMessage,
  loadSessions,
  saveSessions,
} from '@/api/conversation'
import type { AgentOption, SessionMeta, DoneEvent, ConversationTraceDetail } from '@/api/conversation'
import { workflowRunEventsUrl, getWorkflowRunDetail } from '@/api/workflow'
import { subscribeSse } from '@/api/sse'
import type { WorkflowRunEvent } from '@/api/workflow'

// ── 状态 ──────────────────────────────────────────────────────────────────

interface Message {
  id?: number
  traceId?: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  waiting?: boolean
  error?: boolean
  workflowEvents?: string[]
}

const agents         = ref<AgentOption[]>([])
const { t } = useI18n()
const selectedAgentId = ref<number | null>(null)
const sessionList    = ref<SessionMeta[]>([])
const currentSessionId = ref<number | null>(null)

const messages   = ref<Message[]>([])
const inputText  = ref('')
const isStreaming = ref(false)
const messagesEl = ref<HTMLElement | null>(null)
const traceDrawerVisible = ref(false)
const traceLoading = ref(false)
const traceDetail = ref<ConversationTraceDetail | null>(null)

const selectedAgentName = computed(() => {
  return agents.value.find(a => a.id === selectedAgentId.value)?.name ?? 'Gemini'
})

const currentSessionTitle = computed(() => {
  return sessionList.value.find(s => s.id === currentSessionId.value)?.title ?? t('conversation.newConversation')
})

let cancelStream: (() => void) | null = null
let cancelWorkflowEvents: (() => void) | null = null
const ACTIVE_STATE_KEY = 'hify.conversation.active'

// ── 打字机队列（30ms/字符）────────────────────────────────────────────────────
// SSE tokens 批量到达，这里逐字符匀速输出，避免内容整块跳入。
let typeQueue  = ''
let typeTimer: ReturnType<typeof setInterval> | null = null
let typeDoneCb: (() => void) | null = null
let typeAiIdx  = -1

function advanceDrip() {
  if (typeQueue.length > 0) {
    messages.value[typeAiIdx].content += typeQueue[0]
    typeQueue = typeQueue.slice(1)
    scrollBottom()
  }
  // 队列耗尽时：若已收到 done 信号则触发回调，否则停止计时器等待新 token
  if (typeQueue.length === 0) {
    if (typeDoneCb) {
      const cb = typeDoneCb
      typeDoneCb = null
      clearInterval(typeTimer!); typeTimer = null; typeAiIdx = -1
      cb()
    } else {
      clearInterval(typeTimer!); typeTimer = null
    }
  }
}

function enqueue(chars: string, idx: number) {
  typeAiIdx = idx
  typeQueue += chars
  if (typeTimer === null) typeTimer = setInterval(advanceDrip, 30)
}

function stopDrip() {
  if (typeTimer !== null) { clearInterval(typeTimer); typeTimer = null }
  typeQueue = ''; typeDoneCb = null; typeAiIdx = -1
}

// ── 初始化 ────────────────────────────────────────────────────────────────

onMounted(async () => {
  try {
    const res = await getAgentOptions()
    agents.value = (res.records ?? []).filter(a => a.enabled === 1)
    if (agents.value.length > 0) {
      const saved = loadActiveState()
      const savedAgent = saved?.agentId && agents.value.find(a => a.id === saved.agentId)
      const initialAgentId = savedAgent && saved.agentId ? saved.agentId : agents.value[0].id
      selectedAgentId.value = initialAgentId
      await loadSessionsForAgent(initialAgentId)
      if (saved?.sessionId && sessionList.value.some(s => s.id === saved.sessionId)) {
        const session = sessionList.value.find(s => s.id === saved.sessionId)!
        await switchSession(session, false)
      }
    }
  } catch {
    // ignore
  }
})

async function loadSessionsForAgent(agentId: number) {
  currentSessionId.value = null
  messages.value = []
  try {
    sessionList.value = await getConversationSessions(agentId) ?? []
  } catch {
    sessionList.value = loadSessions().filter(s => s.agentId === agentId)
  }
}

// ── Agent / 会话切换 ──────────────────────────────────────────────────────

function onAgentChange(id: number) {
  saveActiveState(id, null)
  loadSessionsForAgent(id)
}

function newSession() {
  closeWorkflowEvents()
  currentSessionId.value = null
  messages.value = []
  saveActiveState(selectedAgentId.value, null)
}

async function switchSession(s: SessionMeta, persist = true) {
  if (isStreaming.value) return
  closeWorkflowEvents()
  currentSessionId.value = s.id
  if (persist) saveActiveState(selectedAgentId.value, s.id)
  messages.value = []
  try {
    const records = await getConversationMessages(s.id)
    messages.value = (records ?? [])
      .filter(m => m.role === 'user' || m.role === 'assistant')
      .map(m => ({
        id: m.id,
        traceId: m.traceId,
        role: m.role as 'user' | 'assistant',
        content: m.content ?? '',
        error: m.status === 'ERROR',
      }))
    scrollBottom()
  } catch {
    messages.value = [{
      role: 'assistant',
      content: t('conversation.messages.historyLoadFailed'),
      error: true,
    }]
  }
}

async function deleteSession(s: SessionMeta) {
  if (isStreaming.value) return
  try {
    await ElMessageBox.confirm(t('conversation.confirm.deleteMessage', { title: s.title }), t('conversation.confirm.deleteTitle'), {
      type: 'warning',
      confirmButtonText: t('conversation.confirm.deleteConfirm'),
      cancelButtonText: t('common.cancel'),
      confirmButtonClass: 'el-button--danger',
    })
  } catch {
    return
  }

  await deleteConversationSession(s.id)
  sessionList.value = sessionList.value.filter(item => item.id !== s.id)
  saveSessions(loadSessions().filter(item => item.id !== s.id))
  if (currentSessionId.value === s.id) {
    closeWorkflowEvents()
    stopDrip()
    currentSessionId.value = null
    messages.value = []
    saveActiveState(selectedAgentId.value, null)
  }
  ElMessage.success(t('conversation.messages.sessionDeleted'))
}

async function clearCurrentSummary() {
  if (!currentSessionId.value || isStreaming.value) return
  try {
    await ElMessageBox.confirm(t('conversation.confirm.clearSummaryMessage'), t('conversation.confirm.clearSummaryTitle'), {
      type: 'warning',
      confirmButtonText: t('conversation.confirm.clearSummaryConfirm'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  await clearConversationSummary(currentSessionId.value)
  ElMessage.success(t('conversation.messages.summaryCleared'))
}

// ── 发送消息 ──────────────────────────────────────────────────────────────

function send() {
  const text = inputText.value.trim()
  if (!text || !selectedAgentId.value || isStreaming.value) return

  inputText.value = ''
  isStreaming.value = true

  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', streaming: true, waiting: true })
  // Keep an index, not the raw object — mutations must go through the reactive
  // proxy so Vue's dependency tracking fires on each token.
  const aiIdx = messages.value.length - 1
  scrollBottom()

  cancelStream = streamMessage(
    selectedAgentId.value,
    currentSessionId.value,
    text,
    {
      onToken(token) {
        messages.value[aiIdx].waiting = false
        enqueue(token, aiIdx)
      },
      onDone(ev: DoneEvent) {
        // SSE 流结束，等打字机队列耗尽后再标记完成
        typeDoneCb = () => {
          messages.value[aiIdx].id = ev.messageId
          messages.value[aiIdx].traceId = ev.traceId
          messages.value[aiIdx].streaming = false
          messages.value[aiIdx].waiting   = false
          isStreaming.value = false
          persistSession(ev.sessionId)
          scrollBottom()
        }
        // 极端情况：队列已空且计时器已停 → 立即触发
        if (typeQueue.length === 0 && typeTimer === null) {
          const cb = typeDoneCb; typeDoneCb = null; cb()
        }
      },
      onWorkflowStart(ev) {
        messages.value[aiIdx].waiting = false
        messages.value[aiIdx].workflowEvents = [t('conversation.messages.workflowStarted', { id: ev.workflowRunId })]
        subscribeWorkflowEvents(ev.workflowRunId, aiIdx)
        scrollBottom()
      },
      onError(errMsg, ev) {
        stopDrip()
        if (ev?.messageId) messages.value[aiIdx].id = ev.messageId
        if (ev?.traceId) messages.value[aiIdx].traceId = ev.traceId
        messages.value[aiIdx].streaming = false
        messages.value[aiIdx].waiting   = false
        messages.value[aiIdx].error     = true
        messages.value[aiIdx].content   = errMsg || t('conversation.messages.requestFailed')
        isStreaming.value = false
      },
    },
  )
}

async function openTrace(msg: Message) {
  if (!msg.id) return
  traceDrawerVisible.value = true
  traceLoading.value = true
  traceDetail.value = null
  try {
    traceDetail.value = await getConversationTrace(msg.id)
  } finally {
    traceLoading.value = false
  }
}

function subscribeWorkflowEvents(runId: number, aiIdx: number) {
  closeWorkflowEvents()
  cancelWorkflowEvents = subscribeSse(workflowRunEventsUrl(runId), {
    onMessage(message) {
      if (message.event !== 'workflow-run-event' && message.event !== 'message') return
      try {
        const data = JSON.parse(message.data) as WorkflowRunEvent
        appendWorkflowEvent(aiIdx, formatWorkflowEvent(data))
        if (data.eventType === 'RUN_SUCCEEDED' || data.eventType === 'RUN_FAILED' || data.eventType === 'RUN_TIMEOUT') {
          closeWorkflowEvents()
          loadWorkflowFinalResult(runId, aiIdx)
        }
      } catch {
        // ignore malformed SSE payloads
      }
    },
    onError() {
      closeWorkflowEvents()
    },
  })
}

function appendWorkflowEvent(aiIdx: number, text: string) {
  const msg = messages.value[aiIdx]
  msg.workflowEvents = [...(msg.workflowEvents ?? []), text].slice(-8)
  scrollBottom()
}

function formatWorkflowEvent(event: WorkflowRunEvent): string {
  if (event.eventType === 'NODE_REPLY') {
    const reply = String(event.payload?.reply ?? '')
    return reply ? `${event.nodeKey}: ${reply}` : `${event.nodeKey}: NODE_REPLY`
  }
  if (event.nodeKey) {
    return `${event.nodeKey}: ${event.status ?? event.eventType}`
  }
  return event.status ? `${event.eventType}: ${event.status}` : event.eventType
}

async function loadWorkflowFinalResult(runId: number, aiIdx: number) {
  try {
    const run = await getWorkflowRunDetail(runId)
    const finalText = run.output || run.error
    if (finalText) {
      messages.value[aiIdx].content = finalText
      messages.value[aiIdx].error = !!run.error
    }
  } catch {
    appendWorkflowEvent(aiIdx, t('conversation.messages.workflowResultFailed'))
  }
}

function closeWorkflowEvents() {
  cancelWorkflowEvents?.()
  cancelWorkflowEvents = null
}

function onKeydown(e: Event | KeyboardEvent) {
  if (!(e instanceof KeyboardEvent)) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onUnmounted(() => {
  stopDrip()
  cancelStream?.()
  closeWorkflowEvents()
})

// ── 会话持久化 ────────────────────────────────────────────────────────────

function persistSession(sessionId: number) {
  if (currentSessionId.value === sessionId) return // 已保存

  currentSessionId.value = sessionId
  saveActiveState(selectedAgentId.value, sessionId)

  const all = loadSessions()
  if (sessionList.value.find(s => s.id === sessionId)) return

  const title = messages.value.find(m => m.role === 'user')?.content ?? t('conversation.newConversation')
  const meta: SessionMeta = {
    id: sessionId,
    title: title.length > 30 ? title.slice(0, 30) + '…' : title,
    agentId: selectedAgentId.value!,
    createdAt: new Date().toISOString(),
  }
  all.unshift(meta)
  saveSessions(all)
  sessionList.value = [meta, ...sessionList.value]
}

function loadActiveState(): { agentId: number | null; sessionId: number | null } | null {
  try {
    return JSON.parse(localStorage.getItem(ACTIVE_STATE_KEY) ?? 'null')
  } catch {
    return null
  }
}

function saveActiveState(agentId: number | null, sessionId: number | null) {
  localStorage.setItem(ACTIVE_STATE_KEY, JSON.stringify({ agentId, sessionId }))
}

// ── 工具函数 ──────────────────────────────────────────────────────────────

function scrollBottom() {
  nextTick(() => {
    if (messagesEl.value) {
      messagesEl.value.scrollTop = messagesEl.value.scrollHeight
    }
  })
}

marked.use({ breaks: true })

function renderMd(text: string): string {
  if (!text) return ''
  return marked.parse(text) as string
}

function renderContent(msg: Message): string {
  const workflowHtml = msg.workflowEvents?.length
    ? `<div class="workflow-events">${msg.workflowEvents.map(item => `<div>${escapeHtml(item)}</div>`).join('')}</div>`
    : ''
  const html = renderMd(msg.content) + workflowHtml
  return msg.streaming ? html + '<span class="cursor">▋</span>' : html
}

function assistantStatus(msg: Message): string {
  if (msg.error) return t('conversation.status.failed')
  if (msg.workflowEvents?.length) return t('conversation.status.workflowRunning')
  if (msg.waiting) return t('conversation.status.preparing')
  if (msg.streaming) return t('conversation.status.streaming')
  return t('conversation.status.done')
}

function formatSessionTime(value?: string): string {
  if (!value) return t('conversation.time.justNow')
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(0, 10)
  const now = Date.now()
  const diff = now - date.getTime()
  if (diff < 60_000) return t('conversation.time.justNow')
  if (diff < 3_600_000) return t('conversation.time.minutesAgo', { count: Math.floor(diff / 60_000) })
  if (diff < 86_400_000) return t('conversation.time.hoursAgo', { count: Math.floor(diff / 3_600_000) })
  return value.slice(0, 10)
}

function escapeHtml(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function formatScore(score?: number): string {
  return typeof score === 'number' ? score.toFixed(4) : '-'
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 56px);
  min-height: 560px;
  overflow: hidden;
  background: #f4f6fb;
}

.sidebar {
  width: 260px;
  min-width: 260px;
  background: #fff;
  border-right: 1px solid #e6eaf2;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 16px 14px 12px;
  border-bottom: 1px solid #edf0f6;
}

.sidebar-kicker {
  margin-bottom: 8px;
  color: #7d879b;
  font-size: 12px;
  font-weight: 600;
}

.sidebar-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  gap: 8px;
  margin-top: 10px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 58px;
  padding: 9px 8px 9px 10px;
  cursor: pointer;
  font-size: 13px;
  color: #4b5568;
  border: 1px solid transparent;
  border-radius: 8px;
  transition: background 0.15s, border-color 0.15s;
  overflow: hidden;
}

.session-item:hover {
  background: #f7f9fc;
}

.session-item.active {
  background: #edf3ff;
  border-color: #d8e4ff;
  color: #2f5fe8;
}

.session-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.session-meta {
  color: #99a2b6;
  font-size: 12px;
}

.session-delete {
  flex: 0 0 auto;
  opacity: 0;
  color: #8d96aa;
  transition: opacity 0.15s, color 0.15s;
}

.session-item:hover .session-delete,
.session-item.active .session-delete {
  opacity: 1;
}

.session-delete:hover {
  color: #f56c6c;
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: #9aa3b6;
}

.chat-panel {
  --chat-content-width: 960px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  height: 64px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid #e7ebf3;
}

.chat-title {
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.chat-subtitle {
  margin-top: 3px;
  color: #7d879b;
  font-size: 12px;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 26px 32px 148px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
}

.messages-placeholder {
  flex: 1;
  width: min(var(--chat-content-width), 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #8a94aa;
}

.placeholder-title {
  color: #313849;
  font-size: 18px;
  font-weight: 700;
}

.placeholder-desc {
  font-size: 14px;
}

.message-row {
  width: min(var(--chat-content-width), 100%);
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.message-row.user      { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }

.message-avatar {
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  border-radius: 50%;
  background: #1f2937;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.bubble {
  max-width: calc(100% - 38px);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.bubble.user {
  max-width: min(720px, 72%);
  padding: 10px 14px;
  border-radius: 14px 14px 4px 14px;
  background: #2f66e8;
  color: #fff;
  box-shadow: 0 6px 16px rgba(47, 102, 232, 0.16);
}

.bubble.assistant {
  padding: 0;
  color: #222938;
}

.assistant-meta {
  height: 28px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8b95a8;
  font-size: 12px;
}

.trace-button {
  width: 24px;
  height: 24px;
  opacity: 0;
  color: #6f7a90;
  transition: opacity 0.15s, color 0.15s;
}

.message-row.assistant:hover .trace-button {
  opacity: 1;
}

.trace-button:hover {
  color: #2f66e8;
}

.bubble--error {
  padding: 10px 12px;
  border: 1px solid #f4c7c7;
  border-radius: 8px;
  background: #fff5f5;
}

.error-text {
  color: #d94a4a;
  font-size: 13px;
}

.trace-panel {
  display: flex;
  flex-direction: column;
}

.trace-section {
  padding-top: 4px;
}

.trace-section dl {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.trace-section dt {
  color: #8b95a8;
}

.trace-section dd {
  margin: 0;
  color: #252c3a;
  word-break: break-word;
}

.trace-item {
  padding: 10px 0;
  border-top: 1px solid #f2f3f5;
}

.trace-item:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.trace-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #252c3a;
}

.trace-meta,
.trace-empty {
  font-size: 12px;
  color: #8b95a8;
}

.trace-item p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #566176;
  line-height: 1.5;
  word-break: break-word;
}

.md-body :deep(p)     { margin: 0 0 .5em; }
.md-body :deep(p:last-child) { margin-bottom: 0; }
.md-body :deep(pre)   { background: #111827; color: #f8fafc; border-radius: 8px; padding: 12px 14px; overflow-x: auto; margin: .7em 0; }
.md-body :deep(code)  { font-family: 'Fira Code', 'Cascadia Code', 'Consolas', 'Menlo', monospace; font-size: 13px; }
.md-body :deep(pre code) { background: none; padding: 0; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 1.4em; margin: .4em 0; }
.md-body :deep(blockquote) { border-left: 3px solid #ddd; margin: .5em 0; padding-left: .8em; color: #909399; }
.md-body :deep(table)  { border-collapse: collapse; width: 100%; margin: .5em 0; }
.md-body :deep(th), .md-body :deep(td) { border: 1px solid #e4e7ed; padding: 6px 10px; }
.md-body :deep(th)     { background: #f5f7fa; }
.md-body :deep(.workflow-events) { margin-top: 8px; padding-top: 8px; border-top: 1px solid #ebeef5; color: #606266; font-size: 12px; line-height: 1.5; }

:deep(.cursor) {
  display: inline-block;
  margin-left: 2px;
  animation: blink .8s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.typing-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 20px;
}

.typing-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #409eff;
  animation: dot-bounce 1.2s ease-in-out infinite;
}

.typing-dots span:nth-child(2) { animation-delay: .2s; }
.typing-dots span:nth-child(3) { animation-delay: .4s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(.7); opacity: .5; }
  40%           { transform: scale(1);  opacity: 1; }
}

.input-bar {
  position: sticky;
  bottom: 0;
  z-index: 2;
  padding: 0 32px 22px;
  display: flex;
  justify-content: center;
  background: linear-gradient(180deg, rgba(244, 246, 251, 0), rgba(244, 246, 251, 0.96) 45%);
}

.input-card {
  width: min(var(--chat-content-width), 100%);
  padding: 9px 10px 8px;
  border: 1px solid #dde4f0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 8px 22px rgba(24, 35, 58, 0.07);
  transition: border-color 0.15s, box-shadow 0.15s;
}

.input-card:focus-within {
  border-color: #c6d2ee;
  box-shadow: 0 10px 24px rgba(24, 35, 58, 0.08);
}

.input-agent {
  display: inline-flex;
  align-items: center;
  max-width: 220px;
  height: 22px;
  margin: 0 2px 7px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f2f5fb;
  color: #6f7a90;
  font-size: 12px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-card :deep(.el-textarea__inner) {
  border: 0 !important;
  border-radius: 0;
  padding: 0;
  background: transparent;
  box-shadow: none !important;
  color: #202838;
  font-size: 14px;
  line-height: 1.55;
  outline: none !important;
}

.composer-input :deep(.el-textarea__inner:focus) {
  border: 0 !important;
  box-shadow: none !important;
  outline: none !important;
}

.composer-input :deep(.el-textarea__inner:focus-visible) {
  border: 0 !important;
  box-shadow: none !important;
  outline: none !important;
}

.input-card :deep(.el-textarea__inner::placeholder) {
  color: #aab3c4;
}

.send-button {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  margin-bottom: 1px;
}

.send-button :deep(.el-icon) {
  font-size: 15px;
}

.composer-hint {
  margin: 6px 3px 0;
  color: #b0b8c8;
  font-size: 11px;
}

.trace-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

@media (max-width: 900px) {
  .sidebar {
    width: 220px;
    min-width: 220px;
  }

  .messages {
    padding: 20px 18px 144px;
  }

  .chat-header,
  .input-bar {
    padding-left: 18px;
    padding-right: 18px;
  }

  .bubble,
  .bubble.user {
    max-width: 86%;
  }
}
</style>
