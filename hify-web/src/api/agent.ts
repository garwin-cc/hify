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
