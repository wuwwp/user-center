<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useCodeSender } from '@/composables/useCodeSender'
import type { EmailCodeCredentials, EmailScene } from '@/api/types'
import { codeRule, confirmRule, emailRule, passwordRule } from '@/utils/formRules'

const props = withDefaults(
  defineProps<{
    scene: EmailScene
    loading: boolean
    submitText: string
    emailPlaceholder?: string
    passwordLabel?: string
    confirmLabel?: string
    hint?: string
  }>(),
  {
    emailPlaceholder: 'name@example.com',
    passwordLabel: '密码',
    confirmLabel: '确认密码',
    hint: '',
  },
)

const emit = defineEmits<{ submit: [credentials: EmailCodeCredentials] }>()

const formRef = ref<FormInstance>()
const form = reactive({ email: '', code: '', password: '', confirm: '' })
const { sending, countdown, send, buttonText } = useCodeSender(props.scene)

const rules: FormRules = {
  email: emailRule,
  code: codeRule,
  password: passwordRule,
  confirm: confirmRule(() => form.password),
}

async function requestCode(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validateField('email')
  } catch {
    return
  }
  await send(form.email.trim())
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  emit('submit', {
    email: form.email.trim(),
    code: form.code.trim(),
    password: form.password,
  })
}
</script>

<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-position="top"
    size="large"
    @submit.prevent="submit"
  >
    <el-form-item label="邮箱" prop="email">
      <el-input v-model="form.email" autocomplete="email" :placeholder="emailPlaceholder" />
    </el-form-item>
    <el-form-item label="验证码" prop="code">
      <div class="code-row">
        <el-input v-model="form.code" inputmode="numeric" maxlength="6" placeholder="6 位数字" />
        <el-button :disabled="sending || countdown > 0" :loading="sending" @click="requestCode">
          {{ buttonText }}
        </el-button>
      </div>
    </el-form-item>
    <el-form-item :label="passwordLabel" prop="password">
      <el-input
        v-model="form.password"
        type="password"
        show-password
        autocomplete="new-password"
        placeholder="8~64 位，至少包含字母与数字"
      />
    </el-form-item>
    <el-form-item :label="confirmLabel" prop="confirm">
      <el-input
        v-model="form.confirm"
        type="password"
        show-password
        autocomplete="new-password"
        placeholder="再次输入密码"
      />
    </el-form-item>
    <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">
      {{ submitText }}
    </el-button>
    <p v-if="hint" class="form-hint">{{ hint }}</p>
  </el-form>
</template>

<style scoped>
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
</style>
