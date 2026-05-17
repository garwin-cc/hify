<template>
  <div class="page-content">
    <PageHeader title="项目管理" description="按项目查看成员并维护项目内角色边界" />

    <el-card class="project-panel">
      <div class="project-panel__head">
        <div class="project-panel__select">
          <el-select
            v-model="selectedProjectId"
            placeholder="选择项目"
            :loading="projectStore.loading"
            style="width: 280px"
            @change="handleProjectChange"
          >
            <el-option
              v-for="project in projectStore.projects"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
          <el-button v-if="auth.isAdmin" type="primary" @click="openCreateDialog">新建项目</el-button>
        </div>
        <div class="project-panel__actions">
          <el-select
            v-model="newMember.userId"
            filterable
            clearable
            placeholder="选择用户"
            :loading="loadingUsers"
            style="width: 220px"
          >
            <el-option
              v-for="user in availableUsers"
              :key="user.id"
              :label="user.displayName ? `${user.displayName}（${user.username}）` : user.username"
              :value="user.id"
            />
          </el-select>
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

    <el-dialog v-model="createDialogVisible" title="新建项目" width="420px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model.trim="createForm.name" maxlength="50" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateProject">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { getUserList, type UserInfo } from '@/api/auth'
import {
  addProjectMember,
  createProject,
  getProjectMembers,
  removeProjectMember,
  updateProjectMemberRole,
  type ProjectMember,
  type ProjectRole,
} from '@/api/project'
import { useAuthStore } from '@/stores/auth'
import { useProjectStore } from '@/stores/project'
import { notifySuccess } from '@/utils/notify'

const projectRoles: ProjectRole[] = ['OWNER', 'DEVELOPER', 'OPERATOR', 'REVIEWER', 'VIEWER']
const auth = useAuthStore()
const projectStore = useProjectStore()
const selectedProjectId = ref<number | null>(projectStore.currentProjectId)
const members = ref<ProjectMember[]>([])
const users = ref<UserInfo[]>([])
const loadingMembers = ref(false)
const loadingUsers = ref(false)
const submitting = ref(false)
const creating = ref(false)
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const newMember = reactive({
  userId: null as number | null,
  role: 'VIEWER' as ProjectRole,
})
const createForm = reactive({
  name: '',
})
const createRules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}
const availableUsers = computed(() => {
  const memberUserIds = new Set(members.value.map((member) => member.userId))
  return users.value.filter((user) => user.status === 'ACTIVE' && !memberUserIds.has(user.id))
})

onMounted(async () => {
  await Promise.all([
    projectStore.loadProjects().catch(() => null),
    loadUsers(),
  ])
  if (!selectedProjectId.value) {
    selectedProjectId.value = projectStore.currentProjectId
  }
  await loadMembers()
})

async function handleProjectChange(projectId: number) {
  projectStore.setCurrentProject(projectId)
  await loadMembers()
}

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

async function loadUsers() {
  loadingUsers.value = true
  try {
    const page = await getUserList(1, 200)
    users.value = page.records ?? []
  } finally {
    loadingUsers.value = false
  }
}

function openCreateDialog() {
  createForm.name = ''
  createDialogVisible.value = true
}

async function handleCreateProject() {
  await createFormRef.value?.validate()
  creating.value = true
  try {
    const project = await createProject({
      name: createForm.name,
      code: generateProjectCode(createForm.name),
    })
    notifySuccess('项目已创建')
    createDialogVisible.value = false
    await projectStore.loadProjects()
    selectedProjectId.value = project.id
    projectStore.setCurrentProject(project.id)
    await loadMembers()
  } finally {
    creating.value = false
  }
}

function generateProjectCode(name: string) {
  const normalized = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
  const prefix = normalized || 'project'
  return `${prefix.slice(0, 32)}-${Date.now().toString(36)}`.slice(0, 50)
}

async function handleAddMember() {
  if (!selectedProjectId.value || !newMember.userId) return
  submitting.value = true
  try {
    await addProjectMember(selectedProjectId.value, newMember.userId, newMember.role)
    notifySuccess('成员已添加')
    newMember.userId = null
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
.project-panel__select {
  display: flex;
  align-items: center;
  gap: 10px;
}
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
  .project-panel__select,
  .project-panel__head,
  .project-panel__actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
