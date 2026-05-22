<template>
  <div class="page-content">
    <PageHeader title="质量评估" description="查看 Agent 负反馈、RAG 有用率和待处理质量样本">
      <template #actions>
        <div class="quality-actions">
          <el-select v-model="selectedProjectId" placeholder="全部项目" clearable class="project-select">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
          <el-select v-model="selectedStatus" placeholder="处理状态" clearable class="status-select">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="REVIEWING" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
          <el-button :loading="loading" @click="loadData">刷新</el-button>
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

      <div class="page-grid page-grid--2">
        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">Agent 质量排行</span>
          </div>
          <el-table :data="overview.agents" size="small" v-loading="loading" empty-text="暂无 Agent 质量数据">
            <el-table-column prop="agentName" label="Agent" min-width="160" />
            <el-table-column prop="feedbackCount" label="反馈" width="90" />
            <el-table-column prop="negativeFeedbackCount" label="负反馈" width="90" />
            <el-table-column prop="negativeFeedbackRate" label="负反馈率" width="120">
              <template #default="{ row }">{{ formatRate(row.negativeFeedbackRate) }}</template>
            </el-table-column>
            <el-table-column prop="openSampleCount" label="待处理" width="90" />
          </el-table>
        </section>

        <section class="hify-card">
          <div class="hify-card__header">
            <span class="hify-card__title">主要问题原因</span>
          </div>
          <el-table :data="overview.issues" size="small" v-loading="loading" empty-text="暂无问题原因">
            <el-table-column prop="issueType" label="原因" min-width="160">
              <template #default="{ row }">{{ issueLabel(row.issueType) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="次数" width="90" />
            <el-table-column prop="rate" label="占比" width="110">
              <template #default="{ row }">{{ formatRate(row.rate) }}</template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <section class="hify-card">
        <div class="hify-card__header">
          <span class="hify-card__title">质量样本池</span>
        </div>
        <el-table :data="samples" size="small" v-loading="loading" empty-text="暂无质量样本">
          <el-table-column prop="agentName" label="Agent" min-width="130" />
          <el-table-column prop="issueType" label="问题" width="130">
            <template #default="{ row }">{{ issueLabel(row.issueType) }}</template>
          </el-table-column>
          <el-table-column prop="userQuestion" label="用户问题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="assistantAnswer" label="回答摘要" min-width="220" show-overflow-tooltip />
          <el-table-column label="RAG" width="100">
            <template #default="{ row }">{{ row.ragTriggered ? (row.ragHit ? '命中' : '未命中') : '-' }}</template>
          </el-table-column>
          <el-table-column prop="reviewStatus" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.reviewStatus)" size="small" effect="light">
                {{ statusLabel(row.reviewStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="traceId" label="traceId" min-width="150" show-overflow-tooltip />
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="markSample(row, 'REVIEWING')">处理中</el-button>
              <el-button link type="success" @click="markSample(row, 'RESOLVED')">解决</el-button>
              <el-button link type="info" @click="markSample(row, 'IGNORED')">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getProjects, type Project } from '@/api/project'
import {
  getQualityOverview,
  getQualitySamples,
  updateQualitySampleStatus,
  type QualityOverview,
  type QualitySample,
} from '@/api/quality'

const loading = ref(false)
const projects = ref<Project[]>([])
const selectedProjectId = ref<number | undefined>()
const selectedStatus = ref<string>('OPEN')
const samples = ref<QualitySample[]>([])

const emptyOverview = (): QualityOverview => ({
  summary: {
    feedbackCount: 0,
    negativeFeedbackCount: 0,
    negativeFeedbackRate: 0,
    openSampleCount: 0,
    ragFeedbackCount: 0,
    ragHelpfulCount: 0,
    ragHelpfulRate: 0,
  },
  agents: [],
  issues: [],
})

const overview = reactive<QualityOverview>(emptyOverview())

const metrics = computed(() => [
  {
    key: 'feedback',
    label: '反馈总数',
    value: formatNumber(overview.summary.feedbackCount),
    hint: `负反馈 ${overview.summary.negativeFeedbackCount}`,
  },
  {
    key: 'negative',
    label: '负反馈率',
    value: formatRate(overview.summary.negativeFeedbackRate),
    hint: 'DISLIKE / 总反馈',
  },
  {
    key: 'open',
    label: '待处理样本',
    value: formatNumber(overview.summary.openSampleCount),
    hint: 'OPEN 和 REVIEWING',
  },
  {
    key: 'rag',
    label: 'RAG 有用率',
    value: formatRate(overview.summary.ragHelpfulRate),
    hint: `${overview.summary.ragHelpfulCount}/${overview.summary.ragFeedbackCount} 次反馈认为有用`,
  },
])

onMounted(async () => {
  projects.value = await getProjects().catch(() => [])
  await loadData()
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      projectId: selectedProjectId.value,
      reviewStatus: selectedStatus.value || undefined,
    }
    const [overviewData, sampleData] = await Promise.all([
      getQualityOverview({ projectId: selectedProjectId.value }),
      getQualitySamples(params),
    ])
    Object.assign(overview, overviewData)
    samples.value = sampleData
  } finally {
    loading.value = false
  }
}

async function markSample(sample: QualitySample, status: string) {
  const { value } = await ElMessageBox.prompt('处理备注', statusLabel(status as QualitySample['reviewStatus']), {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputValue: sample.resolutionNote || '',
  }).catch(() => ({ value: undefined }))
  if (value === undefined) return
  await updateQualitySampleStatus(sample.id, { reviewStatus: status, resolutionNote: value })
  await loadData()
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

function formatRate(value: number) {
  return `${((value || 0) * 100).toFixed(2)}%`
}

function issueLabel(value?: string) {
  const labels: Record<string, string> = {
    WRONG_ANSWER: '答非所问',
    FACT_ERROR: '事实错误',
    RAG_MISS: '知识库未命中',
    INCOMPLETE: '回答不完整',
    TOOL_FAILED: '工具调用失败',
    BAD_FORMAT: '格式不符合预期',
    OTHER: '其他',
  }
  return labels[value || 'OTHER'] || value || '其他'
}

function statusLabel(value: QualitySample['reviewStatus'] | string) {
  const labels: Record<string, string> = {
    OPEN: '待处理',
    REVIEWING: '处理中',
    RESOLVED: '已解决',
    IGNORED: '已忽略',
  }
  return labels[value] || value
}

function statusTagType(value: QualitySample['reviewStatus']) {
  if (value === 'OPEN') return 'danger'
  if (value === 'REVIEWING') return 'warning'
  if (value === 'RESOLVED') return 'success'
  return 'info'
}
</script>

<style scoped>
.quality-actions {
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

.status-select {
  width: 140px;
  flex: 0 0 140px;
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

@media (max-width: 720px) {
  .quality-actions {
    align-items: stretch;
    flex-direction: column;
    width: 100%;
  }

  .project-select,
  .status-select {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
