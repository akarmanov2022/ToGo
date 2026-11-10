import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, expect, it, vi } from 'vitest'
import type { EventDto } from './api'
import EventDetail from './EventDetail.vue'

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

it('сохраняет подтверждение отмены и запрет вступления организатору на странице из PR', async () => {
  apiRequest.mockImplementation((path: string) => {
    if (path === '/api/v1/categories') return Promise.resolve([])
    if (path.includes('/participants?'))
      return Promise.resolve({ items: [event.organizer], total: 1 })
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
