import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import PlacePicker from './PlacePicker.vue'
import type { MeetingPlace } from './types'

const leaflet = vi.hoisted(() => {
  const tileHandlers = new Map<string, () => void>()
  const mapHandlers = new Map<string, () => void>()
  const instance = {
    setView: vi.fn(() => instance),
    fitBounds: vi.fn(),
    on: vi.fn((event: string, handler: () => void) => {
      mapHandlers.set(event, handler)
      return instance
    }),
    off: vi.fn((event: string) => {
      mapHandlers.delete(event)
      return instance
    }),
    invalidateSize: vi.fn(),
    remove: vi.fn(),
    getBounds: vi.fn(() => ({
      getSouth: (): number => 59.925,
      getWest: (): number => 30.3,
      getNorth: (): number => 59.95,
      getEast: (): number => 30.38,
    })),
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
    mapHandlers,
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
  leaflet.mapHandlers.clear()
  leaflet.instance.getBounds.mockReturnValue({
    getSouth: () => 59.925,
    getWest: () => 30.3,
    getNorth: () => 59.95,
    getEast: () => 30.38,
  })
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

it('передаёт область карты для POI-поиска и не сбрасывает ракурс после получения мест', async () => {
  const wrapper = mount(PlacePicker, { props: { modelValue: null, places: [], searchable: true } })
  await wrapper.setProps({ places })
  expect(leaflet.instance.fitBounds).not.toHaveBeenCalled()
  await wrapper
    .findAll('button')
    .find((button) => button.text() === 'Искать в этой области')!
    .trigger('click')
  expect(wrapper.emitted('searchArea')).toEqual([[[59.925, 30.3, 59.95, 30.38]]])
})

it('после перемещения предлагает поиск, но не запускает его автоматически; показывает загрузку', async () => {
  const wrapper = mount(PlacePicker, {
    props: {
      modelValue: null,
      places,
      searchable: true,
      searchedBounds: [59.925, 30.3, 59.95, 30.38],
    },
  })
  expect(wrapper.text()).not.toContain('Искать в этой области')
  leaflet.instance.getBounds.mockReturnValue({
    getSouth: () => 55.75,
    getWest: () => 37.6,
    getNorth: () => 55.77,
    getEast: () => 37.64,
  })
  leaflet.mapHandlers.get('moveend')!()
  await nextTick()
  expect(wrapper.emitted('searchArea')).toBeUndefined()
  const button = wrapper
    .findAll('button')
    .find((button) => button.text() === 'Искать в этой области')!
  await button.trigger('click')
  expect(wrapper.emitted('searchArea')).toEqual([[[55.75, 37.6, 55.77, 37.64]]])
  await wrapper.setProps({ searching: true, searchedBounds: [55.75, 37.6, 55.77, 37.64] })
  const searching = wrapper
    .findAll('button')
    .find((button) => button.text().includes('Ищем места'))!
  expect(searching.attributes('disabled')).toBeDefined()
  expect(searching.attributes('aria-busy')).toBe('true')
  expect(wrapper.find('.place-picker__spinner').exists()).toBe(true)
  expect(wrapper.text()).toContain('Обновляем места')
})

it('показывает компактный выпадающий список с поиском и выбором с клавиатуры', async () => {
  const { picker, model } = mountPicker()
  expect(picker.find('.place-picker__panel').exists()).toBe(false)
  await picker.get('.place-picker__list-toggle').trigger('click')
  expect(picker.get('.place-picker__panel').attributes('role')).toBe('dialog')
  expect(picker.findAll('.place-picker__option')).toHaveLength(2)
  expect(picker.get('.place-picker__list').text()).not.toContain('Невский проспект')
  await picker.get('input[type="search"]').setValue('кафе')
  expect(picker.findAll('.place-picker__option')).toHaveLength(1)
  await picker.get('input').trigger('keydown', { key: 'Enter' })
  expect(model.value).toEqual(places[1])
  expect(picker.find('.place-picker__panel').exists()).toBe(false)
  expect(picker.get('.place-picker__summary').text()).toContain(places[1]!.address)
})

it('не сбрасывает выбранное место при поиске в другой области', async () => {
  const wrapper = mount(PlacePicker, {
    props: { modelValue: places[0]!, places, searchable: true },
  })
  await wrapper.setProps({ places: [] })
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  expect(wrapper.get('.place-picker__summary').text()).toContain(places[0]!.name)
  expect(wrapper.text()).toContain('Выбранное место сохранено')
})

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
    expect(picker.find('.place-picker__panel').exists()).toBe(false)
    await picker.get('.place-picker__list-toggle').trigger('click')
    let buttons = picker.findAll('.place-picker__option')
    expect(buttons[0]!.element.tagName).toBe('BUTTON')
    expect(buttons[0]!.attributes('aria-pressed')).toBe('true')
    await buttons[1]!.trigger('click')
    expect(model.value).toEqual(places[1])
    expect(picker.find('.place-picker__panel').exists()).toBe(false)
    await picker.get('.place-picker__list-toggle').trigger('click')
    buttons = picker.findAll('.place-picker__option')
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
    expect(picker.get('.place-picker__summary').text()).toContain('Выберите место из списка')
    await picker.get('.place-picker__list-toggle').trigger('click')
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
    expect(picker.text()).toContain('не найдено мест этой категории')
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
    await picker.get('.place-picker__list-toggle').trigger('click')
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
