import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import PlacePicker from './PlacePicker.vue'
import type { MeetingPlace } from './types'

const leaflet = vi.hoisted(() => {
  const tileHandlers = new Map<string, () => void>()
  const instance = {
    setView: vi.fn(() => instance),
    fitBounds: vi.fn(),
    on: vi.fn(),
    invalidateSize: vi.fn(),
    remove: vi.fn(),
    attributionControl: { setPrefix: vi.fn() },
  }
  const tiles = {
    on: vi.fn((event: string, handler: () => void) => {
      tileHandlers.set(event, handler)
      return tiles
    }),
    off: vi.fn((event: string) => {
      tileHandlers.delete(event)
      return tiles
    }),
    addTo: vi.fn(),
    redraw: vi.fn(),
  }
  function createMarker() {
    const handlers = new Map<string, () => void>()
    const marker = {
      handlers,
      addTo: vi.fn(() => marker),
      on: vi.fn((event: string, handler: () => void) => {
        handlers.set(event, handler)
        return marker
      }),
      off: vi.fn((event: string) => {
        handlers.delete(event)
        return marker
      }),
      setStyle: vi.fn(),
      bringToFront: vi.fn(),
      remove: vi.fn(),
    }
    return marker
  }
  return {
    instance,
    tiles,
    tileHandlers,
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
const disconnect = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  leaflet.tileHandlers.clear()
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe = vi.fn()
      disconnect = disconnect
    },
  )
  vi.stubGlobal(
    'requestAnimationFrame',
    vi.fn(() => 42),
  )
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
})
afterEach(() => vi.unstubAllGlobals())

const places: MeetingPlace[] = [
  {
    id: 'park',
    name: 'Парк',
    address: 'Санкт-Петербург, Садовая улица, 1',
    latitude: 59.93,
    longitude: 30.33,
    category: 'PICNIC',
  },
  {
    id: 'cafe',
    name: 'Кафе',
    address: 'Санкт-Петербург, Невский проспект, 2',
    latitude: 59.94,
    longitude: 30.34,
    category: 'CAFE',
  },
]

function mountPicker(value: MeetingPlace | null = null) {
  const model = ref<MeetingPlace | null>(value)
  const options = ref([...places])
  const host = mount(
    defineComponent({
      setup: () => () =>
        h(PlacePicker, {
          modelValue: model.value,
          places: options.value,
          'onUpdate:modelValue': (value) => {
            model.value = value
          },
        }),
    }),
  )
  return { host, picker: host.findComponent(PlacePicker), model, options }
}

async function tileEvent(event: string) {
  leaflet.tileHandlers.get(event)?.()
  await nextTick()
}

describe('PlacePicker', () => {
  it('показывает все места и выбирает только готовый маркер с названием и адресом', async () => {
    const { picker, model } = mountPicker()
    expect(leaflet.circleMarker).toHaveBeenCalledTimes(2)
    expect(leaflet.circleMarker).toHaveBeenCalledWith(
      [59.93, 30.33],
      expect.objectContaining({ bubblingMouseEvents: false }),
    )
    const marker = leaflet.circleMarker.mock.results[1]!.value
    marker.handlers.get('click')?.()
    await nextTick()
    expect(model.value).toEqual(places[1])
    expect(picker.get('.place-picker__summary').text()).toContain(places[1]!.name)
    expect(picker.get('.place-picker__summary').text()).toContain(places[1]!.address)
    expect(picker.text()).not.toContain('59.94')
    expect(picker.text()).not.toContain('30.34')
    expect(marker.setStyle).toHaveBeenLastCalledWith(expect.objectContaining({ radius: 12 }))
    expect(marker.bringToFront).toHaveBeenCalled()
  })

  it('убирает значок «Leaflet» из атрибуции, сохраняя © OpenStreetMap', () => {
    mountPicker()
    expect(leaflet.instance.attributionControl.setPrefix).toHaveBeenCalledWith(false)
  })

  it('не подписывается на произвольные клики карты и не предлагает ручной адрес', () => {
    const { picker, model } = mountPicker()
    expect(leaflet.instance.on).not.toHaveBeenCalled()
    expect(picker.find('input').exists()).toBe(false)
    expect(model.value).toBeNull()
  })

  it('позволяет выбрать и сменить место нативными кнопками с доступным состоянием', async () => {
    const { picker, model } = mountPicker(places[0])
    const buttons = picker.findAll('.place-picker__option')
    expect(buttons[0]!.element.tagName).toBe('BUTTON')
    expect(buttons[0]!.attributes('aria-pressed')).toBe('true')
    await buttons[1]!.trigger('click')
    expect(model.value).toEqual(places[1])
    expect(buttons[0]!.attributes('aria-pressed')).toBe('false')
    expect(buttons[1]!.attributes('aria-pressed')).toBe('true')
    expect(leaflet.circleMarker.mock.results[0]!.value.setStyle).toHaveBeenLastCalledWith(
      expect.objectContaining({ radius: 8 }),
    )
  })

  it('сбрасывает удалённое место и удаляет старые маркеры при изменении списка', async () => {
    const { picker, model, options } = mountPicker(places[0])
    const oldMarkers = leaflet.circleMarker.mock.results.map((result) => result.value)
    options.value = [places[1]!]
    await nextTick()
    expect(model.value).toBeNull()
    expect(picker.get('.place-picker__summary').text()).toBe('Место не выбрано')
    expect(picker.findAll('.place-picker__option')).toHaveLength(1)
    oldMarkers.forEach((marker) => {
      expect(marker.remove).toHaveBeenCalledOnce()
      expect(marker.handlers.size).toBe(0)
    })
  })

  it('отклоняет внешнее значение вне списка и поддерживает пустой список', async () => {
    const { picker, model, options } = mountPicker({ ...places[0]!, id: 'unknown' })
    await nextTick()
    expect(model.value).toBeNull()
    options.value = []
    await nextTick()
    expect(picker.text()).toContain('Нет доступных мест встречи')
  })

  it('сохраняет выбор при обновлении списка с тем же id и показывает актуальный адрес', async () => {
    const { picker, model, options } = mountPicker(places[0])
    options.value = [{ ...places[0]!, address: 'Новый адрес' }]
    await nextTick()
    expect(model.value?.id).toBe('park')
    expect(model.value?.address).toBe('Новый адрес')
    expect(picker.get('.place-picker__summary').text()).toContain('Новый адрес')
  })

  it('сохраняет ошибку частичной загрузки и позволяет выбирать офлайн и повторять загрузку', async () => {
    const { picker, model } = mountPicker()
    expect(picker.get('.place-picker__map').attributes('aria-busy')).toBe('true')
    await tileEvent('loading')
    await tileEvent('tileerror')
    await tileEvent('load')
    expect(picker.text()).toContain('Не удалось загрузить карту')
    await picker.get('.place-picker__option').trigger('click')
    expect(model.value).toEqual(places[0])
    await picker.get('.place-picker__retry').trigger('click')
    expect(leaflet.tiles.redraw).toHaveBeenCalledOnce()
    await tileEvent('loading')
    await tileEvent('load')
    expect(picker.find('.place-picker__retry').exists()).toBe(false)
    expect(picker.get('.place-picker__map').attributes('aria-busy')).toBe('false')
    expect(model.value).toEqual(places[0])
  })

  it('повторяет загрузку при восстановлении сети', async () => {
    mountPicker()
    window.dispatchEvent(new Event('online'))
    expect(leaflet.tiles.redraw).not.toHaveBeenCalled()
    await tileEvent('tileerror')
    window.dispatchEvent(new Event('online'))
    expect(leaflet.tiles.redraw).toHaveBeenCalledOnce()
  })

  it('освобождает карту, маркеры, подписки, observer и кадр', () => {
    const { host } = mountPicker()
    const markers = leaflet.circleMarker.mock.results.map((result) => result.value)
    host.unmount()
    expect(disconnect).toHaveBeenCalledOnce()
    expect(window.cancelAnimationFrame).toHaveBeenCalledWith(42)
    expect(leaflet.instance.remove).toHaveBeenCalledOnce()
    expect(leaflet.tileHandlers.size).toBe(0)
    markers.forEach((marker) => {
      expect(marker.remove).toHaveBeenCalledOnce()
      expect(marker.handlers.size).toBe(0)
    })
    vi.mocked(window.requestAnimationFrame).mockClear()
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('orientationchange'))
    window.dispatchEvent(new Event('online'))
    expect(window.requestAnimationFrame).not.toHaveBeenCalled()
    expect(leaflet.tiles.redraw).not.toHaveBeenCalled()
  })
})
