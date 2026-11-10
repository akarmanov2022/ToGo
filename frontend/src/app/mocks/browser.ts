import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

export async function startMocks() {
  if (!import.meta.env.DEV && import.meta.env.MODE !== 'demo') return
  const worker = setupWorker(...handlers)
  await worker.start({
    onUnhandledRequest(request, print) {
      if (new URL(request.url).pathname.startsWith('/api/')) print.error()
    },
  })
}
