import { ElMessage } from 'element-plus'
import { ApiError } from './http'

/** 业务错误直接展示后端文案，网络或未知错误使用页面自己的兜底文案。 */
export function notifyApiError(error: unknown, fallback: string): void {
  ElMessage.error(error instanceof ApiError ? error.message : fallback)
}
