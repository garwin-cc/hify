import { get, post, put, del } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'
import { getProviderList } from '@/api/provider'

// ── Types ──────────────────────────────────────────────────────────────

export interface ModelOption {
  id: number
  name: string
  modelId: string
  modelType?: 'CHAT' | 'EMBEDDING'
  enabled: number
}

export interface ModelGroup {
  providerId: number
  providerName: string
  models: ModelOption[]
}

export interface AgentListItem {
  id: number
  name: string
  description: string
  modelConfigId: number
  modelName: string | null
  modelId: string | null
  workflowId?: number | null
  knowledgeBaseIds: number[]
  temperature: number | null
  memoryEnabled?: number
  toolCount: number
  enabled: number
  createdAt: string
  updatedAt: string
}

export interface AgentDetail {
  id: number
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number
  modelConfig: ModelOption | null
  workflowId?: number | null
  knowledgeBaseIds: number[]
  temperature: number | null
  maxTokens: number | null
  maxContextTurns: number | null
  memoryEnabled?: number
  summaryTriggerMessageCount?: number | null
  summaryMaxTokens?: number | null
  summaryModelConfigId?: number | null
  toolIds: number[]
  enabled: number
  createdAt: string
  updatedAt: string
}

export interface CreateAgentReq {
  name: string
  description?: string
  systemPrompt: string
  modelConfigId: number
  workflowId?: number | null
  knowledgeBaseIds?: number[]
  temperature?: number
  maxTokens?: number
  maxContextTurns?: number
  memoryEnabled?: number
  summaryTriggerMessageCount?: number
  summaryMaxTokens?: number
  summaryModelConfigId?: number | null
  toolIds?: number[]
}

export interface UpdateAgentReq {
  name?: string
  description?: string
  systemPrompt?: string
  modelConfigId?: number
  workflowId?: number | null
  bindWorkflow?: boolean
  knowledgeBaseIds?: number[]
  temperature?: number | null
  maxTokens?: number | null
  maxContextTurns?: number | null
  memoryEnabled?: number | null
  summaryTriggerMessageCount?: number | null
  summaryMaxTokens?: number | null
  summaryModelConfigId?: number | null
  toolIds?: number[] | null
}

export interface AgentQuery {
  name?: string
  enabled?: number
}

export interface AgentVersion {
  id: number
  agentId: number
  versionNo: number
  status: 'DRAFT' | 'TEST' | 'PUBLISHED' | 'ROLLED_BACK' | string
  name: string
  modelConfigId?: number
  workflowId?: number | null
  knowledgeBaseIds?: number[]
  toolIds?: number[]
  maxToolRounds?: number
  publishedAt?: string
  createdAt: string
}

export interface AgentPublishReq {
  description?: string
}

export interface AgentApp {
  id: number
  agentId: number
  publishedVersionId: number
  name: string
  description?: string
  webEnabled: number
  apiEnabled: number
  endpointPath: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface CreateAgentAppReq {
  publishedVersionId: number
  name: string
  description?: string
  webEnabled?: number
  apiEnabled?: number
  endpointPath?: string
}

export interface AgentApiKey {
  id: number
  agentAppId: number
  name: string
  keyPrefix: string
  status: string
  lastUsedAt?: string
  createdAt: string
}

export interface AgentApiKeyCreateResp extends AgentApiKey {
  apiKey: string
}

// ── API ────────────────────────────────────────────────────────────────

export const getModelGroups = async (): Promise<ModelGroup[]> => {
  const result = await getProviderList(1, 100)
  return result.records
    .filter(p => p.enabled === 1)
    .map(p => ({
      providerId: p.id,
      providerName: p.name,
      models: p.models
        .filter(m => m.enabled === 1 && (m.modelType ?? 'CHAT') === 'CHAT')
        .map(m => ({
          id: m.id,
          name: m.name,
          modelId: m.modelId,
          modelType: m.modelType,
          enabled: m.enabled,
        })),
    }))
    .filter(g => g.models.length > 0)
}

export const getAgentList = (
  page: number,
  pageSize: number,
  query: AgentQuery = {},
): Promise<PageData<AgentListItem>> =>
  get('/v1/agents', { page, pageSize, ...query })

export const getAgentDetail = (id: number): Promise<AgentDetail> =>
  get(`/v1/agents/${id}`)

export const createAgent = (data: CreateAgentReq): Promise<AgentDetail> =>
  post('/v1/agents', data)

export const updateAgent = (id: number, data: UpdateAgentReq): Promise<AgentDetail> =>
  put(`/v1/agents/${id}`, data)

export const deleteAgent = (id: number): Promise<void> =>
  del(`/v1/agents/${id}`)

export const toggleAgentEnabled = (id: number, enabled: number): Promise<AgentDetail> =>
  put(`/v1/agents/${id}/enabled/${enabled}`)

export const getAgentVersions = (id: number): Promise<AgentVersion[]> =>
  get(`/v1/agents/${id}/versions`)

export const publishAgentTestVersion = (
  id: number,
  data: AgentPublishReq,
): Promise<AgentVersion> =>
  post(`/v1/agents/${id}/versions/test`, data)

export const publishAgentVersion = (
  id: number,
  data: AgentPublishReq,
): Promise<AgentVersion> =>
  post(`/v1/agents/${id}/versions/publish`, data)

export const rollbackAgentVersion = (
  id: number,
  versionNo: number,
  reason?: string,
): Promise<AgentDetail> =>
  post(`/v1/agents/${id}/versions/${versionNo}/rollback`, { reason })

export const createAgentApp = (id: number, data: CreateAgentAppReq): Promise<AgentApp> =>
  post(`/v1/agents/${id}/apps`, data)

export const getAgentApps = (id: number): Promise<AgentApp[]> =>
  get(`/v1/agents/${id}/apps`)

export const createAgentApiKey = (appId: number, name: string): Promise<AgentApiKeyCreateResp> =>
  post(`/v1/agent-apps/${appId}/api-keys`, { name })

export const getAgentApiKeys = (appId: number): Promise<AgentApiKey[]> =>
  get(`/v1/agent-apps/${appId}/api-keys`)

export const revokeAgentApiKey = (appId: number, keyId: number): Promise<void> =>
  del(`/v1/agent-apps/${appId}/api-keys/${keyId}`)
