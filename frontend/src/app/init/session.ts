import type { QueryClient } from '@tanstack/vue-query'
import { configureApiSession } from '@/common/api/client'
import { useAuthStore } from '@/modules/Auth'

export function bindApiSession(auth: ReturnType<typeof useAuthStore>, queryClient: QueryClient) {
  configureApiSession({
    getToken: () => auth.accessToken,
    onUnauthorized: () => auth.expire(),
  })
  return auth.$subscribe(
    (_mutation, state) => {
      if (state.status === 'expired') queryClient.clear()
    },
    { flush: 'sync', detached: true },
  )
}
