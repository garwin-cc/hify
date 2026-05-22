import { get, put } from '@/utils/request'

export interface QualitySummary {
  feedbackCount: number
  negativeFeedbackCount: number
  negativeFeedbackRate: number
  openSampleCount: number
  ragFeedbackCount: number
  ragHelpfulCount: number
  ragHelpfulRate: number
}

export interface AgentQuality {
  agentId: number
  agentName: string
  feedbackCount: number
  negativeFeedbackCount: number
  negativeFeedbackRate: number
  openSampleCount: number
}

export interface IssueQuality {
  issueType: string
  count: number
  rate: number
}

export interface QualityOverview {
  summary: QualitySummary
  agents: AgentQuality[]
  issues: IssueQuality[]
}

export interface QualitySample {
  id: number
  messageId: number
  sessionId: number
  agentId: number
  agentName?: string
  projectId?: number
  traceId?: string
  userId: number
  rating: string
  issueType?: string
  comment?: string
  correctedAnswer?: string
  reviewStatus: 'OPEN' | 'REVIEWING' | 'RESOLVED' | 'IGNORED'
  resolutionNote?: string
  userQuestion?: string
  assistantAnswer?: string
  ragTriggered?: boolean
  ragHit?: boolean
  mcpTriggered?: boolean
  modelId?: string
  createdAt?: string
  updatedAt?: string
}

export interface QualityQuery {
  projectId?: number
  reviewStatus?: string
  from?: string
  to?: string
}

export const getQualityOverview = (params: QualityQuery = {}): Promise<QualityOverview> =>
  get('/v1/ops/quality/overview', params)

export const getQualitySamples = (params: QualityQuery = {}): Promise<QualitySample[]> =>
  get('/v1/ops/quality/samples', params)

export const updateQualitySampleStatus = (
  sampleId: number,
  data: { reviewStatus: string; resolutionNote?: string },
): Promise<QualitySample> =>
  put(`/v1/ops/quality/samples/${sampleId}/status`, data)
