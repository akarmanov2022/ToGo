import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useCurrentUser } from '@/modules/Auth'
import { fetchUserProfile } from './api'

export function useProfile(userId: MaybeRefOrGetter<string | undefined>) {
  const isOwn = computed(() => !toValue(userId))
  const own = useCurrentUser(isOwn)
  const other = useQuery({
    queryKey: computed(() => ['users', toValue(userId)]),
    queryFn: () => fetchUserProfile(toValue(userId)!),
    enabled: computed(() => !isOwn.value),
  })
  return {
    data: computed(() => (isOwn.value ? own.data.value : other.data.value)),
    isPending: computed(() => (isOwn.value ? own.isPending.value : other.isPending.value)),
    isError: computed(() => (isOwn.value ? own.isError.value : other.isError.value)),
    isFetching: computed(() => (isOwn.value ? own.isFetching.value : other.isFetching.value)),
    refetch: () => (isOwn.value ? own.refetch() : other.refetch()),
  }
}
