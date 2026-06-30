import { fetchJson } from './client'

export interface SessionInfo {
  sessionId: string
  serverName: string
  serverCode: string
  tools: string[]
  servers?: LoadedServerInfo[]
}

export interface LoadedServerInfo {
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

export async function createSession(serverId: number, modelConfigId: number, mcpKey?: string): Promise<SessionInfo> {
  return fetchJson('/api/ai-chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ serverId, modelConfigId, mcpKey }),
  })
}

export async function createMultiServerSession(serverIds: number[], modelConfigId: number, mcpKeys: Record<number, string | null>): Promise<SessionInfo> {
  return fetchJson('/api/ai-chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ serverIds, modelConfigId, mcpKeys }),
  })
}

export async function sendChatMessage(sessionId: string, message: string): Promise<ChatReply> {
  return fetchJson(`/api/ai-chat/sessions/${sessionId}/chat`, {
    method: 'POST',
    body: JSON.stringify({ message }),
  })
}

export async function closeSession(sessionId: string): Promise<void> {
  await fetchJson(`/api/ai-chat/sessions/${sessionId}`, { method: 'DELETE' })
}
