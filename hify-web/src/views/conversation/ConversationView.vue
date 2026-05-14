<template>
  <div class="chat-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <el-select
          v-model="selectedAgentId"
          placeholder="选择 Agent"
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
        <el-button
          size="small"
          type="primary"
          :icon="Plus"
          style="margin-top: 8px; width: 100%"
          @click="newSession"
        >新建会话</el-button>
        <el-button
          size="small"
          style="margin-top: 8px; width: 100%"
          :disabled="!currentSessionId || isStreaming"
          @click="clearCurrentSummary"
        >清空记忆摘要</el-button>
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
          <span class="session-title">{{ s.title }}</span>
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
        <div v-if="sessionList.length === 0" class="session-empty">暂无会话</div>
      </div>
    </aside>

    <div class="chat-panel">
      <div ref="messagesEl" class="messages">
        <div v-if="messages.length === 0" class="messages-placeholder">
          选择 Agent 开始对话
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message-row"
          :class="msg.role"
        >
          <div class="bubble" :class="[msg.role, { 'bubble--error': msg.error }]">
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
            <div v-if="msg.role === 'assistant' && msg.id" class="message-actions">
              <el-button
                text
                size="small"
                :icon="InfoFilled"
                @click="openTrace(msg)"
              >
                运行详情
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="input-bar">
        <div class="input-card">
          <el-input
            v-model="inputText"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            :disabled="isStreaming"
            :placeholder="`问问 ${selectedAgentName}`"
            resize="none"
            @keydown="onKeydown"
          />
          <el-button
            type="primary"
            :icon="isStreaming ? undefined : Promotion"
            :loading="isStreaming"
            :disabled="isStreaming || !selectedAgentId || !inputText.trim()"
            style="align-self: flex-end"
            @click="send"
          >{{ isStreaming ? '生成中' : '发送' }}</el-button>
        </div>
      </div>
    </div>

    <el-drawer v-model="traceDrawerVisible" title="运行详情" size="520px">
      <div v-if="traceLoading" class="trace-empty">加载中...</div>
      <div v-else-if="traceDetail" class="trace-panel">
        <section class="trace-section">
          <h3>基础信息</h3>
          <dl>
            <dt>traceId</dt><dd>{{ traceDetail.traceId }}</dd>
            <dt>状态</dt><dd>{{ traceDetail.status }}</dd>
            <dt>Agent</dt><dd>{{ traceDetail.agent?.name || '-' }}</dd>
            <dt>模型</dt><dd>{{ traceDetail.model?.providerName || '-' }} / {{ traceDetail.model?.modelId || '-' }}</dd>
            <dt>错误</dt><dd>{{ traceDetail.errorMessage || '-' }}</dd>
          </dl>
        </section>

        <section class="trace-section">
          <h3>RAG</h3>
          <div v-if="!traceDetail.rag?.triggered" class="trace-empty">未触发</div>
          <div v-else-if="!traceDetail.rag?.hits.length" class="trace-empty">已触发，无命中或检索失败</div>
          <div v-for="hit in traceDetail.rag?.hits ?? []" :key="`${hit.documentId}-${hit.chunkIndex}`" class="trace-item">
            <div class="trace-item-title">{{ hit.documentName || hit.documentId || '-' }}</div>
            <div class="trace-meta">chunk #{{ hit.chunkIndex ?? '-' }} · score {{ formatScore(hit.score) }}</div>
            <p>{{ hit.contentPreview }}</p>
          </div>
        </section>

        <section class="trace-section">
          <h3>Memory</h3>
          <dl>
            <dt>启用</dt><dd>{{ traceDetail.memory?.enabled ? '是' : '否' }}</dd>
            <dt>使用摘要</dt><dd>{{ traceDetail.memory?.summaryUsed ? '是' : '否' }}</dd>
            <dt>摘要版本</dt><dd>{{ traceDetail.memory?.summaryVersion ?? '-' }}</dd>
            <dt>摘要耗时</dt><dd>{{ traceDetail.memory?.summaryLatencyMs ?? '-' }}ms</dd>
            <dt>摘要错误</dt><dd>{{ traceDetail.memory?.summaryErrorMessage || '-' }}</dd>
          </dl>
        </section>

        <section class="trace-section">
          <h3>MCP</h3>
          <div v-if="!traceDetail.mcp?.triggered" class="trace-empty">未触发</div>
          <div v-for="tool in traceDetail.mcp?.toolCalls ?? []" :key="tool.toolName" class="trace-item">
            <div class="trace-item-title">{{ tool.toolName }}</div>
            <div class="trace-meta">
              {{ tool.success ? '成功' : '失败' }} · {{ tool.elapsedMs ?? '-' }}ms · 参数 {{ tool.argumentKeys?.join(', ') || '-' }}
            </div>
            <p v-if="tool.errorMessage">{{ tool.errorMessage }}</p>
          </div>
        </section>

        <section class="trace-section">
          <h3>LLM</h3>
          <dl>
            <dt>Provider</dt><dd>{{ traceDetail.llm?.providerName || '-' }}</dd>
            <dt>modelId</dt><dd>{{ traceDetail.llm?.modelId || '-' }}</dd>
            <dt>首 token</dt><dd>{{ traceDetail.llm?.firstTokenLatencyMs ?? '-' }}ms</dd>
            <dt>总耗时</dt><dd>{{ traceDetail.llm?.totalLatencyMs ?? '-' }}ms</dd>
            <dt>tokens</dt><dd>{{ traceDetail.llm?.inputTokens ?? '-' }} / {{ traceDetail.llm?.outputTokens ?? '-' }}</dd>
            <dt>错误</dt><dd>{{ traceDetail.llm?.errorMessage || '-' }}</dd>
          </dl>
        </section>

        <section class="trace-section">
          <h3>Workflow</h3>
          <dl>
            <dt>触发</dt><dd>{{ traceDetail.workflow?.triggered ? '是' : '否' }}</dd>
            <dt>workflowId</dt><dd>{{ traceDetail.workflow?.workflowId ?? '-' }}</dd>
            <dt>runId</dt><dd>{{ traceDetail.workflow?.workflowRunId ?? '-' }}</dd>
          </dl>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ChatDotRound, Delete as DeleteIcon, InfoFilled, Plus, Promotion } from '@element-plus/icons-vue'
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
      content: '会话历史加载失败，请稍后重试',
      error: true,
    }]
  }
}

async function deleteSession(s: SessionMeta) {
  if (isStreaming.value) return
  try {
    await ElMessageBox.confirm(`确定删除会话「${s.title}」？`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
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
  ElMessage.success('会话已删除')
}

async function clearCurrentSummary() {
  if (!currentSessionId.value || isStreaming.value) return
  try {
    await ElMessageBox.confirm('确定清空当前会话的记忆摘要？对话消息不会被删除。', '清空记忆摘要', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await clearConversationSummary(currentSessionId.value)
  ElMessage.success('记忆摘要已清空')
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
        messages.value[aiIdx].workflowEvents = [`工作流 #${ev.workflowRunId} 已开始`]
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
        messages.value[aiIdx].content   = errMsg || '请求失败，请稍后重试'
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
    appendWorkflowEvent(aiIdx, '工作流结果加载失败')
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

  const title = messages.value.find(m => m.role === 'user')?.content ?? '新会话'
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
/* ── 整体布局 ── */
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: var(--bg-surface, #f5f5f5);
}

/* ── 左侧边栏 ── */
.sidebar {
  width: 220px;
  min-width: 220px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 16px 12px 8px;
  border-bottom: 1px solid #e4e7ed;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  border-left: 3px solid transparent;
  transition: background 0.15s;
  overflow: hidden;
}

.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; border-left-color: #409eff; color: #409eff; }

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
  flex: 0 0 auto;
  opacity: 0;
  color: #909399;
}

.session-item:hover .session-delete,
.session-item.active .session-delete {
  opacity: 1;
}

.session-delete:hover {
  color: #f56c6c;
}

.session-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #c0c4cc;
}

/* ── 右侧聊天面板 ── */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── 消息列表 ── */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 20px 138px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.messages-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 14px;
}

.message-row {
  display: flex;
}

.message-row.user      { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }

/* ── 气泡 ── */
.bubble {
  max-width: 68%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.65;
  word-break: break-word;
}

.bubble.user {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 3px;
}

.bubble.assistant {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 3px;
}

.bubble--error {
  background: #fff0f0;
  border-color: #fbc4c4;
}

.error-text {
  color: #f56c6c;
  font-size: 13px;
}

.message-actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
}

.message-actions :deep(.el-button) {
  height: 22px;
  padding: 0;
  font-size: 12px;
}

.trace-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.trace-section {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 14px;
}

.trace-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
  color: #303133;
}

.trace-section dl {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.trace-section dt {
  color: #909399;
}

.trace-section dd {
  margin: 0;
  color: #303133;
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
  color: #303133;
}

.trace-meta,
.trace-empty {
  font-size: 12px;
  color: #909399;
}

.trace-item p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
  word-break: break-word;
}

/* Markdown 内容样式重置 */
.md-body :deep(p)     { margin: 0 0 .5em; }
.md-body :deep(p:last-child) { margin-bottom: 0; }
.md-body :deep(pre)   { background: #f5f7fa; border-radius: 6px; padding: 10px 12px; overflow-x: auto; margin: .5em 0; }
.md-body :deep(code)  { font-family: 'Fira Code', 'Cascadia Code', 'Consolas', 'Menlo', monospace; font-size: 13px; }
.md-body :deep(pre code) { background: none; padding: 0; }
.md-body :deep(ul), .md-body :deep(ol) { padding-left: 1.4em; margin: .4em 0; }
.md-body :deep(blockquote) { border-left: 3px solid #ddd; margin: .5em 0; padding-left: .8em; color: #909399; }
.md-body :deep(table)  { border-collapse: collapse; width: 100%; margin: .5em 0; }
.md-body :deep(th), .md-body :deep(td) { border: 1px solid #e4e7ed; padding: 6px 10px; }
.md-body :deep(th)     { background: #f5f7fa; }
.md-body :deep(.workflow-events) { margin-top: 8px; padding-top: 8px; border-top: 1px solid #ebeef5; color: #606266; font-size: 12px; line-height: 1.5; }

/* 光标动画 */
:deep(.cursor) {
  display: inline-block;
  margin-left: 2px;
  animation: blink .8s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* 等待跳点 */
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

/* ── 底部输入区：保留参考图的圆角会话框样式 ── */
.input-bar {
  position: sticky;
  bottom: 0;
  z-index: 2;
  padding: 0 20px 20px;
  display: flex;
  justify-content: center;
  background: linear-gradient(180deg, rgba(245, 245, 245, 0), rgba(245, 245, 245, .72) 58%);
}

.input-card {
  width: min(760px, 100%);
  padding: 16px 18px;
  border: 1px solid rgba(60, 64, 67, .08);
  border-radius: 28px;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 1px 4px rgba(60, 64, 67, .08);
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-card :deep(.el-textarea__inner) {
  box-shadow: none;
  border: 0;
  border-radius: 0;
  padding: 0;
  font-size: 15px;
  line-height: 1.6;
}
</style>
