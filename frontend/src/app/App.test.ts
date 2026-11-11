import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from './App.vue'
import { useAuthStore } from '@/modules/Auth'

const { authenticateTelegram, getCurrentUser, requestWriteAccess, mockMode } = vi.hoisted(() => ({
  authenticateTelegram: vi.fn(),
  getCurrentUser: vi.fn(),
  requestWriteAccess: vi.fn(),
  mockMode: vi.fn(),
}))
vi.mock('@/modules/Auth/api', () => ({ authenticateTelegram, getCurrentUser }))
vi.mock('@/modules/Auth/telegram', () => ({
  getTelegramInitData: () => 'signed-telegram-init-data',
  requestTelegramWriteAccess: requestWriteAccess,
}))
vi.mock('./mocks/mode', () => ({ shouldUseMocks: mockMode, MOCK_INIT_DATA: 'local-msw-init-data' }))
enableAutoUnmount(afterEach)
const user = {
  id: 'telegram-user',
  tgId: 123,
  tgLogin: 'anna',
  name: 'Анна',
  photoUrl: null,
  city: null,
  categories: [],
  createdEventsCount: 0,
  visitedEventsCount: 0,
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  mockMode.mockReturnValue(false)
  authenticateTelegram.mockResolvedValue({
    accessToken: 'real-api-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user,
  })
  getCurrentUser.mockResolvedValue(user)
  requestWriteAccess.mockResolvedValue(false)
})
afterEach(() => {
  vi.useRealTimers()
})

async function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<p>Лента</p>' } }],
  })
  await router.push('/')
  const wrapper = mount(App, {
    global: { plugins: [pinia, router], stubs: { AppLayout: { template: '<slot />' } } },
  })
  await flushPromises()
  return { wrapper, auth: useAuthStore(pinia) }
}

it('автоматически входит с настоящим initData и спрашивает разрешение сообщений после входа', async () => {
  const { wrapper, auth } = await render()
  expect(authenticateTelegram).toHaveBeenCalledWith(
    'signed-telegram-init-data',
    expect.any(AbortSignal),
  )
  expect(getCurrentUser).toHaveBeenCalledOnce()
  expect(auth.status).toBe('authenticated')
  expect(requestWriteAccess).toHaveBeenCalledOnce()
  expect(wrapper.text()).toContain('Лента')
  expect(wrapper.text()).not.toContain('Войти в демо')
})

it('не просит разрешение сообщений при отказе авторизации', async () => {
  authenticateTelegram.mockRejectedValueOnce(new Error('Нет связи'))
  const { wrapper, auth } = await render()
  expect(auth.status).toBe('error')
  expect(requestWriteAccess).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('Не удалось войти')
})

it('не запускает демо-вход автоматически и не запрашивает разрешение бота в демо', async () => {
  mockMode.mockReturnValue(true)
  const { wrapper } = await render()
  expect(authenticateTelegram).not.toHaveBeenCalled()
  expect(requestWriteAccess).not.toHaveBeenCalled()
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(authenticateTelegram).toHaveBeenCalledWith('local-msw-init-data', expect.any(AbortSignal))
  expect(requestWriteAccess).not.toHaveBeenCalled()
})
