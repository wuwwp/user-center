import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/profile',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { title: '个人中心', requiresAuth: true },
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/AdminView.vue'),
      meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/profile',
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth) {
    if (!auth.token) {
      return { name: 'login', query: { returnUrl: to.fullPath } }
    }
    if (!auth.user) {
      try {
        await auth.fetchCurrentUser()
      } catch {
        return { name: 'login', query: { returnUrl: to.fullPath } }
      }
    }
  }

  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'profile' }
  }

  if (to.name === 'login' && auth.token) {
    if (!auth.user) {
      try {
        await auth.fetchCurrentUser()
      } catch {
        return true
      }
    }
    if (auth.user) {
      return auth.isAdmin ? { name: 'admin' } : { name: 'profile' }
    }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${String(to.meta.title)} · 用户中心` : '用户中心'
})

export default router
