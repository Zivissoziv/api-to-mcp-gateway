import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import HttpToolsView from '../views/HttpToolsView.vue'
import McpServersView from '../views/McpServersView.vue'
import AiChatView from '../views/AiChatView.vue'
import { useSession } from '../stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: HomeView,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('../views/DashboardView.vue'),
        },
        {
          path: 'http-tools',
          name: 'http-tools',
          component: HttpToolsView,
        },
        {
          path: 'servers',
          name: 'servers',
          component: McpServersView,
        },
        {
          path: 'ai-chat',
          name: 'ai-chat',
          component: AiChatView,
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
  ],
})

router.beforeEach((to) => {
  const { state } = useSession()
  if (to.name !== 'login' && !state.accessToken) return { name: 'login' }
  if (to.name === 'login' && state.accessToken) return { name: 'home' }
})

export default router
