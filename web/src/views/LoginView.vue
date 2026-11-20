<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { register, resetPassword } from '@/api/auth'
import { recoverAccount } from '@/api/user'
import { notifyApiError } from '@/api/errors'
import { isSafeInternalPath } from '@/api/authStorage'
import { useAuthStore } from '@/stores/auth'
import EmailCodePasswordForm from '@/components/EmailCodePasswordForm.vue'
import type { EmailCodeCredentials } from '@/api/types'
import { emailRule } from '@/utils/formRules'

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
const loginForm = reactive({ email: '', password: '' })

const loginRules: FormRules = {
  email: emailRule,
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

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

async function handleLogin(): Promise<void> {
  if (!loginFormRef.value) return
  try {
    await loginFormRef.value.validate()
  } catch {
    return
  }

  loginLoading.value = true
  try {
    await auth.login({ email: loginForm.email.trim(), password: loginForm.password })
    const returnUrl = route.query.returnUrl
    await router.push(isSafeInternalPath(returnUrl) ? returnUrl : auth.homePath)
  } catch (error) {
    notifyApiError(error, '登录失败，请检查邮箱和密码')
  } finally {
    loginLoading.value = false
  }
}

async function handleRegister(credentials: EmailCodeCredentials): Promise<void> {
  registerLoading.value = true
  try {
    await register(credentials)
    ElMessage.success('注册成功，请登录')
    loginForm.email = credentials.email
    activeTab.value = 'login'
  } catch (error) {
    notifyApiError(error, '注册失败，请检查信息')
  } finally {
    registerLoading.value = false
  }
}

async function handleReset(credentials: EmailCodeCredentials): Promise<void> {
  resetLoading.value = true
  try {
    await resetPassword({
      email: credentials.email,
      code: credentials.code,
      newPassword: credentials.password,
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    loginForm.email = credentials.email
    activeTab.value = 'login'
  } catch (error) {
    notifyApiError(error, '重置失败，请检查验证码')
  } finally {
    resetLoading.value = false
  }
}

async function handleRecover(credentials: EmailCodeCredentials): Promise<void> {
  recoverLoading.value = true
  try {
    await recoverAccount({
      email: credentials.email,
      code: credentials.code,
      newPassword: credentials.password,
    })
    ElMessage.success('账号已恢复，请使用新密码登录')
    loginForm.email = credentials.email
    activeTab.value = 'login'
  } catch (error) {
    notifyApiError(error, '恢复失败，请确认账号已注销且验证码正确')
  } finally {
    recoverLoading.value = false
  }
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
            <EmailCodePasswordForm
              scene="register"
              :loading="registerLoading"
              submit-text="创建账号"
              @submit="handleRegister"
            />
          </el-tab-pane>

          <el-tab-pane label="忘记密码" name="reset">
            <EmailCodePasswordForm
              scene="reset"
              :loading="resetLoading"
              submit-text="重置密码"
              email-placeholder="注册时使用的邮箱"
              password-label="新密码"
              confirm-label="确认新密码"
              hint="验证码会发送到已注册且状态正常的邮箱。"
              @submit="handleReset"
            />
          </el-tab-pane>

          <el-tab-pane label="恢复账号" name="recover">
            <EmailCodePasswordForm
              scene="recover"
              :loading="recoverLoading"
              submit-text="恢复账号"
              email-placeholder="已注销账号的邮箱"
              password-label="新密码"
              confirm-label="确认新密码"
              hint="只有已注销且未被封禁的账号会收到恢复验证码。"
              @submit="handleRecover"
            />
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
