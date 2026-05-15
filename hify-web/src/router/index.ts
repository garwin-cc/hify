import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/conversation' },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/providers',
      name: 'Providers',
      component: () => import('@/views/provider/ProviderList.vue'),
      meta: { roles: ['ADMIN'] },
    },
    {
      path: '/apps',
      name: 'Apps',
      component: () => import('@/views/app/AppPublishView.vue'),
      meta: { roles: ['ADMIN', 'EDITOR'] },
    },
    {
      path: '/agents',
      name: 'Agents',
      component: () => import('@/views/agent/AgentList.vue'),
    },
    {
      path: '/conversation',
      name: 'Conversation',
      component: () => import('@/views/conversation/ConversationView.vue'),
    },
    {
      path: '/knowledge',
      redirect: '/knowledge-bases',
    },
    {
      path: '/knowledge-bases',
      name: 'KnowledgeBases',
      component: () => import('@/views/knowledge/KnowledgeView.vue'),
    },
    {
      path: '/knowledge-bases/:id/documents',
      name: 'KnowledgeDocuments',
      component: () => import('@/views/knowledge/KnowledgeDocumentsView.vue'),
    },
    {
      path: '/workflow',
      redirect: '/workflows',
    },
    {
      path: '/workflows',
      name: 'Workflows',
      component: () => import('@/views/workflow/WorkflowView.vue'),
    },
    {
      path: '/workflows/create',
      name: 'WorkflowCreate',
      component: () => import('@/views/workflow/WorkflowCreateView.vue'),
    },
    {
      path: '/workflow-templates',
      name: 'WorkflowTemplates',
      component: () => import('@/views/workflow/WorkflowTemplateView.vue'),
    },
    {
      path: '/workflow-templates/:id/create',
      name: 'WorkflowTemplateCreate',
      component: () => import('@/views/workflow/WorkflowTemplateCreateView.vue'),
    },
    {
      path: '/workflows/:id/edit',
      name: 'WorkflowEdit',
      component: () => import('@/views/workflow/WorkflowCreateView.vue'),
    },
    {
      path: '/mcp',
      name: 'McpTools',
      component: () => import('@/views/mcp/McpView.vue'),
      meta: { roles: ['ADMIN', 'EDITOR'] },
    },
    {
      path: '/logs',
      name: 'Logs',
      component: () => import('@/views/logs/LogCenterView.vue'),
      meta: { roles: ['ADMIN', 'EDITOR'] },
    },
    {
      path: '/audit',
      name: 'Audit',
      component: () => import('@/views/audit/AuditView.vue'),
      meta: { roles: ['ADMIN'] },
    },
    {
      path: '/settings',
      name: 'SystemSettings',
      component: () => import('@/views/settings/SystemSettingsView.vue'),
      meta: { roles: ['ADMIN'] },
    },
    {
      path: '/users',
      name: 'Users',
      component: () => import('@/views/auth/UserView.vue'),
      meta: { roles: ['ADMIN'] },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return true
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.loaded) {
    await auth.loadMe().catch(() => null)
  }
  const roles = to.meta.roles as string[] | undefined
  if (roles?.length && (!auth.role || !roles.includes(auth.role))) {
    return '/conversation'
  }
  return true
})

export default router
