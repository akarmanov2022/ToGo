import { VectorTile } from '@mapbox/vector-tile'
import Pbf from 'pbf'
import { isInPlaceBounds, placeBounds, PlaceSearchAreaError } from './placeBounds'
import type { MeetingPlace, PlaceBounds } from './types'

export const poiTileMetadataUrl = 'https://tiles.openfreemap.org/planet'
const zoom = 14
const cache = new Map<string, VectorTile>()
const subclasses: Record<string, readonly string[]> = {
  PICNIC: ['park', 'picnic_site'],
  RUN: ['park', 'track', 'running'],
  EXCURSION: ['museum', 'attraction', 'gallery', 'art_gallery'],
  BOARD_GAMES: ['board_games'],
  QUEST: ['escape_game'],
  CINEMA: ['cinema'],
  CAFE: ['cafe', 'restaurant'],
}

export function poiTileCoordinates(bounds?: PlaceBounds): { x: number; y: number }[] {
  const [south, west, north, east] = placeBounds(bounds)
  const x = (lon: number) => Math.floor(((lon + 180) / 360) * 2 ** zoom)
  const y = (lat: number) =>
    Math.floor(((1 - Math.asinh(Math.tan((lat * Math.PI) / 180)) / Math.PI) / 2) * 2 ** zoom)
  const minX = x(west),
    maxX = x(east),
    minY = Math.max(0, y(north)),
    maxY = Math.min(2 ** zoom - 1, y(south))
  if ((maxX - minX + 1) * (maxY - minY + 1) > 36)
    throw new PlaceSearchAreaError('Приблизьте карту, чтобы найти места в этой области.')
  const tiles = []
  for (let tx = minX; tx <= maxX; tx++)
    for (let ty = minY; ty <= maxY; ty++)
      tiles.push({ x: ((tx % 2 ** zoom) + 2 ** zoom) % 2 ** zoom, y: ty })
  return tiles
}

export function extractTilePlaces(
  tile: VectorTile,
  x: number,
  y: number,
  category: string,
  bounds?: PlaceBounds,
): MeetingPlace[] {
  const bbox = placeBounds(bounds)
  const layer = tile.layers.poi
  const result: MeetingPlace[] = []
  if (!layer) return result
  for (let index = 0; index < layer.length; index++) {
    const feature = layer.feature(index)
    const tags = feature.properties
    if (!subclasses[category]?.includes(String(tags.subclass ?? tags.class))) continue
    const name = String(tags['name:ru'] ?? tags.name ?? '').trim()
    if (!name) continue
    const geometry = feature.toGeoJSON(x, y, zoom).geometry
    if (geometry.type !== 'Point') continue
    const [longitude, latitude] = geometry.coordinates
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) continue
    if (!isInPlaceBounds(latitude!, longitude!, bbox)) continue
    result.push({
      id: `osm-poi-${feature.id ?? `${name}-${latitude}-${longitude}`}`,
      name,
      address: name,
      latitude: latitude!,
      longitude: longitude!,
      category,
    })
  }
  return result
}

export async function fetchVectorPlaces(
  category: string,
  signal?: AbortSignal,
  bounds?: PlaceBounds,
): Promise<MeetingPlace[]> {
  if (!Object.hasOwn(subclasses, category)) throw new Error('Выберите категорию места')
  const coordinates = poiTileCoordinates(bounds)
  const controller = new AbortController()
  const abort = () => controller.abort()
  if (signal?.aborted) throw new DOMException('Поиск отменён', 'AbortError')
  signal?.addEventListener('abort', abort, { once: true })
  const timer = setTimeout(abort, 15_000)
  try {
    const metadataResponse = await fetch(poiTileMetadataUrl, {
      signal: controller.signal,
      credentials: 'omit',
      cache: 'default',
    })
    if (!metadataResponse.ok) throw new Error('Не удалось загрузить источник POI')
    const metadata = await metadataResponse.json()
    const template = metadata.tiles?.[0]
    if (
      typeof template !== 'string' ||
      !template.startsWith('https://tiles.openfreemap.org/planet/')
    )
      throw new Error('Некорректный источник POI')
    const places = new Map<string, MeetingPlace>()
    let cursor = 0
    async function worker() {
      while (cursor < coordinates.length) {
        if (controller.signal.aborted) throw new DOMException('Поиск отменён', 'AbortError')
        const { x, y } = coordinates[cursor++]!
        const url = template
          .replace('{z}', String(zoom))
          .replace('{x}', String(x))
          .replace('{y}', String(y))
        let tile = cache.get(url)
        if (!tile) {
          const response = await fetch(url, {
            signal: controller.signal,
            credentials: 'omit',
            cache: 'force-cache',
          })
          if (!response.ok) throw new Error('Не удалось загрузить POI области')
          tile = new VectorTile(new Pbf(new Uint8Array(await response.arrayBuffer())))
          cache.set(url, tile)
          if (cache.size > 64) cache.delete(cache.keys().next().value!)
        }
        for (const place of extractTilePlaces(tile, x, y, category, bounds))
          places.set(place.id, place)
      }
    }
    await Promise.all([worker(), worker()])
    return [...places.values()].sort((a, b) => a.name.localeCompare(b.name, 'ru')).slice(0, 300)
  } finally {
    controller.abort()
    clearTimeout(timer)
    signal?.removeEventListener('abort', abort)
  }
}
