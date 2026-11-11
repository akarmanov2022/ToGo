import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, expect, it, vi } from 'vitest'
import type { Comment } from './api'
import EventComments from './EventComments.vue'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('@/common/api/client', () => ({
  apiRequest,
  ApiError: class extends Error {
    constructor(
      public readonly status: number,
      public readonly body: { message: string } | null,
    ) {
      super(body?.message)
    }
  },
}))
afterEach(() => vi.clearAllMocks())

function note(text: string, login: string | null): Comment {
  return {
    id: `id-${Math.random()}`,
    author: { id: `user-${Math.random()}`, tgLogin: login },
    text,
    createdAt: new Date(Date.now() - Math.floor(Math.random() * 3_600_000)).toISOString(),
  }
}

function render(canComment: boolean) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(EventComments, {
    props: { eventId: 'event', canComment },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  return { wrapper, queryClient }
}

it('показывает комментарии в порядке ответа сервера с автором', async () => {
  const notes = [note('Встречаемся у входа', 'anna'), note('Возьму мяч', 'boris')]
  apiRequest.mockResolvedValue({ items: notes, total: 2, limit: 100, offset: 0 })
  const { wrapper, queryClient } = render(false)
  try {
    await flushPromises()
    expect(wrapper.findAll('.comment').map((item) => item.text())).toEqual([
      expect.stringMatching(/@anna.*Встречаемся у входа/s),
      expect.stringMatching(/@boris.*Возьму мяч/s),
    ])
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('запрашивает комментарии события страницей', async () => {
  apiRequest.mockResolvedValue({ items: [], total: 0, limit: 100, offset: 0 })
  const { wrapper, queryClient } = render(false)
  try {
    await flushPromises()
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/event/comments?limit=100&offset=0')
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('говорит, что комментариев пока нет', async () => {
  apiRequest.mockResolvedValue({ items: [], total: 0, limit: 100, offset: 0 })
  const { wrapper, queryClient } = render(true)
  try {
    await flushPromises()
    expect(wrapper.text()).toContain('Комментариев пока нет')
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('не показывает форму тому, кто не участвует', async () => {
  apiRequest.mockResolvedValue({ items: [], total: 0, limit: 100, offset: 0 })
  const { wrapper, queryClient } = render(false)
  try {
    await flushPromises()
    expect(wrapper.find('form').exists()).toBe(false)
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('не даёт отправить комментарий из одних пробелов', async () => {
  apiRequest.mockResolvedValue({ items: [], total: 0, limit: 100, offset: 0 })
  const { wrapper, queryClient } = render(true)
  try {
    await flushPromises()
    await wrapper.get('textarea').setValue('   \n ')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('публикует комментарий участника без крайних пробелов и очищает поле', async () => {
  const text = `Буду в ${Math.floor(Math.random() * 24)}:00`
  apiRequest.mockImplementation((path: string, options?: { method?: string }) =>
    options?.method === 'POST'
      ? Promise.resolve(note(text, 'me'))
      : Promise.resolve({ items: [], total: 0, limit: 100, offset: 0 }),
  )
  const { wrapper, queryClient } = render(true)
  try {
    await flushPromises()
    await wrapper.get('textarea').setValue(`  ${text}  `)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/event/comments', {
      method: 'POST',
      body: { text },
    })
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('перезапрашивает список после публикации', async () => {
  apiRequest.mockImplementation((path: string, options?: { method?: string }) =>
    options?.method === 'POST'
      ? Promise.resolve(note('Привет', 'me'))
      : Promise.resolve({ items: [], total: 0, limit: 100, offset: 0 }),
  )
  const { wrapper, queryClient } = render(true)
  try {
    await flushPromises()
    await wrapper.get('textarea').setValue('Привет')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(
      apiRequest.mock.calls.filter(([path]) => String(path).includes('comments?')),
    ).toHaveLength(2)
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('показывает отказ сервера при публикации', async () => {
  const { ApiError } = await import('@/common/api/client')
  apiRequest.mockImplementation((path: string, options?: { method?: string }) =>
    options?.method === 'POST'
      ? Promise.reject(
          new ApiError(403, {
            code: 'FORBIDDEN',
            message: 'Комментировать могут только участники события',
            details: [],
          }),
        )
      : Promise.resolve({ items: [], total: 0, limit: 100, offset: 0 }),
  )
  const { wrapper, queryClient } = render(true)
  try {
    await flushPromises()
    await wrapper.get('textarea').setValue('Привет')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('.error').text()).toBe('Комментировать могут только участники события')
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})
