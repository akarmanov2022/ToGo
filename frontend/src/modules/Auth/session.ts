import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import { ApiError } from '@/common/api/client'
import { authenticateTelegram, getCurrentUser, type CurrentUser } from './api'

export const useAuthStore = defineStore('auth', () => {
  const status = ref<'idle' | 'loading' | 'authenticated' | 'outside' | 'expired' | 'error'>('idle')
  const accessToken = ref<string | null>(null)
  const user = shallowRef<CurrentUser | null>(null)
  const errorMessage = ref('')
  let generation = 0
  let expiryTimer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined

  function expire() {
    generation++
    controller?.abort()
    clearTimeout(expiryTimer)
    accessToken.value = null
    user.value = null
    status.value = 'expired'
  }

  async function initialize(initData: string) {
    if (status.value === 'loading' || status.value === 'authenticated') return
    const current = ++generation
    controller?.abort()
    controller = new AbortController()
    errorMessage.value = ''
    if (!initData) {
      status.value = 'outside'
      return
    }
    status.value = 'loading'
    try {
      const result = await authenticateTelegram(initData, controller.signal)
      if (current !== generation) return
      accessToken.value = result.accessToken
      expiryTimer = setTimeout(expire, result.expiresIn * 1000)
      const currentUser = await getCurrentUser(controller.signal)
      if (current !== generation) return
      user.value = currentUser
      status.value = 'authenticated'
    } catch (error) {
      if (current !== generation) return
      clearTimeout(expiryTimer)
      accessToken.value = null
      user.value = null
      if (error instanceof ApiError && error.status === 401) status.value = 'expired'
      else {
        status.value = 'error'
        errorMessage.value = error instanceof Error ? error.message : 'Не удалось выполнить вход'
      }
    }
  }
  return { status, accessToken, user, errorMessage, initialize, expire }
})
