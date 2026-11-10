import { apiRequest } from '@/common/api/client'
import type { components } from '@/common/api/schema'

export type CurrentUser = components['schemas']['CurrentUser']
export type AuthResponse = components['schemas']['AuthResponse']
export const authenticateTelegram = (initData: string, signal?: AbortSignal) =>
  apiRequest<AuthResponse>('/api/v1/auth/telegram', {
    method: 'POST',
    anonymous: true,
    body: { initData },
    signal,
  })
export const getCurrentUser = (signal?: AbortSignal) =>
  apiRequest<CurrentUser>('/api/v1/users/me', { signal })
