<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listAllowlist, addAllowlist, deleteAllowlist } from '../api/network-allowlist'
import type { NetworkAllowlist } from '../api/network-allowlist'
import { ElMessage, ElMessageBox } from 'element-plus'

const entries = ref<NetworkAllowlist[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({ pattern: '', patternType: 'DOMAIN', description: '' })

async function load() {
  loading.value = true
  try { entries.value = await listAllowlist() } finally { loading.value = false }
}

async function save() {
  try {
    await addAllowlist(form.value)
    dialogVisible.value = false
    form.value = { pattern: '', patternType: 'DOMAIN', description: '' }
    await load()
    ElMessage.success('规则已添加')
  } catch (e: any) {
    ElMessage.error('添加失败: ' + (e.message || '未知错误'))
  }
}

async function remove(e: NetworkAllowlist) {
  try {
    await ElMessageBox.confirm(`删除白名单规则 "${e.pattern}"？`, '确认删除', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await deleteAllowlist(e.id)
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
        <h2>网络白名单</h2>
        <p class="page-intro">管理平台允许访问的外部地址。所有上游 API 调用都会受此限制，未在白名单中的域名或 IP 将被拦截，防止 SSRF 攻击。</p>
      </div>
      <el-button type="primary" @click="dialogVisible = true">+ 添加规则</el-button>
    </header>

    <el-table v-loading="loading" :data="entries" stripe style="width:100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column label="模式" min-width="220">
        <template #default="{ row }"><code>{{ row.pattern }}</code></template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ row.patternType }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200">
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">{{ row.enabled ? '启用' : '禁用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="添加白名单规则" width="520px">
      <el-form label-position="top">
        <el-form-item label="模式" required>
          <el-input v-model="form.pattern" placeholder="example.com / 192.168.1.0/24" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.patternType" style="width:100%">
            <el-option label="域名" value="DOMAIN" />
            <el-option label="IP" value="IP" />
            <el-option label="CIDR" value="CIDR" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>
