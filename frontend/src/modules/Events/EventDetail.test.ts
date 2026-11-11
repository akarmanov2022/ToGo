import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, expect, it, vi } from 'vitest'
import type { EventDto } from './api'
import EventDetail from './EventDetail.vue'
import EventEditor from './EventEditor.vue'
import { EventMap } from '@/modules/Map'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('@/common/api/client', () => ({ apiRequest, ApiError: class extends Error {} }))
afterEach(() => vi.clearAllMocks())

const event: EventDto = {
  id: 'own',
  title: 'Своя встреча',
  description: 'Описание',
  location: 'Парк',
  startsAt: new Date(Date.now() + 48 * 3_600_000).toISOString(),
  endsAt: new Date(Date.now() + 51 * 3_600_000).toISOString(),
  registrationClosesAt: new Date(Date.now() + 24 * 3_600_000).toISOString(),
  state: 'recruiting',
  minUsers: 1,
  maxUsers: 5,
  icon: null,
  latitude: null,
  longitude: null,
  category: 'RUN',
  organizer: { id: 'user', tgLogin: 'demo' },
  participantsCount: 1,
  availableSlots: 4,
  isOrganizer: true,
  isParticipant: true,
}

it('показывает на странице события карту только его места', async () => {
  apiRequest.mockImplementation((path: string) => {
    if (path === '/api/v1/categories') return Promise.resolve([])
    if (path.includes('/participants?') || path.includes('/comments?'))
      return Promise.resolve({ items: [], total: 0 })
    return Promise.resolve({ ...event, latitude: 59.93, longitude: 30.33 })
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(EventDetail, {
    props: { eventId: 'own' },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: client }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' }, EventMap: true },
    },
  })
  try {
    await flushPromises()
    expect(wrapper.findComponent(EventMap).props('points')).toEqual([
      { id: 'own', title: 'Парк', latitude: 59.93, longitude: 30.33 },
    ])
    expect(wrapper.findComponent(EventMap).props('selectable')).toBe(false)
  } finally {
    wrapper.unmount()
    client.clear()
  }
})

it('сохраняет подтверждение отмены и запрет вступления организатору на странице из PR', async () => {
  apiRequest.mockImplementation((path: string) => {
    if (path === '/api/v1/categories') return Promise.resolve([])
    if (path.includes('/participants?'))
      return Promise.resolve({ items: [event.organizer], total: 1 })
    if (path.includes('/comments?')) return Promise.resolve({ items: [], total: 0 })
    return Promise.resolve(event)
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(EventDetail, {
    props: { eventId: 'own' },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  try {
    await flushPromises()
    expect(wrapper.get('.facts').text().replace(/\s+/g, ' ')).toContain('Организатор: @demo')
    expect(wrapper.text()).not.toContain('Присоединиться')
    const cancel = wrapper.findAll('button').find((button) => button.text() === 'Отменить событие')!
    await cancel.trigger('click')
    expect(apiRequest).not.toHaveBeenCalledWith('/api/v1/events/own/cancel', expect.anything())
    await wrapper.get('.confirm button').trigger('click')
    await flushPromises()
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/own/cancel', { method: 'POST' })
    expect(wrapper.find('.confirm').exists()).toBe(false)
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('показывает ошибки редактора только после сохранения, не отправляя невалидный PUT', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(EventEditor, {
    props: { event, categories: [{ code: 'RUN', name: 'Пробежка' }] },
    global: { plugins: [[VueQueryPlugin, { queryClient }]] },
  })
  try {
    await wrapper.get('input').setValue('Ошибка!')
    expect(wrapper.findAll('.error')).toHaveLength(0)
    expect((wrapper.get('button[type="submit"]').element as HTMLButtonElement).disabled).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('Название: буквы')
    expect(apiRequest).not.toHaveBeenCalled()
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})

it('даёт участнику оставить комментарий в карточке события', async () => {
  apiRequest.mockImplementation((path: string) => {
    if (path === '/api/v1/categories') return Promise.resolve([])
    if (path.includes('/participants?'))
      return Promise.resolve({ items: [event.organizer], total: 1 })
    if (path.includes('/comments?')) return Promise.resolve({ items: [], total: 0 })
    return Promise.resolve(event)
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(EventDetail, {
    props: { eventId: 'own' },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
  try {
    await flushPromises()
    expect(wrapper.find('.comments form').exists()).toBe(true)
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})
