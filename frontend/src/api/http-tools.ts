import { fetchJson } from './client'

export interface ParamMapping {
  id?: number
  toolId?: number
  name: string
  paramSource: string
  paramLocation: string
  schemaJson: string
  required: boolean
  description: string
}

export interface HttpTool {
  id: number
  name: string
  description: string
  httpMethod: string
  urlTemplate: string
  headers: string | null
  headerTemplate: string | null
  bodyTemplate: string | null
  authConfigId: number | null
  status: string
  createdBy: number
  createdAt: string
  updatedAt: string
}

export async function listTools(): Promise<HttpTool[]> {
  return fetchJson('/api/http-tools')
}

export async function getTool(id: number): Promise<HttpTool> {
  return fetchJson(`/api/http-tools/${id}`)
}

export async function getMappings(toolId: number): Promise<ParamMapping[]> {
  return fetchJson(`/api/http-tools/${toolId}/mappings`)
}

export async function createTool(data: {
  name: string; description?: string; httpMethod: string; urlTemplate: string
  headers?: string; headerTemplate?: string; bodyTemplate?: string; parameterMappings: ParamMapping[]
}): Promise<HttpTool> {
  return fetchJson('/api/http-tools', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateTool(id: number, data: {
  name: string; description?: string; httpMethod: string; urlTemplate: string
  headers?: string; headerTemplate?: string; bodyTemplate?: string; parameterMappings: ParamMapping[]
}): Promise<HttpTool> {
  return fetchJson(`/api/http-tools/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function deleteTool(id: number): Promise<void> {
  await fetchJson(`/api/http-tools/${id}`, { method: 'DELETE' })
}

export async function getToolSchema(toolId: number): Promise<string> {
  return fetchJson(`/api/http-tools/${toolId}/schema`)
}

// ── Schema conversion ──

export interface SchemaField {
  name: string
  type: string
  description: string
  required: boolean
}

export async function schemaToFields(schemaJson: string): Promise<SchemaField[]> {
  return fetchJson('/api/schema/to-fields', { method: 'POST', body: JSON.stringify({ schemaJson }) })
}

export async function fieldsToSchema(fields: SchemaField[]): Promise<string> {
  return fetchJson('/api/schema/from-fields', { method: 'POST', body: JSON.stringify({ fields }) })
}

// ── Tool testing ──

export interface TestResult {
  success: boolean
  statusCode: number
  durationMs: number
  requestSummary: { method: string; url: string; headers: Record<string, string> } | null
  responseSummary: { statusCode: number; headers: Record<string, string>; body: string; bodyTruncated: boolean } | null
  error: string | null
  errorMessage: string | null
}

export interface TestToolRequest {
  httpMethod: string
  urlTemplate: string
  headers: string
  bodyTemplate?: string | null
  parameterMappings: ParamMapping[]
  parameterValues: Record<string, any>
  authConfig?: { authType: string; configJson: string }
}

export async function testTool(req: TestToolRequest): Promise<TestResult> {
  return fetchJson('/api/http-tools/test', { method: 'POST', body: JSON.stringify(req) })
}
