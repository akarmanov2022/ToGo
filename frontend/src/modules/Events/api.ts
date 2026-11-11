import { apiRequest } from '@/common/api/client'
import type { components } from '@/common/api/schema'

export type EventCategory = components['schemas']['EventCategory']
export type Category = components['schemas']['Category']
export type EventDto = components['schemas']['Event']
export type EventPage = components['schemas']['EventPage']
export type Participant = components['schemas']['UserSummary']
export type ParticipantPage = components['schemas']['ParticipantPage']
export type Comment = components['schemas']['Comment']
export type CommentPage = components['schemas']['CommentPage']
export type CreateEventBody = components['schemas']['CreateEventRequest']
export type UpdateEventBody = components['schemas']['UpdateEventRequest']

export interface FeedFilters {
  category: EventCategory | null
  query: string
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

export function fetchFeedPage(
  filters: FeedFilters,
  offset: number,
  now = new Date(),
  signal?: AbortSignal,
): Promise<EventPage> {
  return apiRequest<EventPage>(`/api/v1/events?${buildFeedQuery(filters, offset, now)}`, { signal })
}

export function fetchCategories(): Promise<Category[]> {
  return apiRequest<Category[]>('/api/v1/categories')
}

export function fetchEvent(eventId: string): Promise<EventDto> {
  return apiRequest<EventDto>(`/api/v1/events/${eventId}`)
}

export function createEvent(body: CreateEventBody): Promise<EventDto> {
  return apiRequest<EventDto>('/api/v1/events', { method: 'POST', body })
}

export function updateEvent(eventId: string, body: UpdateEventBody): Promise<EventDto> {
  return apiRequest<EventDto>(`/api/v1/events/${eventId}`, { method: 'PATCH', body })
}

export function cancelEvent(eventId: string): Promise<EventDto> {
  return apiRequest<EventDto>(`/api/v1/events/${eventId}/cancel`, { method: 'POST' })
}

export function joinEvent(eventId: string): Promise<EventDto> {
  return apiRequest<EventDto>(`/api/v1/events/${eventId}/participants/me`, { method: 'PUT' })
}

export function leaveEvent(eventId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/events/${eventId}/participants/me`, { method: 'DELETE' })
}

export function fetchParticipants(eventId: string): Promise<ParticipantPage> {
  return apiRequest<ParticipantPage>(`/api/v1/events/${eventId}/participants?limit=100&offset=0`)
}

export function fetchComments(eventId: string): Promise<CommentPage> {
  return apiRequest<CommentPage>(`/api/v1/events/${eventId}/comments?limit=100&offset=0`)
}

export function publishComment(eventId: string, text: string): Promise<Comment> {
  return apiRequest<Comment>(`/api/v1/events/${eventId}/comments`, {
    method: 'POST',
    body: { text },
  })
}

export async function fetchMyEvents(params: {
  startsFrom?: string
  startsTo?: string
  order?: 'asc' | 'desc'
  state?: components['schemas']['EventState']
  offset?: number
}): Promise<EventPage> {
  const query = new URLSearchParams({ participating: 'true', limit: '100', offset: '0' })
  if (params.startsFrom) query.set('startsFrom', params.startsFrom)
  if (params.startsTo) query.set('startsTo', params.startsTo)
  if (params.state) query.set('state', params.state)
  // order пока нет в контракте. Сортируем полный список, не одну усечённую страницу.
  // После реализации 1.8/8.4 перейти на серверную сортировку и пагинацию.
  const items: EventDto[] = []
  let offset = 0
  while (true) {
    query.set('offset', String(offset))
    const page = await apiRequest<EventPage>(`/api/v1/events?${query}`)
    items.push(...page.items)
    offset += page.items.length
    if (offset >= page.total || !page.items.length) break
  }
  items.sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt) || a.id.localeCompare(b.id))
  if (params.order === 'desc') items.reverse()
  const start = params.offset ?? 0
  return { items: items.slice(start), total: items.length, offset: start, limit: items.length }
}
