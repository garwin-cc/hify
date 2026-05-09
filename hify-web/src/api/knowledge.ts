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

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

export interface KnowledgeDocumentItem {
  id: number
  knowledgeBaseId: number
  name: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  chunkCount: number
  errorMessage: string
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

export const getKnowledgeBaseList = (
  page: number,
  size: number,
  name?: string,
): Promise<PageData<KnowledgeBaseItem>> =>
  get('/v1/knowledge-bases', { page, size, name: name || undefined })

export const getKnowledgeBase = (id: number): Promise<KnowledgeBaseItem> =>
  get(`/v1/knowledge-bases/${id}`)

export const createKnowledgeBase = (data: CreateKnowledgeBaseReq) =>
  post('/v1/knowledge-bases', data)

export const updateKnowledgeBase = (id: number, data: UpdateKnowledgeBaseReq) =>
  put(`/v1/knowledge-bases/${id}`, data)

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
