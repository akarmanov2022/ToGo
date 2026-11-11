import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { currentUserKey, useAuthStore, type CurrentUser } from '@/modules/Auth'
import { createPinia } from 'pinia'
import UserProfile from './UserProfile.vue'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('@/common/api/client', () => ({ apiRequest, ApiError: class extends Error {} }))
enableAutoUnmount(afterEach)
const clients: QueryClient[] = []

const profile: CurrentUser = {
  id: 'user',
  tgId: 1,
  tgLogin: 'anna',
  name: 'Анна',
  photoUrl: null,
  city: 'Санкт-Петербург',
  categories: ['BOARD_GAMES'],
  createdEventsCount: 2,
  visitedEventsCount: 3,
}

beforeEach(() => {
  apiRequest.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/users/')) return Promise.resolve({ ...profile })
    if (path === '/api/v1/categories')
      return Promise.resolve([{ code: 'BOARD_GAMES', name: 'Настолки' }])
    return Promise.resolve({ items: [], total: 0, limit: 100, offset: 0 })
  })
})
afterEach(() => {
  clients.forEach((client) => client.clear())
  clients.length = 0
  vi.clearAllMocks()
})

function render(userId?: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  clients.push(queryClient)
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.user = profile
  auth.status = 'authenticated'
  return mount(UserProfile, {
    props: { userId },
    global: {
      plugins: [pinia, [VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

it('не показывает интересы и «Настолки» в своём профиле и его редакторе', async () => {
  const wrapper = render()
  await flushPromises()
  const summary = wrapper.get('.summary')
  expect(summary.text()).toContain('@anna')
  expect(summary.text()).toContain('создано')
  expect(summary.text()).toContain('посещено')
  expect(summary.text()).not.toContain('Настолки')
  await summary.get('button').trigger('click')
  expect(summary.text()).not.toContain('Интересы')
  expect(summary.findAll('input')).toHaveLength(2)
})

it('не стирает серверные категории при сохранении имени без редактора интересов', async () => {
  const wrapper = render()
  await flushPromises()
  await wrapper.get('.summary button').trigger('click')
  await wrapper.get('.summary input').setValue('Аня')
  await wrapper.get('.summary form').trigger('submit')
  await flushPromises()
  expect(apiRequest).toHaveBeenCalledWith('/api/v1/users/me', {
    method: 'PUT',
    body: { name: 'Аня', city: 'Санкт-Петербург', categories: ['BOARD_GAMES'] },
  })
})

it('не показывает интересы, редактор и архив в чужом профиле', async () => {
  const wrapper = render('other')
  await flushPromises()
  expect(wrapper.get('.summary').text()).not.toContain('Настолки')
  expect(wrapper.text()).not.toContain('Редактировать профиль')
  expect(wrapper.text()).not.toContain('Архив')
  expect(apiRequest.mock.calls.some(([path]) => path === '/api/v1/users/me')).toBe(false)
  expect(apiRequest.mock.calls.some(([path]) => String(path).includes('participating=true'))).toBe(
    false,
  )
})

it('поздний ответ сохранения не восстанавливает приватный кеш завершённой сессии', async () => {
  const wrapper = render()
  await flushPromises()
  let resolveSave!: (value: CurrentUser) => void
  const pending = new Promise<CurrentUser>((resolve) => {
    resolveSave = resolve
  })
  apiRequest.mockImplementation((path: string, options?: { method?: string }) => {
    if (path === '/api/v1/users/me' && options?.method === 'PUT') return pending
    return Promise.resolve({ ...profile })
  })
  await wrapper.get('.summary button').trigger('click')
  await wrapper.get('.summary input').setValue('Борис')
  await wrapper.get('.summary form').trigger('submit')
  useAuthStore().expire()
  const client = clients[clients.length - 1]!
  client.clear()
  resolveSave({ ...profile, name: 'Борис' })
  await flushPromises()
  expect(client.getQueryData(currentUserKey(profile.id))).toBeUndefined()
})
