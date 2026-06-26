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
    <h1>把企业 API，发布成可靠的 MCP 工具。</h1>
    <p class="summary">
      当前已完成 Tool 配置、Server 编组和网络白名单管理。下一步将支持在线测试和发布快照。
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
