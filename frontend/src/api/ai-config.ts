import { fixIds } from './fix-ids'

export interface AiModelConfig {
  id: number
  name: string
  baseUrl: string
  apiKey?: string  // never in response, only for create/update request
  model: string
  timeoutSeconds: number
  enabled: boolean
  createdBy?: number
  createdAt?: string
  updatedAt?: string
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

export async function listConfigs(): Promise<AiModelConfig[]> {
  return fetchJson('/api/ai-config')
}

export async function getConfig(id: number): Promise<AiModelConfig> {
  return fetchJson(`/api/ai-config/${id}`)
}

export async function createConfig(data: {
  name: string
  baseUrl: string
  apiKey: string
  model: string
  timeoutSeconds: number
  enabled: boolean
}): Promise<AiModelConfig> {
  return fetchJson('/api/ai-config', { method: 'POST', body: JSON.stringify(data) })
}

export async function updateConfig(id: number, data: {
  name?: string
  baseUrl?: string
  apiKey?: string
  model?: string
  timeoutSeconds?: number
  enabled?: boolean
}): Promise<AiModelConfig> {
  return fetchJson(`/api/ai-config/${id}`, { method: 'PUT', body: JSON.stringify(data) })
}

export async function deleteConfig(id: number): Promise<void> {
  const res = await fetch(`/api/ai-config/${id}`, { method: 'DELETE', headers: headers() })
  if (!res.ok) throw new Error('Failed to delete config')
}
