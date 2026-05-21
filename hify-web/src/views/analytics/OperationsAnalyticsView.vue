<template>
  <div class="page-content">
    <PageHeader title="运营分析" description="查看 Agent、RAG、Workflow、MCP 和模型调用的轻量运营指标">
      <template #actions>
        <div class="analytics-actions">
          <el-select v-model="selectedProjectId" placeholder="全部项目" clearable class="project-select">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
          <el-date-picker
            v-model="range"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="range-picker"
          />
          <el-button :loading="loading" @click="loadOverview">刷新</el-button>
        </div>
      </template>
    </PageHeader>

    <div class="page-stack">
      <div class="page-grid page-grid--metrics">
        <section v-for="metric in metrics" :key="metric.key" class="hify-card metric-card">
          <span class="metric-card__label">{{ metric.label }}</span>
          <strong class="metric-card__value">{{ metric.value }}</strong>
          <span class="metric-card__hint">{{ metric.hint }}</span>
        </section>
      </div>

      <section class="hify-card">
        <div class="hify-card__header">
          <span class="hify-card__title">问题诊断</span>
        </div>
        <div v-if="overview.diagnostics.length" class="diagnostic-list">
          <article v-for="issue in overview.diagnostics" :key="issue.type" class="diagnostic-item">
            <div class="diagnostic-item__main">
              <div class="diagnostic-item__title-row">
                <el-tag :type="severityTagType(issue.severity)" effect="light" size="small">
                  {{ severityLabel(issue.severity) }}
                </el-tag>
                <strong>{{ issue.title }}</strong>
              </div>
              <p>{{ issue.description }}</p>
              <span>{{ issue.recommendation }}</span>
            </div>
            <div class="diagnostic-item__meta">
              <strong>{{ formatNumber(issue.impactCount) }}</strong>
              <span>{{ issue.primarySignal || formatRate(issue.rate) }}</span>
              <small v-if="issue.traceId">traceId: {{ issue.traceId }}</small>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无诊断问题" :image-size="80" />
      </section>

      <div class="page-grid page-grid--2">
        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">Agent 使用排行</span>
          </div>
          <el-table :data="overview.agents" size="small" v-loading="loading" empty-text="暂无 Agent 使用数据">
            <el-table-column prop="agentName" label="Agent" min-width="160" />
            <el-table-column prop="conversationCount" label="对话" width="90" />
            <el-table-column prop="totalTokens" label="Tokens" width="110">
              <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="failureRate" label="失败率" width="120">
              <template #default="{ row }">
                <el-progress :percentage="percent(row.failureRate)" :stroke-width="8" :show-text="false" />
                <span class="rate-text">{{ formatRate(row.failureRate) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">模型与 Token</span>
          </div>
          <el-table :data="overview.models" size="small" v-loading="loading" empty-text="暂无模型调用数据">
            <el-table-column prop="modelId" label="模型" min-width="150" />
            <el-table-column prop="providerType" label="Provider" width="110" />
            <el-table-column prop="callCount" label="调用" width="80" />
            <el-table-column prop="totalTokens" label="Tokens" width="110">
              <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="avgLatencyMs" label="平均耗时" width="110">
              <template #default="{ row }">{{ Math.round(row.avgLatencyMs) }}ms</template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <div class="page-grid page-grid--2">
        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">Workflow 成功率</span>
          </div>
          <el-table :data="overview.workflows" size="small" v-loading="loading" empty-text="暂无 Workflow 运行数据">
            <el-table-column prop="workflowName" label="工作流" min-width="160" />
            <el-table-column prop="runCount" label="运行" width="80" />
            <el-table-column prop="successCount" label="成功" width="80" />
            <el-table-column prop="failureCount" label="失败" width="80" />
            <el-table-column prop="successRate" label="成功率" width="120">
              <template #default="{ row }">{{ formatRate(row.successRate) }}</template>
            </el-table-column>
          </el-table>
        </section>

        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">MCP 失败率</span>
          </div>
          <el-table :data="overview.mcpTools" size="small" v-loading="loading" empty-text="暂无 MCP 调用数据">
            <el-table-column prop="toolName" label="工具" min-width="160" />
            <el-table-column prop="callCount" label="调用" width="80" />
            <el-table-column prop="failureCount" label="失败" width="80" />
            <el-table-column prop="failureRate" label="失败率" width="120">
              <template #default="{ row }">{{ formatRate(row.failureRate) }}</template>
            </el-table-column>
            <el-table-column prop="lastErrorSummary" label="最近错误" min-width="150" />
          </el-table>
        </section>
      </div>

      <section class="hify-card">
        <div class="hify-card__header">
          <span class="hify-card__title">错误原因排行</span>
        </div>
        <el-table :data="overview.errors" size="small" v-loading="loading" empty-text="暂无失败记录">
          <el-table-column prop="sourceType" label="来源" width="140" />
          <el-table-column prop="errorCode" label="错误码" width="180" />
          <el-table-column prop="errorMessage" label="错误摘要" min-width="220" />
          <el-table-column prop="count" label="次数" width="90" />
          <el-table-column prop="lastSeenAt" label="最近出现" width="180" />
        </el-table>
      </section>

      <div class="page-grid page-grid--2">
        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">慢 LLM 调用</span>
          </div>
          <el-table :data="overview.slowLlmCalls" size="small" v-loading="loading" empty-text="暂无慢调用数据">
            <el-table-column prop="traceId" label="traceId" min-width="180" />
            <el-table-column prop="modelId" label="模型" min-width="140" />
            <el-table-column prop="latencyMs" label="耗时" width="100">
              <template #default="{ row }">{{ row.latencyMs }}ms</template>
            </el-table-column>
            <el-table-column prop="totalTokens" label="Tokens" width="100">
              <template #default="{ row }">{{ formatNumber(row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="errorCode" label="错误码" min-width="120" />
          </el-table>
        </section>

        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">风险对话</span>
          </div>
          <el-table :data="overview.riskConversations" size="small" v-loading="loading" empty-text="暂无风险对话">
            <el-table-column prop="traceId" label="traceId" min-width="180" />
            <el-table-column prop="agentName" label="Agent" min-width="130" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="ragHit" label="RAG" width="100">
              <template #default="{ row }">{{ row.ragTriggered ? (row.ragHit ? '命中' : '未命中') : '-' }}</template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误摘要" min-width="160" />
          </el-table>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  getOperationsAnalyticsOverview,
  type OperationsAnalyticsOverview,
  type DiagnosticIssue,
} from '@/api/analytics'
import { getProjects, type Project } from '@/api/project'

const loading = ref(false)
const projects = ref<Project[]>([])
const selectedProjectId = ref<number | undefined>()
const range = ref<[string, string]>(defaultRange())

const emptyOverview = (): OperationsAnalyticsOverview => ({
  summary: {
    conversationCount: 0,
    failedConversationCount: 0,
    conversationFailureRate: 0,
    totalTokens: 0,
    ragTriggeredCount: 0,
    ragHitCount: 0,
    ragHitRate: 0,
    workflowRunCount: 0,
    workflowSuccessCount: 0,
    workflowSuccessRate: 0,
    mcpCallCount: 0,
    mcpFailureCount: 0,
    mcpFailureRate: 0,
  },
  agents: [],
  models: [],
  workflows: [],
  mcpTools: [],
  errors: [],
  slowLlmCalls: [],
  riskConversations: [],
  diagnostics: [],
})

const overview = reactive<OperationsAnalyticsOverview>(emptyOverview())

const metrics = computed(() => [
  {
    key: 'conversation',
    label: 'Agent 对话',
    value: formatNumber(overview.summary.conversationCount),
    hint: `失败 ${overview.summary.failedConversationCount} · ${formatRate(overview.summary.conversationFailureRate)}`,
  },
  {
    key: 'tokens',
    label: 'Token 消耗',
    value: formatNumber(overview.summary.totalTokens),
    hint: '输入和输出 token 合计',
  },
  {
    key: 'rag',
    label: 'RAG 命中率',
    value: formatRate(overview.summary.ragHitRate),
    hint: `${overview.summary.ragHitCount}/${overview.summary.ragTriggeredCount} 次触发命中`,
  },
  {
    key: 'workflow',
    label: 'Workflow 成功率',
    value: formatRate(overview.summary.workflowSuccessRate),
    hint: `${overview.summary.workflowSuccessCount}/${overview.summary.workflowRunCount} 次终态运行成功`,
  },
  {
    key: 'mcp',
    label: 'MCP 失败率',
    value: formatRate(overview.summary.mcpFailureRate),
    hint: `${overview.summary.mcpFailureCount}/${overview.summary.mcpCallCount} 次调用失败`,
  },
])

onMounted(async () => {
  projects.value = await getProjects().catch(() => [])
  await loadOverview()
})

async function loadOverview() {
  loading.value = true
  try {
    const data = await getOperationsAnalyticsOverview({
      projectId: selectedProjectId.value,
      from: range.value?.[0],
      to: range.value?.[1],
    })
    Object.assign(overview, data)
  } finally {
    loading.value = false
  }
}

function defaultRange(): [string, string] {
  const end = new Date()
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)
  return [toLocalIso(start), toLocalIso(end)]
}

function toLocalIso(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

function formatRate(value: number) {
  return `${((value || 0) * 100).toFixed(2)}%`
}

function percent(value: number) {
  return Math.round((value || 0) * 100)
}

function severityLabel(severity: DiagnosticIssue['severity']) {
  const labels: Record<DiagnosticIssue['severity'], string> = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
  }
  return labels[severity] || '低'
}

function severityTagType(severity: DiagnosticIssue['severity']) {
  if (severity === 'HIGH') {
    return 'danger'
  }
  if (severity === 'MEDIUM') {
    return 'warning'
  }
  return 'info'
}
</script>

<style scoped>
.analytics-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  max-width: 100%;
}

.project-select {
  width: 180px;
  flex: 0 0 180px;
}

.range-picker {
  width: 360px;
  flex: 0 0 360px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 116px;
  min-width: 0;
}

.metric-card__label {
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.metric-card__value {
  font-size: 28px;
  line-height: 1.1;
  color: var(--text-primary);
  font-weight: var(--font-semibold);
}

.metric-card__hint {
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.rate-text {
  display: inline-block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: var(--text-xs);
}

.diagnostic-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.diagnostic-item {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: var(--space-4);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
}

.diagnostic-item__main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
}

.diagnostic-item__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--text-primary);
}

.diagnostic-item__title-row strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diagnostic-item__main p,
.diagnostic-item__main span {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.diagnostic-item__meta {
  display: flex;
  width: 180px;
  flex: 0 0 180px;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
  text-align: right;
  color: var(--text-secondary);
}

.diagnostic-item__meta strong {
  color: var(--text-primary);
  font-size: 24px;
  line-height: 1;
}

.diagnostic-item__meta span,
.diagnostic-item__meta small {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1180px) {
}

@media (max-width: 720px) {
  .analytics-actions {
    align-items: stretch;
    flex-direction: column;
    width: 100%;
  }

  .range-picker,
  .project-select {
    width: 100%;
    flex-basis: auto;
  }

  .diagnostic-list {
    grid-template-columns: 1fr;
  }

  .diagnostic-item {
    flex-direction: column;
  }

  .diagnostic-item__meta {
    width: 100%;
    flex-basis: auto;
    align-items: flex-start;
    text-align: left;
  }
}
</style>
