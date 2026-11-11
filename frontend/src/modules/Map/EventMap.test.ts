import { afterEach, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import EventMap from './EventMap.vue'
import type { EventMapPoint } from './types'

const leaflet = vi.hoisted(() => {
  const instance = {
    setView: vi.fn(() => instance),
    fitBounds: vi.fn(),
    on: vi.fn(),
    invalidateSize: vi.fn(),
    remove: vi.fn(),
    attributionControl: { setPrefix: vi.fn() },
  }
  const tiles = {
    on: vi.fn(() => tiles),
    off: vi.fn(() => tiles),
    addTo: vi.fn(),
    redraw: vi.fn(),
  }
  function createMarker() {
    const marker = {
      on: vi.fn(() => marker),
      addTo: vi.fn(() => marker),
      off: vi.fn(() => marker),
      remove: vi.fn(),
    }
    return marker
  }
  return {
    instance,
    tiles,
    map: vi.fn(() => instance),
    tileLayer: vi.fn(() => tiles),
    circleMarker: vi.fn(createMarker),
  }
})

vi.mock('leaflet', () => ({
  map: leaflet.map,
  tileLayer: leaflet.tileLayer,
  circleMarker: leaflet.circleMarker,
}))

enableAutoUnmount(afterEach)

it('показывает под картой только события с координатами и открывает их', async () => {
  const points: EventMapPoint[] = [
    { id: 'coffee', title: 'Кофе', latitude: 59.93, longitude: 30.33 },
    { id: 'games', title: 'Настолки', latitude: 55.75, longitude: 37.62 },
    { id: 'no-point', title: 'Без точки', latitude: null, longitude: null },
  ]
  const wrapper = mount(EventMap, { props: { points } })
  const items = wrapper.findAll('.map-list button')
  expect(items.map((item) => item.text())).toEqual(['Кофе', 'Настолки'])
  await items[0]!.trigger('click')
  expect(wrapper.emitted('select')![0]).toEqual(['coffee'])
  expect(leaflet.instance.fitBounds).toHaveBeenCalled()
})

it('сообщает, когда у найденных событий нет координат', () => {
  const wrapper = mount(EventMap, {
    props: { points: [{ id: 'no-point', title: 'Без точки', latitude: null, longitude: null }] },
  })
  expect(wrapper.text()).toContain('У найденных событий нет координат')
})
