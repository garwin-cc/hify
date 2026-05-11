<template>
  <div class="page-content">
    <PageHeader
      title="工作流模板"
      description="从内置模板复制创建工作流，创建后可继续编辑节点配置"
    >
      <template #actions>
        <el-button @click="router.push('/workflows')">
          <el-icon style="margin-right: 4px"><ArrowLeft /></el-icon>
          返回工作流
        </el-button>
        <el-button type="primary" @click="router.push('/workflows/create')">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          空白创建
        </el-button>
      </template>
    </PageHeader>

    <div class="template-toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索模板名称"
        style="width: 260px"
        @keyup.enter="loadTemplates"
        @clear="loadTemplates"
      />
      <el-segmented v-model="category" :options="categoryOptions" @change="loadTemplates" />
      <el-button @click="loadTemplates">搜索</el-button>
    </div>

    <div v-loading="loading" class="template-grid">
      <el-empty v-if="templates.length === 0" description="暂无可用工作流模板" />
      <article
        v-for="template in templates"
        :key="template.id"
        class="template-card"
      >
        <div class="template-card__head">
          <div class="template-icon">{{ iconText(template.icon) }}</div>
          <el-tag size="small">{{ template.category || '通用' }}</el-tag>
        </div>
        <h3>{{ template.name }}</h3>
        <p>{{ template.description || '暂无描述' }}</p>
        <div v-if="template.tags?.length" class="template-card__tags">
          <el-tag v-for="tag in template.tags" :key="tag" size="small" type="info">
            {{ tag }}
          </el-tag>
        </div>
        <div class="template-card__meta">
          <span>{{ template.nodeCount }} 个节点</span>
          <span>v{{ template.latestVersionNo || 1 }}</span>
          <span>{{ template.usageCount || 0 }} 次使用</span>
          <span v-if="template.builtin === 1">内置模板</span>
        </div>
        <div class="template-card__actions">
          <el-button @click="exportTemplate(template)">
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            导出
          </el-button>
          <el-button type="primary" @click="useTemplate(template.id)">
            使用模板
          </el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Download, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  exportWorkflowTemplate,
  getWorkflowTemplateList,
  type WorkflowTemplateListItem,
} from '@/api/workflow'

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const category = ref('')
const templates = ref<WorkflowTemplateListItem[]>([])

const categoryOptions = [
  { label: '全部', value: '' },
  { label: '研发', value: '研发' },
  { label: '客服', value: '客服' },
  { label: '知识库', value: '知识库' },
  { label: '工具调用', value: '工具调用' },
]

function iconText(icon: string) {
  if (icon === 'service') return '客'
  if (icon === 'knowledge') return '知'
  if (icon === 'code') return '研'
  return '流'
}

async function loadTemplates() {
  loading.value = true
  try {
    const page = await getWorkflowTemplateList(1, 100, category.value, keyword.value.trim())
    templates.value = page.records
  } finally {
    loading.value = false
  }
}

function useTemplate(id: number) {
  router.push(`/workflow-templates/${id}/create`)
}

async function exportTemplate(template: WorkflowTemplateListItem) {
  const versionId = template.currentVersionId
  if (!versionId) return
  const resp = await exportWorkflowTemplate(template.id, versionId)
  const blob = new Blob([JSON.stringify(resp.templateJson, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = resp.filename
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(loadTemplates)
</script>

<style scoped>
.template-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 260px;
}

.template-card {
  display: flex;
  min-height: 230px;
  flex-direction: column;
  padding: 18px;
  background: #fff;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.template-card__head,
.template-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.template-icon {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: var(--primary-color);
  font-weight: var(--font-semibold);
  background: #eef3ff;
  border: 1px solid #d7e1ff;
  border-radius: 8px;
}

.template-card h3 {
  margin: 18px 0 8px;
  color: var(--text-primary);
  font-size: 18px;
}

.template-card p {
  flex: 1;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.template-card__meta {
  margin: 18px 0 14px;
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.template-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 24px;
  margin-top: 14px;
}

.template-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
