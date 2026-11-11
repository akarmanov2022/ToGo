export interface Coordinates {
  latitude: number
  longitude: number
}

export type PlaceBounds = [south: number, west: number, north: number, east: number]

export interface EventMapPoint {
  id: string
  title: string
  latitude: number | null
  longitude: number | null
}

export interface MeetingPlace extends Coordinates {
  id: string
  name: string
  address: string
  category: string
}
