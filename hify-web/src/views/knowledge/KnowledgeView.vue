<template>
  <div class="page-content">
    <PageHeader
      title="知识库管理"
      description="管理 RAG 知识库，维护文档数量、启用状态和基础信息"
    >
      <template #actions>
        <el-button type="primary" @click="handleCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          新建知识库
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card search-bar">
      <el-input
        v-model="filterName"
        placeholder="搜索知识库名称"
        clearable
        style="width: 280px"
        @keyup.enter="tableRef?.refresh()"
        @change="tableRef?.refresh()"
        @clear="tableRef?.refresh()"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :row-style="{ height: '56px' }"
        empty-text="暂无知识库，点击「新建知识库」开始创建"
      >
        <template #name="{ row }">
          <el-button link type="primary" class="name-link" @click="goDocuments(row)">
            {{ row.name }}
          </el-button>
        </template>

        <template #description="{ row }">
          <span v-if="row.description" class="description-text">{{ row.description }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #enabled="{ row }">
          <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #documentCount="{ row }">
          <span v-if="row.documentCount > 0" class="count-text">{{ row.documentCount }}</span>
          <span v-else class="no-data">–</span>
        </template>

        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <template #actions="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" text @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新建知识库' : '编辑知识库'"
      width="560px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        label-position="right"
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入知识库名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="描述知识库用途（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="向量模型" prop="embeddingModelConfigId">
          <el-select
            v-model="form.embeddingModelConfigId"
            placeholder="请选择向量模型"
            filterable
            :loading="loadingEmbeddingModels"
            :disabled="editingId !== null && editingDocumentCount > 0"
            style="width: 100%"
          >
            <el-option
              v-for="model in embeddingModels"
              :key="model.id"
              :label="`${model.name}（${model.modelId}）`"
              :value="model.id"
            />
          </el-select>
          <div v-if="editingId !== null && editingDocumentCount > 0" class="form-hint">
            已有文档的知识库不能切换向量模型，避免新旧分块向量维度不一致。
          </div>
          <div v-else-if="embeddingModels.length === 0" class="form-hint">
            请先在模型管理中把可用模型标记为“向量”。
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'
import { useBreakpoint } from '@/composables/useBreakpoint'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBaseList,
  updateKnowledgeBase,
  type KnowledgeBaseItem,
} from '@/api/knowledge'
import { getEnabledModelConfigs, type ModelConfig } from '@/api/provider'

const router = useRouter()
const { isNarrow } = useBreakpoint()

const columns = computed<HifyColumn[]>(() => [
  { label: '名称', slot: 'name', minWidth: '180' },
  ...(!isNarrow.value ? [
    { label: '描述', slot: 'description', minWidth: '260' } as HifyColumn,
  ] : []),
  { label: '状态', slot: 'enabled', width: '90' },
  { label: '文档数量', slot: 'documentCount', width: '110', align: 'center' },
  ...(!isNarrow.value ? [
    { label: '创建时间', slot: 'createdAt', width: '120' } as HifyColumn,
  ] : []),
  { label: '操作', slot: 'actions', width: '150', align: 'right' },
])

const tableRef = ref<{ refresh: () => void; load: () => void }>()
const filterName = ref('')

function fetchList(page: number, pageSize: number) {
  return getKnowledgeBaseList(page, pageSize, filterName.value.trim())
}

function goDocuments(row: KnowledgeBaseItem) {
  router.push(`/knowledge-bases/${row.id}/documents`)
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  description: '',
  embeddingModelConfigId: null as number | null,
})
const editingDocumentCount = ref(0)
const loadingEmbeddingModels = ref(false)
const embeddingModels = ref<ModelConfig[]>([])

const rules: FormRules = {
  name: [
    { required: true, message: '知识库名称不能为空', trigger: 'blur' },
    { min: 1, max: 100, message: '名称长度不能超过 100 个字符', trigger: 'blur' },
  ],
  embeddingModelConfigId: [
    { required: true, message: '请选择向量模型', trigger: 'change' },
  ],
}

onMounted(() => {
  loadEmbeddingModels()
})

async function loadEmbeddingModels() {
  loadingEmbeddingModels.value = true
  try {
    embeddingModels.value = await getEnabledModelConfigs('EMBEDDING')
  } catch {
    embeddingModels.value = []
  } finally {
    loadingEmbeddingModels.value = false
  }
}

function resetForm() {
  form.name = ''
  form.description = ''
  form.embeddingModelConfigId = embeddingModels.value[0]?.id ?? null
  editingDocumentCount.value = 0
}

function handleCreate() {
  resetForm()
  editingId.value = null
  dialogVisible.value = true
}

function handleEdit(row: KnowledgeBaseItem) {
  editingId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.embeddingModelConfigId = row.embeddingModelConfigId
  editingDocumentCount.value = row.documentCount
  dialogVisible.value = true
}

function handleDialogClosed() {
  formRef.value?.clearValidate()
  submitting.value = false
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (form.embeddingModelConfigId === null) return

  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      embeddingModelConfigId: form.embeddingModelConfigId,
    }
    if (editingId.value === null) {
      await createKnowledgeBase(payload)
      notifySuccess('知识库已创建')
    } else {
      await updateKnowledgeBase(editingId.value, payload)
      notifySuccess('知识库已更新')
    }
    dialogVisible.value = false
    tableRef.value?.refresh()
  } catch {
    // request interceptor has shown the error message
  } finally {
    submitting.value = false
  }
}

const { confirm } = useConfirm()

async function handleDelete(row: KnowledgeBaseItem) {
  const deleted = await confirm(
    `确定删除知识库「${row.name}」？关联文档和分块会一起删除。`,
    () => deleteKnowledgeBase(row.id),
    { successMsg: '知识库已删除' },
  )
  if (deleted) tableRef.value?.refresh()
}
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.name-link {
  padding: 0;
  font-weight: 600;
}

.description-text {
  display: inline-block;
  max-width: 520px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.count-text {
  font-family: var(--font-mono);
  color: var(--text-primary);
}

.date-text {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

.no-data {
  color: var(--text-tertiary);
}

.form-hint {
  margin-top: 6px;
  font-size: var(--text-xs);
  line-height: 1.4;
  color: var(--text-tertiary);
}
</style>
