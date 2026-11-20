<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { register, resetPassword } from '@/api/auth'
import { recoverAccount } from '@/api/user'
import { ApiError } from '@/api/http'
import { isSafeInternalPath } from '@/api/authStorage'
import { useAuthStore } from '@/stores/auth'
import { useCodeSender } from '@/composables/useCodeSender'

type TabName = 'login' | 'register' | 'reset' | 'recover'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const activeTab = ref<TabName>('login')
const loginLoading = ref(false)
const registerLoading = ref(false)
const resetLoading = ref(false)
const recoverLoading = ref(false)

const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()
const resetFormRef = ref<FormInstance>()
const recoverFormRef = ref<FormInstance>()

const loginForm = reactive({ email: '', password: '' })
const registerForm = reactive({ email: '', code: '', password: '', confirm: '' })
const resetForm = reactive({ email: '', code: '', newPassword: '', confirm: '' })
const recoverForm = reactive({ email: '', code: '', newPassword: '', confirm: '' })

const emailRule = [
  { required: true, message: '请输入邮箱', trigger: 'blur' },
  { type: 'email' as const, message: '邮箱格式不正确', trigger: 'blur' },
  { max: 128, message: '邮箱长度不能超过 128', trigger: 'blur' },
]

const passwordRule = {
  required: true,
  pattern: /^(?=.*[A-Za-z])(?=.*[0-9])[!-~]{8,64}$/,
  message: '密码为 8~64 位，且至少包含字母与数字',
  trigger: 'blur',
}

const codeRule = [
  { required: true, message: '请输入验证码', trigger: 'blur' },
  { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' },
]

function confirmRule(getPassword: () => string) {
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

const loginRules: FormRules = {
  email: emailRule,
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  email: emailRule,
  code: codeRule,
  password: [passwordRule],
  confirm: [confirmRule(() => registerForm.password)],
}

const resetRules: FormRules = {
  email: emailRule,
  code: codeRule,
  newPassword: [passwordRule],
  confirm: [confirmRule(() => resetForm.newPassword)],
}

const recoverRules: FormRules = {
  email: emailRule,
  code: codeRule,
  newPassword: [passwordRule],
  confirm: [confirmRule(() => recoverForm.newPassword)],
}

const {
  sending: registerSending,
  countdown: registerCountdown,
  send: sendRegisterCode,
  buttonText: registerCodeText,
} = useCodeSender('register')
const {
  sending: resetSending,
  countdown: resetCountdown,
  send: sendResetCode,
  buttonText: resetCodeText,
} = useCodeSender('reset')
const {
  sending: recoverSending,
  countdown: recoverCountdown,
  send: sendRecoverCode,
  buttonText: recoverCodeText,
} = useCodeSender('recover')

const cardCopy = computed(() => {
  switch (activeTab.value) {
    case 'register':
      return { title: '创建账号', subtitle: '用邮箱验证码注册，之后用邮箱登录。' }
    case 'reset':
      return { title: '重置密码', subtitle: '验证邮箱所有权后设置新密码。' }
    case 'recover':
      return { title: '恢复账号', subtitle: '已注销账号可以用邮箱验证码恢复。' }
    default:
      return { title: '欢迎回来', subtitle: '登录后继续管理你的账号。' }
  }
})

async function validate(formRef: FormInstance | undefined): Promise<boolean> {
  if (!formRef) return false
  try {
    await formRef.validate()
    return true
  } catch {
    return false
  }
}

async function validateEmail(formRef: FormInstance | undefined): Promise<boolean> {
  if (!formRef) return false
  try {
    await formRef.validateField('email')
    return true
  } catch {
    return false
  }
}

function showError(error: unknown, fallback: string): void {
  ElMessage.error(error instanceof ApiError ? error.message : fallback)
}

function homePath(): string {
  return auth.isAdmin ? '/admin' : '/profile'
}

async function handleLogin(): Promise<void> {
  if (!(await validate(loginFormRef.value))) return
  loginLoading.value = true
  try {
    await auth.login({ email: loginForm.email.trim(), password: loginForm.password })
    const returnUrl = route.query.returnUrl
    await router.push(isSafeInternalPath(returnUrl) ? returnUrl : homePath())
  } catch (error) {
    showError(error, '登录失败，请检查邮箱和密码')
  } finally {
    loginLoading.value = false
  }
}

async function handleRegister(): Promise<void> {
  if (!(await validate(registerFormRef.value))) return
  registerLoading.value = true
  try {
    await register({
      email: registerForm.email.trim(),
      code: registerForm.code.trim(),
      password: registerForm.password,
    })
    ElMessage.success('注册成功，请登录')
    loginForm.email = registerForm.email.trim()
    activeTab.value = 'login'
  } catch (error) {
    showError(error, '注册失败，请检查信息')
  } finally {
    registerLoading.value = false
  }
}

async function handleReset(): Promise<void> {
  if (!(await validate(resetFormRef.value))) return
  resetLoading.value = true
  try {
    await resetPassword({
      email: resetForm.email.trim(),
      code: resetForm.code.trim(),
      newPassword: resetForm.newPassword,
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    loginForm.email = resetForm.email.trim()
    activeTab.value = 'login'
  } catch (error) {
    showError(error, '重置失败，请检查验证码')
  } finally {
    resetLoading.value = false
  }
}

async function handleRecover(): Promise<void> {
  if (!(await validate(recoverFormRef.value))) return
  recoverLoading.value = true
  try {
    await recoverAccount({
      email: recoverForm.email.trim(),
      code: recoverForm.code.trim(),
      newPassword: recoverForm.newPassword,
    })
    ElMessage.success('账号已恢复，请使用新密码登录')
    loginForm.email = recoverForm.email.trim()
    activeTab.value = 'login'
  } catch (error) {
    showError(error, '恢复失败，请确认账号已注销且验证码正确')
  } finally {
    recoverLoading.value = false
  }
}

async function requestCode(
  formRef: FormInstance | undefined,
  email: string,
  send: (email: string) => Promise<void>,
): Promise<void> {
  if (!(await validateEmail(formRef))) return
  await send(email.trim())
}

function applyTabFromQuery(): void {
  const tab = route.query.tab
  if (tab === 'register' || tab === 'reset' || tab === 'recover') {
    activeTab.value = tab
  }
}

onMounted(applyTabFromQuery)
watch(() => route.query.tab, applyTabFromQuery)
</script>

<template>
  <main class="auth-page">
    <section class="auth-brand">
      <p class="eyebrow">UNIFIED IDENTITY</p>
      <h1>一个账号，<br />连接所有项目。</h1>
      <p class="brand-copy">
        注册、登录、找回密码与账号恢复都在这里完成。身份只保留一份，业务项目各自维护权限。
      </p>
      <ul class="brand-points">
        <li>邮箱验证码，不保存明文密码</li>
        <li>令牌可吊销，账号状态实时生效</li>
        <li>管理员入口独立鉴权</li>
      </ul>
      <div class="brand-mark-large">UC</div>
    </section>

    <section class="auth-panel">
      <div class="auth-card">
        <header class="card-header">
          <p class="eyebrow">USER CENTER</p>
          <h2>{{ cardCopy.title }}</h2>
          <p>{{ cardCopy.subtitle }}</p>
        </header>

        <el-tabs v-model="activeTab" class="auth-tabs">
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              size="large"
              @submit.prevent="handleLogin"
            >
              <el-form-item label="邮箱" prop="email">
                <el-input
                  v-model="loginForm.email"
                  autocomplete="username"
                  placeholder="name@example.com"
                />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="请输入密码"
                />
              </el-form-item>
              <el-button
                class="submit-button"
                type="primary"
                native-type="submit"
                :loading="loginLoading"
              >
                登录
              </el-button>
              <p class="form-hint">忘记密码？切换到“忘记密码”。账号已注销？切换到“恢复账号”。</p>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              size="large"
              @submit.prevent="handleRegister"
            >
              <el-form-item label="邮箱" prop="email">
                <el-input
                  v-model="registerForm.email"
                  autocomplete="email"
                  placeholder="name@example.com"
                />
              </el-form-item>
              <el-form-item label="验证码" prop="code">
                <div class="code-row">
                  <el-input
                    v-model="registerForm.code"
                    inputmode="numeric"
                    maxlength="6"
                    placeholder="6 位数字"
                  />
                  <el-button
                    :disabled="registerSending || registerCountdown > 0"
                    :loading="registerSending"
                    @click="requestCode(registerFormRef, registerForm.email, sendRegisterCode)"
                  >
                    {{ registerCodeText }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="8~64 位，至少包含字母与数字"
                />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirm">
                <el-input
                  v-model="registerForm.confirm"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="再次输入密码"
                />
              </el-form-item>
              <el-button
                class="submit-button"
                type="primary"
                native-type="submit"
                :loading="registerLoading"
              >
                创建账号
              </el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="忘记密码" name="reset">
            <el-form
              ref="resetFormRef"
              :model="resetForm"
              :rules="resetRules"
              label-position="top"
              size="large"
              @submit.prevent="handleReset"
            >
              <el-form-item label="邮箱" prop="email">
                <el-input
                  v-model="resetForm.email"
                  autocomplete="email"
                  placeholder="注册时使用的邮箱"
                />
              </el-form-item>
              <el-form-item label="验证码" prop="code">
                <div class="code-row">
                  <el-input
                    v-model="resetForm.code"
                    inputmode="numeric"
                    maxlength="6"
                    placeholder="6 位数字"
                  />
                  <el-button
                    :disabled="resetSending || resetCountdown > 0"
                    :loading="resetSending"
                    @click="requestCode(resetFormRef, resetForm.email, sendResetCode)"
                  >
                    {{ resetCodeText }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input
                  v-model="resetForm.newPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="8~64 位，至少包含字母与数字"
                />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirm">
                <el-input
                  v-model="resetForm.confirm"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="再次输入新密码"
                />
              </el-form-item>
              <el-button
                class="submit-button"
                type="primary"
                native-type="submit"
                :loading="resetLoading"
              >
                重置密码
              </el-button>
              <p class="form-hint">验证码会发送到已注册且状态正常的邮箱。</p>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="恢复账号" name="recover">
            <el-form
              ref="recoverFormRef"
              :model="recoverForm"
              :rules="recoverRules"
              label-position="top"
              size="large"
              @submit.prevent="handleRecover"
            >
              <el-form-item label="邮箱" prop="email">
                <el-input
                  v-model="recoverForm.email"
                  autocomplete="email"
                  placeholder="已注销账号的邮箱"
                />
              </el-form-item>
              <el-form-item label="验证码" prop="code">
                <div class="code-row">
                  <el-input
                    v-model="recoverForm.code"
                    inputmode="numeric"
                    maxlength="6"
                    placeholder="6 位数字"
                  />
                  <el-button
                    :disabled="recoverSending || recoverCountdown > 0"
                    :loading="recoverSending"
                    @click="requestCode(recoverFormRef, recoverForm.email, sendRecoverCode)"
                  >
                    {{ recoverCodeText }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input
                  v-model="recoverForm.newPassword"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="8~64 位，至少包含字母与数字"
                />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirm">
                <el-input
                  v-model="recoverForm.confirm"
                  type="password"
                  show-password
                  autocomplete="new-password"
                  placeholder="再次输入新密码"
                />
              </el-form-item>
              <el-button
                class="submit-button"
                type="primary"
                native-type="submit"
                :loading="recoverLoading"
              >
                恢复账号
              </el-button>
              <p class="form-hint">只有已注销且未被封禁的账号会收到恢复验证码。</p>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  width: 100%;
  overflow-x: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 32px;
  padding: 40px;
  background: var(--uc-bg);
  align-items: stretch;
}

.auth-brand {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  padding: 64px;
  border-radius: 24px;
  background: var(--uc-ink);
  color: #f7f3e9;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 11px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--uc-primary-soft);
  font-weight: 700;
}

.auth-brand h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 54px);
  line-height: 1.12;
  letter-spacing: -0.03em;
  color: #fbf7ee;
}

.brand-copy {
  max-width: 34em;
  margin: 24px 0 0;
  color: #cfc8ba;
  line-height: 1.8;
}

.brand-points {
  margin: 32px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 10px;
  color: #bdb5a6;
  font-size: 14px;
}

.brand-points li::before {
  content: '—';
  color: var(--uc-primary-soft);
  margin-right: 10px;
}

.brand-mark-large {
  position: absolute;
  right: 32px;
  bottom: 8px;
  font-size: 180px;
  line-height: 1;
  font-weight: 700;
  letter-spacing: -0.08em;
  color: rgba(255, 255, 255, 0.04);
  user-select: none;
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
}

.auth-card {
  width: 100%;
  max-width: 480px;
  min-width: 0;
  padding: 36px;
  border: 1px solid var(--uc-line);
  border-radius: 20px;
  background: var(--uc-surface);
  box-shadow: 0 24px 60px rgba(34, 32, 28, 0.08);
}

.card-header h2 {
  margin: 0 0 6px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.card-header p:last-child {
  margin: 0;
  color: var(--uc-muted);
  font-size: 14px;
}

.auth-tabs {
  margin-top: 24px;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
  min-width: 0;
}

.code-row .el-button {
  width: 132px;
}

.submit-button {
  width: 100%;
  margin-top: 4px;
}

.form-hint {
  margin: 14px 0 0;
  color: var(--uc-muted);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: minmax(0, 1fr);
    padding: 20px;
  }

  .auth-brand {
    min-height: auto;
    padding: 36px 28px;
  }

  .auth-brand h1 {
    font-size: 32px;
  }

  .brand-mark-large {
    font-size: 120px;
  }

  .auth-panel {
    padding-bottom: 24px;
  }

  .auth-card {
    padding: 24px;
  }
}
</style>
