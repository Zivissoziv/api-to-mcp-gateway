<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { listTools, createTool, updateTool, deleteTool, getMappings } from '../api/http-tools'
import type { HttpTool, ParamMapping } from '../api/http-tools'
import { ElMessage, ElMessageBox } from 'element-plus'

const tools = ref<HttpTool[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)

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
  form.value = { name: '', description: '', httpMethod: 'GET', urlTemplate: '', headers: '' }
  bodyTemplate.value = ''
  headerTemplate.value = ''
  vars.value = []
  dialogVisible.value = true
}

async function openEdit(t: HttpTool) {
  isEditing.value = true
  form.value = {
    name: t.name, description: t.description, httpMethod: t.httpMethod,
    urlTemplate: t.urlTemplate, headers: t.headers ?? '',
  }
  bodyTemplate.value = ''
  headerTemplate.value = ''
  try {
    const ms = await getMappings(t.id)
    const bodyParams = ms.filter(m => m.paramSource === 'BODY')
    if (bodyParams.length > 0) {
      bodyTemplate.value = JSON.stringify(Object.fromEntries(bodyParams.map(m => [m.name, `$\{${m.name}\}`])), null, 2)
    }
    const headerParams = ms.filter(m => m.paramSource === 'HEADER')
    if (headerParams.length > 0) {
      headerTemplate.value = JSON.stringify(Object.fromEntries(headerParams.map(m => [m.name, `$\{${m.name}\}`])), null, 2)
    }
    setTimeout(() => parseVarsFromMappings(ms), 0)
  } catch {
    vars.value = []
  }
  dialogVisible.value = true
}

async function save() {
  try {
    const payload = { ...form.value, parameterMappings: buildParamMappings() }
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

async function openEditClick(t: HttpTool) {
  editingId.value = t.id
  await openEdit(t)
}

async function remove(t: HttpTool) {
  try {
    await ElMessageBox.confirm(`确定删除接口 "${t.name}"？`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteTool(t.id)
    await load()
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
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

    <el-table v-loading="loading" :data="tools" stripe style="width:100%">
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
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button size="small" text @click="openEditClick(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑接口' : '注册接口'" width="1100px" top="5vh" destroy-on-close>
      <el-form label-position="top" @submit.prevent="save">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
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
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-row :gutter="24">
          <!-- 左侧：模板编辑 -->
          <el-col :span="12">
            <el-form-item label="URL 模板">
              <template #label><span>URL 模板 <span class="editor-hint">用 {id} 或 ${name} 标记变量</span></span></template>
              <el-input v-model="form.urlTemplate" placeholder="https://api.example.com/users/{id}" />
            </el-form-item>
            <el-form-item label="请求头模板">
              <template #label><span>请求头模板 <span class="editor-hint">用 ${xxx} 标记变量</span></span></template>
              <el-input v-model="headerTemplate" type="textarea" :rows="4" class="mono-input" placeholder='{"Authorization": "Bearer ${token}"}' />
            </el-form-item>
            <el-form-item label="请求体模板">
              <template #label><span>请求体模板 <span class="editor-hint">用 ${xxx} 标记变量</span></span></template>
              <el-input v-model="bodyTemplate" type="textarea" :rows="12" class="mono-input" placeholder='{\n  "name": "${name}"\n}' />
            </el-form-item>
          </el-col>

          <!-- 右侧：变量定义 -->
          <el-col :span="12">
            <div class="var-header">
              <span class="var-header-label">变量定义</span>
              <span class="editor-hint" v-if="vars.length === 0">编辑左侧模板后将自动识别</span>
              <span class="editor-hint" v-else>共 {{ vars.length }} 个变量</span>
            </div>
            <el-table v-if="vars.length" :data="vars" stripe size="small" max-height="240" class="var-table-ep">
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

            <!-- Schema 预览 -->
            <div v-if="computedSchema" class="schema-preview-inline">
              <el-form-item label="自动生成的 AI 输入 Schema">
                <pre class="schema-code code-sm">{{ computedSchema }}</pre>
              </el-form-item>
            </div>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">{{ isEditing ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
