import { http } from './http'
import type { AdminPasswordResetResult, PageResult, UserStatus, UserSummary } from './types'

export interface ListUsersParams {
  page: number
  size: number
  status?: UserStatus | ''
  keyword?: string
}

export function listUsers(params: ListUsersParams): Promise<PageResult<UserSummary>> {
  return http
    .get<PageResult<UserSummary>>('/admin/users', {
      params: {
        page: params.page,
        size: params.size,
        status: params.status || undefined,
        keyword: params.keyword?.trim() || undefined,
      },
    })
    .then((response) => response.data)
}

export function changeUserStatus(userAccount: string, status: UserStatus): Promise<void> {
  return http
    .put(`/admin/users/${encodeURIComponent(userAccount)}/status`, { status })
    .then(() => undefined)
}

export function resetUserPassword(userAccount: string): Promise<AdminPasswordResetResult> {
  return http
    .post<AdminPasswordResetResult>(
      `/admin/users/${encodeURIComponent(userAccount)}/password/reset`,
    )
    .then((response) => response.data)
}
