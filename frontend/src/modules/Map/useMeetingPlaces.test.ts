import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, expect, it, vi } from 'vitest'
import { useMeetingPlaces } from './useMeetingPlaces'
import type { PlaceBounds } from './types'
vi.mock('./vectorPlaces', () => ({
  fetchVectorPlaces: vi.fn().mockRejectedValue(new Error('Vector service unavailable')),
}))

afterEach(() => vi.unstubAllGlobals())

it('отменяет старый поиск при смене категории и повторно использует кеш', async () => {
  const requests: { signal: AbortSignal; resolve: (value: Response) => void }[] = []
  const fetch = vi.fn(
    (_url: string, options: RequestInit) =>
      new Promise<Response>((resolve) => {
        requests.push({ signal: options.signal as AbortSignal, resolve })
      }),
  )
  vi.stubGlobal('fetch', fetch)
  const category = ref('CAFE')
  const client = new QueryClient()
  const wrapper = mount(
    defineComponent({
      setup() {
        const places = useMeetingPlaces(category)
        return { data: places.data }
      },
      template: '<div>{{ data?.[0]?.name }}</div>',
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } },
  )
  try {
    category.value = 'CINEMA'
    await flushPromises()
    expect(requests[0]!.signal.aborted).toBe(true)
    requests[1]!.resolve(
      new Response(
        JSON.stringify({
          elements: [{ type: 'node', id: 1, lat: 59.9, lon: 30.3, tags: { name: 'Кино' } }],
        }),
      ),
    )
    await flushPromises()
    expect(wrapper.text()).toBe('Кино')
    category.value = 'CAFE'
    await flushPromises()
    category.value = 'CINEMA'
    await flushPromises()
    expect(wrapper.text()).toBe('Кино')
    expect(fetch).toHaveBeenCalledTimes(3)
    expect(requests[2]!.signal.aborted).toBe(true)
  } finally {
    wrapper.unmount()
    client.clear()
  }
})

it('сохраняет старые точки во время поиска новой области, но не при смене категории', async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          elements: [
            { type: 'node', id: 1, lat: 55.76, lon: 37.62, tags: { name: 'Кино в Москве' } },
          ],
        }),
      ),
    )
    .mockImplementation(() => new Promise(() => {}))
  vi.stubGlobal('fetch', fetch)
  const bounds = ref<PlaceBounds>([55.75, 37.6, 55.77, 37.64])
  const category = ref('CINEMA')
  const client = new QueryClient()
  const wrapper = mount(
    defineComponent({
      setup() {
        const places = useMeetingPlaces(category, true, bounds)
        return { data: places.data, loading: places.isFetching }
      },
      template: '<div>{{ data?.[0]?.name }} {{ loading }}</div>',
    }),
    { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } },
  )
  try {
    await flushPromises()
    expect(wrapper.text()).toContain('Кино в Москве')
    bounds.value = [48.85, 2.3, 48.87, 2.33]
    await flushPromises()
    expect(wrapper.text()).toContain('Кино в Москве true')
    category.value = 'CAFE'
    await flushPromises()
    expect(wrapper.text()).not.toContain('Кино в Москве')
  } finally {
    wrapper.unmount()
    client.clear()
  }
})
