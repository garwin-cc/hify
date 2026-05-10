import { get, post, put, del } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'

export type WorkflowStatus = 'DRAFT' | 'PUBLISHED'

export interface WorkflowListItem {
  id: number
  name: string
  description: string
  enabled: number
  startNodeKey: string
  createdAt: string
  updatedAt: string
}

export interface WorkflowDetail extends WorkflowListItem {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
}

export interface WorkflowNode {
  nodeKey: string
  nodeType: string
  name: string
  config?: Record<string, unknown>
  positionX?: number
  positionY?: number
}

export interface WorkflowEdge {
  sourceNodeKey: string
  targetNodeKey: string
  edgeType?: string
  conditionExpression?: string | null
  sortOrder?: number
}

export interface WorkflowTemplateRequirement {
  key: string
  type: 'MODEL' | 'KNOWLEDGE_BASE' | 'TOOL'
  label: string
  required: boolean
}

export interface WorkflowTemplateListItem {
  id: number
  name: string
  description: string
  category: string
  icon: string
  enabled: number
  builtin: number
  nodeCount: number
  createdAt: string
}

export interface WorkflowTemplateDetail extends WorkflowTemplateListItem {
  configJson: Record<string, unknown>
  requirements: WorkflowTemplateRequirement[]
  updatedAt: string
}

export interface CreateWorkflowFromTemplateReq {
  name: string
  description?: string
  enabled?: number
  bindings: Record<string, number>
}

export interface CreateWorkflowReq {
  name: string
  description?: string
  enabled?: number
  startNodeKey: string
  nodes: WorkflowNode[]
  edges?: WorkflowEdge[]
}

export interface WorkflowConfigJson {
  enabled?: number
  status?: WorkflowStatus
  startNodeKey: string
  nodes: WorkflowNode[]
  edges?: WorkflowEdge[]
}

export interface WorkflowNodeRun {
  id: number
  workflowRunId: number
  nodeKey: string
  nodeType: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'SKIPPED'
  outputs: Record<string, unknown>
  error?: string
  elapsedMs?: number
  createdAt: string
  finishedAt?: string
}

export interface WorkflowRun {
  id: number
  workflowId: number
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELED'
  input: string
  output?: string
  error?: string
  currentNodeKey?: string
  timeoutAt?: string
  runMode?: 'SYNC' | 'ASYNC'
  elapsedMs?: number
  createdAt: string
  finishedAt?: string
  nodeRuns: WorkflowNodeRun[]
}

export const getWorkflowList = (
  page: number,
  size: number,
): Promise<PageData<WorkflowListItem>> =>
  get('/v1/workflows', { page, size })

export const getWorkflowTemplateList = (
  page: number,
  size: number,
  category?: string,
  name?: string,
): Promise<PageData<WorkflowTemplateListItem>> =>
  get('/v1/workflow-templates', { page, size, category: category || undefined, name: name || undefined })

export const getWorkflowTemplateDetail = (id: number): Promise<WorkflowTemplateDetail> =>
  get(`/v1/workflow-templates/${id}`)

export const createWorkflowFromTemplate = (
  id: number,
  data: CreateWorkflowFromTemplateReq,
): Promise<WorkflowDetail> =>
  post(`/v1/workflow-templates/${id}/create-workflow`, data)

export const createWorkflow = (data: CreateWorkflowReq) =>
  post('/v1/workflows', data)

export const getWorkflowDetail = (id: number): Promise<WorkflowDetail> =>
  get(`/v1/workflows/${id}`)

export const updateWorkflow = (id: number, data: CreateWorkflowReq): Promise<WorkflowDetail> =>
  put(`/v1/workflows/${id}`, data)

export const deleteWorkflow = (id: number) =>
  del(`/v1/workflows/${id}`)

export const runWorkflow = (id: number, userMessage: string): Promise<WorkflowRun> =>
  post(`/v1/workflows/${id}/run`, { userMessage })

export const startAsyncWorkflowRun = (id: number, userMessage: string): Promise<WorkflowRun> =>
  post(`/v1/workflows/${id}/runs`, { userMessage })

export const getWorkflowRunDetail = (runId: number): Promise<WorkflowRun> =>
  get(`/v1/workflow-runs/${runId}`)

export const getLatestWorkflowRun = (id: number): Promise<WorkflowRun | null> =>
  get(`/v1/workflows/${id}/runs/latest`)

export function workflowStatusOf(row: WorkflowListItem): WorkflowStatus {
  return row.enabled === 1 ? 'PUBLISHED' : 'DRAFT'
}
