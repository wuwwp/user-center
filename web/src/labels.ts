import type { UserRole, UserStatus } from '@/api/types'

export const ROLE_LABELS: Record<UserRole, string> = {
  USER: '普通用户',
  ADMIN: '管理员',
}

export const STATUS_LABELS: Record<UserStatus, string> = {
  NORMAL: '正常',
  BANNED: '封禁',
}

export function roleLabel(role: UserRole | undefined): string {
  return role ? ROLE_LABELS[role] : ROLE_LABELS.USER
}

export function statusLabel(status: UserStatus): string {
  return STATUS_LABELS[status]
}

/** 角色标签配色；新增角色时只改这里。 */
export function roleTagType(role: UserRole | undefined): 'success' | 'info' {
  return role === 'ADMIN' ? 'success' : 'info'
}

/** 状态标签配色；新增状态时只改这里。 */
export function statusTagType(status: UserStatus): 'danger' | 'success' {
  return status === 'BANNED' ? 'danger' : 'success'
}

/** 封禁/解封互为反向操作。 */
export function nextStatus(status: UserStatus): UserStatus {
  return status === 'BANNED' ? 'NORMAL' : 'BANNED'
}

export function statusActionLabel(status: UserStatus): string {
  return status === 'BANNED' ? '解封' : '封禁'
}
