<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  getStatsByServer,
  getStatsByTool,
  getStatsByIp,
  getServerDetail,
  type ServerCallStats,
  type ToolCallStats,
  type IpCallStats,
  type ServerDetail,
} from '../api/stats'
import { Search } from '@element-plus/icons-vue'

const activeTab = ref('server')

const searchQuery = ref('')

const servers = ref<ServerCallStats[]>([])
const tools = ref<ToolCallStats[]>([])
const ips = ref<IpCallStats[]>([])
const loading = ref(false)

const filteredServers = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return servers.value
  return servers.value.filter(s => s.serverCode.toLowerCase().includes(q))
})
const filteredTools = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return tools.value
  return tools.value.filter(t =>
    t.serverCode.toLowerCase().includes(q) ||
    (t.toolName || '').toLowerCase().includes(q)
  )
})
const filteredIps = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return ips.value
  return ips.value.filter(i => i.clientIp.includes(q))
})

const drillDialogVisible = ref(false)
const drillServerCode = ref('')
const drillDetail = ref<ServerDetail | null>(null)
const drillLoading = ref(false)

async function loadAll() {
  loading.value = true
  try {
    const [s, t, i] = await Promise.all([
      getStatsByServer(),
      getStatsByTool(),
      getStatsByIp(),
    ])
    servers.value = s
    tools.value = t
    ips.value = i
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

async function openDrill(serverCode: string) {
  drillServerCode.value = serverCode
  drillDialogVisible.value = true
  drillLoading.value = true
  drillDetail.value = null
  try {
    drillDetail.value = await getServerDetail(serverCode)
  } catch { /* ignore */ } finally {
    drillLoading.value = false
  }
}

function formatMs(ms: number): string {
  if (ms < 1000) return ms.toFixed(0) + ' ms'
  return (ms / 1000).toFixed(2) + ' s'
}

function formatTime(t: string | null): string {
  if (!t) return '—'
  // keep the raw string — it's already a friendly ISO / SQL format
  return t
}

onMounted(loadAll)
</script>

<template>
  <section class="page">
    <div class="page-header">
      <h2>调用统计</h2>
      <p class="page-intro">按 MCP 服务、接口、IP 维度查看调用数据。</p>
    </div>

    <div class="search-bar">
      <el-input v-model="searchQuery" placeholder="搜索服务编码、接口名称或 IP…" clearable prefix-icon="Search" />
    </div>

    <el-tabs v-model="activeTab" class="stats-tabs">
      <!-- 按服务查看 -->
      <el-tab-pane label="按服务查看" name="server">
        <el-table
          :data="filteredServers"
          v-loading="loading"
          stripe
          style="width: 100%"
          @row-click="(row: ServerCallStats) => openDrill(row.serverCode)"
        >
          <el-table-column prop="serverCode" label="服务编码" min-width="140" />
          <el-table-column prop="callCount" label="调用次数" width="100" sortable />
          <el-table-column prop="successCount" label="成功次数" width="100" sortable />
          <el-table-column prop="uniqueIps" label="唯一 IP" width="90" sortable />
          <el-table-column prop="avgDurationMs" label="平均耗时" width="110" sortable>
            <template #default="{ row }: { row: ServerCallStats }">
              {{ formatMs(row.avgDurationMs) }}
            </template>
          </el-table-column>
          <el-table-column prop="lastCallAt" label="最后调用" min-width="160">
            <template #default="{ row }: { row: ServerCallStats }">
              {{ formatTime(row.lastCallAt) }}
            </template>
          </el-table-column>
        </el-table>
        <p v-if="!loading && servers.length === 0" class="empty-hint">暂无调用记录。</p>
      </el-tab-pane>

      <!-- 按接口查看 -->
      <el-tab-pane label="按接口查看" name="tool">
        <el-table
          :data="filteredTools"
          v-loading="loading"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="serverCode" label="服务编码" min-width="130" />
          <el-table-column prop="toolName" label="接口名称" min-width="150" />
          <el-table-column prop="callCount" label="调用次数" width="90" sortable />
          <el-table-column prop="successCount" label="成功次数" width="90" sortable />
          <el-table-column prop="uniqueIps" label="唯一 IP" width="80" sortable />
          <el-table-column prop="avgDurationMs" label="平均耗时" width="100" sortable>
            <template #default="{ row }: { row: ToolCallStats }">
              {{ formatMs(row.avgDurationMs) }}
            </template>
          </el-table-column>
          <el-table-column prop="lastCallAt" label="最后调用" min-width="160">
            <template #default="{ row }: { row: ToolCallStats }">
              {{ formatTime(row.lastCallAt) }}
            </template>
          </el-table-column>
        </el-table>
        <p v-if="!loading && tools.length === 0" class="empty-hint">暂无调用记录。</p>
      </el-tab-pane>

      <!-- 按 IP 查看 -->
      <el-tab-pane label="按 IP 查看" name="ip">
        <el-table
          :data="filteredIps"
          v-loading="loading"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="clientIp" label="IP 地址" min-width="160" />
          <el-table-column prop="callCount" label="调用次数" width="120" sortable />
          <el-table-column prop="lastCallAt" label="最后调用" min-width="180">
            <template #default="{ row }: { row: IpCallStats }">
              {{ formatTime(row.lastCallAt) }}
            </template>
          </el-table-column>
        </el-table>
        <p v-if="!loading && ips.length === 0" class="empty-hint">暂无调用记录。</p>
      </el-tab-pane>
    </el-tabs>

    <!-- 下钻对话框 -->
    <el-dialog
      v-model="drillDialogVisible"
      :title="`服务详情 — ${drillServerCode}`"
      width="800"
      top="5vh"
    >
      <div v-loading="drillLoading">
        <template v-if="drillDetail">
          <h4 style="margin: 0 0 12px;">接口统计</h4>
          <el-table :data="drillDetail.tools" stripe size="small" style="width: 100%; margin-bottom: 24px;">
            <el-table-column prop="toolName" label="接口名称" min-width="140" />
            <el-table-column prop="callCount" label="调用次数" width="90" sortable />
            <el-table-column prop="successCount" label="成功次数" width="90" sortable />
            <el-table-column prop="uniqueIps" label="唯一 IP" width="80" sortable />
            <el-table-column prop="avgDurationMs" label="平均耗时" width="100" sortable>
              <template #default="{ row }: { row: ToolCallStats }">
                {{ formatMs(row.avgDurationMs) }}
              </template>
            </el-table-column>
          </el-table>

          <h4 style="margin: 0 0 12px;">IP 统计</h4>
          <el-table :data="drillDetail.ips" stripe size="small" style="width: 100%;">
            <el-table-column prop="clientIp" label="IP 地址" min-width="160" />
            <el-table-column prop="callCount" label="调用次数" width="100" sortable />
            <el-table-column prop="lastCallAt" label="最后调用" min-width="160">
              <template #default="{ row }: { row: IpCallStats }">
                {{ formatTime(row.lastCallAt) }}
              </template>
            </el-table-column>
          </el-table>

          <p v-if="drillDetail.tools.length === 0 && drillDetail.ips.length === 0" class="empty-hint">
            该服务暂无调用记录。
          </p>
        </template>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.stats-tabs {
  margin-top: 8px;
}
.stats-tabs :deep(.el-table) {
  width: 100%;
}
.empty-hint {
  text-align: center;
  color: #999;
  margin: 40px 0;
  font-size: 14px;
}
</style>
