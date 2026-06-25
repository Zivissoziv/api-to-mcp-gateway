import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './HomeView.vue'
import DashboardView from './DashboardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: HomeView,
      children: [
        { path: '', name: 'home', component: DashboardView },
      ],
    },
  ],
})

describe('HomeView', () => {
  it('shows sidebar navigation items', async () => {
    router.push('/')
    await router.isReady()
    const wrapper = mount(HomeView, { global: { plugins: [router] } })
    expect(wrapper.text()).toContain('HTTP Tool')
    expect(wrapper.text()).toContain('MCP Server')
    expect(wrapper.text()).toContain('网络白名单')
  })
})
