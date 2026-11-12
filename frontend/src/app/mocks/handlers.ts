import { http, HttpResponse, type PathParams } from 'msw'
import type { components, paths } from '@/common/api/schema'
import {
  categories,
  createEventFixtures,
  currentUser,
  localAccessToken,
  localInitData,
  otherUser,
} from './fixtures'

type Schema = components['schemas']
type Event = Schema['Event']
type ApiError = Schema['ApiError']
type Method = 'get' | 'post' | 'put' | 'patch' | 'delete'
type Operation<P extends keyof paths, M extends Method> = M extends keyof paths[P]
  ? paths[P][M]
  : never
type Json<T> = T extends { content: { 'application/json': infer B } } ? B : never
type Body<P extends keyof paths, M extends Method> =
  Operation<P, M> extends { requestBody: infer B } ? Json<B> : never
type Reply<P extends keyof paths, M extends Method> =
  Operation<P, M> extends { responses: infer R } ? Json<R[keyof R]> : never

let user: Schema['CurrentUser']
let events: Event[]
let participants: Map<string, string[]>
let comments: Map<string, Schema['Comment'][]>
let ratings: Map<string, Map<string, number>>

export function resetMockData() {
  user = structuredClone(currentUser)
  events = createEventFixtures()
  participants = new Map(events.map((event) => [event.id, [event.organizer.id]]))
  comments = new Map(events.map((event) => [event.id, []]))
  ratings = new Map(events.map((event) => [event.id, new Map()]))
}
resetMockData()

class MockError extends Error {
  constructor(
    public status: number,
    public payload: ApiError,
  ) {
    super(payload.message)
  }
}

function fail(status: number, code: ApiError['code'], message: string, field?: string): never {
  throw new MockError(status, { code, message, details: field ? [{ field, message }] : [] })
}
function invalid(field: string): never {
  return fail(400, 'VALIDATION_ERROR', `Некорректное поле: ${field}`, field)
}

function endpoint<P extends keyof paths, M extends Method>(
  method: M,
  path: P,
  resolve: (
    request: Request,
    params: PathParams,
  ) => Promise<HttpResponse<Reply<P, M>>> | HttpResponse<Reply<P, M>>,
) {
  return http[method]<PathParams, Body<P, M>, Reply<P, M>>(
    `*${path.replace(/\{(\w+)\}/g, ':$1')}`,
    async ({ request, params }) => {
      try {
        if (
          path !== '/api/v1/auth/telegram' &&
          request.headers.get('Authorization') !== `Bearer ${localAccessToken}`
        ) {
          fail(401, 'UNAUTHORIZED', 'Нужен локальный MSW-токен')
        }
        return await resolve(request, params)
      } catch (error) {
        if (!(error instanceof MockError)) throw error
        return HttpResponse.json(error.payload as Reply<P, M>, {
          status: error.status,
          headers:
            error.payload.code === 'UNAUTHORIZED' ? { 'WWW-Authenticate': 'Bearer' } : undefined,
        })
      }
    },
  )
}

async function body(
  request: Request,
  allowed: string[],
  required = allowed,
): Promise<Record<string, unknown>> {
  let data: unknown
  try {
    data = await request.json()
  } catch {
    return invalid('body')
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) return invalid('body')
  const result = data as Record<string, unknown>
  for (const key of Object.keys(result)) if (!allowed.includes(key)) invalid(key)
  for (const key of required) if (!(key in result)) invalid(key)
  return result
}
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function uuid(value: unknown, field: string): string {
  if (typeof value !== 'string' || !uuidPattern.test(value)) return invalid(field)
  return value
}
function text(value: unknown, field: string, max = Infinity, empty = false): string {
  if (typeof value !== 'string' || value.length > max || (!empty && !value.trim()))
    return invalid(field)
  return value
}
function date(value: unknown, field: string): string {
  if (
    typeof value !== 'string' ||
    !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)$/i.test(value) ||
    !Number.isFinite(Date.parse(value))
  )
    return invalid(field)
  return new Date(value).toISOString()
}
function count(value: unknown, field: string): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > 50)
    return invalid(field)
  return value
}
function category(value: unknown, field = 'category'): Schema['EventCategory'] {
  const match = categories.find((item) => item.code === value)
  return match?.code ?? invalid(field)
}
function selectedCategories(value: unknown): Schema['EventCategory'][] {
  if (!Array.isArray(value) || new Set(value).size !== value.length) return invalid('categories')
  return value.map((item) => category(item, 'categories'))
}
function profileCounts(id: string) {
  const scores = events
    .filter((event) => event.organizer.id === id)
    .flatMap((event) => [...(ratings.get(event.id)?.values() ?? [])])
  return {
    organizerRating: {
      average: scores.length
        ? Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10
        : 0,
      count: scores.length,
    },
    createdEventsCount: events.filter((event) => event.organizer.id === id).length,
    visitedEventsCount: events.filter(
      (event) => view(event).state === 'finished' && participants.get(event.id)!.includes(id),
    ).length,
  }
}
function summary(id: string): Schema['UserSummary'] {
  const profile = id === user.id ? user : otherUser
  return { id: profile.id, tgLogin: profile.tgLogin }
}
function view(event: Event): Event {
  const members = participants.get(event.id)!
  const scores = [...(ratings.get(event.id)?.values() ?? [])]
  if (event.state === 'recruiting' && Date.now() >= Date.parse(event.registrationClosesAt)) {
    event.state = members.length < event.minUsers ? 'cancelled_by_system' : 'registration_closed'
  }
  if (event.state === 'registration_closed' && Date.now() >= Date.parse(event.endsAt))
    event.state = 'finished'
  return {
    ...event,
    myRating: ratings.get(event.id)?.get(user.id) ?? null,
    rating: scores.length
      ? {
          average: Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10,
          count: scores.length,
        }
      : null,
    organizer: summary(event.organizer.id),
    participantsCount: members.length,
    availableSlots: event.maxUsers - members.length,
    isParticipant: members.includes(user.id),
    isOrganizer: event.organizer.id === user.id,
  }
}
function findEvent(params: PathParams): Event {
  const id = uuid(params.eventId, 'eventId')
  const event = events.find((item) => item.id === id)
  if (!event) return fail(404, 'EVENT_NOT_FOUND', 'Событие не найдено')
  view(event)
  return event
}
function organizer(event: Event) {
  if (event.organizer.id !== user.id)
    fail(403, 'FORBIDDEN', 'Только организатор может выполнить действие')
}
function recruiting(event: Event) {
  if (event.state === 'registration_closed') fail(409, 'REGISTRATION_CLOSED', 'Регистрация закрыта')
  if (event.state !== 'recruiting')
    fail(409, 'INVALID_EVENT_STATE', 'Действие недоступно в этом состоянии')
}
function page<T>(items: T[], request: Request) {
  const params = new URL(request.url).searchParams
  const integer = (key: string, fallback: number, min: number, max = Number.MAX_SAFE_INTEGER) => {
    const value = params.get(key)
    if (value === null) return fallback
    if (
      !/^\d+$/.test(value) ||
      !Number.isSafeInteger(Number(value)) ||
      Number(value) < min ||
      Number(value) > max
    )
      return invalid(key)
    return Number(value)
  }
  const limit = integer('limit', 20, 1, 100)
  const offset = integer('offset', 0, 0)
  return { items: items.slice(offset, offset + limit), total: items.length, limit, offset }
}
function validateTimes(
  event: Pick<Event, 'registrationClosesAt' | 'startsAt' | 'endsAt' | 'minUsers' | 'maxUsers'>,
) {
  if (!(
    Date.now() < Date.parse(event.registrationClosesAt) &&
    Date.parse(event.registrationClosesAt) <= Date.parse(event.startsAt) &&
    Date.parse(event.startsAt) < Date.parse(event.endsAt)
  ))
    invalid('registrationClosesAt')
  if (event.minUsers > event.maxUsers) invalid('maxUsers')
}

export const handlers = [
  endpoint('post', '/api/v1/auth/telegram', async (request) => {
    const data = await body(request, ['initData'])
    if (typeof data.initData !== 'string' || data.initData.length === 0) invalid('initData')
    if (data.initData !== localInitData)
      fail(401, 'INVALID_TELEGRAM_INIT_DATA', 'Допускаются только локальные MSW initData')
    return HttpResponse.json(
      {
        accessToken: localAccessToken,
        tokenType: 'Bearer',
        expiresIn: 3600,
        user: { ...user, ...profileCounts(user.id) },
      },
      { headers: { 'Cache-Control': 'no-store' } },
    )
  }),
  endpoint('get', '/api/v1/users/me', () =>
    HttpResponse.json({ ...user, ...profileCounts(user.id) }),
  ),
  endpoint('put', '/api/v1/users/me', async (request) => {
    const data = await body(request, ['name', 'city', 'categories'], ['name', 'categories'])
    const next = {
      ...user,
      name: text(data.name, 'name', 100),
      city: data.city == null ? null : text(data.city, 'city', 100),
      categories: selectedCategories(data.categories),
      ...profileCounts(user.id),
    }
    user = next
    return HttpResponse.json(user)
  }),
  endpoint('get', '/api/v1/users/{userId}', (_request, params) => {
    const id = uuid(params.userId, 'userId')
    if (id !== user.id && id !== otherUser.id) fail(404, 'USER_NOT_FOUND', 'Пользователь не найден')
    if (id === otherUser.id) return HttpResponse.json({ ...otherUser, ...profileCounts(id) })
    return HttpResponse.json({
      id: user.id,
      tgLogin: user.tgLogin,
      name: user.name,
      photoUrl: user.photoUrl,
      city: user.city,
      categories: user.categories,
      ...profileCounts(id),
    })
  }),
  endpoint('get', '/api/v1/categories', () => HttpResponse.json(categories)),
  endpoint('put', '/api/v1/events/{eventId}/rating', async (request, params) => {
    const event = findEvent(params)
    const data = await body(request, ['score'])
    if (
      typeof data.score !== 'number' ||
      !Number.isInteger(data.score) ||
      data.score < 1 ||
      data.score > 5
    )
      invalid('score')
    if (event.organizer.id === user.id || !participants.get(event.id)!.includes(user.id))
      fail(403, 'FORBIDDEN', 'Оценить событие могут только участники, кроме организатора')
    if (event.state !== 'finished')
      fail(409, 'INVALID_EVENT_STATE', 'Можно оценить только завершённое событие')
    const scores = ratings.get(event.id) ?? new Map<string, number>()
    if (scores.has(user.id)) fail(409, 'ALREADY_RATED', 'Вы уже оценили это событие')
    scores.set(user.id, data.score as number)
    ratings.set(event.id, scores)
    return HttpResponse.json(view(event))
  }),
  endpoint('get', '/api/v1/events', (request) => {
    const params = new URL(request.url).searchParams
    const query = (params.get('query') ?? '').trim().toLowerCase()
    const selectedCategory = params.has('category') ? category(params.get('category')) : null
    const from = params.has('startsFrom')
      ? Date.parse(date(params.get('startsFrom'), 'startsFrom'))
      : -Infinity
    const to = params.has('startsTo')
      ? Date.parse(date(params.get('startsTo'), 'startsTo'))
      : Infinity
    if (from >= to) invalid('startsTo')
    const state = params.get('state')
    if (
      state !== null &&
      ![
        'recruiting',
        'registration_closed',
        'cancelled_by_organizer',
        'cancelled_by_system',
        'finished',
      ].includes(state)
    )
      invalid('state')
    for (const key of ['participating', 'hasFreePlaces'])
      if (params.has(key) && !['true', 'false'].includes(params.get(key)!)) invalid(key)
    const items = events
      .map(view)
      .filter(
        (event) =>
          event.title.toLowerCase().includes(query) &&
          (!selectedCategory || event.category === selectedCategory) &&
          Date.parse(event.startsAt) >= from &&
          Date.parse(event.startsAt) < to &&
          (!state || event.state === state) &&
          (params.get('participating') !== 'true' || event.isParticipant) &&
          (params.get('hasFreePlaces') !== 'true' ||
            (event.state === 'recruiting' &&
              event.availableSlots > 0 &&
              Date.now() < Date.parse(event.registrationClosesAt))),
      )
      .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt) || a.id.localeCompare(b.id))
    return HttpResponse.json(page(items, request))
  }),
  endpoint('post', '/api/v1/events', async (request) => {
    const data = await body(
      request,
      [
        'title',
        'description',
        'location',
        'startsAt',
        'endsAt',
        'registrationClosesAt',
        'minUsers',
        'maxUsers',
        'icon',
        'latitude',
        'longitude',
        'category',
      ],
      [
        'title',
        'description',
        'location',
        'startsAt',
        'endsAt',
        'registrationClosesAt',
        'minUsers',
        'maxUsers',
        'category',
      ],
    )
    if ('latitude' in data !== 'longitude' in data) invalid('latitude')
    for (const [key, max] of [
      ['latitude', 90],
      ['longitude', 180],
    ] as const) {
      if (
        key in data &&
        (typeof data[key] !== 'number' || !Number.isFinite(data[key]) || Math.abs(data[key]) > max)
      )
        invalid(key)
    }
    const event: Event = {
      id: crypto.randomUUID(),
      title: text(data.title, 'title', 100),
      description: text(data.description, 'description', 500),
      location: text(data.location, 'location'),
      startsAt: date(data.startsAt, 'startsAt'),
      endsAt: date(data.endsAt, 'endsAt'),
      registrationClosesAt: date(data.registrationClosesAt, 'registrationClosesAt'),
      minUsers: count(data.minUsers, 'minUsers'),
      maxUsers: count(data.maxUsers, 'maxUsers'),
      category: category(data.category),
      icon: 'icon' in data ? text(data.icon, 'icon', Infinity, true) || null : null,
      latitude: (data.latitude as number | undefined) ?? null,
      longitude: (data.longitude as number | undefined) ?? null,
      organizer: summary(user.id),
      state: 'recruiting',
      participantsCount: 1,
      availableSlots: 0,
      isParticipant: true,
      isOrganizer: true,
    }
    validateTimes(event)
    events.push(event)
    participants.set(event.id, [user.id])
    comments.set(event.id, [])
    return HttpResponse.json(view(event), {
      status: 201,
      headers: { Location: `/api/v1/events/${event.id}` },
    })
  }),
  endpoint('get', '/api/v1/events/{eventId}', (_request, params) =>
    HttpResponse.json(view(findEvent(params))),
  ),
  endpoint('patch', '/api/v1/events/{eventId}', async (request, params) => {
    const event = findEvent(params)
    organizer(event)
    recruiting(event)
    const data = await body(
      request,
      ['title', 'description', 'registrationClosesAt', 'icon', 'minUsers', 'maxUsers', 'category'],
      [],
    )
    if (!Object.keys(data).length) invalid('body')
    const next = { ...event }
    if ('title' in data) next.title = text(data.title, 'title', 100)
    if ('description' in data) next.description = text(data.description, 'description', 500)
    if ('icon' in data) next.icon = text(data.icon, 'icon', Infinity, true) || null
    if ('registrationClosesAt' in data)
      next.registrationClosesAt = date(data.registrationClosesAt, 'registrationClosesAt')
    if ('minUsers' in data) next.minUsers = count(data.minUsers, 'minUsers')
    if ('maxUsers' in data) next.maxUsers = count(data.maxUsers, 'maxUsers')
    if ('category' in data) next.category = category(data.category)
    validateTimes(next)
    if (next.maxUsers < participants.get(event.id)!.length)
      fail(409, 'CAPACITY_BELOW_PARTICIPANTS', 'Участников больше новой вместимости')
    Object.assign(event, next)
    return HttpResponse.json(view(event))
  }),
  endpoint('post', '/api/v1/events/{eventId}/cancel', (_request, params) => {
    const event = findEvent(params)
    organizer(event)
    if (event.state !== 'cancelled_by_organizer') {
      if (
        !['recruiting', 'registration_closed'].includes(event.state) ||
        Date.now() >= Date.parse(event.startsAt)
      )
        fail(409, 'INVALID_EVENT_STATE', 'Событие нельзя отменить')
      event.state = 'cancelled_by_organizer'
    }
    return HttpResponse.json(view(event))
  }),
  endpoint('get', '/api/v1/events/{eventId}/participants', (request, params) => {
    const event = findEvent(params)
    return HttpResponse.json(page([...participants.get(event.id)!].sort().map(summary), request))
  }),
  endpoint('put', '/api/v1/events/{eventId}/participants/me', (_request, params) => {
    const event = findEvent(params)
    const members = participants.get(event.id)!
    if (!members.includes(user.id)) {
      recruiting(event)
      if (members.length >= event.maxUsers) fail(409, 'EVENT_FULL', 'Все места заняты')
      members.push(user.id)
    }
    return HttpResponse.json(view(event))
  }),
  endpoint('delete', '/api/v1/events/{eventId}/participants/me', (_request, params) => {
    const event = findEvent(params)
    const members = participants.get(event.id)!
    if (members.includes(user.id)) {
      if (event.organizer.id === user.id)
        fail(409, 'ORGANIZER_CANNOT_LEAVE', 'Организатор не может выйти')
      recruiting(event)
      participants.set(
        event.id,
        members.filter((id) => id !== user.id),
      )
    }
    return new HttpResponse(null, { status: 204 })
  }),
  endpoint('get', '/api/v1/events/{eventId}/comments', (request, params) => {
    const event = findEvent(params)
    return HttpResponse.json(
      page(
        [...comments.get(event.id)!].sort(
          (a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id),
        ),
        request,
      ),
    )
  }),
  endpoint('post', '/api/v1/events/{eventId}/comments', async (request, params) => {
    const event = findEvent(params)
    if (!participants.get(event.id)!.includes(user.id))
      fail(403, 'FORBIDDEN', 'Комментировать могут только участники')
    const data = await body(request, ['text'])
    const comment: Schema['Comment'] = {
      id: crypto.randomUUID(),
      author: summary(user.id),
      text: text(data.text, 'text', 1000),
      createdAt: new Date().toISOString(),
    }
    comments.get(event.id)!.push(comment)
    return HttpResponse.json(comment, { status: 201 })
  }),
]
