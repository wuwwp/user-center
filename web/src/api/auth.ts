import { http } from './http'
import type { EmailScene, LoginResult, RegisterResult } from './types'

export interface LoginPayload {
  email: string
  password: string
}

export interface RegisterPayload {
  email: string
  code: string
  password: string
}

export interface SendCodePayload {
  email: string
  scene: EmailScene
}

export interface ResetPasswordPayload {
  email: string
  code: string
  newPassword: string
}

export function login(payload: LoginPayload): Promise<LoginResult> {
  return http.post<LoginResult>('/auth/login', payload).then((response) => response.data)
}

export function register(payload: RegisterPayload): Promise<RegisterResult> {
  return http.post<RegisterResult>('/auth/register', payload).then((response) => response.data)
}

export function sendEmailCode(payload: SendCodePayload): Promise<void> {
  return http.post('/auth/email/code', payload).then(() => undefined)
}

export function logout(): Promise<void> {
  return http.post('/auth/logout').then(() => undefined)
}

export function resetPassword(payload: ResetPasswordPayload): Promise<void> {
  return http.post('/auth/password/reset', payload).then(() => undefined)
}
