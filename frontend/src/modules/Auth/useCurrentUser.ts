import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getCurrentUser } from './api'
import { useAuthStore } from './session'

export function useCurrentUser() {
  const auth = useAuthStore()
  return useQuery({
    queryKey: computed(() => ['current-user', auth.user?.id]),
    queryFn: ({ signal }) => getCurrentUser(signal),
    enabled: computed(() => auth.status === 'authenticated'),
    staleTime: 30_000,
  })
}
