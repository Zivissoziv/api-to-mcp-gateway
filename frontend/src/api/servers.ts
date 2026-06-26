import { fetchJson } from './client'

export interface McpServer {
  id: number
  code: string
  name: string
  description: string
  status: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface McpServerTool {
  id: number
  serverId: number
  toolId: number
  sortOrder: number
  createdAt: string
}

export async function listServers(): Promise<McpServer[]> {
  return fetchJson('/api/servers')
}

export async function getServer(id: number): Promise<McpServer> {
  return fetchJson(`/api/servers/${id}`)
}

export async function createServer(data: { code: string; name: string; description?: string }): Promise<McpServer> {
  return fetchJson('/api/servers', {
    method: 'POST', body: JSON.stringify(data),
  })
}

export async function updateServer(id: number, data: { code: string; name: string; description?: string }): Promise<McpServer> {
  return fetchJson(`/api/servers/${id}`, {
    method: 'PUT', body: JSON.stringify(data),
  })
}

export async function deleteServer(id: number): Promise<void> {
  await fetchJson(`/api/servers/${id}`, { method: 'DELETE' })
}

export async function getServerTools(serverId: number): Promise<McpServerTool[]> {
  return fetchJson(`/api/servers/${serverId}/tools`)
}

export async function bindTool(serverId: number, toolId: number): Promise<McpServerTool> {
  return fetchJson(`/api/servers/${serverId}/tools`, {
    method: 'POST', body: JSON.stringify({ toolId }),
  })
}

export async function unbindTool(serverId: number, toolId: number): Promise<void> {
  await fetchJson(`/api/servers/${serverId}/tools/${toolId}`, { method: 'DELETE' })
}

// -- Publish / MCP Key --

export async function publishServer(id: number, mcpKey?: string): Promise<{ rawMcpKey: string; server: McpServer }> {
  return fetchJson(`/api/servers/${id}/publish`, {
    method: 'POST',
    body: JSON.stringify(mcpKey ? { mcpKey } : {}),
  })
}

export async function unpublishServer(id: number): Promise<McpServer> {
  return fetchJson(`/api/servers/${id}/unpublish`, { method: 'POST' })
}

export interface ConnectionInfo {
  serverCode: string
  mcpPath: string
  mcpKey: string | null
}

export async function getConnectionInfo(id: number): Promise<ConnectionInfo> {
  return fetchJson(`/api/servers/${id}/connection-info`)
}

export async function resetMcpKey(id: number, mcpKey?: string): Promise<{ rawMcpKey: string }> {
  return fetchJson(`/api/servers/${id}/reset-key`, {
    method: 'POST',
    body: JSON.stringify(mcpKey ? { mcpKey } : {}),
  })
}
