import { fetchJson } from './client'

export interface AiModelConfig {
  id: number
  name: string
  baseUrl: string
  apiKey?: string
  model: string
  timeoutSeconds: number
  enabled: boolean
  createdBy?: number
  createdAt?: string
  updatedAt?: string
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
  await fetchJson(`/api/ai-config/${id}`, { method: 'DELETE' })
}
