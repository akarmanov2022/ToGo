import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, expect, it, vi } from 'vitest'
import { CreateEvent } from '@/modules/Events'
import { EventSchedule, toScheduleTimestamp } from '@/modules/EventSchedule'
import { useAuthStore } from '@/modules/Auth'

const { circleMarker } = vi.hoisted(() => ({ circleMarker: vi.fn() }))
vi.mock('leaflet', () => ({
  map: () => {
    const map = {
      setView: () => map,
      fitBounds: vi.fn(),
      invalidateSize: vi.fn(),
      remove: vi.fn(),
      on: vi.fn(),
      off: vi.fn(),
      getBounds: () => ({ getSouth: () => 59.915, getWest: () => 30.29, getNorth: () => 59.955, getEast: () => 30.38 }),
      attributionControl: { setPrefix: vi.fn() },
    }
    return map
  },
  tileLayer: () => {
    const tiles = { on: () => tiles, off: vi.fn(), addTo: vi.fn() }
    return tiles
  },
  circleMarker,
}))
afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

it('категория → запрос OSM → маркеры → выбор точки → POST с местом и автоматическим эмодзи', async () => {
  const categories = [
    { code: 'PICNIC', name: 'Пикник', tag: '[leisure=park]', emoji: '🧺' },
    { code: 'CINEMA', name: 'Кино', tag: '[amenity=cinema]', emoji: '🎬' },
    { code: 'CAFE', name: 'Кафе/Ресторан', tag: '[amenity~^(cafe|restaurant)$]', emoji: '☕' },
    { code: 'RUN', name: 'Пробежка', tag: '[leisure=park]', emoji: '🏃' },
    {
      code: 'EXCURSION',
      name: 'Поход на экскурсию',
      tag: '[tourism~^(museum|attraction|gallery)$]',
      emoji: '🏛️',
    },
    { code: 'QUEST', name: 'Квест', tag: '[leisure=escape_game]', emoji: '🔎' },
    { code: 'BOARD_GAMES', name: 'Настолки', tag: '[board_games=yes]', emoji: '🎲' },
  ]
  const clicks: (() => void)[] = []
  circleMarker.mockImplementation(() => {
    const marker = {
      addTo: () => marker,
      on: (_event: string, click: () => void) => {
        clicks.push(click)
        return marker
      },
      off: vi.fn(),
      remove: vi.fn(),
      setStyle: vi.fn(),
      bringToFront: vi.fn(),
    }
    return marker
  })
  const json = (value: unknown) =>
    new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
  let active = categories[0]!
  const fetch = vi.fn((url: string, options?: RequestInit) => {
    if (url.includes('/interpreter')) {
      const query = (options!.body as URLSearchParams).get('data')!
      expect(query.replace(/["\s]/g, '')).toContain(active.tag)
      expect(query).toContain('(59.915, 30.29, 59.955, 30.38)')
      expect(query).toContain('out body center')
      // Типичный ответ Overpass: парк — полигон с center, кинотеатр — node с lat/lon.
      return Promise.resolve(
        json({
          elements: [
            {
              type: active.code === 'PICNIC' ? 'way' : 'node',
              id: 1,
              ...(active.code === 'PICNIC'
                ? { center: { lat: 59.95, lon: 30.32 } }
                : { lat: 59.94, lon: 30.31 }),
              tags: { name: `Место ${active.name}` },
            },
          ],
        }),
      )
    }
    if (url.includes('/categories')) return Promise.resolve(json(categories))
    if (options?.method === 'POST') return Promise.resolve(json({ id: 'created' }))
    return Promise.resolve(json({ items: [], total: 0 }))
  })
  vi.stubGlobal('fetch', fetch)
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.status = 'authenticated'
  auth.user = {
    id: 'user',
    tgId: 1,
    tgLogin: 'user',
    name: 'Анна',
    photoUrl: null,
    city: null,
    categories: [],
    createdEventsCount: 0,
    visitedEventsCount: 0,
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  const wrapper = mount(CreateEvent, {
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient: client }], router] },
  })
  try {
    await flushPromises()
    expect(wrapper.text()).not.toContain('Иконка-эмодзи')
    for (const category of categories) {
      active = category
      await wrapper.get('.category-toggle').trigger('click')
      await wrapper
        .findAll('[role="option"]')
        .find((option) => option.text() === category.name)!
        .trigger('click')
      await flushPromises()
      expect(wrapper.get('.place-picker__summary').text()).toContain('Выберите место из списка')
      expect(circleMarker).toHaveBeenLastCalledWith(
        category.code === 'PICNIC' ? [59.95, 30.32] : [59.94, 30.31],
        expect.anything(),
      )
      clicks.at(-1)!()
      await flushPromises()
      expect(wrapper.get('.place-picker__summary').text()).toContain(`Место ${category.name}`)
      expect(wrapper.get('.event-card .icon').text()).toBe(category.emoji)
    }
    await wrapper.get('input').setValue('Вечер настолок')
    await wrapper.get('textarea').setValue('Играем вместе')
    const day = new Date()
    day.setDate(day.getDate() + 2)
    const date = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
    wrapper.findComponent(EventSchedule).vm.$emit('update:modelValue', {
      startsAt: toScheduleTimestamp({ date, time: '18:00' }),
      endsAt: toScheduleTimestamp({ date, time: '21:00' }),
      registrationClosesAt: toScheduleTimestamp({ date, time: '15:00' }),
    })
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const post = fetch.mock.calls.find(
      ([url, options]) => url === '/api/v1/events' && options?.method === 'POST',
    )
    expect(post).toBeDefined()
    expect(JSON.parse(post![1]!.body as string)).toMatchObject({
      category: 'BOARD_GAMES',
      icon: '🎲',
      latitude: 59.94,
      longitude: 30.31,
      location: 'Место Настолки',
    })
  } finally {
    wrapper.unmount()
    client.clear()
  }
})
