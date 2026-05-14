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
  status?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  currentVersionId?: number
  latestVersionNo?: number
  tags?: string[]
  nodeTypes?: string[]
  nodeCount: number
  requirementCount?: number
  usageCount?: number
  lastUsedAt?: string
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

export interface CreateTemplateFromWorkflowReq {
  workflowId: number
  name: string
  description?: string
  category?: string
  icon?: string
  tags?: string[]
  publish?: boolean
  changelog?: string
}

export interface WorkflowTemplateExportResp {
  filename: string
  templateJson: Record<string, unknown>
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
  status: 'RUNNING' | 'WAITING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELED' | 'SKIPPED'
  inputSnapshot?: Record<string, unknown>
  outputs: Record<string, unknown>
  error?: string
  elapsedMs?: number
  startedAt?: string
  createdAt: string
  finishedAt?: string
  callTraces?: WorkflowNodeCallTrace[]
}

export interface WorkflowNodeCallTrace {
  id: number
  workflowRunId: number
  workflowNodeRunId?: number
  nodeKey: string
  nodeType: string
  callType: 'LLM' | 'API_CALL' | 'MCP' | 'HUMAN_REVIEW' | 'CODE_TASK' | string
  target: string
  requestSnapshot?: Record<string, unknown>
  responseSnapshot?: Record<string, unknown>
  status: string
  errorMessage?: string
  durationMs?: number
  startedAt?: string
  finishedAt?: string
}

export interface WorkflowRun {
  id: number
  workflowId: number
  workflowVersionId?: number
  traceId?: string
  rerunFromRunId?: number
  status: 'RUNNING' | 'WAITING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELED'
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

export interface WorkflowReviewTask {
  id: number
  workflowRunId: number
  nodeKey: string
  status: string
  title: string
  content: string
  actions: string[]
  allowEdit: boolean
  outputVariable: string
}

export interface WorkflowRunEvent {
  id: number
  workflowRunId: number
  eventSeq: number
  eventType: string
  nodeKey?: string
  status?: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface WorkflowNodeDebugResult {
  status: 'SUCCESS' | 'FAILED'
  outputs: Record<string, unknown>
  error?: string
  elapsedMs?: number
}

export interface WorkflowVersion {
  id: number
  workflowId: number
  versionNo: number
  changeSummary: string
  snapshotJson: WorkflowDetail
  createdAt: string
}

export interface WorkflowVersionDiff {
  workflowId: number
  leftVersionNo: number
  rightVersionNo: number
  summary: Record<string, unknown>
}

export interface WorkflowVariable {
  nodeKey: string
  nodeType: string
  variable: string
  expression: string
  label: string
}

export interface WorkflowPublish {
  id: number
  workflowId: number
  workflowVersionId: number
  publishType: string
  publishStatus: string
  endpointKey?: string
  toolKey?: string
  displayName?: string
  grayPercent?: number
  publishedAt?: string
}

export interface WorkflowTrigger {
  id: number
  workflowId: number
  workflowVersionId?: number
  triggerType: string
  triggerKey: string
  cronExpression?: string
  enabled: number
  nextFireAt?: string
  lastFireAt?: string
  lastRunId?: number
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

export const createTemplateFromWorkflow = (
  data: CreateTemplateFromWorkflowReq,
): Promise<WorkflowTemplateDetail> =>
  post('/v1/workflow-templates/from-workflow', data)

export const exportWorkflowTemplate = (
  id: number,
  versionId: number,
): Promise<WorkflowTemplateExportResp> =>
  get(`/v1/workflow-templates/${id}/versions/${versionId}/export`)

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

export const rerunWorkflowRun = (runId: number): Promise<WorkflowRun> =>
  post(`/v1/workflow-runs/${runId}/rerun`)

export const getWorkflowReviewTask = (runId: number): Promise<WorkflowReviewTask> =>
  get(`/v1/workflow-runs/${runId}/review`)

export const submitWorkflowReview = (
  runId: number,
  data: { action: string; comment?: string; editedContent?: string },
): Promise<WorkflowRun> =>
  post(`/v1/workflow-runs/${runId}/review`, data)

export const workflowRunEventsUrl = (runId: number, afterEventSeq = 0): string =>
  `/api/v1/workflow-runs/${runId}/events?after=${Math.max(0, afterEventSeq)}`

export const getLatestWorkflowRun = (id: number): Promise<WorkflowRun | null> =>
  get(`/v1/workflows/${id}/runs/latest`)

export const debugWorkflowNode = (
  id: number,
  nodeKey: string,
  data: { userMessage?: string; variables?: Record<string, unknown> },
): Promise<WorkflowNodeDebugResult> =>
  post(`/v1/workflows/${id}/nodes/${nodeKey}/debug`, data)

export const getWorkflowVersions = (id: number): Promise<WorkflowVersion[]> =>
  get(`/v1/workflows/${id}/versions`)

export const getWorkflowVersionDiff = (
  id: number,
  leftVersionNo: number,
  rightVersionNo: number,
): Promise<WorkflowVersionDiff> =>
  get(`/v1/workflows/${id}/versions/${leftVersionNo}/diff/${rightVersionNo}`)

export const restoreWorkflowVersion = (id: number, versionNo: number): Promise<WorkflowDetail> =>
  post(`/v1/workflows/${id}/versions/${versionNo}/restore`, {})

export const rollbackWorkflowVersion = (
  id: number,
  versionNo: number,
  reason?: string,
): Promise<WorkflowDetail> =>
  post(`/v1/workflows/${id}/versions/${versionNo}/rollback`, { reason })

export const publishWorkflow = (
  id: number,
  data: { publishType: string; displayName?: string; grayPercent?: number },
): Promise<WorkflowPublish> =>
  post(`/v1/workflows/${id}/publish`, data)

export const getWorkflowPublishes = (id: number): Promise<WorkflowPublish[]> =>
  get(`/v1/workflows/${id}/publishes`)

export const getWorkflowVariables = (id: number): Promise<WorkflowVariable[]> =>
  get(`/v1/workflows/${id}/variables`)

export const getWorkflowReviewTasks = (
  page: number,
  size: number,
  query: Record<string, unknown> = {},
): Promise<PageData<WorkflowReviewTask>> =>
  get('/v1/workflow-runs/reviews', { page, size, ...query })

export function workflowStatusOf(row: WorkflowListItem): WorkflowStatus {
  return row.enabled === 1 ? 'PUBLISHED' : 'DRAFT'
}
