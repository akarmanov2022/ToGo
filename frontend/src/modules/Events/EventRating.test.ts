import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, expect, it, vi } from 'vitest'
import { ApiError } from '@/common/api/client'
import type { EventDto } from './api'
import EventRating from './EventRating.vue'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('@/common/api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/common/api/client')>()),
  apiRequest,
}))
enableAutoUnmount(afterEach)
afterEach(() => vi.clearAllMocks())
const event: EventDto = {
  id: 'meeting',
  title: 'Встреча',
  description: 'Описание',
  location: 'Парк',
  startsAt: '2026-11-01T12:00:00Z',
  endsAt: '2026-11-01T14:00:00Z',
  registrationClosesAt: '2026-11-01T10:00:00Z',
  state: 'finished',
  category: 'RUN',
  icon: null,
  latitude: null,
  longitude: null,
  minUsers: 1,
  maxUsers: 5,
  organizer: { id: 'organizer', tgLogin: null },
  participantsCount: 2,
  availableSlots: 3,
  isOrganizer: false,
  isParticipant: true,
  rating: { average: 4.3, count: 3 },
  myRating: null,
}
function render(overrides: Partial<EventDto> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(EventRating, {
    props: { event: { ...event, ...overrides } },
    global: { plugins: [[VueQueryPlugin, { queryClient: client }]] },
  })
  return { wrapper, client }
}

it('показывает среднее и число оценок', () => {
  const { wrapper } = render()
  expect(wrapper.get('.average').text()).toContain('4.3')
  expect(wrapper.text()).toContain('Оценок: 3')
  expect(wrapper.findAll('.stars button')).toHaveLength(5)
})

it.each<Partial<EventDto>>([
  { isOrganizer: true },
  { isParticipant: false },
  { state: 'recruiting' },
  { state: 'registration_closed' },
  { state: 'cancelled_by_system' },
  { state: 'cancelled_by_organizer' },
])('не предлагает оценивать при %j', (overrides) => {
  expect(render(overrides).wrapper.find('.stars').exists()).toBe(false)
})

it('не показывает пустой блок постороннему без оценок', () => {
  expect(render({ isParticipant: false, rating: null }).wrapper.find('section').exists()).toBe(
    false,
  )
})

it('посылает выбранную оценку один раз и обновляет кеш ответом сервера', async () => {
  let resolve!: (value: EventDto) => void
  apiRequest.mockImplementation(
    () =>
      new Promise<EventDto>((done) => {
        resolve = done
      }),
  )
  const { wrapper, client } = render()
  await wrapper.get('button[aria-label="4 из 5"]').trigger('click')
  await flushPromises()
  expect(apiRequest).toHaveBeenCalledWith('/api/v1/events/meeting/rating', {
    method: 'PUT',
    body: { score: 4 },
  })
  expect(
    wrapper.findAll('button').every((button) => button.attributes('disabled') !== undefined),
  ).toBe(true)
  const saved = { ...event, myRating: 4, rating: { average: 4.2, count: 4 } }
  resolve(saved)
  await flushPromises()
  expect(client.getQueryData(['events', event.id])).toEqual(saved)
  await wrapper.setProps({ event: saved })
  expect(wrapper.text()).toContain('4.2')
  expect(wrapper.text()).toContain('Ваша оценка сохранена')
  expect(wrapper.findAll('.filled')).toHaveLength(4)
  await wrapper.get('button').trigger('click')
  expect(apiRequest).toHaveBeenCalledTimes(1)
  client.clear()
})

it('показывает причину отказа и разрешает повтор после ошибки', async () => {
  apiRequest.mockRejectedValue(
    new ApiError(409, {
      code: 'ALREADY_RATED',
      message: 'Вы уже оценили это событие',
      details: [],
    }),
  )
  const { wrapper } = render()
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.get('[role="alert"]').text()).toBe('Вы уже оценили это событие')
  expect(wrapper.get('button').attributes('disabled')).toBeUndefined()
})
