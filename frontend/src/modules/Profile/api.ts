import { apiGet, apiSendJson } from '@/common/api/client'
import type { EventCategory } from '@/modules/Events'

export interface CurrentUser {
  id: string
  tgId: number
  tgLogin: string | null
  name: string
  photoUrl: string | null
  city: string | null
  categories: EventCategory[]
  createdEventsCount: number
  visitedEventsCount: number
}

export interface UserProfile {
  id: string
  tgLogin: string | null
  name: string
  photoUrl: string | null
  city: string | null
  categories: EventCategory[]
  createdEventsCount: number
  visitedEventsCount: number
}

export interface UpdateProfileBody {
  name: string
  city?: string | null
  categories: EventCategory[]
}

export function fetchCurrentUser(): Promise<CurrentUser> {
  return apiGet<CurrentUser>('/api/v1/users/me')
}

export function updateCurrentUser(body: UpdateProfileBody): Promise<CurrentUser> {
  return apiSendJson<CurrentUser>('/api/v1/users/me', 'PUT', body)
}

export function fetchUserProfile(userId: string): Promise<UserProfile> {
  return apiGet<UserProfile>(`/api/v1/users/${userId}`)
}

export function avatarLetter(name: string): string {
  const trimmed = name.trim()
  return trimmed ? trimmed[0]!.toUpperCase() : '?'
}
