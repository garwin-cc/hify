<template>
  <div class="page-content">
    <PageHeader title="权限与审计" description="管理账号、身份源，并查看项目角色和资源授权边界">
      <template #actions>
        <el-button :loading="loading" @click="loadIdentityProviders">刷新身份源</el-button>
        <el-button type="primary" @click="openProviderDialog()">新增身份源</el-button>
      </template>
    </PageHeader>

    <div class="audit-grid">
      <section class="hify-card">
        <div class="hify-card__header">
          <span class="hify-card__title">项目角色模型</span>
        </div>
        <el-table :data="roleRows" size="small">
          <el-table-column prop="role" label="角色" width="140" />
          <el-table-column prop="scope" label="适用范围" width="160" />
          <el-table-column prop="permissions" label="权限说明" min-width="240" />
        </el-table>
      </section>

      <section class="hify-card">
        <div class="hify-card__header">
          <span class="hify-card__title">资源授权边界</span>
        </div>
        <div class="policy-list">
          <div v-for="item in policyRows" :key="item.title" class="policy-item">
            <strong>{{ item.title }}</strong>
            <span>{{ item.description }}</span>
          </div>
        </div>
      </section>
    </div>

    <section class="hify-card">
      <div class="hify-card__header">
        <span class="hify-card__title">身份源</span>
        <span class="hint-text">clientSecret 不回显，更新时留空表示不修改</span>
      </div>
      <el-table :data="identityProviders" size="small" v-loading="loading">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="issuerUrl" label="Issuer" min-width="180" />
        <el-table-column prop="ldapUrl" label="LDAP URL" min-width="180" />
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openProviderDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDeleteProvider(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && identityProviders.length === 0" description="暂无企业身份源" :image-size="80" />
    </section>

    <section class="hify-card">
      <div class="hify-card__header">
        <span class="hify-card__title">统一审计日志</span>
      </div>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="后端已在 auth/model/agent/workflow/mcp 等模块写入审计记录；当前仓库尚未暴露统一 audit-log 查询 Controller，本页保留入口并避免展示不完整数据。"
      />
    </section>

    <el-dialog v-model="providerDialogVisible" :title="editingProviderId ? '编辑身份源' : '新增身份源'" width="560px">
      <el-form :model="providerForm" label-width="110px">
        <el-form-item label="名称" required><el-input v-model="providerForm.name" /></el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="providerForm.type" style="width: 100%">
            <el-option label="OIDC" value="OIDC" />
            <el-option label="LDAP" value="LDAP" />
            <el-option label="SAML" value="SAML" />
          </el-select>
        </el-form-item>
        <el-form-item label="Issuer URL"><el-input v-model="providerForm.issuerUrl" /></el-form-item>
        <el-form-item label="Client ID"><el-input v-model="providerForm.clientId" /></el-form-item>
        <el-form-item label="Client Secret"><el-input v-model="providerForm.clientSecret" type="password" show-password /></el-form-item>
        <el-form-item label="LDAP URL"><el-input v-model="providerForm.ldapUrl" /></el-form-item>
        <el-form-item label="Base DN"><el-input v-model="providerForm.ldapBaseDn" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="providerEnabled" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="扩展配置"><el-input v-model="providerForm.configJson" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveProvider">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import {
  createIdentityProvider,
  deleteIdentityProvider,
  getIdentityProviders,
  updateIdentityProvider,
  type IdentityProvider,
} from '@/api/system'
import { notifySuccess } from '@/utils/notify'

const loading = ref(false)
const submitting = ref(false)
const identityProviders = ref<IdentityProvider[]>([])
const providerDialogVisible = ref(false)
const editingProviderId = ref<number | null>(null)
const providerEnabled = ref(true)
const providerForm = reactive({
  name: '',
  type: 'OIDC',
  issuerUrl: '',
  clientId: '',
  clientSecret: '',
  ldapUrl: '',
  ldapBaseDn: '',
  configJson: '',
})

const roleRows = [
  { role: 'Owner', scope: '项目', permissions: '项目内资源管理、成员管理、发布和回滚' },
  { role: 'Developer', scope: '项目', permissions: '创建和编辑 Agent、Workflow、知识库、工具绑定' },
  { role: 'Operator', scope: '项目', permissions: '运行、发布、查看日志、处理运行失败和人审任务' },
  { role: 'Reviewer', scope: '项目', permissions: '查看资源并处理人工审核任务' },
  { role: 'Viewer', scope: '项目', permissions: '只读访问已授权资源和运行结果' },
]

const policyRows = [
  { title: '应用', description: 'App 归属项目后，API Key 和调用日志按项目隔离。' },
  { title: '知识库', description: 'Agent 绑定知识库时必须校验项目权限，避免跨项目检索敏感内容。' },
  { title: '工作流', description: '发布、回滚、人审和运行记录都应带 projectId 和 traceId。' },
  { title: 'MCP 工具', description: '工具不能默认全局可用，绑定和调用需要项目授权和审计。' },
]

onMounted(loadIdentityProviders)

async function loadIdentityProviders() {
  loading.value = true
  try {
    identityProviders.value = await getIdentityProviders()
  } finally {
    loading.value = false
  }
}

function openProviderDialog(row?: IdentityProvider) {
  editingProviderId.value = row?.id ?? null
  providerForm.name = row?.name ?? ''
  providerForm.type = row?.type ?? 'OIDC'
  providerForm.issuerUrl = row?.issuerUrl ?? ''
  providerForm.clientId = row?.clientId ?? ''
  providerForm.clientSecret = ''
  providerForm.ldapUrl = row?.ldapUrl ?? ''
  providerForm.ldapBaseDn = row?.ldapBaseDn ?? ''
  providerForm.configJson = row?.configJson ?? ''
  providerEnabled.value = (row?.enabled ?? 1) === 1
  providerDialogVisible.value = true
}

async function handleSaveProvider() {
  if (!providerForm.name.trim()) {
    ElMessage.warning('请输入身份源名称')
    return
  }
  submitting.value = true
  try {
    const payload = {
      ...providerForm,
      name: providerForm.name.trim(),
      enabled: providerEnabled.value ? 1 : 0,
      clientSecret: providerForm.clientSecret || undefined,
    }
    if (editingProviderId.value) {
      await updateIdentityProvider(editingProviderId.value, payload)
    } else {
      await createIdentityProvider(payload)
    }
    providerDialogVisible.value = false
    await loadIdentityProviders()
    notifySuccess('身份源已保存')
  } finally {
    submitting.value = false
  }
}

async function handleDeleteProvider(id: number) {
  await deleteIdentityProvider(id)
  await loadIdentityProviders()
  notifySuccess('身份源已删除')
}
</script>

<style scoped>
.audit-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
  gap: 16px;
  margin-bottom: 16px;
}

.policy-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.policy-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-color-light);
}

.policy-item span,
.hint-text {
  color: var(--text-tertiary);
  font-size: var(--text-sm);
}

@media (max-width: 900px) {
  .audit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
