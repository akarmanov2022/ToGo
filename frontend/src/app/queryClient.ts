import { QueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (count, error) =>
        error.name !== 'AbortError' &&
        !(error instanceof ApiError && error.status < 500) &&
        count < 1,
    },
    mutations: { retry: false },
  },
})
