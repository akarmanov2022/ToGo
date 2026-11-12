import type { components } from '@/common/api/schema'
import type { Coordinates } from './types'

type CoordinateFields = Pick<components['schemas']['CreateEventRequest'], 'latitude' | 'longitude'>

export function toCoordinateFields(point: Coordinates | null): CoordinateFields {
  if (!point) return {}
  if (
    !Number.isFinite(point.latitude) ||
    !Number.isFinite(point.longitude) ||
    Math.abs(point.latitude) > 90 ||
    Math.abs(point.longitude) > 180
  )
    throw new RangeError('Выберите корректную точку на карте')
  return { latitude: point.latitude, longitude: point.longitude }
}

export function wrapLongitude(longitude: number): number {
  if (longitude >= -180 && longitude <= 180) return longitude
  return ((((longitude + 180) % 360) + 360) % 360) - 180
}
