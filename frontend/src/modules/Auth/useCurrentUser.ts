import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getCurrentUser } from './api'
import { useAuthStore } from './session'
import { currentUserKey } from './currentUserKey'

export function useCurrentUser(enabled: MaybeRefOrGetter<boolean> = true) {
  const auth = useAuthStore()
  return useQuery({
    queryKey: computed(() => currentUserKey(auth.user?.id)),
    queryFn: ({ signal }) => getCurrentUser(signal),
    enabled: computed(() => auth.status === 'authenticated' && toValue(enabled)),
    staleTime: 30_000,
  })
}
