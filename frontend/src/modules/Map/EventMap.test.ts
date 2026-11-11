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
      bindTooltip: vi.fn(),
      bindPopup: vi.fn(),
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
afterEach(() => vi.clearAllMocks())

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

it('не сбрасывает зум при ежесекундном обновлении массива с теми же точками', async () => {
  const points = [
    { id: 'a', title: 'Первая', latitude: 59.93, longitude: 30.33 },
    { id: 'b', title: 'Вторая', latitude: 59.97, longitude: 30.37 },
  ]
  const wrapper = mount(EventMap, { props: { points } })
  expect(leaflet.circleMarker).toHaveBeenCalledTimes(2)
  expect(leaflet.map).toHaveBeenCalledWith(expect.anything(), { scrollWheelZoom: true })
  expect(leaflet.instance.fitBounds).toHaveBeenCalledTimes(1)
  await wrapper.setProps({ points: points.map((point) => ({ ...point })) })
  expect(leaflet.instance.fitBounds).toHaveBeenCalledTimes(1)
  expect(leaflet.circleMarker).toHaveBeenCalledTimes(2)
  await wrapper.setProps({ points: [{ ...points[0]!, title: 'Новое название' }, points[1]!] })
  expect(leaflet.instance.fitBounds).toHaveBeenCalledTimes(1)
  await wrapper.get('.show-all').trigger('click')
  expect(leaflet.instance.fitBounds).toHaveBeenCalledTimes(2)
})

it('показывает оба события с одинаковыми координатами в popup со счётчиком', async () => {
  const wrapper = mount(EventMap, {
    props: {
      points: [
        { id: 'a', title: 'Кофе', latitude: 59.93, longitude: 30.33 },
        { id: 'b', title: 'Обед', latitude: 59.93, longitude: 30.33 },
      ],
    },
  })
  expect(leaflet.circleMarker).toHaveBeenCalledTimes(1)
  const marker = leaflet.circleMarker.mock.results[0]!.value
  expect(marker.bindTooltip.mock.calls[0][0].textContent).toBe('2')
  const buttons = (marker.bindPopup.mock.calls[0][0] as HTMLElement).querySelectorAll('button')
  expect([...buttons].map((button) => button.textContent)).toEqual(['Кофе', 'Обед'])
  buttons[0]!.click()
  buttons[1]!.click()
  expect(wrapper.emitted('select')).toEqual([['a'], ['b']])
  expect(wrapper.findAll('.map-list button')).toHaveLength(2)
})

it('сообщает, когда у найденных событий нет координат', () => {
  const wrapper = mount(EventMap, {
    props: { points: [{ id: 'no-point', title: 'Без точки', latitude: null, longitude: null }] },
  })
  expect(wrapper.text()).toContain('У найденных событий нет координат')
})
