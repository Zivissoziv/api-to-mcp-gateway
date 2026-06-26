import { fixIds } from './fix-ids'

export interface CallSummary {
  totalCalls: number
  uniqueServers: number
  uniqueTools: number
  uniqueIps: number
}

export interface ServerCallStats {
  serverCode: string
  callCount: number
  successCount: number
  uniqueIps: number
  avgDurationMs: number
  lastCallAt: string | null
}

export interface ToolCallStats {
  serverCode: string
  toolName: string
  callCount: number
  successCount: number
  uniqueIps: number
  avgDurationMs: number
  lastCallAt: string | null
}

export interface IpCallStats {
  clientIp: string
  callCount: number
  lastCallAt: string | null
}

export interface ServerDetail {
  tools: ToolCallStats[]
  ips: IpCallStats[]
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

export async function getSummary(): Promise<CallSummary> {
  return fetchJson('/api/stats/summary')
}

export async function getStatsByServer(): Promise<ServerCallStats[]> {
  return fetchJson('/api/stats/by-server')
}

export async function getStatsByTool(): Promise<ToolCallStats[]> {
  return fetchJson('/api/stats/by-tool')
}

export async function getStatsByIp(): Promise<IpCallStats[]> {
  return fetchJson('/api/stats/by-ip')
}

export async function getServerDetail(serverCode: string): Promise<ServerDetail> {
  return fetchJson(`/api/stats/servers/${encodeURIComponent(serverCode)}`)
}
