import { computed, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sendEmailCode } from '@/api/auth'
import { ApiError } from '@/api/http'
import type { EmailScene } from '@/api/types'

export function useCodeSender(scene: EmailScene) {
  const countdown = ref(0)
  const sending = ref(false)
  let timer: ReturnType<typeof setInterval> | undefined

  function startCountdown(seconds: number): void {
    countdown.value = Math.max(1, Math.min(seconds, 300))
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) {
        clearInterval(timer)
        timer = undefined
      }
    }, 1000)
  }

  async function send(email: string): Promise<void> {
    if (countdown.value > 0 || sending.value) return
    sending.value = true
    try {
      await sendEmailCode({ email, scene })
      startCountdown(60)
      ElMessage.success('验证码已发送，请查收邮箱')
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.retryAfter && error.retryAfter > 0) {
          startCountdown(error.retryAfter)
        }
        ElMessage.error(error.message)
      } else {
        ElMessage.error('验证码发送失败，请稍后重试')
      }
    } finally {
      sending.value = false
    }
  }

  const buttonText = computed(() => {
    if (sending.value) return '发送中…'
    if (countdown.value > 0) return `${countdown.value}s 后重发`
    return '获取验证码'
  })

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })

  return { countdown, sending, send, buttonText }
}
