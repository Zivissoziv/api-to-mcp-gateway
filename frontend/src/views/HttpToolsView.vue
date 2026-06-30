<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { listTools, createTool, updateTool, deleteTool, getMappings, testTool } from '../api/http-tools'
import type { HttpTool, ParamMapping, TestResult } from '../api/http-tools'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Search } from '@element-plus/icons-vue'

const tools = ref<HttpTool[]>([])
const loading = ref(false)
const searchQuery = ref('')
const filteredTools = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return tools.value
  return tools.value.filter(t =>
    t.name.toLowerCase().includes(q) ||
    (t.description || '').toLowerCase().includes(q) ||
    t.urlTemplate.toLowerCase().includes(q)
  )
})
const dialogVisible = ref(false)
const isEditing = ref(false)
const currentStep = ref(1)
const testPassed = ref(false)
const testResult = ref<TestResult | null>(null)

const form = ref({
  name: '', description: '', httpMethod: 'GET', urlTemplate: '', headers: '',
})

const bodyTemplate = ref('')
const headerTemplate = ref('')

interface VarDef {
  key: string
  paramSource: string
  paramLocation: string
  type: string
  required: boolean
  description: string
}
const vars = ref<VarDef[]>([])
const paramValues = ref<Record<string, any>>({})

function extractVarKeys(source: string): string[] {
  const matches = source.match(/\$\{(\w+)\}/g)
  if (!matches) return []
  return [...new Set(matches.map((m: string) => m.replace(/[${}]/g, '')))]
}

function extractPathKeys(url: string): string[] {
  const matches = url.match(/\{(\w+)\}/g)
  if (!matches) return []
  return [...new Set(matches.map((m: string) => m.replace(/[{}]/g, '')))]
}

function syncVars() {
  const fromPath = extractPathKeys(form.value.urlTemplate)
  const fromHeader = extractVarKeys(headerTemplate.value)
  const fromBody = extractVarKeys(bodyTemplate.value)
  const allKeys = new Set([...fromPath, ...fromHeader, ...fromBody])
  const existing = new Map(vars.value.map(v => [v.key, v]))
  const newVars: VarDef[] = []
  allKeys.forEach(k => {
    const old = existing.get(k)
    const inPath = fromPath.includes(k)
    newVars.push({
      key: k,
      paramSource: old?.paramSource ?? (inPath ? 'PATH' : 'BODY'),
      paramLocation: old?.paramLocation ?? (inPath ? `{${k}}` : k),
      type: old?.type ?? 'string',
      required: old?.required ?? inPath,
      description: old?.description ?? '',
    })
  })
  vars.value = newVars
  // Reset param values when vars change
  paramValues.value = {}
  testPassed.value = false
  testResult.value = null
}

watch(() => form.value.urlTemplate, syncVars)
watch(bodyTemplate, syncVars)
watch(headerTemplate, syncVars)

const computedSchema = computed(() => {
  const fields = vars.value.map(v => ({ name: v.key, type: v.type, description: v.description, required: v.required }))
  if (fields.length === 0) return ''
  const schema: any = { type: 'object', properties: {}, required: [] }
  for (const f of fields) {
    schema.properties[f.name] = { type: f.type }
    if (f.description) schema.properties[f.name].description = f.description
    if (f.required) schema.required.push(f.name)
  }
  if (schema.required.length === 0) delete schema.required
  return JSON.stringify(schema, null, 2)
})

function buildParamMappings(): ParamMapping[] {
  return vars.value.map((v, i) => ({
    name: v.key,
    paramSource: v.paramSource as any,
    paramLocation: v.paramLocation,
    schemaJson: JSON.stringify({ type: v.type, description: v.description }),
    required: v.required,
    description: v.description,
  }))
}

function guessTypeFromSchema(schemaJson: string): string {
  try { return JSON.parse(schemaJson).type || 'string' } catch { return 'string' }
}

function parseVarsFromMappings(ms: ParamMapping[]) {
  const fromPath = extractPathKeys(form.value.urlTemplate)
  const allKeys = new Set([...fromPath, ...extractVarKeys(bodyTemplate.value), ...extractVarKeys(headerTemplate.value)])
  if (allKeys.size === 0 && ms.length > 0) {
    vars.value = ms.map(m => ({
      key: m.name,
      paramSource: m.paramSource,
      paramLocation: m.paramLocation,
      type: guessTypeFromSchema(m.schemaJson),
      required: m.required,
      description: m.description,
    }))
    return
  }
  const mapped = new Map(ms.map(m => [m.name, m]))
  const result: VarDef[] = []
  allKeys.forEach(k => {
    const m = mapped.get(k)
    const inPath = fromPath.includes(k)
    result.push({
      key: k,
      paramSource: m?.paramSource ?? (inPath ? 'PATH' : 'BODY'),
      paramLocation: m?.paramLocation ?? (inPath ? `{${k}}` : k),
      type: m ? guessTypeFromSchema(m.schemaJson) : 'string',
      required: m?.required ?? inPath,
      description: m?.description ?? '',
    })
  })
  vars.value = result
}

async function load() {
  loading.value = true
  try { tools.value = await listTools() } finally { loading.value = false }
}

function openCreate() {
  isEditing.value = false
  currentStep.value = 1
  testPassed.value = false
  testResult.value = null
  form.value = { name: '', description: '', httpMethod: 'GET', urlTemplate: '', headers: '' }
  bodyTemplate.value = ''
  headerTemplate.value = ''
  vars.value = []
  paramValues.value = {}
  dialogVisible.value = true
}

async function openEdit(t: HttpTool) {
  isEditing.value = true
  currentStep.value = 1
  testPassed.value = false
  testResult.value = null
  paramValues.value = {}
  form.value = {
    name: t.name, description: t.description, httpMethod: t.httpMethod,
    urlTemplate: t.urlTemplate, headers: t.headers ?? '',
  }
  headerTemplate.value = t.headerTemplate ?? ''
  bodyTemplate.value = t.bodyTemplate ?? ''
  try {
    const ms = await getMappings(t.id)
    setTimeout(() => parseVarsFromMappings(ms), 0)
  } catch {
    vars.value = []
  }
  dialogVisible.value = true
}

async function save() {
  if (templateError.value) {
    ElMessage.warning(templateError.value)
    currentStep.value = 1
    return
  }
  try {
    const payload = {
      ...form.value,
      headerTemplate: headerTemplate.value || null,
      bodyTemplate: bodyTemplate.value || null,
      parameterMappings: buildParamMappings(),
    }
    if (isEditing.value) {
      await updateTool(editingId.value!, payload)
    } else {
      await createTool(payload)
    }
    dialogVisible.value = false
    await load()
    ElMessage.success(isEditing.value ? '接口已更新' : '接口已创建')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

const editingId = ref<number | null>(null)

// ── Template validation ──

const headerError = computed(() => {
  if (!headerTemplate.value) return ''
  const lines = headerTemplate.value.split('\n').map(l => l.trim()).filter(Boolean)
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const colon = line.indexOf(':')
    if (colon <= 0) return `第 ${i + 1} 行格式错误：需要 "Key: Value"`
    const key = line.substring(0, colon).trim()
    if (!key) return `第 ${i + 1} 行缺少 Header 名称`
  }
  return ''
})

const bodyError = computed(() => {
  if (!bodyTemplate.value) return ''
  try {
    // Replace ${var} with null (valid JSON literal) to validate structure
    const resolved = bodyTemplate.value.replace(/\$\{(\w+)\}/g, 'null')
    JSON.parse(resolved)
    return ''
  } catch (e: any) {
    return '请求体模板不是合法的 JSON：' + (e.message || '')
  }
})

const templateError = computed(() => headerError.value || bodyError.value)

function beautifyBodyTemplate() {
  if (!bodyTemplate.value.trim()) {
    ElMessage.warning('请求体模板为空')
    return
  }

  const placeholders: Array<{ value: string; quoted: boolean }> = []
  const protectedJson = bodyTemplate.value.replace(/\$\{(\w+)\}/g, (match, _key, offset, source) => {
    const token = `__MCP_TEMPLATE_VAR_${placeholders.length}__`
    const quoted = source[offset - 1] === '"' && source[offset + match.length] === '"'
    placeholders.push({ value: match, quoted })
    return quoted ? token : `"${token}"`
  })

  try {
    const parsed = JSON.parse(protectedJson)
    bodyTemplate.value = JSON.stringify(parsed, null, 2).replace(/"__MCP_TEMPLATE_VAR_(\d+)__"/g, (_, index) => {
      const placeholder = placeholders[Number(index)]
      if (!placeholder) return ''
      return placeholder.quoted ? `"${placeholder.value}"` : placeholder.value
    })
    ElMessage.success('请求体模板已美化')
  } catch (e: any) {
    ElMessage.warning('请求体模板不是合法的 JSON：' + (e.message || ''))
  }
}

async function openEditClick(t: HttpTool) {
  editingId.value = t.id
  await openEdit(t)
}

const viewDialogVisible = ref(false)
const viewToolData = ref<{ tool: HttpTool; mappings: ParamMapping[] } | null>(null)

async function viewTool(t: HttpTool) {
  try {
    const ms = await getMappings(t.id)
    viewToolData.value = { tool: t, mappings: ms }
    viewDialogVisible.value = true
  } catch {
    ElMessage.error('加载工具详情失败')
  }
}

async function remove(t: HttpTool) {
  try {
    await ElMessageBox.confirm(`确定删除接口 "${t.name}"？`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteTool(t.id)
    await load()
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

// ── Step 2: Online test ──

async function executeTest() {
  testResult.value = null
  testPassed.value = false

  const vals: Record<string, any> = {}
  for (const v of vars.value) {
    const val = paramValues.value[v.key]
    if (val !== undefined && val !== '') {
      vals[v.key] = v.type === 'integer' || v.type === 'number' ? Number(val) : val
    }
  }

  try {
    const result = await testTool({
      httpMethod: form.value.httpMethod,
      urlTemplate: form.value.urlTemplate,
      headers: [
        headerTemplate.value || '',
        form.value.headers || '',
      ].filter(Boolean).join('\n'),
      bodyTemplate: bodyTemplate.value || null,
      parameterMappings: buildParamMappings(),
      parameterValues: vals,
    })
    testResult.value = result
    if (result.success) {
      testPassed.value = true
      ElMessage.success(`测试通过 — ${result.statusCode} (${result.durationMs}ms)`)
    }
  } catch (e: any) {
    testResult.value = {
      success: false, statusCode: 0, durationMs: 0,
      requestSummary: null, responseSummary: null,
      error: 'CONNECTION_FAILED', errorMessage: e.message || '请求失败',
    }
  }
}

// ── Step navigation ──

function canGoStep2(): boolean {
  if (form.value.name.trim() === '') {
    ElMessage.warning('请填写接口名称')
    return false
  }
  if (form.value.urlTemplate.trim() === '') {
    ElMessage.warning('请填写 URL 模板')
    return false
  }
  if (headerError.value) {
    ElMessage.warning(headerError.value)
    return false
  }
  if (bodyError.value) {
    ElMessage.warning(bodyError.value)
    return false
  }
  return true
}

function goToStep2() {
  if (!canGoStep2()) {
    ElMessage.warning('请先填写接口名称和 URL 模板')
    return
  }
  // Rebuild paramValues from vars for fresh Step 2
  for (const v of vars.value) {
    if (paramValues.value[v.key] === undefined) {
      paramValues.value[v.key] = ''
    }
  }
  currentStep.value = 2
}

function goToStep3() {
  currentStep.value = 3
}

function goToStep1() {
  testPassed.value = false
  testResult.value = null
  currentStep.value = 1
}

function goBackToStep2() {
  currentStep.value = 2
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h2>接口注册</h2>
        <p class="page-intro">把企业已有的 HTTP API 登记到平台——编写请求模板，定义变量描述。AI 客户端会自动按定义调用。</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 注册接口</el-button>
    </header>

    <div class="search-bar">
      <el-input v-model="searchQuery" placeholder="搜索接口名称、描述或 URL…" clearable prefix-icon="Search" />
    </div>

    <el-table v-loading="loading" :data="filteredTools" stripe style="width:100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="方法" width="90">
        <template #default="{ row }">
          <code class="method">{{ row.httpMethod }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="urlTemplate" label="URL" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="badge" :class="row.status.toLowerCase()">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230">
        <template #default="{ row }">
          <el-button size="small" text @click="viewTool(row)">查看</el-button>
          <el-button size="small" text @click="openEditClick(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建向导弹窗（三步） -->
<el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑接口' : '注册接口'"
      width="1100px" top="5vh" destroy-on-close
      :close-on-click-modal="false"
      class="m4-dialog"
    >
      <el-steps :active="currentStep - 1" align-center style="margin-bottom:24px">
        <el-step title="基础配置" description="填写接口信息" />
        <el-step title="在线测试" description="验证接口可用" />
        <el-step :title="isEditing ? '确认更新' : '确认创建'" description="检查并保存" />
      </el-steps>

      <!-- ── Step 1: Basic Configuration ── -->
      <div v-show="currentStep === 1">
        <el-form label-position="top" @submit.prevent="goToStep2">
          <el-row :gutter="12" class="compact-basic-row">
            <el-col :span="7">
              <el-form-item label="名称" required>
                <el-input v-model="form.name" placeholder="get_user_info" @input="form.name = form.name.replace(/[^a-zA-Z0-9_-]/g, '')" />
              </el-form-item>
            </el-col>
            <el-col :span="4">
              <el-form-item label="方法" required>
                <el-select v-model="form.httpMethod" style="width:100%">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="PATCH" value="PATCH" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="13">
              <el-form-item label="URL 模板" required>
                <template #label><span>URL 模板 <span class="editor-hint">用 {id} 或 ${name} 标记变量</span></span></template>
                <el-input v-model="form.urlTemplate" placeholder="https://api.example.com/users/{id}" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="描述">
                <el-input v-model="form.description" placeholder="简要说明这个接口的用途" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16" class="template-grid">
            <el-col :span="11">
              <el-form-item
                label="请求头模板"
                :error="headerError"
                :validate-status="headerError ? 'error' : undefined"
              >
                <template #label><span>请求头模板 <span class="editor-hint">每行一个 Key: Value，用 ${xxx} 标记变量</span></span></template>
                <el-input v-model="headerTemplate" type="textarea" :rows="4" class="mono-input" placeholder='Authorization: Bearer ${token}' />
              </el-form-item>
              <el-form-item
                label="请求体模板"
                :error="bodyError"
                :validate-status="bodyError ? 'error' : undefined"
              >
                <template #label>
                  <span class="template-label">
                    <span>请求体模板 <span class="editor-hint">JSON 对象，用 ${xxx} 标记变量</span></span>
                    <el-button size="small" text type="primary" :icon="MagicStick" @click.stop="beautifyBodyTemplate">
                      美化 JSON
                    </el-button>
                  </span>
                </template>
                <el-input v-model="bodyTemplate" type="textarea" :rows="9" class="mono-input" placeholder='{"name": "${name}"}' />
              </el-form-item>
            </el-col>

            <el-col :span="13">
              <div class="var-header">
                <span class="var-header-label">变量定义</span>
                <span class="editor-hint" v-if="vars.length === 0">编辑左侧模板后将自动识别</span>
                <span class="editor-hint" v-else>共 {{ vars.length }} 个变量</span>
              </div>
              <el-table v-if="vars.length" :data="vars" stripe size="small" max-height="390" class="var-table-ep">
                <el-table-column label="变量" width="90">
                  <template #default="{ row }"><code class="var-key">$\{{ row.key }}</code></template>
                </el-table-column>
                <el-table-column label="来源" width="90">
                  <template #default="{ row }">
                    <el-select v-model="row.paramSource" size="small">
                      <el-option label="Path" value="PATH" />
                      <el-option label="Query" value="QUERY" />
                      <el-option label="Header" value="HEADER" />
                      <el-option label="Body" value="BODY" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">
                    <el-select v-model="row.type" size="small">
                      <el-option label="string" value="string" />
                      <el-option label="integer" value="integer" />
                      <el-option label="number" value="number" />
                      <el-option label="boolean" value="boolean" />
                      <el-option label="object" value="object" />
                      <el-option label="array" value="array" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="必填" width="50" align="center">
                  <template #default="{ row }">
                    <el-checkbox v-model="row.required" />
                  </template>
                </el-table-column>
                <el-table-column label="描述" min-width="140">
                  <template #default="{ row }">
                    <el-input v-model="row.description" size="small" placeholder="参数说明" />
                  </template>
                </el-table-column>
              </el-table>
              <p v-else class="var-empty">暂无变量</p>

              <el-collapse v-if="computedSchema" style="margin-top:12px">
                <el-collapse-item title="AI 输入 Schema（点击展开）">
                  <pre class="schema-code" style="max-height:400px;overflow:auto;font-size:12px;background:#1e1e1e;color:#d4d4d4;padding:12px;border-radius:4px">{{ computedSchema }}</pre>
                </el-collapse-item>
              </el-collapse>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- ── Step 2: Online Test ── -->
      <div v-show="currentStep === 2">
        <div class="test-intro">
          <p>填写参数值，点击执行测试验证接口可正常调用。<strong>2xx 状态码即通过测试。</strong></p>
        </div>

        <!-- Parameter input form -->
        <div class="test-params" v-if="vars.length > 0">
          <div v-for="v in vars" :key="v.key" class="test-param-row">
            <label class="test-param-label">
              <span v-if="v.required" class="required-star">*</span>
              {{ v.key }}
              <span class="test-param-meta">{{ v.paramSource }} / {{ v.type }}</span>
            </label>
            <div class="test-param-control">
              <template v-if="v.type === 'boolean'">
                <el-switch v-model="paramValues[v.key]" />
              </template>
              <template v-else-if="v.type === 'integer' || v.type === 'number'">
                <el-input-number v-model="paramValues[v.key]" :min="undefined" :max="undefined" style="width:100%" />
              </template>
              <template v-else>
                <el-input v-model="paramValues[v.key]" :placeholder="v.description || `请输入 ${v.key}`" />
              </template>
            </div>
          </div>
        </div>
        <div v-else class="test-params-empty">
          <p>该接口没有定义变量参数，将直接调用 URL。</p>
        </div>

        <div class="test-action">
          <el-button type="primary" @click="executeTest" :loading="false">▶ 执行测试</el-button>
        </div>

        <!-- Test result -->
        <div v-if="testResult" class="test-result" :class="{ 'test-passed': testResult.success, 'test-failed': !testResult.success }">
          <div class="test-result-header">
            <span v-if="testResult.success" class="test-badge pass">✓ 测试通过</span>
            <span v-else class="test-badge fail">✗ 测试失败</span>
            <span class="test-status-code" v-if="testResult.statusCode">{{ testResult.statusCode }}</span>
            <span class="test-duration" v-if="testResult.durationMs">{{ testResult.durationMs }}ms</span>
          </div>

          <!-- Error message -->
          <div v-if="testResult.errorMessage" class="test-error-msg">
            {{ testResult.errorMessage }}
          </div>

          <!-- Request summary -->
          <div v-if="testResult.requestSummary" class="test-section">
            <h4>请求</h4>
            <div class="test-line">{{ testResult.requestSummary.method }} {{ testResult.requestSummary.url }}</div>
            <div v-if="Object.keys(testResult.requestSummary.headers).length" class="test-headers">
              <div v-for="(v, k) in testResult.requestSummary.headers" :key="k" class="test-line">{{ k }}: {{ v }}</div>
            </div>
          </div>

          <!-- Response summary -->
          <div v-if="testResult.responseSummary" class="test-section">
            <h4>响应</h4>
            <pre class="test-body">{{ testResult.responseSummary.body || '(空响应体)' }}</pre>
            <div v-if="testResult.responseSummary.bodyTruncated" class="test-truncated">（响应体已截断）</div>
          </div>
        </div>
      </div>

      <!-- ── Step 3: Confirmation ── -->
      <div v-show="currentStep === 3">
        <div class="confirm-summary">
          <div class="confirm-passed-badge">✓ 已通过在线测试</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="名称">{{ form.name }}</el-descriptions-item>
            <el-descriptions-item label="方法">{{ form.httpMethod }}</el-descriptions-item>
            <el-descriptions-item label="URL 模板" :span="2">{{ form.urlTemplate }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ form.description || '（无）' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="vars.length" class="confirm-section">
            <h4>参数映射（{{ vars.length }} 个）</h4>
            <el-table :data="vars" stripe size="small">
              <el-table-column label="变量" prop="key" width="100" />
              <el-table-column label="来源" prop="paramSource" width="80" />
              <el-table-column label="类型" prop="type" width="80" />
              <el-table-column label="必填" width="60">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="描述" prop="description" />
            </el-table>
          </div>

          <div v-if="testResult" class="confirm-test-result">
            <h4>测试结果</h4>
            <p>状态码 {{ testResult.statusCode }} · 耗时 {{ testResult.durationMs }}ms</p>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>

        <!-- Step 1 footer -->
        <template v-if="currentStep === 1">
          <el-button type="primary" @click="goToStep2">下一步</el-button>
        </template>

        <!-- Step 2 footer -->
        <template v-if="currentStep === 2">
          <el-button @click="goToStep1">上一步</el-button>
          <el-button type="primary" :disabled="!testPassed" @click="goToStep3">
            {{ testPassed ? '下一步' : '请先通过测试' }}
          </el-button>
        </template>

        <!-- Step 3 footer -->
        <template v-if="currentStep === 3">
          <el-button @click="goBackToStep2">上一步</el-button>
          <el-button type="primary" @click="save">{{ isEditing ? '保存' : '创建' }}</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 查看弹窗（只读） -->
    <el-dialog v-model="viewDialogVisible" title="查看接口" width="900px" top="8vh" destroy-on-close>
      <template v-if="viewToolData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="名称">{{ viewToolData.tool.name }}</el-descriptions-item>
          <el-descriptions-item label="方法">{{ viewToolData.tool.httpMethod }}</el-descriptions-item>
          <el-descriptions-item label="URL 模板" :span="2">{{ viewToolData.tool.urlTemplate }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ viewToolData.tool.description || '（无）' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ viewToolData.tool.status }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ viewToolData.tool.updatedAt }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="viewToolData.mappings.length" style="margin-top:16px">
          <h4 style="margin:0 0 8px;font-size:14px">参数映射（{{ viewToolData.mappings.length }} 个）</h4>
          <el-table :data="viewToolData.mappings" stripe size="small">
            <el-table-column label="变量" prop="name" width="100" />
            <el-table-column label="来源" prop="paramSource" width="80" />
            <el-table-column label="位置" prop="paramLocation" />
            <el-table-column label="类型" width="80">
              <template #default="{ row }">{{ row.schemaJson ? guessTypeFromSchema(row.schemaJson) : 'string' }}</template>
            </el-table-column>
            <el-table-column label="必填" width="60">
              <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="描述" prop="description" />
          </el-table>
        </div>
        <p v-else style="color:#999;margin-top:16px">无参数映射</p>

        <div v-if="viewToolData.tool.headerTemplate" style="margin-top:16px">
          <h4 style="margin:0 0 4px;font-size:14px">请求头模板</h4>
          <pre style="background:#f5f5f5;padding:8px;border-radius:4px;font-size:12px;white-space:pre-wrap">{{ viewToolData.tool.headerTemplate }}</pre>
        </div>
        <div v-if="viewToolData.tool.bodyTemplate" style="margin-top:16px">
          <h4 style="margin:0 0 4px;font-size:14px">请求体模板</h4>
          <pre style="background:#f5f5f5;padding:8px;border-radius:4px;font-size:12px;white-space:pre-wrap">{{ viewToolData.tool.bodyTemplate }}</pre>
        </div>
      </template>
      <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.test-intro {
  margin-bottom: 16px;
  color: #666;
}
.test-intro strong {
  color: #333;
}
.test-params {
  margin-bottom: 16px;
}
.test-param-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.test-param-label {
  width: 160px;
  flex-shrink: 0;
  font-size: 14px;
  font-weight: 500;
}
.required-star {
  color: #e74c3c;
  margin-right: 2px;
}
.test-param-meta {
  display: block;
  font-size: 11px;
  color: #999;
  font-weight: 400;
}
.test-param-control {
  flex: 1;
}
.test-params-empty {
  color: #999;
  margin-bottom: 16px;
}
.test-action {
  margin-bottom: 16px;
}
.test-result {
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 16px;
  background: #fafafa;
}
.test-result.test-passed {
  border-color: #67c23a;
  background: #f0f9eb;
}
.test-result.test-failed {
  border-color: #f56c6c;
  background: #fef0f0;
}
.test-result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.test-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
}
.test-badge.pass {
  background: #67c23a;
  color: #fff;
}
.test-badge.fail {
  background: #f56c6c;
  color: #fff;
}
.test-status-code {
  font-family: monospace;
  font-size: 14px;
}
.test-duration {
  color: #999;
  font-size: 13px;
}
.test-error-msg {
  color: #e74c3c;
  margin-bottom: 12px;
  font-size: 13px;
}
.test-section {
  margin-top: 12px;
}
.test-section h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #555;
}
.test-line {
  font-family: monospace;
  font-size: 12px;
  color: #333;
  margin-bottom: 2px;
  word-break: break-all;
}
.test-headers {
  margin-top: 4px;
}
.test-body {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.test-truncated {
  font-size: 12px;
  color: #e67e22;
  margin-top: 4px;
}
.confirm-summary {
  padding: 8px 0;
}
.confirm-passed-badge {
  display: inline-block;
  background: #67c23a;
  color: #fff;
  padding: 4px 14px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 16px;
}
.confirm-section {
  margin-top: 20px;
}
.confirm-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #333;
}
.confirm-test-result {
  margin-top: 20px;
  padding: 12px 16px;
  background: #f0f9eb;
  border-radius: 6px;
}
.confirm-test-result h4 {
  margin: 0 0 4px;
  font-size: 14px;
}
.confirm-test-result p {
  margin: 0;
  color: #555;
}
.template-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.compact-basic-row :deep(.el-form-item) {
  margin-bottom: 12px;
}
.template-grid :deep(.el-form-item) {
  margin-bottom: 12px;
}
.var-table-ep {
  width: 100%;
}

/* M4 dialog: scrollable body when content overflows */
.m4-dialog .el-dialog__body {
  max-height: 65vh;
  overflow-y: auto;
}
</style>
