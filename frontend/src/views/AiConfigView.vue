<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listConfigs, createConfig, updateConfig, deleteConfig } from '../api/ai-config'
import type { AiModelConfig } from '../api/ai-config'
import { ElMessage, ElMessageBox } from 'element-plus'

const configs = ref<AiModelConfig[]>([])
const loading = ref(false)

const formDialog = ref(false)
const editing = ref<AiModelConfig | null>(null)
const form = ref({ name: '', baseUrl: '', apiKey: '', model: '', timeoutSeconds: 60, enabled: false })

async function load() {
  loading.value = true
  try { configs.value = await listConfigs() } finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', baseUrl: '', apiKey: '', model: 'gpt-4o', timeoutSeconds: 60, enabled: false }
  formDialog.value = true
}

function openEdit(c: AiModelConfig) {
  editing.value = c
  form.value = {
    name: c.name, baseUrl: c.baseUrl, apiKey: '',
    model: c.model, timeoutSeconds: c.timeoutSeconds, enabled: c.enabled,
  }
  formDialog.value = true
}

async function save() {
  try {
    if (editing.value) {
      const data: any = { name: form.value.name, baseUrl: form.value.baseUrl, model: form.value.model, timeoutSeconds: form.value.timeoutSeconds, enabled: form.value.enabled }
      if (form.value.apiKey) data.apiKey = form.value.apiKey
      await updateConfig(editing.value.id, data)
    } else {
      await createConfig(form.value as any)
    }
    formDialog.value = false
    await load()
    ElMessage.success(editing.value ? '配置已更新' : '配置已创建')
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

async function remove(c: AiModelConfig) {
  try {
    await ElMessageBox.confirm(`确定删除模型配置 "${c.name}"？`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteConfig(c.id)
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
        <h2>AI 模型配置</h2>
        <p class="page-intro">配置 OpenAI 兼容的 AI 模型，用于 AI 聊天测试时调用。API Key 加密存储，查询时不回显。</p>
      </div>
      <el-button type="primary" @click="openCreate">+ 新增配置</el-button>
    </header>

    <el-table v-loading="loading" :data="configs" stripe style="width:100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column label="模型" width="140">
        <template #default="{ row }"><code>{{ row.model }}</code></template>
      </el-table-column>
      <el-table-column label="Base URL" min-width="200">
        <template #default="{ row }"><code class="url-cell">{{ row.baseUrl }}</code></template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <span class="badge" :class="row.enabled ? 'published' : 'draft'">{{ row.enabled ? '启用' : '禁用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" text @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formDialog" :title="editing ? '编辑配置' : '新增配置'" width="560px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="my-openai-config" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com" />
        </el-form-item>
        <el-form-item label="模型" required>
          <el-input v-model="form.model" placeholder="gpt-4o" />
        </el-form-item>
        <el-form-item label="API Key" :required="!editing">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="editing ? '留空则不修改' : ''" />
          <p v-if="editing" style="color:#999;font-size:12px;margin-top:4px">留空表示不修改已存储的 Key</p>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="超时 (秒)">
              <el-input-number v-model="form.timeoutSeconds" :min="10" :max="300" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label=" ">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" style="margin-top:6px" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
