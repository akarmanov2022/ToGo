import { apiDelete, apiGet, apiSendJson } from '@/common/api/client'
import type { EventState } from './rules'

export type EventCategory =
  | 'PICNIC'
  | 'RUN'
  | 'EXCURSION'
  | 'BOARD_GAMES'
  | 'QUEST'
  | 'CINEMA'
  | 'CAFE'

export interface Category {
  code: EventCategory
  name: string
}

export interface EventDto {
  id: string
  title: string
  description: string
  location: string
  startsAt: string
  endsAt: string
  registrationClosesAt: string
  state: EventState
  icon: string | null
  latitude: number | null
  longitude: number | null
  minUsers: number
  maxUsers: number
  organizer: { id: string; tgLogin: string | null }
  category: EventCategory
  participantsCount: number
  availableSlots: number
  isParticipant: boolean
  isOrganizer: boolean
}

export interface EventPage {
  items: EventDto[]
  total: number
  limit: number
  offset: number
}

export interface Participant {
  id: string
  tgLogin: string | null
}

export interface ParticipantPage {
  items: Participant[]
  total: number
  limit: number
  offset: number
}

export interface FeedFilters {
  category: EventCategory | null
  query: string
}

export interface CreateEventBody {
  title: string
  description: string
  location: string
  startsAt: string
  endsAt: string
  registrationClosesAt: string
  minUsers: number
  maxUsers: number
  category: EventCategory
  icon?: string
  latitude?: number
  longitude?: number
}

export interface UpdateEventBody {
  title?: string
  description?: string
  registrationClosesAt?: string
  icon?: string
  minUsers?: number
  maxUsers?: number
  category?: EventCategory
}

export const PAGE_SIZE = 12

export function buildFeedQuery(filters: FeedFilters, offset: number, now: Date): string {
  const params = new URLSearchParams({
    startsFrom: now.toISOString(),
    limit: String(PAGE_SIZE),
    offset: String(offset),
  })
  if (filters.category) params.set('category', filters.category)
  const query = filters.query.trim()
  if (query) params.set('query', query)
  return params.toString()
}

export function fetchFeedPage(filters: FeedFilters, offset: number): Promise<EventPage> {
  return apiGet<EventPage>(`/api/v1/events?${buildFeedQuery(filters, offset, new Date())}`)
}

export function fetchCategories(): Promise<Category[]> {
  return apiGet<Category[]>('/api/v1/categories')
}

export function fetchEvent(eventId: string): Promise<EventDto> {
  return apiGet<EventDto>(`/api/v1/events/${eventId}`)
}

export function createEvent(body: CreateEventBody): Promise<EventDto> {
  return apiSendJson<EventDto>('/api/v1/events', 'POST', body)
}

export function updateEvent(eventId: string, body: UpdateEventBody): Promise<EventDto> {
  return apiSendJson<EventDto>(`/api/v1/events/${eventId}`, 'PATCH', body)
}

export function cancelEvent(eventId: string): Promise<EventDto> {
  return apiSendJson<EventDto>(`/api/v1/events/${eventId}/cancel`, 'POST')
}

export function joinEvent(eventId: string): Promise<EventDto> {
  return apiSendJson<EventDto>(`/api/v1/events/${eventId}/participants/me`, 'PUT')
}

export function leaveEvent(eventId: string): Promise<void> {
  return apiDelete(`/api/v1/events/${eventId}/participants/me`)
}

export function fetchParticipants(eventId: string): Promise<ParticipantPage> {
  return apiGet<ParticipantPage>(`/api/v1/events/${eventId}/participants?limit=100&offset=0`)
}

export function fetchMyEvents(params: {
  startsFrom?: string
  startsTo?: string
  order?: 'asc' | 'desc'
  state?: EventState
  offset?: number
}): Promise<EventPage> {
  const query = new URLSearchParams({
    participating: 'true',
    limit: String(PAGE_SIZE),
    offset: String(params.offset ?? 0),
    order: params.order ?? 'asc',
  })
  if (params.startsFrom) query.set('startsFrom', params.startsFrom)
  if (params.startsTo) query.set('startsTo', params.startsTo)
  if (params.state) query.set('state', params.state)
  return apiGet<EventPage>(`/api/v1/events?${query}`)
}
