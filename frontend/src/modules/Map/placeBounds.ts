import type { PlaceBounds } from './types'

export const initialPlaceBounds: PlaceBounds = [59.915, 30.29, 59.955, 30.38]
const mercatorLatitude = 85.05112878
export class PlaceSearchAreaError extends Error {}

export function placeBounds(bounds: PlaceBounds = initialPlaceBounds): PlaceBounds {
  if (!bounds.every(Number.isFinite)) throw new PlaceSearchAreaError('Некорректная область карты')
  let width = bounds[3] - bounds[1]
  if (width < 0 && width > -360) width += 360
  const west = bounds[1] >= -180 && bounds[1] < 180 ? bounds[1] : Number((((((bounds[1] + 180) % 360) + 360) % 360) - 180).toFixed(10))
  const bbox: PlaceBounds = [Math.max(bounds[0], -mercatorLatitude), west, Math.min(bounds[2], mercatorLatitude), west + width]
  if (bbox[0] >= bbox[2] || bbox[1] >= bbox[3])
    throw new PlaceSearchAreaError('Некорректная область карты')
  if (bbox[2] - bbox[0] > 0.35 || bbox[3] - bbox[1] > 0.8)
    throw new PlaceSearchAreaError('Приблизьте карту, чтобы найти места в этой области.')
  return bbox
}

/** Leaflet допускает повторные копии мира и область через 180-й меридиан. */
export function isInPlaceBounds(latitude: number, longitude: number, bounds: PlaceBounds): boolean {
  const [south, west, north, east] = placeBounds(bounds)
  const lon = longitude < west ? longitude + 360 : longitude
  return Number.isFinite(latitude) && Number.isFinite(longitude) && latitude >= south && latitude <= north && lon >= west && lon <= east
}

export function placeBoundsParts(bounds?: PlaceBounds): PlaceBounds[] {
  const [south, west, north, east] = placeBounds(bounds)
  return east > 180 ? [[south, west, north, 180], [south, -180, north, east - 360]] : [[south, west, north, east]]
}
