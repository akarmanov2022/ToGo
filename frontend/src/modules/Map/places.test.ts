import { afterEach, expect, it, vi } from 'vitest'
import {
  fetchPlaces,
  parsePlaces,
  placesQuery,
  overpassEndpoint,
  overpassBackupEndpoint,
} from './places'

afterEach(() => vi.unstubAllGlobals())
vi.mock('./vectorPlaces', () => ({
  fetchVectorPlaces: vi.fn().mockRejectedValue(new Error('Vector service unavailable')),
}))

it('ограничивает поиск областью и выбирает теги категории без произвольной подстановки', () => {
  expect(placesQuery('CINEMA')).toContain('["amenity"="cinema"]')
  expect(placesQuery('QUEST')).toContain('["leisure"="escape_game"]')
  expect(placesQuery('CAFE')).toContain('(59.915, 30.29, 59.955, 30.38)')
  expect(placesQuery('CAFE')).not.toContain('area')
  expect(placesQuery('CINEMA')).toContain('out body center')
  expect(placesQuery('CINEMA')).not.toContain('out center tags')
  expect(placesQuery('BOARD_GAMES')).not.toContain('["amenity"="cafe"]')
  expect(() => placesQuery('unknown')).toThrow('Выберите категорию')
})

it('строит POI-запрос в любом городе и просит приблизить слишком большую область', () => {
  expect(placesQuery('PICNIC', [59.9, 30.3, 60, 30.4])).toContain('(59.9, 30.3, 60, 30.4)')
  expect(placesQuery('PICNIC', [48.85, 2.3, 48.87, 2.33])).toContain('(48.85, 2.3, 48.87, 2.33)')
  expect(() => placesQuery('PICNIC', [59.63, 29.4, 60.25, 30.85])).toThrow('Приблизьте карту')
})

it('читает точки и центры полигонов, сохраняет адрес, пропускает непригодные объекты', () => {
  const places = parsePlaces(
    [
      {
        type: 'node',
        id: 1,
        lat: 59.9,
        lon: 30.3,
        tags: { name: 'Кофе', 'addr:street': 'Невский', 'addr:housenumber': '1' },
      },
      { type: 'way', id: 2, center: { lat: 59.95, lon: 30.35 }, tags: { name: 'Парк' } },
      { type: 'node', id: 3, tags: { name: 'Без координат' } },
      { type: 'node', id: 4, lat: 200, lon: 30, tags: { name: 'Ошибка' } },
    ],
    'CAFE',
  )
  expect(places).toHaveLength(2)
  expect(places[0]).toMatchObject({ address: 'Кофе, Невский, 1', latitude: 59.9, longitude: 30.3 })
  expect(places[1]).toMatchObject({ id: 'osm-way-2', address: 'Парк' })
})

it('запрашивает OSM без токена ToGo и cookie, поддерживает отмену', async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ elements: [] })))
  vi.stubGlobal('fetch', fetch)
  await expect(fetchPlaces('CINEMA', new AbortController().signal)).resolves.toEqual([])
  expect(fetch).toHaveBeenCalledWith(
    overpassEndpoint,
    expect.objectContaining({ credentials: 'omit', signal: expect.any(AbortSignal) }),
  )
  expect(fetch.mock.calls[0]![1].headers).toBeUndefined()
})

it('не выдаёт незавершённый поиск за пустой список', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ elements: [], remark: 'timeout' }))),
      ),
  )
  await expect(fetchPlaces('CAFE')).rejects.toThrow('не завершил поиск')
})

it('при сетевом отказе основного сервиса запрашивает резервный и сохраняет координаты узла', async () => {
  const fetch = vi
    .fn()
    .mockRejectedValueOnce(new TypeError('network'))
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          elements: [{ type: 'node', id: 1, lat: 59.9, lon: 30.3, tags: { name: 'Кинотеатр' } }],
        }),
      ),
    )
  vi.stubGlobal('fetch', fetch)
  await expect(fetchPlaces('CINEMA')).resolves.toEqual([
    expect.objectContaining({ name: 'Кинотеатр', latitude: 59.9, longitude: 30.3 }),
  ])
  expect(fetch.mock.calls.map(([url]) => url)).toEqual([overpassEndpoint, overpassBackupEndpoint])
})

it('отмена категории не запускает резервный поиск', async () => {
  const controller = new AbortController()
  const fetch = vi.fn().mockImplementation(() => {
    controller.abort()
    return Promise.reject(new DOMException('Aborted', 'AbortError'))
  })
  vi.stubGlobal('fetch', fetch)
  await expect(fetchPlaces('CINEMA', controller.signal)).rejects.toMatchObject({
    name: 'AbortError',
  })
  expect(fetch).toHaveBeenCalledTimes(1)
})
