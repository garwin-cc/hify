import { get, post, put, del } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'

export interface KnowledgeBaseItem {
  id: number
  name: string
  description: string
  enabled: number
  embeddingModelConfigId: number | null
  documentCount: number
  chunkCount: number
  retrievalMode: string
  topK: number
  candidateTopK: number
  scoreThreshold: number
  chunkSize: number
  chunkOverlap: number
  maxContextTokens: number
  rerankEnabled: number
  rerankModelConfigId: number | null
  rerankTopN: number
  createdAt: string
  updatedAt: string
}

export interface CreateKnowledgeBaseReq {
  name: string
  description?: string
  embeddingModelConfigId: number
}

export interface UpdateKnowledgeBaseReq {
  name?: string
  description?: string
  enabled?: number
  embeddingModelConfigId?: number
}

export interface UpdateKnowledgeRetrievalConfigReq {
  retrievalMode?: string
  topK?: number
  candidateTopK?: number
  scoreThreshold?: number
  chunkSize?: number
  chunkOverlap?: number
  maxContextTokens?: number
  rerankEnabled?: number
  rerankModelConfigId?: number | null
  rerankTopN?: number
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED' | 'CANCELED'

export interface KnowledgeDocumentItem {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  processStage: string
  processProgress: number
  processedChunkCount: number
  chunkCount: number
  errorMessage: string
  errorCode: string
  failedStage: string
  retryable: number
  cancelRequested: number
  retryCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeChunkItem {
  id: number
  knowledgeBaseId: number
  documentId: number
  chunkIndex: number
  content: string
  metadata: Record<string, unknown>
  createdAt: string
}

export interface KnowledgeSearchReq {
  queryText: string
  topK?: number
  candidateTopK?: number
  scoreThreshold?: number
  retrievalMode?: string
  includeTrace?: boolean
}

export interface KnowledgeSearchHit {
  traceId?: string
  rank?: number
  id: number
  knowledgeBaseId: number
  documentId: string
  documentName?: string
  chunkIndex: number
  content: string
  score?: number
  finalScore?: number
  vectorScore?: number
  metadata: Record<string, unknown>
  createdAt: string
}

export const getKnowledgeBaseList = (
  page: number,
  size: number,
  name?: string,
): Promise<PageData<KnowledgeBaseItem>> =>
  get('/v1/knowledge-bases', { page, size, name: name || undefined })

export const getKnowledgeBase = (id: number): Promise<KnowledgeBaseItem> =>
  get(`/v1/knowledge-bases/${id}`)

export const createKnowledgeBase = (data: CreateKnowledgeBaseReq): Promise<KnowledgeBaseItem> =>
  post('/v1/knowledge-bases', data)

export const updateKnowledgeBase = (id: number, data: UpdateKnowledgeBaseReq): Promise<KnowledgeBaseItem> =>
  put(`/v1/knowledge-bases/${id}`, data)

export const updateKnowledgeRetrievalConfig = (
  id: number,
  data: UpdateKnowledgeRetrievalConfigReq,
) : Promise<KnowledgeBaseItem> =>
  put(`/v1/knowledge-bases/${id}/retrieval-config`, data)

export const deleteKnowledgeBase = (id: number) =>
  del(`/v1/knowledge-bases/${id}`)

export const getDocumentList = (
  knowledgeBaseId: number,
  page: number,
  size: number,
): Promise<PageData<KnowledgeDocumentItem>> =>
  get(`/v1/knowledge-bases/${knowledgeBaseId}/documents`, { page, size })

export const uploadKnowledgeDocument = (knowledgeBaseId: number, file: File): Promise<number> => {
  const formData = new FormData()
  formData.append('file', file)
  return post(`/v1/knowledge-bases/${knowledgeBaseId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const getDocument = (id: number): Promise<KnowledgeDocumentItem> =>
  get(`/v1/documents/${id}`)

export const getDocumentChunks = (id: number): Promise<KnowledgeChunkItem[]> =>
  get(`/v1/documents/${id}/chunks`)

export const deleteDocument = (id: number) =>
  del(`/v1/documents/${id}`)

export const retryDocument = (id: number) =>
  post(`/v1/documents/${id}/retry`)

export const cancelDocument = (id: number) =>
  post(`/v1/documents/${id}/cancel`)

export const testKnowledgeRetrieval = (
  knowledgeBaseId: number,
  data: KnowledgeSearchReq,
): Promise<KnowledgeSearchHit[]> =>
  post(`/v1/knowledge-bases/${knowledgeBaseId}/retrieval-test`, data)
