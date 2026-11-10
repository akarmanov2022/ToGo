import { flushPromises, mount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { components } from '@/common/api/schema'
import EventFeed from './EventFeed.vue'

type Event = components['schemas']['Event']

vi.mock('@/common/api/client', () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
  ApiError: class ApiError extends Error {},
}))

const apiRequest = vi.fn()

const now = Date.now()
const iso = (shiftHours: number) => new Date(now + shiftHours * 3_600_000).toISOString()

function fixture(overrides: Partial<Event> & { id: string; title: string }): Event {
  return {
    description: 'Описание',
    location: 'Санкт-Петербург',
    startsAt: iso(5),
    endsAt: iso(8),
    registrationClosesAt: iso(3),
    state: 'recruiting',
    icon: null,
    latitude: null,
    longitude: null,
    minUsers: 1,
    maxUsers: 4,
    organizer: { id: 'u1', tgLogin: 'anna' },
    category: 'CINEMA',
    participantsCount: 1,
    availableSlots: 3,
    isParticipant: false,
    isOrganizer: false,
    ...overrides,
  } as Event
}

function mountFeed() {
  return mount(EventFeed, {
    global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]] },
  })
}

function card(wrapper: ReturnType<typeof mountFeed>, title: string) {
  return wrapper.findAll('.event-card').find((item) => item.text().includes(title))!
}

it('показывает кнопку по роли пользователя: организатор, участник, свободно, забито', async () => {
  const events = [
    fixture({ id: 'own', title: 'Своя встреча', isOrganizer: true }),
    fixture({ id: 'joined', title: 'Уже иду', isParticipant: true }),
    fixture({ id: 'free', title: 'Свободная встреча' }),
    fixture({ id: 'full', title: 'Забитая встреча', availableSlots: 0, participantsCount: 4 }),
    fixture({ id: 'gone', title: 'Отменённая встреча', state: 'cancelled_by_organizer' }),
  ]
  apiRequest.mockImplementation((path: string) => {
    if (path === '/api/v1/categories') return Promise.resolve([{ code: 'CINEMA', name: 'Кино' }])
    return Promise.resolve({ items: [...events], total: events.length, limit: 100, offset: 0 })
  })
  const wrapper = mountFeed()
  try {
    await flushPromises()
    expect(wrapper.findAll('.event-card')).toHaveLength(4)
    const own = card(wrapper, 'Своя встреча')
    expect(own.text()).not.toContain('Присоединиться')
    expect(own.get('button.join-button').text()).toBe('Отменить событие')
    expect(card(wrapper, 'Уже иду').get('button.join-button').text()).toBe('Отписаться от события')
    expect(card(wrapper, 'Свободная встреча').get('button.join-button').text()).toBe(
      'Присоединиться',
    )
    expect(
      card(wrapper, 'Свободная встреча').get('button.join-button').attributes('disabled'),
    ).toBeUndefined()

    const fullButton = card(wrapper, 'Забитая встреча').get('button.join-button')
    expect(fullButton.text()).toBe('Присоединиться')
    expect(fullButton.attributes('disabled')).toBeDefined()
  } finally {
    wrapper.unmount()
  }
})

it('отменяет событие организатором только после подтверждения', async () => {
  const own = fixture({ id: 'own', title: 'Своя встреча', isOrganizer: true })
  apiRequest.mockImplementation((path: string, options?: { method?: string }) => {
    if (path === '/api/v1/categories') return Promise.resolve([])
    if (path === '/api/v1/events/own/cancel' && options?.method === 'POST')
      return Promise.resolve({ ...own, state: 'cancelled_by_organizer' })
    return Promise.resolve({ items: [own], total: 1, limit: 100, offset: 0 })
  })
  const wrapper = mountFeed()
  try {
    await flushPromises()
    await card(wrapper, 'Своя встреча').get('button.join-button').trigger('click')
    await flushPromises()
    expect(apiRequest).not.toHaveBeenCalledWith('/api/v1/events/own/cancel', expect.anything())
    expect(card(wrapper, 'Своя встреча').get('.confirm').text()).toContain(
      'Отменить событие? Это действие нельзя отменить.',
    )

    await card(wrapper, 'Своя встреча').get('.confirm button').trigger('click')
    await flushPromises()
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/own/cancel', { method: 'POST' })
  } finally {
    wrapper.unmount()
  }
})

it('присоединяется по кнопке свободной карточки', async () => {
  const free = fixture({ id: 'free', title: 'Свободная встреча' })
  apiRequest.mockImplementation((path: string, options?: { method?: string }) => {
    if (path === '/api/v1/categories') return Promise.resolve([])
    if (path === '/api/v1/events/free/participants/me' && options?.method === 'PUT')
      return Promise.resolve({ ...free, isParticipant: true })
    return Promise.resolve({ items: [free], total: 1, limit: 100, offset: 0 })
  })
  const wrapper = mountFeed()
  try {
    await flushPromises()
    await card(wrapper, 'Свободная встреча').get('button.join-button').trigger('click')
    await flushPromises()
    expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/free/participants/me', {
      method: 'PUT',
    })
  } finally {
    wrapper.unmount()
  }
})
