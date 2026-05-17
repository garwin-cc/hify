<template>
  <div class="page-content">
    <PageHeader title="项目成员" description="按项目查看成员并维护项目内角色边界" />

    <el-card class="project-panel">
      <div class="project-panel__head">
        <el-select
          v-model="selectedProjectId"
          placeholder="选择项目"
          :loading="projectStore.loading"
          style="width: 280px"
          @change="loadMembers"
        >
          <el-option
            v-for="project in projectStore.projects"
            :key="project.id"
            :label="`${project.name}（${project.code}）`"
            :value="project.id"
          />
        </el-select>
        <div class="project-panel__actions">
          <el-input-number v-model="newMember.userId" :min="1" controls-position="right" placeholder="用户 ID" />
          <el-select v-model="newMember.role" style="width: 150px">
            <el-option v-for="role in projectRoles" :key="role" :label="role" :value="role" />
          </el-select>
          <el-button type="primary" :disabled="!selectedProjectId" :loading="submitting" @click="handleAddMember">
            添加成员
          </el-button>
        </div>
      </div>

      <el-table v-loading="loadingMembers" :data="members" empty-text="暂无成员">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名" min-width="140" />
        <el-table-column label="项目角色" width="180">
          <template #default="{ row }">
            <el-select v-model="row.role" size="small" @change="(role: ProjectRole) => handleRoleChange(row, role)">
              <el-option v-for="role in projectRoles" :key="role" :label="role" :value="role" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :loading="submitting" @click="handleRemove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  addProjectMember,
  getProjectMembers,
  removeProjectMember,
  updateProjectMemberRole,
  type ProjectMember,
  type ProjectRole,
} from '@/api/project'
import { useProjectStore } from '@/stores/project'
import { notifySuccess } from '@/utils/notify'

const projectRoles: ProjectRole[] = ['OWNER', 'DEVELOPER', 'OPERATOR', 'REVIEWER', 'VIEWER']
const projectStore = useProjectStore()
const selectedProjectId = ref<number | null>(projectStore.currentProjectId)
const members = ref<ProjectMember[]>([])
const loadingMembers = ref(false)
const submitting = ref(false)
const newMember = reactive({
  userId: 1,
  role: 'VIEWER' as ProjectRole,
})

onMounted(async () => {
  await projectStore.loadProjects().catch(() => null)
  if (!selectedProjectId.value) {
    selectedProjectId.value = projectStore.currentProjectId
  }
  await loadMembers()
})

async function loadMembers() {
  if (!selectedProjectId.value) {
    members.value = []
    return
  }
  loadingMembers.value = true
  try {
    members.value = await getProjectMembers(selectedProjectId.value)
  } finally {
    loadingMembers.value = false
  }
}

async function handleAddMember() {
  if (!selectedProjectId.value) return
  submitting.value = true
  try {
    await addProjectMember(selectedProjectId.value, newMember.userId, newMember.role)
    notifySuccess('成员已添加')
    await loadMembers()
  } finally {
    submitting.value = false
  }
}

async function handleRoleChange(row: ProjectMember, role: ProjectRole) {
  if (!selectedProjectId.value) return
  submitting.value = true
  try {
    await updateProjectMemberRole(selectedProjectId.value, row.userId, role)
    notifySuccess('角色已更新')
    await loadMembers()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: ProjectMember) {
  if (!selectedProjectId.value) return
  await ElMessageBox.confirm(`确认移除 ${row.username}？`, '移除成员', { type: 'warning' })
  submitting.value = true
  try {
    await removeProjectMember(selectedProjectId.value, row.userId)
    notifySuccess('成员已移除')
    await loadMembers()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.project-panel { border-radius: 8px; }
.project-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.project-panel__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
@media (max-width: 900px) {
  .project-panel__head,
  .project-panel__actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
