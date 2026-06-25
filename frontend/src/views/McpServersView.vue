<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listServers, createServer, updateServer, deleteServer, getServerTools, bindTool, unbindTool } from '../api/servers'
import { listTools, getMappings } from '../api/http-tools'
import type { McpServer, McpServerTool } from '../api/servers'
import type { HttpTool, ParamMapping } from '../api/http-tools'
import { ElMessage, ElMessageBox } from 'element-plus'

const servers = ref<McpServer[]>([])
const loading = ref(false)

// 创建/编辑弹窗
const formDialog = ref(false)
const editing = ref<McpServer | null>(null)
const form = ref({ code: '', name: '', description: '' })

// 编组弹窗
const detailDialog = ref(false)
const detailServer = ref<McpServer | null>(null)
const boundTools = ref<McpServerTool[]>([])
const allTools = ref<HttpTool[]>([])
const availableTools = ref<HttpTool[]>([])
const bindToolId = ref('')
const toolMappings = ref<Record<number, ParamMapping[]>>({})
const expandedToolId = ref<number | null>(null)

async function load() {
  loading.value = true
  try { servers.value = await listServers() } finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  form.value = { code: '', name: '', description: '' }
  formDialog.value = true
}

function openEdit(s: McpServer) {
  editing.value = s
  form.value = { code: s.code, name: s.name, description: s.description }
  formDialog.value = true
}

async function save() {
  try {
    if (editing.value) {
      await updateServer(editing.value.id, form.value)
    } else {
      await createServer(form.value)
    }
    formDialog.value = false
    await load()
    ElMessage.success(editing.value ? '配置已更新' : '配置已创建')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

async function remove(s: McpServer) {
  try {
    await ElMessageBox.confirm(`确定删除配置 "${s.name}"？`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteServer(s.id)
    await load()
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

async function openDetail(s: McpServer) {
  detailServer.value = s
  boundTools.value = await getServerTools(s.id)
  allTools.value = await listTools()
  toolMappings.value = {}
  expandedToolId.value = null
  availableTools.value = allTools.value.filter(t => !boundTools.value.some(bt => bt.toolId === t.id))
  bindToolId.value = ''
  detailDialog.value = true
}

async function doBind() {
  if (!detailServer.value || !bindToolId.value) return
  await bindTool(detailServer.value.id, Number(bindToolId.value))
  boundTools.value = await getServerTools(detailServer.value.id)
  availableTools.value = allTools.value.filter(t => !boundTools.value.some(bt => bt.toolId === t.id))
  bindToolId.value = ''
}

async function doUnbind(toolId: number) {
  if (!detailServer.value) return
  await unbindTool(detailServer.value.id, toolId)
  boundTools.value = await getServerTools(detailServer.value.id)
  availableTools.value = allTools.value.filter(t => !boundTools.value.some(bt => bt.toolId === t.id))
}

async function toggleToolParams(toolId: number) {
  if (expandedToolId.value === toolId) { expandedToolId.value = null; return }
  expandedToolId.value = toolId
  if (!toolMappings.value[toolId]) {
    try { toolMappings.value[toolId] = await getMappings(toolId) } catch { toolMappings.value[toolId] = [] }
  }
}

function getToolById(toolId: number): HttpTool | undefined {
  return allTools.value.find(t => t.id === toolId)
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h2>发布配置</h2>
        <p class="page-intro">把已注册的接口自由组合成一个 MCP Server。一个接口可以加入多个 Server，配置完成后可发布给 AI 客户端调用。</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新建配置</el-button>
    </header>

    <el-table v-loading="loading" :data="servers" stripe style="width:100%">
      <el-table-column label="代码" width="140">
        <template #default="{ row }"><code>{{ row.code }}</code></template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="badge" :class="row.status.toLowerCase()">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button size="small" text @click="openDetail(row)">编组</el-button>
          <el-button size="small" text @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="formDialog" :title="editing ? '编辑配置' : '新建配置'" width="520px">
      <el-form label-position="top">
        <el-form-item label="代码" required>
          <el-input v-model="form.code" placeholder="my-api-server" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编组弹窗 -->
    <el-dialog v-model="detailDialog" :title="detailServer?.name + ' — 接口编组'" width="720px">
      <div class="bind-section">
        <el-row :gutter="8">
          <el-col :span="18">
            <el-select v-model="bindToolId" style="width:100%" placeholder="选择要绑定的接口…">
              <el-option v-for="t in availableTools" :key="t.id" :value="t.id" :label="`[${t.httpMethod}] ${t.name} — ${t.urlTemplate}`" />
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-button type="primary" :disabled="!bindToolId" @click="doBind" style="width:100%">+ 绑定</el-button>
          </el-col>
        </el-row>
      </div>

      <div v-if="boundTools.length" class="bound-list">
        <div v-for="bt in boundTools" :key="bt.id" class="bound-item">
          <div class="bound-row" @click="toggleToolParams(bt.toolId)">
            <span class="bound-expand">{{ expandedToolId === bt.toolId ? '▾' : '▸' }}</span>
            <code class="method">{{ getToolById(bt.toolId)?.httpMethod }}</code>
            <span class="bound-name">{{ getToolById(bt.toolId)?.name }}</span>
            <span class="url-cell bound-url">{{ getToolById(bt.toolId)?.urlTemplate }}</span>
            <el-button size="small" text type="danger" @click.stop="doUnbind(bt.toolId)">解绑</el-button>
          </div>
          <div v-if="expandedToolId === bt.toolId" class="bound-detail">
            <el-table v-if="toolMappings[bt.toolId]?.length" :data="toolMappings[bt.toolId]" stripe size="small" style="width:100%">
              <el-table-column label="参数名">
                <template #default="{ row }"><code>{{ row.name }}</code></template>
              </el-table-column>
              <el-table-column label="来源" width="80">
                <template #default="{ row }">{{ { PATH: 'Path', QUERY: 'Query', HEADER: 'Header', BODY: 'Body' }[row.paramSource] ?? row.paramSource }}</template>
              </el-table-column>
              <el-table-column label="位置" width="120">
                <template #default="{ row }"><code>{{ row.paramLocation }}</code></template>
              </el-table-column>
              <el-table-column label="必填" width="50" align="center">
                <template #default="{ row }">{{ row.required ? '✓' : '-' }}</template>
              </el-table-column>
            </el-table>
            <p v-else class="param-empty">暂无参数映射</p>
          </div>
        </div>
      </div>
      <p v-else class="empty">尚未绑定 Tool</p>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>
