<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { listServers, createServer, updateServer, deleteServer, getServerTools, bindTool, unbindTool, publishServer, unpublishServer, getConnectionInfo, resetMcpKey } from '../api/servers'
import type { McpServer, McpServerTool, ConnectionInfo } from '../api/servers'
import { listTools, getMappings } from '../api/http-tools'
import type { HttpTool, ParamMapping } from '../api/http-tools'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, QuestionFilled, Search } from '@element-plus/icons-vue'

function handleCmd(cmd: string, s: McpServer) {
  switch (cmd) {
    case 'detail': openDetail(s); break
    case 'edit': openEdit(s); break
    case 'publish': doPublish(s); break
    case 'conn': showConnectionInfo(s); break
    case 'resetKey': doResetKey(s); break
    case 'unpublish': doUnpublish(s); break
    case 'delete': remove(s); break
  }
}

const servers = ref<McpServer[]>([])
const loading = ref(false)
const searchQuery = ref('')
const filteredServers = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return servers.value
  return servers.value.filter(s =>
    s.name.toLowerCase().includes(q) ||
    s.code.toLowerCase().includes(q) ||
    (s.description || '').toLowerCase().includes(q)
  )
})

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
const serverToolCounts = ref<Record<number, number>>({})
const allHttpTools = ref<HttpTool[]>([])

// 发布弹窗
const publishDialog = ref(false)
const publishServerId = ref<number>(0)
const publishMcpKey = ref('')
const publishAutoGenerate = ref(true)
const publishResult = ref<{ rawMcpKey: string } | null>(null)

// 连接信息弹窗
const connDialog = ref(false)
const connInfo = ref<ConnectionInfo | null>(null)
const connServerName = ref('')

// Key 重置弹窗
const resetKeyDialog = ref(false)
const resetServerId = ref<number>(0)
const resetServerName = ref('')
const resetMcpKeyInput = ref('')
const resetAutoGenerate = ref(true)
const resetResult = ref('')

async function load() {
  loading.value = true
  try {
    servers.value = await listServers()
    allHttpTools.value = await listTools()
    await loadToolCounts()
  } finally { loading.value = false }
}

async function loadToolCounts() {
  const counts: Record<number, number> = {}
  await Promise.all(servers.value.map(async s => {
    try {
      const tools = await getServerTools(s.id)
      counts[s.id] = tools.length
    } catch { counts[s.id] = 0 }
  }))
  serverToolCounts.value = counts
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

async function doPublish(s: McpServer) {
  publishServerId.value = s.id
  publishMcpKey.value = ''
  publishAutoGenerate.value = true
  publishResult.value = null
  publishDialog.value = true
}

async function confirmPublish() {
  try {
    const key = publishAutoGenerate.value ? undefined : publishMcpKey.value || undefined
    const result = await publishServer(publishServerId.value, key)
    publishResult.value = { rawMcpKey: result.rawMcpKey }
    await load()
    ElMessage.success('已发布')
  } catch (e: any) {
    ElMessage.error('发布失败: ' + (e.message || '未知错误'))
  }
}

async function doUnpublish(s: McpServer) {
  try {
    await ElMessageBox.confirm(`确定取消发布 "${s.name}"？`, '确认', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    await unpublishServer(s.id)
    await load()
    ElMessage.success('已取消发布')
  } catch { /* cancelled */ }
}

async function showConnectionInfo(s: McpServer) {
  connServerName.value = s.name
  try {
    connInfo.value = await getConnectionInfo(s.id)
  } catch {
    connInfo.value = { serverCode: s.code, mcpPath: `/mcp/${s.code}`, mcpKey: null }
  }
  connDialog.value = true
}

async function doResetKey(s: McpServer) {
  resetServerId.value = s.id
  resetServerName.value = s.name
  resetMcpKeyInput.value = ''
  resetAutoGenerate.value = true
  resetResult.value = ''
  resetKeyDialog.value = true
}

async function confirmResetKey() {
  try {
    const key = resetAutoGenerate.value ? undefined : resetMcpKeyInput.value || undefined
    const result = await resetMcpKey(resetServerId.value, key)
    resetResult.value = result.rawMcpKey
    ElMessage.success('Key 已重置')
  } catch (e: any) {
    ElMessage.error('重置失败: ' + (e.message || '未知错误'))
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard?.writeText(text)
  ElMessage.success('已复制')
}

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <h2>发布 MCP 服务</h2>
        <p class="page-intro">把已注册的接口自由组合成一个 MCP Server。一个接口可以加入多个 Server，配置完成后可发布给 AI 客户端调用。</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新建服务</el-button>
    </header>

    <div class="search-bar">
      <el-input v-model="searchQuery" placeholder="搜索服务名称、端点或描述…" clearable prefix-icon="Search" />
    </div>

    <el-table v-loading="loading" :data="filteredServers" stripe style="width:100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column label="绑定工具" width="80">
        <template #default="{ row }">{{ serverToolCounts[row.id] ?? '—' }}</template>
      </el-table-column>
      <el-table-column min-width="200">
        <template #header>
          <span class="endpoint-header">
            MCP 端点
            <el-tooltip placement="top" effect="dark" popper-class="mcp-protocol-tooltip">
              <template #content>
                <div class="tooltip-title">Streamable HTTP</div>
                <div>每个服务都是独立 endpoint：<code>/mcp/{serverCode}</code></div>
                <div><code>Mcp-Session-Id</code> 是可选会话机制；本平台初始化时会返回。</div>
                <div>因此后续 <code>tools/list</code>、<code>tools/call</code> 需要携带该 session 和 MCP Key。</div>
                <div><code>GET</code> 打开 SSE 事件流，<code>DELETE</code> 关闭会话。</div>
              </template>
              <el-icon class="help-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
        </template>
        <template #default="{ row }"><code class="url-cell">/mcp/{{ row.code }}</code></template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <span class="badge" :class="row.status.toLowerCase()" style="white-space:nowrap">{{ row.status === 'PUBLISHED' ? '已发布' : row.status === 'DRAFT' ? '草稿' : row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="100">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleDateString('zh-CN') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" size="small" text type="primary" @click="doPublish(row)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" size="small" text @click="showConnectionInfo(row)">连接</el-button>
          <el-button size="small" text @click="openDetail(row)">编组</el-button>
          <el-dropdown trigger="click" @command="(cmd:string) => handleCmd(cmd, row)">
            <el-button size="small" text type="info">
              ···
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="resetKey" style="color:#e6a23c">重置 Key</el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'PUBLISHED'" command="unpublish" style="color:#e6a23c">取消发布</el-dropdown-item>
                <el-dropdown-item command="delete" style="color:#f56c6c" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="formDialog" :title="editing ? '编辑服务' : '新建服务'" width="520px">
      <el-form label-position="top">
        <el-form-item label="MCP 端点" required>
          <el-input v-model="form.code" placeholder="my-api-server" />
          <p class="form-hint">唯一标识，作为 MCP 请求路径的一部分，创建后不可修改。如：/mcp/my-api-server</p>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：用户服务、订单服务" />
          <p class="form-hint">给这个 MCP 服务起个容易识别的名字</p>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简单说明这个服务的作用…" />
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

    <!-- 发布弹窗 -->
    <el-dialog v-model="publishDialog" title="发布 MCP Server" width="520px" :close-on-click-modal="false">
      <template v-if="!publishResult">
        <p style="margin-bottom:16px">发布后将对外提供服务。MCP Key 自动生成，可在连接信息中查看。</p>
      </template>
      <template v-else>
        <el-alert type="success" title="发布成功" show-icon style="margin-bottom:16px" />
        <div class="key-display">
          <label>MCP Key</label>
          <div class="key-row">
            <code class="key-value">{{ publishResult.rawMcpKey }}</code>
            <el-button size="small" @click="copyToClipboard(publishResult.rawMcpKey)">复制</el-button>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="publishDialog = false">{{ publishResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!publishResult" type="primary" @click="confirmPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 连接信息弹窗 -->
    <el-dialog v-model="connDialog" title="连接信息" width="580px">
      <div v-if="connInfo" class="conn-info">
        <div class="info-row">
          <label>Server 代码</label>
          <code>{{ connInfo.serverCode }}</code>
        </div>
        <div class="info-row">
          <label>MCP 端点路径</label>
          <code>{{ connInfo.mcpPath }}</code>
        </div>
        <div class="info-row">
          <label>协议类型</label>
          <code>Streamable HTTP</code>
          <p class="protocol-help">
            使用同一个端点处理 <code>POST</code>、<code>GET</code> 和 <code>DELETE</code>。
            <code>Mcp-Session-Id</code> 在协议中是可选机制；本平台会在初始化响应中返回该头。
            <code>POST</code> 请求需发送 JSON-RPC 2.0，建议同时声明
            <code>Accept: application/json, text/event-stream</code>。
          </p>
        </div>
        <div class="info-row" v-if="connInfo.mcpKey">
          <label>MCP Key</label>
          <div class="key-row">
            <code class="key-value">{{ connInfo.mcpKey }}</code>
            <el-button size="small" @click="copyToClipboard(connInfo.mcpKey!)">复制</el-button>
          </div>
        </div>
        <div class="info-row">
          <label>初始化会话</label>
          <pre class="curl-example">curl -i -X POST http://localhost:8080{{ connInfo.mcpPath }} \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"demo-client","version":"1.0.0"}}}'</pre>
          <p class="protocol-help">本平台响应头会返回 <code>Mcp-Session-Id</code>；后续请求需携带它。</p>
        </div>
        <div class="info-row">
          <label>列出工具</label>
          <pre class="curl-example">curl -X POST http://localhost:8080{{ connInfo.mcpPath }} \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "MCP-Protocol-Version: 2025-06-18" \
  -H "Mcp-Session-Id: &lt;session-id&gt;" \
  -H "Authorization: Bearer &lt;mcp-key&gt;" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'</pre>
        </div>
        <div class="info-row">
          <label>Claude Desktop 配置示例</label>
          <pre class="curl-example">{
  "mcpServers": {
    "{{ connServerName }}": {
      "type": "streamable-http",
      "url": "http://your-host:8080{{ connInfo.mcpPath }}"
    }
  }
}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="connDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 重置 Key 弹窗 -->
    <el-dialog v-model="resetKeyDialog" title="重置 MCP Key" width="480px" :close-on-click-modal="false">
      <template v-if="!resetResult">
        <p style="margin-bottom:16px">重置后，使用旧 Key 的客户端将立即无法访问。</p>
        <el-form label-position="top">
          <el-form-item>
            <el-checkbox v-model="resetAutoGenerate" label="自动生成新 Key" />
          </el-form-item>
          <el-form-item v-if="!resetAutoGenerate" label="自定义新 Key">
            <el-input v-model="resetMcpKeyInput" placeholder="输入自定义 Key，留空则自动生成" />
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-alert type="success" title="Key 已重置" show-icon style="margin-bottom:16px" />
        <div class="key-display">
          <label>新的 MCP Key</label>
          <div class="key-row">
            <code class="key-value">{{ resetResult }}</code>
            <el-button size="small" @click="copyToClipboard(resetResult)">复制</el-button>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="resetKeyDialog = false">{{ resetResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!resetResult" type="danger" @click="confirmResetKey">确认重置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.key-display { margin-bottom: 16px; }
.key-display label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #666; }
.key-row { display: flex; align-items: center; gap: 8px; }
.key-value {
  flex: 1; padding: 8px 12px; background: #f5f5f0; border-radius: 4px;
  font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 14px;
  word-break: break-all;
}
.conn-info .info-row { margin-bottom: 16px; }
.conn-info .info-row label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 4px; color: #666; }
.curl-example {
  background: #1e1e1e; color: #d4d4d4; padding: 12px 16px; border-radius: 6px;
  font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 13px;
  overflow-x: auto; white-space: pre; margin: 0;
}
.endpoint-header {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.help-icon {
  color: #909399;
  cursor: help;
  font-size: 15px;
  vertical-align: middle;
}
.help-icon:hover {
  color: #409eff;
}
.protocol-help {
  margin: 6px 0 0;
  color: #606266;
  line-height: 1.6;
}
:global(.mcp-protocol-tooltip) {
  max-width: 420px;
  line-height: 1.6;
}
:global(.mcp-protocol-tooltip code) {
  color: #f5d76e;
}
:global(.mcp-protocol-tooltip .tooltip-title) {
  margin-bottom: 4px;
  font-weight: 700;
  color: #fff;
}
</style>
