import type { MeetingPlace, PlaceBounds } from './types'
import { isInPlaceBounds, placeBoundsParts, PlaceSearchAreaError } from './placeBounds'
import { fetchVectorPlaces } from './vectorPlaces'

export const overpassEndpoint = 'https://overpass-api.de/api/interpreter'
export const overpassBackupEndpoint = 'https://maps.mail.ru/osm/tools/overpass/api/interpreter'
const filters: Record<string, Record<string, string | RegExp>[]> = {
  PICNIC: [{ tourism: 'picnic_site' }, { leisure: 'park' }],
  RUN: [{ leisure: 'park' }, { leisure: 'track', sport: /^(running|athletics)$/ }],
  EXCURSION: [{ tourism: /^(museum|attraction|gallery)$/ }],
  BOARD_GAMES: [{ club: 'board_games' }, { board_games: 'yes' }, { leisure: 'board_games' }],
  QUEST: [{ leisure: 'escape_game' }],
  CINEMA: [{ amenity: 'cinema' }],
  CAFE: [{ amenity: /^(cafe|restaurant)$/ }],
}

export function placesQuery(category: string, bounds?: PlaceBounds): string {
  const selectors = filters[category]
  if (!Object.hasOwn(filters, category)) throw new Error('Выберите категорию места')
  const boxes = placeBoundsParts(bounds)
  // Резервный поиск исходных тегов: только текущая область, не весь город.
  const statements = selectors!
    .flatMap((tags) => {
      const filter = Object.entries(tags)
        .map(
          ([key, value]) =>
            `[${JSON.stringify(key)}${value instanceof RegExp ? '~' : '='}${JSON.stringify(value instanceof RegExp ? value.source : value)}]`,
        )
        .join('')
      return boxes.map((bbox) => `nwr${filter}[name](${bbox.join(', ')});`)
    })
    .join('')
  return `[out:json][timeout:15];(${statements});out body center 300;`
}

interface OsmElement {
  type: string
  id: number
  lat?: number
  lon?: number
  center?: { lat: number; lon: number }
  tags?: Record<string, string>
}

export function parsePlaces(
  elements: OsmElement[],
  category: string,
  bounds?: PlaceBounds,
): MeetingPlace[] {
  const places = new Map<string, MeetingPlace>()
  for (const element of elements) {
    const tags = element.tags ?? {}
    const latitude = element.lat ?? element.center?.lat
    const longitude = element.lon ?? element.center?.lon
    if (!tags.name || !Number.isFinite(latitude) || !Number.isFinite(longitude)) continue
    if (Math.abs(latitude!) > 90 || Math.abs(longitude!) > 180) continue
    if (bounds && !isInPlaceBounds(latitude!, longitude!, bounds)) continue
    const street = [tags['addr:street'] ?? tags['addr:place'], tags['addr:housenumber']]
      .filter(Boolean)
      .join(', ')
    const address = tags['addr:full'] || [tags['addr:city'], street].filter(Boolean).join(', ')
    const place = {
      id: `osm-${element.type}-${element.id}`,
      name: tags['name:ru'] || tags.name,
      // У OSM адрес может отсутствовать: не выдумываем улицу или номер дома.
      address: address
        ? `${tags['name:ru'] || tags.name}, ${address}`
        : tags['name:ru'] || tags.name,
      latitude: latitude!,
      longitude: longitude!,
      category,
    }
    places.set(`${place.name}:${latitude}:${longitude}`, place)
  }
  return [...places.values()].sort((a, b) => a.name.localeCompare(b.name, 'ru'))
}

const retryAfter = new Map<string, number>()
export async function fetchPlaces(
  category: string,
  signal?: AbortSignal,
  bounds?: PlaceBounds,
): Promise<MeetingPlace[]> {
  const query = placesQuery(category, bounds)
  try {
    const places = await fetchVectorPlaces(category, signal, bounds)
    // Для редких настольных клубов векторная схема не сохраняет board_games=yes.
    // Пустую выдачу этой категории дополнительно проверяем по исходным тегам OSM.
    if (places.length || category !== 'BOARD_GAMES') return places
  } catch (error) {
    if (signal?.aborted || error instanceof PlaceSearchAreaError) throw error
  }
  let error = new Error('Сервис мест перегружен. Повторите через 30 секунд.')
  // Не зацикливаем ретраи и не отправляем один поиск нескольким серверам параллельно.
  for (const endpoint of [overpassEndpoint, overpassBackupEndpoint]) {
    signal?.throwIfAborted()
    if (Date.now() < (retryAfter.get(endpoint) ?? 0)) continue
    // AbortSignal.any/timeout есть не во всех Telegram WebView. Обычный AbortController
    // поддерживает и смену категории, и ограничение времени запроса в старых WebView.
    const controller = new AbortController()
    const abort = () => controller.abort()
    signal?.addEventListener('abort', abort, { once: true })
    const timer = setTimeout(abort, 20_000)
    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        body: new URLSearchParams({ data: query }),
        credentials: 'omit',
        signal: controller.signal,
      })
      if (!response.ok) {
        if ([429, 406, 500, 502, 503, 504].includes(response.status))
          retryAfter.set(endpoint, Date.now() + 30_000)
        throw new Error('Не удалось загрузить места OpenStreetMap. Попробуйте позже.')
      }
      const data = await response.json()
      if (data.remark || !Array.isArray(data.elements))
        throw new Error('Сервис мест не завершил поиск. Попробуйте позже.')
      return parsePlaces(data.elements, category, bounds)
    } catch (cause) {
      signal?.throwIfAborted()
      error =
        cause instanceof Error && cause.message.startsWith('Сервис мест')
          ? cause
          : new Error(
              'Не удалось загрузить места OpenStreetMap. Проверьте подключение и повторите поиск.',
            )
    } finally {
      clearTimeout(timer)
      signal?.removeEventListener('abort', abort)
    }
  }
  throw error
}
