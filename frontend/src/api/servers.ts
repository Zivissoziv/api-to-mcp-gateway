import { fixIds } from './fix-ids'

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

const headers = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${sessionStorage.getItem('accessToken')}`,
})

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, { ...init, headers: { ...headers(), ...init?.headers as any } })
  if (!res.ok) {
    const body = await res.text()
    throw new Error(body ? JSON.parse(body).message || `Request failed (${res.status})` : `Request failed (${res.status})`)
  }
  return fixIds(await res.json()) as T
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
  const res = await fetch(`/api/servers/${id}`, { method: 'DELETE', headers: headers() })
  if (!res.ok) throw new Error('Failed to delete server')
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
  const res = await fetch(`/api/servers/${serverId}/tools/${toolId}`, { method: 'DELETE', headers: headers() })
  if (!res.ok) throw new Error('Failed to unbind tool')
}
