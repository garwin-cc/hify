<template>
  <div class="page-content">
    <PageHeader
      title="从模板创建工作流"
      description="选择当前环境中的模型、知识库或 MCP 工具后生成真实工作流"
    >
      <template #actions>
        <el-button @click="router.push('/workflow-templates')">
          <el-icon style="margin-right: 4px"><ArrowLeft /></el-icon>
          返回模板
        </el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="create-layout">
      <section class="hify-card form-card">
        <el-form label-position="top">
          <el-form-item label="工作流名称" required>
            <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="请输入工作流名称" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" maxlength="500" show-word-limit type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.enabled" style="width: 180px">
              <el-option label="DRAFT" :value="0" />
              <el-option label="PUBLISHED" :value="1" />
            </el-select>
          </el-form-item>

          <div v-if="requirements.length" class="requirement-section">
            <div class="section-title">资源绑定</div>
            <el-form-item
              v-for="requirement in requirements"
              :key="requirement.key"
              :label="requirement.label"
              :required="requirement.required"
            >
              <el-select
                v-model="bindings[requirement.key]"
                clearable
                filterable
                :placeholder="placeholderOf(requirement.type)"
                style="width: 100%"
              >
                <el-option
                  v-for="option in optionsOf(requirement.type)"
                  :key="option.id"
                  :label="option.label"
                  :value="option.id"
                />
              </el-select>
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button @click="router.push('/workflow-templates')">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="handleCreate">
              创建工作流
            </el-button>
          </div>
        </el-form>
      </section>

      <aside class="hify-card template-preview">
        <div class="preview-head">
          <div>
            <h3>{{ template?.name || '-' }}</h3>
            <p>{{ template?.description || '暂无描述' }}</p>
          </div>
          <el-tag>{{ template?.category || '通用' }}</el-tag>
        </div>
        <div class="preview-meta">
          <span>{{ template?.nodeCount ?? 0 }} 个节点</span>
          <span>v{{ template?.latestVersionNo || 1 }}</span>
          <span v-if="template?.builtin === 1">内置模板</span>
        </div>
        <div class="node-preview">
          <div
            v-for="node in previewNodes"
            :key="node.nodeKey"
            class="node-preview__item"
          >
            <el-tag size="small" effect="plain">{{ node.nodeType }}</el-tag>
            <div>
              <strong>{{ node.name }}</strong>
              <small>{{ node.nodeKey }}</small>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createWorkflowFromTemplate,
  getWorkflowTemplateDetail,
  type WorkflowTemplateDetail,
  type WorkflowTemplateRequirement,
} from '@/api/workflow'
import { getEnabledModelConfigs } from '@/api/provider'
import { getKnowledgeBaseList } from '@/api/knowledge'
import { getMcpToolOptions } from '@/api/mcp'
import { notifySuccess } from '@/utils/notify'

type RequirementType = WorkflowTemplateRequirement['type']
type SelectOption = { id: number; label: string }

const route = useRoute()
const router = useRouter()
const templateId = computed(() => Number(route.params.id))
const loading = ref(false)
const submitting = ref(false)
const template = ref<WorkflowTemplateDetail | null>(null)
const modelOptions = ref<SelectOption[]>([])
const knowledgeOptions = ref<SelectOption[]>([])
const toolOptions = ref<SelectOption[]>([])
const bindings = reactive<Record<string, number | undefined>>({})
const form = reactive({
  name: '',
  description: '',
  enabled: 0,
})

const requirements = computed(() => template.value?.requirements ?? [])
const previewNodes = computed(() => {
  const nodes = template.value?.configJson?.nodes
  return Array.isArray(nodes) ? nodes as Array<{ nodeKey: string; nodeType: string; name: string }> : []
})

async function loadPage() {
  loading.value = true
  try {
    const [detail, models, knowledgePage, tools] = await Promise.all([
      getWorkflowTemplateDetail(templateId.value),
      getEnabledModelConfigs('CHAT'),
      getKnowledgeBaseList(1, 100),
      getMcpToolOptions().catch(() => []),
    ])
    template.value = detail
    form.name = detail.name
    form.description = detail.description
    modelOptions.value = models.map((model) => ({
      id: model.id,
      label: `${model.name}（${model.modelId}）`,
    }))
    knowledgeOptions.value = knowledgePage.records
      .filter((item) => item.enabled === 1)
      .map((item) => ({ id: item.id, label: item.name }))
    toolOptions.value = tools.map((tool) => ({
      id: tool.id,
      label: `${tool.serverName} / ${tool.name}`,
    }))
  } catch {
    router.push('/workflow-templates')
  } finally {
    loading.value = false
  }
}

function optionsOf(type: RequirementType) {
  if (type === 'MODEL') return modelOptions.value
  if (type === 'KNOWLEDGE_BASE') return knowledgeOptions.value
  if (type === 'TOOL') return toolOptions.value
  return []
}

function placeholderOf(type: RequirementType) {
  if (type === 'MODEL') return '请选择模型'
  if (type === 'KNOWLEDGE_BASE') return '请选择知识库'
  if (type === 'TOOL') return '请选择 MCP 工具'
  return '请选择资源'
}

function validateForm() {
  if (!form.name.trim()) {
    ElMessage.error('工作流名称不能为空')
    return false
  }
  const missing = requirements.value.find((item) => item.required && !bindings[item.key])
  if (missing) {
    ElMessage.error(`请选择${missing.label}`)
    return false
  }
  return true
}

async function handleCreate() {
  if (!validateForm()) return
  submitting.value = true
  try {
    const workflow = await createWorkflowFromTemplate(templateId.value, {
      name: form.name.trim(),
      description: form.description.trim(),
      enabled: form.enabled,
      bindings: Object.fromEntries(
        Object.entries(bindings).filter(([, value]) => value != null),
      ) as Record<string, number>,
    })
    notifySuccess('工作流已创建')
    router.push(`/workflows/${workflow.id}/edit`)
  } catch {
    // request interceptor has shown the error message
  } finally {
    submitting.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped>
.create-layout {
  display: grid;
  grid-template-columns: minmax(380px, 520px) minmax(0, 1fr);
  gap: 16px;
}

.form-card,
.template-preview {
  padding: 18px;
}

.requirement-section {
  padding-top: 14px;
  margin-top: 10px;
  border-top: 1px solid var(--border-light);
}

.section-title {
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 12px;
}

.preview-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.preview-head h3 {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-size: 18px;
}

.preview-head p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.preview-meta {
  display: flex;
  gap: 14px;
  margin: 14px 0;
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

.node-preview {
  display: grid;
  gap: 10px;
}

.node-preview__item {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 52px;
  padding: 10px 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
}

.node-preview__item div {
  display: grid;
  gap: 4px;
}

.node-preview__item strong {
  color: var(--text-primary);
  font-size: var(--text-sm);
}

.node-preview__item small {
  color: var(--text-tertiary);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .create-layout {
    grid-template-columns: 1fr;
  }
}
</style>
