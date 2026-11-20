import { http } from './http'
import type { CurrentUser } from './types'

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

export interface DeleteAccountPayload {
  code: string
}

export interface RecoverAccountPayload {
  email: string
  code: string
  newPassword: string
}

export function getCurrentUser(): Promise<CurrentUser> {
  return http.get<CurrentUser>('/users/me').then((response) => response.data)
}

export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return http.put('/users/me/password', payload).then(() => undefined)
}

export function deleteAccount(payload: DeleteAccountPayload): Promise<void> {
  return http.delete('/users/me', { data: payload }).then(() => undefined)
}

export function recoverAccount(payload: RecoverAccountPayload): Promise<void> {
  return http.post('/users/me/recover', payload).then(() => undefined)
}
