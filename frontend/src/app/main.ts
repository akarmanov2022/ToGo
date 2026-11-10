import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import App from './App.vue'
import { router } from './router'
import { useAuthStore } from '@/modules/Auth'
import { bindApiSession } from './init/session'
import { queryClient } from './queryClient'
import { shouldUseMocks } from './mocks/mode'
import './styles/tokens.css'
import './styles/base.css'
import './styles/login.css'

async function bootstrap() {
  if (shouldUseMocks()) {
    const { startMocks } = await import('./mocks/browser')
    await startMocks()
  }
  const pinia = createPinia()
  const app = createApp(App).use(pinia).use(VueQueryPlugin, { queryClient }).use(router)
  const auth = useAuthStore(pinia)
  bindApiSession(auth, queryClient)
  app.mount('#app')
}

void bootstrap().catch(() => {
  document.getElementById('app')!.textContent =
    'Не удалось запустить приложение. Проверьте соединение и перезагрузите страницу.'
})
