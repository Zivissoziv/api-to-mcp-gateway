import { fixIds } from './fix-ids'

export interface NetworkAllowlist {
  id: number
  pattern: string
  patternType: string
  description: string
  enabled: boolean
  createdBy: number
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

export async function listAllowlist(): Promise<NetworkAllowlist[]> {
  return fetchJson('/api/network-allowlist')
}

export async function addAllowlist(data: { pattern: string; patternType: string; description?: string }): Promise<NetworkAllowlist> {
  return fetchJson('/api/network-allowlist', {
    method: 'POST', body: JSON.stringify(data),
  })
}

export async function deleteAllowlist(id: number): Promise<void> {
  const res = await fetch(`/api/network-allowlist/${id}`, { method: 'DELETE', headers: headers() })
  if (!res.ok) throw new Error('Failed to delete allowlist entry')
}
