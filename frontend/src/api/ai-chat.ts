import { fixIds } from './fix-ids'

export interface SessionInfo {
  sessionId: string
  serverName: string
  serverCode: string
  tools: string[]
}

export interface ToolCallInfo {
  toolName: string
  params: Record<string, any>
  resultText: string | null
  success: boolean
  statusCode: number
  error: string | null
}

export interface ChatReply {
  reply: string
  toolCalls: ToolCallInfo[]
  durationMs: number
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

export async function createSession(serverId: number, modelConfigId: number): Promise<SessionInfo> {
  return fetchJson('/api/ai-chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ serverId, modelConfigId }),
  })
}

export async function sendChatMessage(sessionId: string, message: string): Promise<ChatReply> {
  return fetchJson(`/api/ai-chat/sessions/${sessionId}/chat`, {
    method: 'POST',
    body: JSON.stringify({ message }),
  })
}

export async function closeSession(sessionId: string): Promise<void> {
  const res = await fetch(`/api/ai-chat/sessions/${sessionId}`, {
    method: 'DELETE', headers: headers(),
  })
  if (!res.ok) throw new Error('Failed to close session')
}
