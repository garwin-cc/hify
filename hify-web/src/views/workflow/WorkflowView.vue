<template>
  <div class="page-content">
    <PageHeader
      title="工作流管理"
      description="管理可被 Agent 绑定触发的工作流定义"
    >
      <template #actions>
        <el-button @click="$router.push('/workflow-templates')">
          从模板创建
        </el-button>
        <el-button type="primary" tag="a" href="/workflows/create">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          新建工作流
        </el-button>
      </template>
    </PageHeader>

    <div class="hify-card hify-card--flush">
      <HifyTable
        ref="tableRef"
        :columns="columns"
        :api="fetchList"
        :row-style="{ height: '56px' }"
        empty-text="暂无工作流，点击「新建工作流」开始创建"
      >
        <template #name="{ row }">
          <span class="workflow-name">{{ row.name }}</span>
        </template>

        <template #status="{ row }">
          <el-tag size="small" :type="workflowStatusOf(row) === 'PUBLISHED' ? 'success' : 'info'">
            {{ workflowStatusOf(row) }}
          </el-tag>
        </template>

        <template #createdAt="{ row }">
          <span class="date-text">{{ row.createdAt?.slice(0, 10) }}</span>
        </template>

        <template #actions="{ row }">
          <el-button size="small" tag="a" :href="`/workflows/${row.id}/edit`">编辑</el-button>
          <el-button size="small" type="danger" text @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useConfirm } from '@/composables/useConfirm'
import { useProjectStore } from '@/stores/project'
import {
  deleteWorkflow,
  getWorkflowList,
  workflowStatusOf,
  type WorkflowListItem,
} from '@/api/workflow'

const { isNarrow } = useBreakpoint()
const { confirm } = useConfirm()
const projectStore = useProjectStore()

const tableRef = ref<{ refresh: () => void; load: () => void }>()

const columns = computed<HifyColumn[]>(() => [
  { label: '名称', slot: 'name', minWidth: '220' },
  { label: '状态', slot: 'status', width: '130' },
  ...(!isNarrow.value ? [
    { label: '创建时间', slot: 'createdAt', width: '140' } as HifyColumn,
  ] : []),
  { label: '操作', slot: 'actions', width: '160', align: 'right' },
])

function fetchList(page: number, pageSize: number) {
  return getWorkflowList(page, pageSize, projectStore.currentProjectId)
}

async function handleDelete(row: WorkflowListItem) {
  const deleted = await confirm(
    `确定删除工作流「${row.name}」？`,
    () => deleteWorkflow(row.id),
    { successMsg: '工作流已删除' },
  )
  if (deleted) tableRef.value?.refresh()
}
</script>

<style scoped>
.workflow-name {
  font-weight: var(--font-semibold);
  color: var(--text-primary);
}

.date-text {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

</style>
