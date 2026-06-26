<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { createSession, sendChatMessage, closeSession } from '../api/ai-chat'
import { listServers, getConnectionInfo } from '../api/servers'
import { listConfigs, createConfig, updateConfig, deleteConfig } from '../api/ai-config'
import type { McpServer } from '../api/servers'
import type { AiModelConfig } from '../api/ai-config'
import type { SessionInfo, ChatReply, ToolCallInfo } from '../api/ai-chat'
import { ElMessage, ElMessageBox } from 'element-plus'

// ---------- 聊天 ----------
const servers = ref<McpServer[]>([])
const configs = ref<AiModelConfig[]>([])
const session = ref<SessionInfo | null>(null)
const selectedServerId = ref<number | null>(null)
const selectedConfigId = ref<number | null>(null)
const mcpKeyLoading = ref(false)
const messages = ref<{ role: string; content: string; toolCalls?: ToolCallInfo[] }[]>([])
const inputText = ref('')
const sending = ref(false)
const chatLogEl = ref<HTMLElement | null>(null)

// ---------- 模型配置管理 ----------
const configDialog = ref(false)
const editingConfig = ref<AiModelConfig | null>(null)
const configForm = ref({ name: '', baseUrl: '', apiKey: '', model: 'gpt-4o', timeoutSeconds: 60, enabled: false })
const loadingConfigs = ref(false)

// ---------- 模型配置管理列表弹窗 ----------
const configManagerDialog = ref(false)

async function openConfigManager() {
  configs.value = await listConfigs()
  configManagerDialog.value = true
}

async function load() {
  servers.value = (await listServers()).filter(s => s.status === 'PUBLISHED')
  configs.value = await listConfigs()
}

async function onServerChange() {
  if (!selectedServerId.value) return
  mcpKeyLoading.value = true
  try {
    const info = await getConnectionInfo(selectedServerId.value)
    if (info.mcpKey) {
      // stored for use in startSession
      ;(window as any).__mcpKeyCache = info.mcpKey
    }
  } catch { /* ignore */ } finally {
    mcpKeyLoading.value = false
  }
}

async function startSession() {
  if (!selectedServerId.value || !selectedConfigId.value) {
    ElMessage.warning('请选择 Server 和模型配置')
    return
  }
  try {
    if (session.value) {
      await closeSession(session.value.sessionId).catch(() => {})
    }
    const mcpKey = (window as any).__mcpKeyCache || undefined
    session.value = await createSession(selectedServerId.value, selectedConfigId.value, mcpKey)
    messages.value = []
    messages.value.push({
      role: 'system',
      content: `已加载 Server "${session.value.serverName}"（${session.value.tools.length} 个 Tool）：${session.value.tools.join(', ')}`,
    })
    ElMessage.success('会话已创建')
  } catch (e: any) {
    ElMessage.error('创建会话失败: ' + (e.message || '未知错误'))
  }
}

async function send() {
  const msg = inputText.value.trim()
  if (!msg || !session.value || sending.value) return
  inputText.value = ''
  messages.value.push({ role: 'user', content: msg })
  sending.value = true
  scrollToBottom()
  try {
    const reply = await sendChatMessage(session.value.sessionId, msg)
    messages.value.push({ role: 'assistant', content: reply.reply, toolCalls: reply.toolCalls?.length ? reply.toolCalls : undefined })
  } catch (e: any) {
    messages.value.push({ role: 'assistant', content: '请求出错: ' + (e.message || '未知错误') })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => { if (chatLogEl.value) chatLogEl.value.scrollTop = chatLogEl.value.scrollHeight })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() }
}

// ---------- 模型配置 CRUD ----------
function openConfigCreate() {
  editingConfig.value = null
  configForm.value = { name: '', baseUrl: '', apiKey: '', model: 'gpt-4o', timeoutSeconds: 60, enabled: false }
  configDialog.value = true
}

function openConfigEdit(c: AiModelConfig) {
  editingConfig.value = c
  configForm.value = { name: c.name, baseUrl: c.baseUrl, apiKey: '', model: c.model, timeoutSeconds: c.timeoutSeconds, enabled: c.enabled }
  configDialog.value = true
}

async function saveConfig() {
  try {
    if (editingConfig.value) {
      const data: any = { name: configForm.value.name, baseUrl: configForm.value.baseUrl, model: configForm.value.model, timeoutSeconds: configForm.value.timeoutSeconds, enabled: configForm.value.enabled }
      if (configForm.value.apiKey) data.apiKey = configForm.value.apiKey
      await updateConfig(editingConfig.value.id, data)
    } else {
      await createConfig(configForm.value as any)
    }
    configDialog.value = false
    configs.value = await listConfigs()
    ElMessage.success(editingConfig.value ? '配置已更新' : '配置已创建')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

async function removeConfig(c: AiModelConfig) {
  try {
    await ElMessageBox.confirm(`确定删除 "${c.name}"？`, '确认', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteConfig(c.id)
    configs.value = await listConfigs()
    ElMessage.success('已删除')
  } catch { /* cancelled */ }
}

watch(selectedServerId, () => { onServerChange() })

onMounted(load)
</script>

<template>
  <section class="page ai-chat-page">
    <header class="page-header">
      <div>
        <h2>AI 聊天测试</h2>
        <p class="page-intro">选择已发布的 MCP Server 和 AI 模型，通过自然语言对话验证 Tool 是否能被正确理解和调用。</p>
      </div>
    </header>

    <div class="chat-layout">
      <!-- left: config -->
      <aside class="chat-sidebar">
        <div class="sidebar-section">
          <label class="field-label">MCP Server</label>
          <el-select v-model="selectedServerId" style="width:100%" placeholder="选择已发布的 Server…">
            <el-option v-for="s in servers" :key="s.id" :value="s.id" :label="s.name" />
          </el-select>
        </div>
        <div class="sidebar-section">
          <div class="field-row">
            <label class="field-label">AI 模型</label>
            <el-button size="small" text @click="openConfigManager">管理</el-button>
          </div>
          <el-select v-model="selectedConfigId" style="width:100%" placeholder="选择模型…">
            <el-option v-for="c in configs" :key="c.id" :value="c.id" :label="c.name">
              <span>{{ c.name }}</span>
              <span v-if="c.enabled" style="color:#67c23a;font-size:12px;float:right">✓ 启用</span>
            </el-option>
          </el-select>
        </div>
        <el-button type="primary" style="width:100%;margin-bottom:16px" :disabled="!selectedServerId || !selectedConfigId || mcpKeyLoading" @click="startSession">{{ mcpKeyLoading ? '加载中…' : '开始会话' }}</el-button>

        <div v-if="session" class="sidebar-section">
          <label class="field-label">已加载 Tool（{{ session.tools.length }}）</label>
          <div class="tool-list">
            <div v-for="t in session.tools" :key="t" class="tool-item">☑ {{ t }}</div>
          </div>
        </div>
      </aside>

      <!-- right: chat -->
      <div class="chat-main">
        <div v-if="!session" class="chat-placeholder">
          <p>请选择左侧配置后点击"开始会话"</p>
        </div>
        <template v-else>
          <div ref="chatLogEl" class="chat-log">
            <div v-for="(msg, i) in messages" :key="i" class="chat-msg" :class="msg.role">
              <div class="msg-label">{{ msg.role === 'user' ? '你' : msg.role === 'system' ? '系统' : 'AI' }}</div>
              <div class="msg-content" v-if="msg.content">{{ msg.content }}</div>
              <div v-if="msg.toolCalls?.length" class="tool-calls">
                <div v-for="(tc, j) in msg.toolCalls" :key="j" class="tool-call-card">
                  <div class="tc-header">
                    <code>{{ tc.toolName }}</code>
                    <span class="tc-status" :class="tc.success ? 'ok' : 'err'">{{ tc.success ? '✓' : '✗' }} {{ tc.statusCode }}</span>
                  </div>
                  <div v-if="Object.keys(tc.params).length" class="tc-section">
                    <label>参数</label>
                    <pre>{{ JSON.stringify(tc.params, null, 2) }}</pre>
                  </div>
                  <div v-if="tc.resultText" class="tc-section">
                    <label>结果</label>
                    <pre>{{ tc.resultText }}</pre>
                  </div>
                  <div v-if="tc.error" class="tc-section">
                    <label>错误</label>
                    <pre class="tc-error">{{ tc.error }}</pre>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="sending" class="chat-msg assistant">
              <div class="msg-label">AI</div>
              <div class="msg-content thinking">思考中…</div>
            </div>
          </div>
          <div class="chat-input">
            <el-input v-model="inputText" type="textarea" :rows="2" placeholder="输入消息..." :disabled="!session || sending" @keydown="handleKeydown" />
            <el-button type="primary" :disabled="!inputText.trim() || sending" :loading="sending" @click="send">发送</el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- 模型配置弹窗 -->
    <el-dialog v-model="configDialog" :title="editingConfig ? '编辑模型配置' : '新增模型配置'" width="520px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="configForm.name" placeholder="my-openai-config" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="configForm.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>
        <el-form-item label="模型" required>
          <el-input v-model="configForm.model" placeholder="gpt-4o" />
        </el-form-item>
        <el-form-item label="API Key" :required="!editingConfig">
          <el-input v-model="configForm.apiKey" type="password" show-password :placeholder="editingConfig ? '留空则不修改' : ''" />
          <p v-if="editingConfig" style="color:#999;font-size:12px;margin-top:4px">留空表示不修改已存储的 Key</p>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超时 (秒)">
              <el-input-number v-model="configForm.timeoutSeconds" :min="10" :max="300" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label=" ">
              <el-switch v-model="configForm.enabled" active-text="启用" inactive-text="禁用" style="margin-top:6px" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="configDialog = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模型配置管理弹窗 -->
    <el-dialog v-model="configManagerDialog" title="模型配置管理" width="640px">
      <div style="text-align:right;margin-bottom:12px">
        <el-button size="small" type="primary" @click="openConfigCreate">+ 新增</el-button>
      </div>
      <el-table :data="configs" stripe size="small" style="width:100%">
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column label="模型" width="120">
          <template #default="{ row }"><code>{{ row.model }}</code></template>
        </el-table-column>
        <el-table-column label="Base URL" min-width="180">
          <template #default="{ row }"><code class="url-cell">{{ row.baseUrl }}</code></template>
        </el-table-column>
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <span class="badge" :class="row.enabled ? 'published' : 'draft'">{{ row.enabled ? '启用' : '禁用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" text @click="openConfigEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="removeConfig(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="configManagerDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.ai-chat-page .page-header { margin-bottom: 16px; }
.chat-layout { display: flex; gap: 16px; height: calc(100vh - 200px); min-height: 500px; }
.chat-sidebar {
  width: 280px; flex-shrink: 0;
  background: #fff; border-radius: 8px; padding: 20px;
  overflow-y: auto;
}
.sidebar-section { margin-bottom: 12px; }
.field-label { display: block; font-size: 13px; font-weight: 600; color: #666; margin-bottom: 6px; }
.field-row { display: flex; justify-content: space-between; align-items: center; }
.tool-list { max-height: 200px; overflow-y: auto; }
.tool-item { padding: 4px 0; font-size: 13px; color: #409eff; }
.config-item { padding: 8px 0; border-bottom: 1px solid #f0f0eb; }
.config-item:last-child { border-bottom: none; }
.config-row { display: flex; justify-content: space-between; align-items: center; }
.config-name { font-size: 13px; font-weight: 500; }
.config-actions { margin-top: 4px; display: flex; gap: 4px; }
.chat-main {
  flex: 1; display: flex; flex-direction: column;
  background: #fff; border-radius: 8px; overflow: hidden;
}
.chat-placeholder { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; font-size: 15px; }
.chat-log { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.chat-msg { max-width: 85%; }
.chat-msg.user { align-self: flex-end; }
.chat-msg.assistant, .chat-msg.system { align-self: flex-start; }
.msg-label { font-size: 12px; font-weight: 600; color: #888; margin-bottom: 4px; }
.msg-content { padding: 10px 14px; border-radius: 8px; line-height: 1.5; font-size: 14px; white-space: pre-wrap; }
.chat-msg.user .msg-content { background: #409eff; color: #fff; }
.chat-msg.assistant .msg-content { background: #f0f0eb; color: #333; }
.chat-msg.system .msg-content { background: #fdf6ec; color: #7c6a3a; font-size: 13px; }
.thinking { color: #999; font-style: italic; }
.tool-calls { margin-top: 8px; display: flex; flex-direction: column; gap: 8px; }
.tool-call-card { background: #f8f8f4; border: 1px solid #e8e8e0; border-radius: 6px; padding: 10px 12px; }
.tc-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.tc-header code { font-weight: 600; font-size: 13px; }
.tc-status { font-size: 12px; }
.tc-status.ok { color: #67c23a; }
.tc-status.err { color: #f56c6c; }
.tc-section { margin-top: 6px; }
.tc-section label { font-size: 12px; font-weight: 600; color: #888; display: block; margin-bottom: 2px; }
.tc-section pre { margin: 0; font-size: 12px; background: #fff; padding: 6px 8px; border-radius: 4px; white-space: pre-wrap; word-break: break-all; max-height: 200px; overflow-y: auto; }
.tc-error { color: #f56c6c; }
.chat-input { display: flex; gap: 8px; padding: 12px 16px; border-top: 1px solid #eee; }
.chat-input .el-textarea { flex: 1; }
</style>
