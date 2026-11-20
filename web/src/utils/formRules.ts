import type { FormRules } from 'element-plus'

// 与后端 ValidationConstants / VerificationCodeConstants 保持一致。
export const EMAIL_MAX_LENGTH = 128
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$/
export const PASSWORD_MESSAGE = '密码必须为 8~64 位，且至少包含字母与数字'
export const CODE_PATTERN = /^\d{6}$/
export const CODE_MESSAGE = '验证码为 6 位数字'

type FormRule = NonNullable<FormRules[string]>

export const emailRule: FormRule[] = [
  { required: true, message: '请输入邮箱', trigger: 'blur' },
  { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  { max: EMAIL_MAX_LENGTH, message: `邮箱长度不能超过 ${EMAIL_MAX_LENGTH}`, trigger: 'blur' },
]

export const passwordRule: FormRule[] = [
  {
    required: true,
    pattern: PASSWORD_PATTERN,
    message: PASSWORD_MESSAGE,
    trigger: 'blur',
  },
]

export const codeRule: FormRule[] = [
  { required: true, message: '请输入验证码', trigger: 'blur' },
  { pattern: CODE_PATTERN, message: CODE_MESSAGE, trigger: 'blur' },
]

export function confirmRule(getPassword: () => string): FormRule {
  return {
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value) {
        callback(new Error('请再次输入密码'))
      } else if (value !== getPassword()) {
        callback(new Error('两次输入的密码不一致'))
      } else {
        callback()
      }
    },
    trigger: 'blur',
  }
}
