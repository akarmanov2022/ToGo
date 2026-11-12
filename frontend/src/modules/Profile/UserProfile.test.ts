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

it('повторяет загрузку профиля настоящим кликом после ошибки', async () => {
  apiRequest.mockRejectedValueOnce(new Error('offline'))
  const wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('Не удалось загрузить профиль')
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.find('.summary').exists()).toBe(true)
})

it('повторяет загрузку списка настоящим кликом после ошибки', async () => {
  let fail = true
  apiRequest.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/users/')) return Promise.resolve({ ...profile })
    if (path === '/api/v1/categories') return Promise.resolve([])
    return fail ? Promise.reject(new Error('offline')) : Promise.resolve({ items: [], total: 0 })
  })
  const wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('Не удалось загрузить список')
  fail = false
  const retry = wrapper.findAll('button').find((button) => button.text() === 'Повторить')!
  await retry.trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('Здесь пока пусто')
})

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

it.each([undefined, 'other'])('показывает рейтинг под фото в профиле %s', async (userId) => {
  apiRequest.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/users/'))
      return Promise.resolve({ ...profile, organizerRating: { average: 4.3, count: 3 } })
    if (path === '/api/v1/categories') return Promise.resolve([])
    return Promise.resolve({ items: [], total: 0 })
  })
  const wrapper = render(userId)
  await flushPromises()
  expect(wrapper.get('.organizer-rating').text()).toContain('4.3')
  expect(wrapper.findAll('.rating-stars svg')).toHaveLength(5)
  expect(wrapper.findAll('.rating-stars svg.filled')).toHaveLength(4)
  expect(wrapper.get('.rating-caption').text()).toBe('На основе 3 оценок')
  expect(wrapper.get('.organizer-rating').attributes('aria-label')).toBe(
    'Рейтинг организатора: 4.3',
  )
})

it('показывает 0.0 без оценок, не вычисляя рейтинг на клиенте', async () => {
  const wrapper = render('other')
  await flushPromises()
  expect(wrapper.get('.organizer-rating').text()).toContain('0.0')
  expect(wrapper.get('.rating-empty').text()).toBe('Пока нет оценок')
  expect(wrapper.get('.organizer-rating').classes()).toContain('organizer-rating--empty')
  expect(wrapper.get('.organizer-rating').text()).not.toContain('из 5')
  expect(wrapper.findAll('.rating-stars svg.filled')).toHaveLength(0)
})

it('показывает созданные события другого пользователя со всех страниц, но не чужие участия', async () => {
  const base = {
    description: '',
    location: 'Парк',
    startsAt: '2026-12-01T12:00:00Z',
    state: 'finished',
    category: 'RUN',
    participantsCount: 2,
    maxUsers: 5,
  }
  apiRequest.mockImplementation((path: string) => {
    if (path.startsWith('/api/v1/users/')) return Promise.resolve({ ...profile, id: 'other' })
    if (path === '/api/v1/categories') return Promise.resolve([])
    const offset = Number(new URL(path, 'http://localhost').searchParams.get('offset'))
    const items =
      offset === 0
        ? [
            { ...base, id: 'a', title: 'Созданная встреча', organizer: { id: 'other' } },
            { ...base, id: 'b', title: 'Не его встреча', organizer: { id: 'third' } },
          ]
        : [{ ...base, id: 'c', title: 'Встреча со следующей страницы', organizer: { id: 'other' } }]
    return Promise.resolve({ items, total: 3 })
  })
  const wrapper = render('other')
  await flushPromises()
  expect(wrapper.text()).toContain('Созданные события')
  expect(wrapper.findAll('.event-card')).toHaveLength(2)
  expect(wrapper.text()).toContain('Встреча со следующей страницы')
  expect(wrapper.text()).not.toContain('Не его встреча')
  expect(wrapper.text()).not.toContain('только для просмотра')
  expect(apiRequest.mock.calls.some(([path]) => String(path).includes('participating='))).toBe(
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
