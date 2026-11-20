import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn<() => Promise<unknown>>(),
  logout: vi.fn<() => Promise<void>>(),
}))
vi.mock('@/api/user', () => ({
  getCurrentUser: vi.fn<() => Promise<unknown>>(),
}))

import * as authApi from '@/api/auth'
import { getCurrentUser } from '@/api/user'

const mockedLogin = vi.mocked(authApi.login)
const mockedLogout = vi.mocked(authApi.logout)
const mockedGetCurrentUser = vi.mocked(getCurrentUser)

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('stores the token and loads the current user on login', async () => {
    mockedLogin.mockResolvedValue({
      token: 'token-1',
      tokenType: 'Bearer',
      expiresIn: 7200,
    })
    mockedGetCurrentUser.mockResolvedValue({
      userAccount: 'u00000001',
      nickname: '小明',
      email: 'a@example.com',
      role: 'USER',
    })

    const store = useAuthStore()
    const user = await store.login({ email: 'a@example.com', password: 'Passw0rd1' })

    expect(user.role).toBe('USER')
    expect(store.token).toBe('token-1')
    expect(store.isAdmin).toBe(false)
    expect(store.homePath).toBe('/profile')
    expect(sessionStorage.getItem('user-center.token')).toBe('token-1')
  })

  it('loads the current user once when ensureUser is called repeatedly', async () => {
    const store = useAuthStore()
    store.token = 'token-1'
    mockedGetCurrentUser.mockResolvedValue({
      userAccount: 'u00000001',
      nickname: null,
      email: 'a@example.com',
      role: 'USER',
    })

    await Promise.all([store.ensureUser(), store.ensureUser()])

    expect(mockedGetCurrentUser).toHaveBeenCalledTimes(1)
    expect(store.user?.userAccount).toBe('u00000001')
  })

  it('clears local state when fetching the current user fails', async () => {
    const store = useAuthStore()
    store.token = 'expired-token'
    sessionStorage.setItem('user-center.token', 'expired-token')
    mockedGetCurrentUser.mockRejectedValue(new Error('401'))

    await expect(store.fetchCurrentUser()).rejects.toThrow('401')
    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(sessionStorage.getItem('user-center.token')).toBeNull()
  })

  it('clears local state after logout even if the API fails', async () => {
    const store = useAuthStore()
    store.token = 'token-1'
    sessionStorage.setItem('user-center.token', 'token-1')
    mockedLogout.mockRejectedValue(new Error('network'))

    await store.logout()

    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(sessionStorage.getItem('user-center.token')).toBeNull()
  })
})
