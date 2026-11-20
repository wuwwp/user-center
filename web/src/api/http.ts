import axios, { AxiosError } from 'axios'
import type { ProblemDetail } from './types'
import { clearToken, getToken } from './authStorage'

const PUBLIC_PATHS = [
  '/auth/login',
  '/auth/register',
  '/auth/email/code',
  '/auth/password/reset',
  '/users/me/recover',
]

export class ApiError extends Error {
  readonly status?: number
  readonly code?: number
  readonly retryAfter?: number

  constructor(
    message: string,
    options: { status?: number; code?: number; retryAfter?: number } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.retryAfter = options.retryAfter
  }
}

function isPublicRequest(url: string | undefined): boolean {
  return PUBLIC_PATHS.some((path) => url?.includes(path))
}

function redirectToLogin(): void {
  if (typeof window === 'undefined') return
  if (window.location.pathname === '/login') return
  const returnUrl = window.location.pathname + window.location.search
  window.location.assign(`/login?returnUrl=${encodeURIComponent(returnUrl)}`)
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  if (!isPublicRequest(config.url)) {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    const response = error.response
    const problem = response?.data
    const status = response?.status
    const retryAfterHeader = response?.headers?.['retry-after']
    const retryAfter = Number(retryAfterHeader)

    if (status === 401 && error.config?.headers?.Authorization) {
      clearToken()
      redirectToLogin()
    }

    const message =
      problem?.detail ||
      problem?.title ||
      (status === 403 ? '无权限执行此操作' : '请求失败，请稍后重试')

    return Promise.reject(
      new ApiError(message, {
        status,
        code: problem?.code,
        retryAfter: Number.isFinite(retryAfter) ? retryAfter : undefined,
      }),
    )
  },
)
