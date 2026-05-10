import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/providers' },
    {
      path: '/providers',
      name: 'Providers',
      component: () => import('@/views/provider/ProviderList.vue'),
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
    },
  ],
})

export default router
