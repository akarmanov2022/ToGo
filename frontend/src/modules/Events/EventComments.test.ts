import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '@/common/api/client'
import EventComments from './EventComments.vue'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('@/common/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/common/api/client')>()
  return {
    ...actual,
    apiRequest: (...args: unknown[]) => apiRequest(...args),
  }
})
const clients: QueryClient[] = []
const wrappers: VueWrapper[] = []
const comment = {
  id: 'comment',
  text: 'Встречаемся у входа',
  author: { id: 'u', tgLogin: 'anna' },
  createdAt: '2026-11-10T12:00:00Z',
}
const emptyPage = () => ({ items: [], total: 0, limit: 20, offset: 0 })

// Важно: блок с фигурными скобками. Стрелка «() => apiRequest.mockResolvedValue(...)»
// возвращала бы сам мок, и vitest вызывал бы его как cleanup-функцию после теста.
beforeEach(() => {
  apiRequest.mockResolvedValue(emptyPage())
})
// Отмонтируем компоненты до очистки кэша и сброса моков, иначе наблюдатель
// TanStack Query успевает доделать свой запрос уже внутри следующего теста.
afterEach(async () => {
  wrappers.forEach((wrapper) => wrapper.unmount())
  wrappers.length = 0
  await flushPromises()
  clients.forEach((client) => client.clear())
  clients.length = 0
  vi.resetAllMocks()
})

function render(isParticipant = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  clients.push(queryClient)
  const wrapper = mount(EventComments, {
    props: { eventId: 'event', isParticipant },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

it('запрещает пустой комментарий, публикует текст и показывает ответ сервера', async () => {
  apiRequest.mockImplementation((path: string, options?: { method?: string }) =>
    Promise.resolve(options?.method === 'POST' ? comment : emptyPage()),
  )
  const wrapper = render()
  await flushPromises()
  expect(wrapper.get('button[type=submit]').attributes('disabled')).toBeDefined()
  await wrapper.get('textarea').setValue('   ')
  expect(wrapper.get('button[type=submit]').attributes('disabled')).toBeDefined()
  await wrapper.get('textarea').setValue(' Встречаемся у входа ')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/event/comments', {
    method: 'POST',
    body: { text: 'Встречаемся у входа' },
  })
  expect(wrapper.get('.comment-list').text()).toContain(comment.text)
  expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('')
})
it('позволяет читать, но не писать неучастнику', async () => {
  apiRequest.mockResolvedValue({ items: [comment], total: 1, limit: 20, offset: 0 })
  const wrapper = render(false)
  await flushPromises()
  expect(wrapper.text()).toContain(comment.text)
  expect(wrapper.find('textarea').exists()).toBe(false)
})
it('честно обрабатывает 501 и не показывает фальшивый рабочий редактор', async () => {
  apiRequest.mockRejectedValue(new ApiError(501, null))
  const wrapper = render()
  await flushPromises()
  expect(wrapper.text()).toContain('Комментарии пока недоступны на сервере')
  expect(wrapper.find('form').exists()).toBe(false)
  expect(apiRequest).toHaveBeenCalledTimes(1)
})
it('показывает отказ сервера без потери набранного текста', async () => {
  apiRequest.mockImplementation((_path: string, options?: { method?: string }) =>
    options?.method === 'POST'
      ? Promise.reject(
          new ApiError(403, {
            code: 'FORBIDDEN',
            message: 'Только участники могут комментировать',
            details: [],
          }),
        )
      : Promise.resolve(emptyPage()),
  )
  const wrapper = render()
  await flushPromises()
  await wrapper.get('textarea').setValue('Текст')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(wrapper.text()).toContain('Только участники могут комментировать')
  expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('Текст')
})
