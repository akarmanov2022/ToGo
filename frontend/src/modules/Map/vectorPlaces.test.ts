import { VectorTile } from '@mapbox/vector-tile'
import Pbf from 'pbf'
import { afterEach, expect, it, vi } from 'vitest'
import {
  extractTilePlaces,
  fetchVectorPlaces,
  poiTileCoordinates,
  poiTileMetadataUrl,
} from './vectorPlaces'

afterEach(() => vi.unstubAllGlobals())
const x = Math.floor(((30.3351 + 180) / 360) * 2 ** 14)
const y = Math.floor(
  ((1 - Math.asinh(Math.tan((59.9343 * Math.PI) / 180)) / Math.PI) / 2) * 2 ** 14,
)

function tileBytes(subclass: string) {
  const pbf = new Pbf()
  pbf.writeMessage(
    3,
    (_: undefined, layer) => {
      layer.writeVarintField(15, 2)
      layer.writeStringField(1, 'poi')
      layer.writeVarintField(5, 4096)
      for (const key of ['name', 'subclass']) layer.writeStringField(3, key)
      for (const value of ['Место из OSM', subclass])
        layer.writeMessage(4, (value, p) => p.writeStringField(1, value), value)
      layer.writeMessage(
        2,
        (_: undefined, feature) => {
          feature.writeVarintField(1, 123)
          feature.writePackedVarint(2, [0, 0, 1, 1])
          feature.writeVarintField(3, 1)
          feature.writePackedVarint(4, [9, 4096, 4096])
        },
        undefined,
      )
    },
    undefined,
  )
  return pbf.finish()
}

it.each([
  ['PICNIC', 'park'],
  ['RUN', 'park'],
  ['CINEMA', 'cinema'],
  ['CAFE', 'cafe'],
  ['EXCURSION', 'museum'],
  ['QUEST', 'escape_game'],
  ['BOARD_GAMES', 'board_games'],
])('извлекает POI категории %s из настоящего формата MVT', (category, subclass) => {
  const tile = new VectorTile(new Pbf(tileBytes(subclass)))
  const places = extractTilePlaces(tile, x, y, category)
  expect(places).toHaveLength(1)
  expect(places[0]).toMatchObject({ name: 'Место из OSM', category, id: 'osm-poi-123' })
  expect(places[0]!.latitude).toBeGreaterThan(59.9)
  expect(places[0]!.longitude).toBeGreaterThan(30.3)
  expect(extractTilePlaces(tile, x, y, category === 'CINEMA' ? 'PICNIC' : 'CINEMA')).toHaveLength(0)
})

it('ограничивает число тайлов, не скачивает весь город', () => {
  expect(poiTileCoordinates().length).toBeLessThanOrEqual(36)
  expect(() => poiTileCoordinates([59.8, 30.1, 60.1, 30.6])).toThrow('Приблизьте карту')
})

it('получает реальные бинарные POI-тайлы без авторизации и переиспользует их между категориями', async () => {
  const fetch = vi.fn((url: string) => {
    if (url === poiTileMetadataUrl)
      return Promise.resolve(
        new Response(
          JSON.stringify({
            tiles: ['https://tiles.openfreemap.org/planet/test/1/{z}/{x}/{y}.pbf'],
          }),
        ),
      )
    return Promise.resolve(
      new Response(
        url.endsWith(`/${x}/${y}.pbf`)
          ? Uint8Array.from(tileBytes('cinema')).buffer
          : new ArrayBuffer(0),
      ),
    )
  })
  vi.stubGlobal('fetch', fetch)
  const places = await fetchVectorPlaces('CINEMA')
  expect(places).toHaveLength(1)
  const requests = fetch.mock.calls.length
  await expect(fetchVectorPlaces('PICNIC')).resolves.toEqual([])
  expect(fetch.mock.calls.length).toBe(requests + 1)
  for (const call of vi.mocked(globalThis.fetch).mock.calls) {
    expect(call[1]).toMatchObject({ credentials: 'omit' })
    expect(call[1]?.headers).toBeUndefined()
  }
})

it('не запускает запросы для отменённого поиска', async () => {
  const fetch = vi.fn()
  vi.stubGlobal('fetch', fetch)
  const controller = new AbortController()
  controller.abort()
  await expect(fetchVectorPlaces('CINEMA', controller.signal)).rejects.toMatchObject({
    name: 'AbortError',
  })
  expect(fetch).not.toHaveBeenCalled()
})
