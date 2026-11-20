<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import AppShell from '@/layouts/AppShell.vue'
import { changePassword, deleteAccount } from '@/api/user'
import { notifyApiError } from '@/api/errors'
import { useAuthStore } from '@/stores/auth'
import { useCodeSender } from '@/composables/useCodeSender'
import { confirmRule, passwordRule } from '@/utils/formRules'
import { roleLabel } from '@/labels'

const auth = useAuthStore()
const router = useRouter()

const passwordFormRef = ref<FormInstance>()
const passwordLoading = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const deleteDialogVisible = ref(false)
const deleteCode = ref('')
const deleteLoading = ref(false)
const {
  sending: deleteSending,
  send: sendDeleteCode,
  buttonText: deleteCodeText,
  countdown: deleteCountdown,
} = useCodeSender('delete')

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: passwordRule,
  confirm: [confirmRule(() => passwordForm.newPassword)],
}

async function handleChangePassword(): Promise<void> {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  passwordLoading.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已修改，请重新登录')
    auth.clear()
    await router.push({ name: 'login' })
  } catch (error) {
    notifyApiError(error, '修改密码失败')
  } finally {
    passwordLoading.value = false
  }
}

async function requestDeleteCode(): Promise<void> {
  if (!auth.user?.email) return
  await sendDeleteCode(auth.user.email)
}

async function handleDelete(): Promise<void> {
  if (!/^\d{6}$/.test(deleteCode.value)) {
    ElMessage.warning('请输入 6 位验证码')
    return
  }
  try {
    await ElMessageBox.confirm(
      '注销后账号将无法登录，只能用邮箱验证码恢复。确认继续？',
      '注销账号',
      { type: 'warning', confirmButtonText: '确认注销', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  deleteLoading.value = true
  try {
    await deleteAccount({ code: deleteCode.value })
    ElMessage.success('账号已注销')
    auth.clear()
    await router.push({ name: 'login' })
  } catch (error) {
    notifyApiError(error, '注销失败，请检查验证码')
  } finally {
    deleteLoading.value = false
  }
}

function openDeleteDialog(): void {
  deleteCode.value = ''
  deleteDialogVisible.value = true
}
</script>

<template>
  <AppShell>
    <div class="page-heading">
      <div>
        <p class="page-kicker">ACCOUNT</p>
        <h1>个人中心</h1>
        <p class="page-subtitle">查看账号资料，修改密码，或管理账号生命周期。</p>
      </div>
    </div>

    <div class="content-grid">
      <section class="surface-card">
        <header class="section-header">
          <h2>账号资料</h2>
          <el-tag :type="auth.isAdmin ? 'success' : 'info'" effect="plain">
            {{ roleLabel(auth.user?.role) }}
          </el-tag>
        </header>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="账号">
            {{ auth.user?.userAccount || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="昵称">
            {{ auth.user?.nickname || '未设置' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ auth.user?.email || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            {{ roleLabel(auth.user?.role) }}
          </el-descriptions-item>
        </el-descriptions>
        <p class="card-note">账号标识由系统生成，不参与登录；登录使用邮箱。</p>
      </section>

      <section class="surface-card">
        <header class="section-header">
          <h2>修改密码</h2>
        </header>
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          @submit.prevent="handleChangePassword"
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="8~64 位，至少包含字母与数字"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm">
            <el-input
              v-model="passwordForm.confirm"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-button type="primary" native-type="submit" :loading="passwordLoading">
            保存新密码
          </el-button>
        </el-form>
        <p class="card-note">修改后当前登录令牌会立即失效，需要重新登录。</p>
      </section>
    </div>

    <section class="surface-card danger-card">
      <header class="section-header">
        <div>
          <h2>账号生命周期</h2>
          <p class="section-subtitle">注销是逻辑删除，账号可以用邮箱验证码恢复。</p>
        </div>
      </header>
      <div class="danger-actions">
        <div>
          <h3>注销账号</h3>
          <p>注销后立即退出登录，管理员列表中不再显示该账号。</p>
        </div>
        <el-button type="danger" plain @click="openDeleteDialog">注销账号</el-button>
      </div>
      <div class="danger-actions">
        <div>
          <h3>恢复已注销账号</h3>
          <p>如果你已经注销账号，可以带着邮箱验证码去恢复。</p>
        </div>
        <el-button @click="router.push({ name: 'login', query: { tab: 'recover' } })">
          去恢复
        </el-button>
      </div>
    </section>

    <el-dialog v-model="deleteDialogVisible" title="注销账号" width="min(92vw, 460px)">
      <p class="dialog-warning">注销后账号无法登录，只能通过邮箱验证码恢复。请先获取注销验证码。</p>
      <el-form label-position="top" @submit.prevent="handleDelete">
        <el-form-item label="注销验证码">
          <div class="code-row">
            <el-input
              v-model="deleteCode"
              inputmode="numeric"
              maxlength="6"
              placeholder="6 位数字"
            />
            <el-button
              :disabled="deleteSending || deleteCountdown > 0"
              :loading="deleteSending"
              @click="requestDeleteCode"
            >
              {{ deleteCodeText }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleteLoading" @click="handleDelete">
          确认注销
        </el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<style scoped>
.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-kicker {
  margin: 0 0 8px;
  color: var(--uc-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.page-heading h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 8px 0 0;
  color: var(--uc-muted);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
}

.surface-card {
  padding: 24px;
  border: 1px solid var(--uc-line);
  border-radius: 16px;
  background: var(--uc-surface);
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.section-header h2 {
  margin: 0;
  font-size: 18px;
}

.section-subtitle {
  margin: 6px 0 0;
  color: var(--uc-muted);
  font-size: 13px;
}

.card-note {
  margin: 16px 0 0;
  color: var(--uc-muted);
  font-size: 12px;
  line-height: 1.7;
}

.danger-card {
  margin-top: 20px;
}

.danger-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 16px 0;
  border-top: 1px solid var(--uc-line);
}

.danger-actions:first-of-type {
  border-top: none;
  padding-top: 0;
}

.danger-actions h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

.danger-actions p {
  margin: 0;
  color: var(--uc-muted);
  font-size: 13px;
  line-height: 1.6;
}

.dialog-warning {
  margin: 0 0 16px;
  color: var(--uc-danger);
  font-size: 13px;
  line-height: 1.7;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-row .el-input {
  flex: 1;
}

.code-row .el-button {
  width: 132px;
}

@media (max-width: 960px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .danger-actions {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
