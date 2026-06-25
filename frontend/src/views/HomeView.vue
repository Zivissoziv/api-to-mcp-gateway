<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useSession } from '../stores/session'

const router = useRouter()
const session = useSession()
function logout() {
  session.clear()
  router.push('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="sidebar-header">
        <strong>MCP Gateway</strong>
      </div>
      <nav class="sidebar-nav">
        <router-link to="/" class="nav-item" exact-active-class="active">
          <span class="dot"></span> 首页
        </router-link>
        <router-link to="/http-tools" class="nav-item" active-class="active">
          <span class="dot"></span> 接口注册
        </router-link>
        <router-link to="/servers" class="nav-item" active-class="active">
          <span class="dot"></span> 发布配置
        </router-link>
        <router-link to="/network-allowlist" class="nav-item" active-class="active">
          <span class="dot"></span> 网络白名单
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <button class="text-button" @click="logout">退出登录</button>
      </div>
    </aside>
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
}
.sidebar {
  width: 220px;
  background: #282825;
  color: #f3efe5;
  display: flex;
  flex-direction: column;
  padding: 24px 0;
}
.sidebar-header {
  padding: 0 20px 24px;
  font-size: 15px;
  border-bottom: 1px solid #3d3d3a;
}
.sidebar-nav {
  flex: 1;
  padding: 12px 0;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  color: #bcb7ad;
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s;
}
.nav-item:hover { background: #363632; color: #f3efe5; }
.nav-item.active { background: #3d3d3a; color: #fff; font-weight: 600; }
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid #3d3d3a;
}
.main-content {
  flex: 1;
  padding: 32px 40px;
  background: #f2efe8;
  overflow-y: auto;
}
</style>
