<template>
  <div class="page-content">
    <PageHeader title="用户管理" description="管理内部账号、角色和账号状态">
      <template #actions>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </template>
    </PageHeader>

    <HifyTable
      :columns="columns"
      :api="loadUsers"
      :default-page-size="10"
      empty-text="暂无用户"
      ref="tableRef"
    >
      <template #role="{ row }">
        <el-tag>{{ row.role }}</el-tag>
      </template>
      <template #status="{ row }">
        <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
      </template>
      <template #actions="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link type="primary" @click="openPassword(row)">重置密码</el-button>
        <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
      </template>
    </HifyTable>

    <HifyFormDialog
      v-model="dialogVisible"
      :title="editingId ? '编辑用户' : '新增用户'"
      :model="form"
      :rules="rules"
      :loading="submitting"
      @submit="handleSubmit"
    >
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" :disabled="Boolean(editingId)" />
      </el-form-item>
      <el-form-item label="显示名" prop="displayName">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item v-if="!editingId" label="初始密码" prop="password">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-select v-model="form.role" style="width: 100%">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="EDITOR" value="EDITOR" />
          <el-option label="VIEWER" value="VIEWER" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="editingId" label="状态" prop="status">
        <el-select v-model="form.status" style="width: 100%">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="DISABLED" value="DISABLED" />
        </el-select>
      </el-form-item>
    </HifyFormDialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="420px">
      <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码" />
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleResetPassword">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormRules } from 'element-plus'
import HifyTable, { type HifyColumn } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createUser,
  deleteUser,
  getUserList,
  resetUserPassword,
  updateUser,
  type UserInfo,
  type UserRole,
  type UserStatus,
} from '@/api/auth'
import { notifySuccess } from '@/utils/notify'

const submitting = ref(false)
const tableRef = ref<{ refresh: () => void }>()
const dialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const passwordUserId = ref<number | null>(null)
const newPassword = ref('')
const form = reactive({
  username: '',
  displayName: '',
  password: '',
  role: 'VIEWER' as UserRole,
  status: 'ACTIVE' as UserStatus,
})

const columns: HifyColumn[] = [
  { prop: 'username', label: '用户名' },
  { prop: 'displayName', label: '显示名' },
  { prop: 'role', label: '角色', slot: 'role', width: 120 },
  { prop: 'status', label: '状态', slot: 'status', width: 120 },
  { prop: 'lastLoginAt', label: '最近登录', width: 180 },
  { prop: 'actions', label: '操作', slot: 'actions', width: 220, fixed: 'right' },
]

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const loadUsers = (page: number, pageSize: number) => getUserList(page, pageSize)

function resetForm() {
  editingId.value = null
  form.username = ''
  form.displayName = ''
  form.password = ''
  form.role = 'VIEWER'
  form.status = 'ACTIVE'
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: UserInfo) {
  editingId.value = row.id
  form.username = row.username
  form.displayName = row.displayName
  form.password = ''
  form.role = row.role
  form.status = row.status
  dialogVisible.value = true
}

function openPassword(row: UserInfo) {
  passwordUserId.value = row.id
  newPassword.value = ''
  passwordDialogVisible.value = true
}

async function handleSubmit(done: (success?: boolean) => void) {
  submitting.value = true
  try {
    if (editingId.value) {
      await updateUser(editingId.value, {
        displayName: form.displayName.trim(),
        role: form.role,
        status: form.status,
      })
      notifySuccess('用户已更新')
    } else {
      await createUser({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        password: form.password,
        role: form.role,
      })
      notifySuccess('用户已创建')
    }
    tableRef.value?.refresh()
    done(true)
  } catch {
    done(false)
  } finally {
    submitting.value = false
  }
}

async function handleResetPassword() {
  if (!passwordUserId.value || !newPassword.value) {
    ElMessage.error('请输入新密码')
    return
  }
  submitting.value = true
  try {
    await resetUserPassword(passwordUserId.value, newPassword.value)
    passwordDialogVisible.value = false
    notifySuccess('密码已重置')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: UserInfo) {
  await deleteUser(row.id)
  notifySuccess('用户已删除')
  tableRef.value?.refresh()
}
</script>
