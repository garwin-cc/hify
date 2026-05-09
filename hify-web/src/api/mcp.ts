import { del, get, post, put } from '@/utils/request'
import type { PageData } from '@/components/HifyTable.vue'

export interface McpServer {
  id: number
  name: string
  description: string
  endpoint: string
  enabled: number
  toolCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface McpTool {
  id: number
  mcpServerId: number
  name: string
  description: string
  inputSchema: Record<string, unknown>
}

export interface McpServerDetail {
  server: McpServer
  tools: McpTool[]
}

export interface McpServerQuery {
  name?: string
  enabled?: number
}

export interface SaveMcpServerRequest {
  name: string
  description?: string
  endpoint: string
  enabled: number
}

export interface McpConnectivityTestResult {
  success: boolean
  message?: string
  latencyMs: number
  tools: McpTool[]
}

export const getMcpServerPage = (
  page: number,
  pageSize: number,
  query: McpServerQuery = {},
): Promise<PageData<McpServer>> =>
  get('/v1/mcp-servers', { page, pageSize, ...query })

export const getMcpServerList = (): Promise<McpServer[]> =>
  getMcpServerPage(1, 100, { enabled: 1 }).then((page) => page.records)

export const getMcpServerDetail = (id: number): Promise<McpServerDetail> =>
  get(`/v1/mcp-servers/${id}`)

export const createMcpServer = (payload: SaveMcpServerRequest): Promise<McpServer> =>
  post('/v1/mcp-servers', payload)

export const updateMcpServer = (id: number, payload: SaveMcpServerRequest): Promise<McpServer> =>
  put(`/v1/mcp-servers/${id}`, payload)

export const deleteMcpServer = (id: number): Promise<void> =>
  del(`/v1/mcp-servers/${id}`)

export const testMcpServer = (id: number): Promise<McpConnectivityTestResult> =>
  post(`/v1/mcp-servers/${id}/test`)

export interface McpToolOption extends McpTool {
  serverName: string
}

export const getMcpToolOptions = async (): Promise<McpToolOption[]> => {
  const servers = await getMcpServerList()
  const details = await Promise.all(
    servers
      .filter((server) => (server.toolCount ?? 0) > 0)
      .map((server) => getMcpServerDetail(server.id)),
  )
  return details.flatMap((detail) =>
    detail.tools.map((tool) => ({
      ...tool,
      serverName: detail.server.name,
    })),
  )
}
