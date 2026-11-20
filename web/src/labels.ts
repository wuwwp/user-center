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
