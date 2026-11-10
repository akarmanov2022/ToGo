import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import LocationPicker from './LocationPicker.vue'
import type { Coordinates } from './types'

const leaflet = vi.hoisted(() => {
  type Handler = (event: { latlng: { lat: number; lng: number } }) => void
  const mapHandlers = new Map<string, Handler>()
  const tileHandlers = new Map<string, () => void>()
  const instance = {
    setView: vi.fn(() => instance),
    attributionControl: { setPrefix: vi.fn() },
    on: vi.fn((event: string, handler: Handler) => {
      mapHandlers.set(event, handler)
      return instance
    }),
    off: vi.fn((event: string) => {
      mapHandlers.delete(event)
      return instance
    }),
    invalidateSize: vi.fn(),
    remove: vi.fn(),
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
    addTo: vi.fn(() => tiles),
    redraw: vi.fn(),
  }
  const marker = {
    addTo: vi.fn(() => marker),
    setLatLng: vi.fn(),
    remove: vi.fn(),
  }
  return {
    instance,
    tiles,
    marker,
    mapHandlers,
    tileHandlers,
    map: vi.fn(() => instance),
    tileLayer: vi.fn(() => tiles),
    circleMarker: vi.fn(() => marker),
  }
})

vi.mock('leaflet', () => ({
  map: leaflet.map,
  tileLayer: leaflet.tileLayer,
  circleMarker: leaflet.circleMarker,
}))

enableAutoUnmount(afterEach)

let resizeCallback: ResizeObserverCallback
let frameCallback: FrameRequestCallback
const disconnect = vi.fn()
const observe = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  leaflet.mapHandlers.clear()
  leaflet.tileHandlers.clear()
  vi.stubGlobal(
    'ResizeObserver',
    class {
      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback
      }
      observe = observe
      disconnect = disconnect
    },
  )
  vi.stubGlobal(
    'requestAnimationFrame',
    vi.fn((callback: FrameRequestCallback) => {
      frameCallback = callback
      return 42
    }),
  )
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
})

afterEach(() => vi.unstubAllGlobals())

function mountPicker(value: Coordinates | null = null) {
  const model = ref<Coordinates | null>(value)
  const host = mount(
    defineComponent({
      setup: () => () =>
        h(LocationPicker, {
          modelValue: model.value,
          address: '  Невский проспект, 1  ',
          'onUpdate:modelValue': (value) => {
            model.value = value
          },
        }),
    }),
  )
  return { host, picker: host.findComponent(LocationPicker), model }
}

async function tileEvent(event: string) {
  leaflet.tileHandlers.get(event)?.()
  await nextTick()
}

describe('LocationPicker', () => {
  it('убирает значок «Leaflet» из атрибуции, сохраняя © OpenStreetMap', () => {
    mountPicker()
    expect(leaflet.instance.attributionControl.setPrefix).toHaveBeenCalledWith(false)
  })

  it('выбирает и меняет точку через v-model, перемещая существующий маркер', async () => {
    const { picker, model } = mountPicker()
    expect(picker.text()).toContain('Невский проспект, 1')
    expect(leaflet.circleMarker).not.toHaveBeenCalled()

    leaflet.mapHandlers.get('click')?.({ latlng: { lat: 59.93, lng: 30.33 } })
    await nextTick()

    expect(model.value).toEqual({ latitude: 59.93, longitude: 30.33 })
    expect(leaflet.circleMarker).toHaveBeenCalledWith([59.93, 30.33], expect.any(Object))
    expect(leaflet.marker.addTo).toHaveBeenCalledWith(leaflet.instance)
    expect(picker.text()).toContain('59.93000, 30.33000')

    leaflet.mapHandlers.get('click')?.({ latlng: { lat: 60, lng: 31 } })
    await nextTick()

    expect(model.value).toEqual({ latitude: 60, longitude: 31 })
    expect(leaflet.marker.setLatLng).toHaveBeenLastCalledWith([60, 31])
    expect(leaflet.circleMarker).toHaveBeenCalledTimes(1)
  })

  it('отображает исходную точку, очищает её и позволяет выбрать новую', async () => {
    const { picker, model } = mountPicker({ latitude: 59, longitude: 30 })
    expect(leaflet.circleMarker).toHaveBeenCalledWith([59, 30], expect.any(Object))
    expect(leaflet.instance.setView).toHaveBeenCalledWith([59, 30], 11)

    await picker.get('.location-picker__clear').trigger('click')

    expect(model.value).toBeNull()
    expect(leaflet.marker.remove).toHaveBeenCalledTimes(1)
    expect(picker.find('.location-picker__clear').exists()).toBe(false)
    expect(picker.text()).toContain('Отметьте место на карте')

    leaflet.mapHandlers.get('click')?.({ latlng: { lat: 58, lng: 29 } })
    await nextTick()
    expect(leaflet.circleMarker).toHaveBeenCalledTimes(2)
    expect(model.value).toEqual({ latitude: 58, longitude: 29 })
  })

  it('синхронизирует маркер с внешними изменениями и очисткой модели', async () => {
    const { model } = mountPicker()
    model.value = { latitude: 59, longitude: 30 }
    await nextTick()
    model.value = { latitude: 60, longitude: 31 }
    await nextTick()
    expect(leaflet.marker.setLatLng).toHaveBeenLastCalledWith([60, 31])
    model.value = null
    await nextTick()
    expect(leaflet.marker.remove).toHaveBeenCalledOnce()
  })

  it('показывает загрузку и не скрывает ошибку при завершении частично неудачного цикла', async () => {
    const { picker } = mountPicker()
    expect(picker.text()).toContain('Загружаем карту')
    expect(picker.get('.location-picker__map').attributes('aria-busy')).toBe('true')
    await tileEvent('loading')
    await tileEvent('tileerror')
    await tileEvent('load')
    expect(picker.text()).toContain('Не удалось загрузить карту')
    expect(picker.get('.location-picker__map').attributes('aria-busy')).toBe('false')
  })

  it('повторяет загрузку без потери точки и восстанавливается после успеха', async () => {
    const value = { latitude: 59, longitude: 30 }
    const { picker, model } = mountPicker(value)
    await tileEvent('tileerror')
    await picker.get('.location-picker__retry').trigger('click')
    expect(leaflet.tiles.redraw).toHaveBeenCalledOnce()
    expect(picker.text()).toContain('Загружаем карту')
    await tileEvent('loading')
    await tileEvent('load')
    expect(picker.text()).not.toContain('Не удалось загрузить карту')
    expect(picker.text()).not.toContain('Загружаем карту')
    expect(model.value).toEqual(value)
    expect(leaflet.marker.remove).not.toHaveBeenCalled()
    expect(leaflet.map).toHaveBeenCalledOnce()
  })

  it('восстанавливается в новом цикле загрузки, но сохраняет повторную ошибку', async () => {
    const { picker } = mountPicker()
    await tileEvent('tileerror')
    await picker.get('.location-picker__retry').trigger('click')
    await tileEvent('tileerror')
    await tileEvent('load')
    expect(picker.find('.location-picker__retry').exists()).toBe(true)
    await tileEvent('loading')
    await tileEvent('load')
    expect(picker.find('.location-picker__retry').exists()).toBe(false)
  })

  it('повторяет неудачную загрузку после восстановления сети', async () => {
    mountPicker()
    window.dispatchEvent(new Event('online'))
    expect(leaflet.tiles.redraw).not.toHaveBeenCalled()
    await tileEvent('tileerror')
    window.dispatchEvent(new Event('online'))
    await nextTick()
    expect(leaflet.tiles.redraw).toHaveBeenCalledOnce()
  })

  it('объединяет resize контейнера, окна и ориентации в один кадр', () => {
    mountPicker()
    expect(observe).toHaveBeenCalledWith(expect.any(HTMLElement))
    frameCallback(0)
    vi.mocked(window.requestAnimationFrame).mockClear()
    resizeCallback([], {} as ResizeObserver)
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('orientationchange'))
    expect(window.requestAnimationFrame).toHaveBeenCalledOnce()
    frameCallback(0)
    expect(leaflet.instance.invalidateSize).toHaveBeenLastCalledWith({
      pan: false,
      debounceMoveend: true,
    })
  })

  it('работает без ResizeObserver и освобождает карту, подписки и ожидающий кадр', () => {
    vi.stubGlobal('ResizeObserver', undefined)
    const { host } = mountPicker()
    host.unmount()
    expect(window.cancelAnimationFrame).toHaveBeenCalledWith(42)
    expect(leaflet.instance.off).toHaveBeenCalledWith('click')
    expect(leaflet.instance.remove).toHaveBeenCalledOnce()
    expect(leaflet.tileHandlers.size).toBe(0)
    vi.mocked(window.requestAnimationFrame).mockClear()
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('orientationchange'))
    window.dispatchEvent(new Event('online'))
    expect(window.requestAnimationFrame).not.toHaveBeenCalled()
    expect(leaflet.tiles.redraw).not.toHaveBeenCalled()
  })

  it('отключает ResizeObserver при размонтировании', () => {
    const { host } = mountPicker()
    host.unmount()
    expect(disconnect).toHaveBeenCalledOnce()
  })
})
