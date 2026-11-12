import { apiRequest } from '@/common/api/client'
import type { components } from '@/common/api/schema'
import { localDate, toScheduleTimestamp } from '@/modules/EventSchedule'
import { isVisibleInFeed } from './actions'

export type EventCategory = components['schemas']['EventCategory']
export type Category = components['schemas']['Category']
export type EventDto = components['schemas']['Event']
export type EventPage = components['schemas']['EventPage']
export type Participant = components['schemas']['UserSummary']
export type ParticipantPage = components['schemas']['ParticipantPage']
export type CreateEventBody = components['schemas']['CreateEventRequest']
export type UpdateEventBody = components['schemas']['UpdateEventRequest']
export type EventComment = components['schemas']['Comment']
export type CommentPage = components['schemas']['CommentPage']

export function fetchComments(eventId: string, offset: number): Promise<CommentPage> {
  return apiRequest<CommentPage>(`/api/v1/events/${eventId}/comments?limit=20&offset=${offset}`)
}
export function postComment(eventId: string, text: string): Promise<EventComment> {
  return apiRequest<EventComment>(`/api/v1/events/${eventId}/comments`, {
    method: 'POST',
    body: { text },
  })
}

export interface FeedFilters {
  category: EventCategory | null
  query: string
  dateFrom?: string
  dateTo?: string
  hasFreePlaces?: boolean
}

export const PAGE_SIZE = 12

export function buildFeedQuery(filters: FeedFilters, offset: number, now: Date): string {
  const from = filters.dateFrom
    ? toScheduleTimestamp({ date: filters.dateFrom, time: '00:00' })
    : ''
  let to = ''
  if (filters.dateTo) {
    const nextDay = new Date(`${filters.dateTo}T12:00:00`)
    nextDay.setDate(nextDay.getDate() + 1)
    to = toScheduleTimestamp({ date: localDate(nextDay), time: '00:00' })
  }
  const params = new URLSearchParams({
    startsFrom: new Date(
      Math.max(now.getTime(), from ? Date.parse(from) : now.getTime()),
    ).toISOString(),
    limit: String(PAGE_SIZE),
    offset: String(offset),
  })
  if (filters.category) params.set('category', filters.category)
  const query = filters.query.trim()
  if (query) params.set('query', query)
  if (to) params.set('startsTo', to)
  if (filters.hasFreePlaces) params.set('hasFreePlaces', 'true')
  return params.toString()
}

export function validateFeedDates(filters: Pick<FeedFilters, 'dateFrom' | 'dateTo'>): string {
  for (const date of [filters.dateFrom, filters.dateTo]) {
    if (date && !toScheduleTimestamp({ date, time: '00:00' })) return 'Укажите корректные даты'
  }
  return filters.dateFrom && filters.dateTo && filters.dateFrom > filters.dateTo
    ? 'Дата окончания диапазона не может быть раньше начала'
    : ''
}

export async function fetchFeedMap(
  filters: FeedFilters,
  now: Date,
  signal?: AbortSignal,
): Promise<EventDto[]> {
  const events: EventDto[] = []
  let offset = 0
  while (true) {
    const page = await fetchFeedPage(filters, offset, now, signal)
    events.push(...page.items)
    offset += page.items.length
    if (offset >= page.total || !page.items.length) break
  }
  return [...new Map(events.filter(isVisibleInFeed).map((event) => [event.id, event])).values()]
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

export function rateEvent(eventId: string, score: number): Promise<EventDto> {
  return apiRequest<EventDto>(`/api/v1/events/${eventId}/rating`, {
    method: 'PUT',
    body: { score },
  })
}

// В контракте пока нет фильтра по организатору: просматриваем публичные страницы,
// не используя participating=true (это личные участия текущего пользователя).
export async function fetchOrganizerEvents(
  userId: string,
  signal?: AbortSignal,
): Promise<EventDto[]> {
  const items: EventDto[] = []
  let offset = 0
  while (true) {
    const page = await apiRequest<EventPage>(`/api/v1/events?limit=100&offset=${offset}`, {
      signal,
    })
    items.push(...page.items.filter((event) => event.organizer.id === userId))
    offset += page.items.length
    if (!page.items.length || offset >= page.total) break
  }
  return [...new Map(items.map((event) => [event.id, event])).values()].sort(
    (a, b) => Date.parse(b.startsAt) - Date.parse(a.startsAt) || a.id.localeCompare(b.id),
  )
}

export function fetchParticipants(eventId: string): Promise<ParticipantPage> {
  return apiRequest<ParticipantPage>(`/api/v1/events/${eventId}/participants?limit=100&offset=0`)
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
