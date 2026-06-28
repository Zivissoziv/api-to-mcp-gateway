<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/auth'
import { useSession } from '../stores/session'

const router = useRouter()
const session = useSession()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: 'admin', password: 'Admin@123456' })

async function submit() {
  loading.value = true
  error.value = ''
  try {
    session.save(await login(form.username, form.password))
    await router.push('/')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-shell">
    <section class="login-intro">
      <p class="eyebrow">MCP GATEWAY</p>
      <h1>把企业 API，发布成可靠的 MCP 工具</h1>
      <p>登录后即可管理 API Tool、组合 MCP Server，并完成协议与 AI 验证。</p>
    </section>
    <section class="login-panel">
      <div>
        <p class="step-label">IDENTITY / P1</p>
        <h2>登录控制台</h2>
      </div>
      <form @submit.prevent="submit">
        <label>
          用户名
          <input v-model="form.username" autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="form.password" type="password" autocomplete="current-password" />
        </label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '正在验证…' : '进入平台' }}
        </button>
      </form>
      <p class="login-hint">初始账号：admin / Admin@123456</p>
    </section>
  </main>
</template>

