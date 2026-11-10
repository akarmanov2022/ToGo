export interface Coordinates {
  latitude: number
  longitude: number
}

export interface MeetingPlace extends Coordinates {
  id: string
  name: string
  address: string
  category: string
}
