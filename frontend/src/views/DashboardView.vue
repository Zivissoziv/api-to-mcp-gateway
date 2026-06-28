<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listTools } from '../api/http-tools'
import { listServers } from '../api/servers'
import { getSummary } from '../api/stats'

const toolCount = ref(0)
const serverCount = ref(0)
const callCount = ref(0)

async function load() {
  try { toolCount.value = (await listTools()).length } catch { }
  try { serverCount.value = (await listServers()).length } catch { }
  try { const s = await getSummary(); callCount.value = s.totalCalls } catch { }
}
onMounted(load)
</script>

<template>
  <section class="hero" style="max-width: 800px;">
    <p class="eyebrow">MCP GATEWAY</p>
    <h1>API to MCP</h1>
    <p class="summary">
      MCP（Model Context Protocol，模型上下文协议）是让AI模型能以统一标准连接外部工具、数据源和系统能力的通用接口层，相当于AI的“USB接口”或“智能插线板”，让不同AI和工具之间“即插即用”
    </p>
    <div class="stats-row">
      <div class="stat-card">
        <span class="stat-value">{{ toolCount }}</span>
        <span class="stat-label">已注册 API</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ serverCount }}</span>
        <span class="stat-label">MCP 服务</span>
      </div>
      <div class="stat-card">
        <span class="stat-value">{{ callCount }}</span>
        <span class="stat-label">调用次数</span>
      </div>
    </div>
  </section>
</template>
