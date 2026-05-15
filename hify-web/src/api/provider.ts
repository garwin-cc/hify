import { get, post, put, del } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'

// ── Types ──────────────────────────────────────────────────────────────

export interface ModelConfig {
  id: number
  providerId: number
  name: string
  modelId: string
  modelType: 'CHAT' | 'EMBEDDING'
  contextSize: number | null
  extraParams: Record<string, unknown>
  enabled: number
  sortOrder: number
}

export interface ProviderListItem {
  id: number
  name: string
  type: string
  baseUrl: string
  enabled: number
  sortOrder: number
  createdAt: string
  healthStatus: string | null   // UP / DOWN / DEGRADED / UNKNOWN / null
  lastCheckAt: string | null
  lastErrorAt: string | null
  alertStatus: string | null
  failCount: number | null
  latencyMs: number | null
  errorMessage: string | null
  modelCount: number            // enabled models count
  models: ModelConfig[]         // all models (for expand)
}

export interface ConnectivityTestResult {
  success: boolean
  latencyMs: number
  modelCount: number | null
  errorMessage: string | null
}

export interface CreateProviderReq {
  name: string
  type: string
  apiKey?: string
  baseUrl?: string
  sortOrder?: number
}

export interface UpdateProviderReq {
  name?: string
  type?: string
  apiKey?: string
  baseUrl?: string
  enabled?: number
  sortOrder?: number
}

export interface CreateModelConfigReq {
  providerId: number
  name: string
  modelId: string
  modelType: 'CHAT' | 'EMBEDDING'
  contextSize?: number
}

// ── Constants ──────────────────────────────────────────────────────────

export const PROVIDER_TYPES = [
  { label: 'OpenAI',            value: 'OPENAI'             },
  { label: 'Anthropic (Claude)', value: 'ANTHROPIC'         },
  { label: 'DeepSeek',          value: 'DEEPSEEK'           },
  { label: '阿里百炼',          value: 'ALIBABA'            },
  { label: 'Ollama',            value: 'OLLAMA'             },
  { label: 'OpenAI 兼容',       value: 'OPENAI_COMPATIBLE'  },
]

// ── API ────────────────────────────────────────────────────────────────

export const getProviderList = (page: number, pageSize: number): Promise<PageData<ProviderListItem>> =>
  get('/v1/providers', { page, pageSize })

export const createProvider = (data: CreateProviderReq) =>
  post('/v1/providers', data)

export const updateProvider = (id: number, data: UpdateProviderReq) =>
  put(`/v1/providers/${id}`, data)

export const deleteProvider = (id: number) =>
  del(`/v1/providers/${id}`)

export const testConnection = (id: number): Promise<ConnectivityTestResult> =>
  post(`/v1/providers/${id}/test-connection`)

export const getEnabledModelConfigs = (modelType?: 'CHAT' | 'EMBEDDING'): Promise<ModelConfig[]> =>
  get('/v1/model-configs', { modelType })

export const createModelConfig = (data: CreateModelConfigReq): Promise<ModelConfig> =>
  post('/v1/model-configs', data)

export const updateModelConfigType = (
  id: number,
  modelType: 'CHAT' | 'EMBEDDING',
): Promise<ModelConfig> =>
  put(`/v1/model-configs/${id}/type`, { modelType })
