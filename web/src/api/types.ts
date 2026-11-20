export type UserRole = 'USER' | 'ADMIN'
export type UserStatus = 'NORMAL' | 'BANNED'
export type EmailScene = 'register' | 'reset' | 'recover' | 'delete'

export interface CurrentUser {
  userAccount: string
  nickname: string | null
  email: string
  role: UserRole
}

export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
}

export interface RegisterResult {
  userAccount: string
}

/** 邮箱验证码场景下三个表单共用的提交数据。 */
export interface EmailCodeCredentials {
  email: string
  code: string
  password: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface UserSummary {
  userAccount: string
  nickname: string | null
  emailMasked: string
  role: UserRole
  status: UserStatus
  createTime: string | null
  lastLoginTime: string | null
}

export interface AdminPasswordResetResult {
  userAccount: string
  newPassword: string
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  code?: number
}
