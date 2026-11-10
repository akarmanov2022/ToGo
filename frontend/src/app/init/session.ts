import type { QueryClient } from '@tanstack/vue-query'
import { configureApiSession } from '@/common/api/client'
import { currentUserKey, useAuthStore } from '@/modules/Auth'

export function bindApiSession(auth: ReturnType<typeof useAuthStore>, queryClient: QueryClient) {
  configureApiSession({
    getToken: () => auth.accessToken,
    onUnauthorized: () => auth.expire(),
  })
  return auth.$subscribe(
    (_mutation, state) => {
      if (state.status === 'expired') queryClient.clear()
      if (state.status === 'authenticated' && state.user)
        queryClient.setQueryData(currentUserKey(state.user.id), state.user)
    },
    { flush: 'sync', detached: true },
  )
}
