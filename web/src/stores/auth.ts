import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import { getCurrentUser } from '@/api/user'
import type { CurrentUser } from '@/api/types'
import { clearToken, getToken, setToken } from '@/api/authStorage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<CurrentUser | null>(null)
  const loading = ref(false)

  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const homePath = computed(() => (isAdmin.value ? '/admin' : '/profile'))

  let pendingUser: Promise<CurrentUser> | null = null

  function clear(): void {
    token.value = null
    user.value = null
    clearToken()
  }

  async function fetchCurrentUser(): Promise<CurrentUser | null> {
    if (!token.value) {
      user.value = null
      return null
    }
    try {
      user.value = await getCurrentUser()
      return user.value
    } catch (error) {
      clear()
      throw error
    }
  }

  /** 路由守卫共用入口：已有用户直接返回，并发调用只发一次请求。 */
  async function ensureUser(): Promise<CurrentUser> {
    if (user.value) return user.value
    if (!token.value) throw new Error('未登录')
    if (!pendingUser) {
      pendingUser = fetchCurrentUser()
        .then((current) => {
          if (!current) throw new Error('登录状态无效')
          return current
        })
        .finally(() => {
          pendingUser = null
        })
    }
    return pendingUser
  }

  async function login(payload: authApi.LoginPayload): Promise<CurrentUser> {
    loading.value = true
    try {
      const result = await authApi.login(payload)
      token.value = result.token
      setToken(result.token)
      const currentUser = await fetchCurrentUser()
      if (!currentUser) {
        throw new Error('登录状态无效')
      }
      return currentUser
    } finally {
      loading.value = false
    }
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await authApi.logout()
      }
    } catch {
      // 令牌可能已经失效；本地状态仍然要清干净。
    } finally {
      clear()
    }
  }

  return {
    token,
    user,
    loading,
    isAdmin,
    homePath,
    login,
    logout,
    fetchCurrentUser,
    ensureUser,
    clear,
  }
})
