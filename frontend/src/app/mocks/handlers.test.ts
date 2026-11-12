import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { setupServer } from 'msw/node'
import type { components } from '../../common/api/schema'
import { handlers, resetMockData } from './handlers'
import {
  categories,
  createEventFixtures,
  currentUser,
  localAccessToken,
  localInitData,
  otherUser,
} from './fixtures'

type Schema = components['schemas']
const server = setupServer(...handlers)
const eventId = createEventFixtures()[0].id
const ownEventId = createEventFixtures()[1].id
const url = (path: string) => `/api/v1${path}`
const eventUrl = (suffix = '', id = eventId) => url(`/events/${id}${suffix}`)

function request(
  path: string,
  method = 'GET',
  data?: unknown,
  token: string | null = localAccessToken,
) {
  return fetch(`http://localhost${path}`, {
    method,
    headers: {
      ...(token === null ? {} : { Authorization: `Bearer ${token}` }),
      ...(data === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: data === undefined ? undefined : JSON.stringify(data),
  })
}
async function error(response: Response, status: number, code: Schema['ApiError']['code']) {
  expect(response.status).toBe(status)
  expect(await response.json()).toEqual(
    expect.objectContaining({ code, message: expect.any(String), details: expect.any(Array) }),
  )
}
function newEvent(): Schema['CreateEventRequest'] {
  const future = (hours: number) => new Date(Date.now() + hours * 3_600_000).toISOString()
  return {
    title: 'Тестовое событие',
    description: 'Описание тестового события',
    location: 'Парк',
    startsAt: future(24),
    endsAt: future(26),
    registrationClosesAt: future(12),
    minUsers: 1,
    maxUsers: 5,
    category: 'BOARD_GAMES',
  }
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetMockData())
afterEach(() => {
  server.resetHandlers()
  vi.restoreAllMocks()
})
afterAll(() => server.close())

describe('local MSW contract', () => {
  it('сохраняет единственную оценку и обновляет рейтинг организатора', async () => {
    await request(eventUrl('/participants/me'), 'PUT')
    const later = Date.now() + 60 * 3_600_000
    vi.spyOn(Date, 'now').mockReturnValue(later)
    const rated = await request(eventUrl('/rating'), 'PUT', { score: 4 })
    expect(rated.status).toBe(200)
    expect(await rated.json()).toMatchObject({ myRating: 4, rating: { average: 4, count: 1 } })
    await error(await request(eventUrl('/rating'), 'PUT', { score: 5 }), 409, 'ALREADY_RATED')
    const profile = await request(url(`/users/${otherUser.id}`))
    expect(await profile.json()).toMatchObject({ organizerRating: { average: 4, count: 1 } })
  })
  it('accepts only the explicit local initData, not Telegram-like strings', async () => {
    const response = await request(url('/auth/telegram'), 'POST', { initData: localInitData }, null)
    expect(response.headers.get('Cache-Control')).toBe('no-store')
    expect(await response.json()).toEqual({
      accessToken: localAccessToken,
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: currentUser,
    })
    for (const initData of ['query_id=123&hash=fake', `${localInitData} `]) {
      await error(
        await request(url('/auth/telegram'), 'POST', { initData }, null),
        401,
        'INVALID_TELEGRAM_INIT_DATA',
      )
    }
    for (const initData of ['', null, 123]) {
      await error(
        await request(url('/auth/telegram'), 'POST', { initData }, null),
        400,
        'VALIDATION_ERROR',
      )
    }
    await error(
      await request(url('/auth/telegram'), 'POST', { initData: localInitData, tgId: 1 }),
      400,
      'VALIDATION_ERROR',
    )
  })

  it('requires the exact local bearer token on every protected route', async () => {
    const routes = [
      url('/users/me'),
      url(`/users/${otherUser.id}`),
      url('/categories'),
      url('/events'),
      eventUrl(),
      eventUrl('/participants'),
      eventUrl('/comments'),
    ]
    for (const path of routes)
      for (const token of [null, 'wrong']) {
        const response = await request(path, 'GET', undefined, token)
        expect(response.headers.get('WWW-Authenticate')).toBe('Bearer')
        await error(response, 401, 'UNAUTHORIZED')
      }
    for (const [method, path] of [
      ['PUT', url('/users/me')],
      ['POST', url('/events')],
      ['PATCH', eventUrl()],
      ['POST', eventUrl('/cancel')],
      ['PUT', eventUrl('/participants/me')],
      ['DELETE', eventUrl('/participants/me')],
      ['POST', eventUrl('/comments')],
    ]) {
      await error(await request(path, method, undefined, 'wrong'), 401, 'UNAUTHORIZED')
    }
  })

  it('updates profiles, hides tgId on public profiles, and resets memory', async () => {
    expect(await (await request(url('/users/me'))).json()).toEqual(currentUser)
    const response = await request(url('/users/me'), 'PUT', { name: 'Новое имя', categories: [] })
    expect(await response.json()).toEqual({
      ...currentUser,
      name: 'Новое имя',
      city: null,
      categories: [],
    })
    const profile = await (await request(url(`/users/${currentUser.id}`))).json()
    expect(profile.name).toBe('Новое имя')
    expect(profile).toMatchObject({ createdEventsCount: 1, visitedEventsCount: 0 })
    expect(profile).not.toHaveProperty('tgId')
    expect(await (await request(url(`/users/${otherUser.id}`))).json()).toEqual(otherUser)
    await error(
      await request(url('/users/me'), 'PUT', {
        name: 'Имя',
        categories: ['UNKNOWN'],
      }),
      400,
      'VALIDATION_ERROR',
    )
    await error(await request(url('/users/not-a-uuid')), 400, 'VALIDATION_ERROR')
    await error(
      await request(url('/users/019a0000-0000-7000-8000-000000000999')),
      404,
      'USER_NOT_FOUND',
    )
    resetMockData()
    expect(await (await request(url('/users/me'))).json()).toEqual(currentUser)
  })

  it('returns categories and combines event filters before pagination', async () => {
    expect(await (await request(url('/categories'))).json()).toEqual(categories)
    expect(categories.map((category) => category.code)).toEqual([
      'PICNIC',
      'RUN',
      'EXCURSION',
      'BOARD_GAMES',
      'QUEST',
      'CINEMA',
      'CAFE',
    ])
    const all: Schema['EventPage'] = await (await request(url('/events'))).json()
    expect(all.total).toBe(3)
    expect(all.items[0].state).toBe('finished')
    const page: Schema['EventPage'] = await (await request(url('/events?limit=1&offset=1'))).json()
    expect(page).toMatchObject({ total: 3, limit: 1, offset: 1, items: [all.items[1]] })
    const query = new URLSearchParams({
      query: '  НАСТОЛОК ',
      category: 'BOARD_GAMES',
      state: 'recruiting',
      hasFreePlaces: 'true',
      startsFrom: all.items[1].startsAt,
      startsTo: all.items[2].startsAt,
    })
    expect((await (await request(url(`/events?${query}`))).json()).items).toEqual([all.items[1]])
    expect(
      (await (await request(url('/events?participating=true'))).json()).items.map(
        (event: Schema['Event']) => event.id,
      ),
    ).toEqual([ownEventId])
    expect((await (await request(url('/events?category=PICNIC'))).json()).total).toBe(0)
    for (const query of [
      'limit=0',
      'limit=101',
      'offset=-1',
      'state=unknown',
      'participating=yes',
      'startsFrom=bad',
      'category=bad',
    ]) {
      await error(await request(url(`/events?${query}`)), 400, 'VALIDATION_ERROR')
    }
  })

  it('creates, reads, patches and idempotently cancels an organizer event', async () => {
    const response = await request(url('/events'), 'POST', newEvent())
    expect(response.status).toBe(201)
    const created: Schema['Event'] = await response.json()
    expect(response.headers.get('Location')).toBe(url(`/events/${created.id}`))
    expect(created).toMatchObject({
      description: newEvent().description,
      category: 'BOARD_GAMES',
      icon: null,
      latitude: null,
      longitude: null,
      participantsCount: 1,
      availableSlots: 4,
      isOrganizer: true,
      isParticipant: true,
      organizer: { id: currentUser.id },
    })
    expect(await (await request(eventUrl('', created.id))).json()).toEqual(created)
    const changed = await request(eventUrl('', created.id), 'PATCH', {
      title: 'Изменено',
      icon: '',
      description: 'Новое описание',
      category: 'CAFE',
      maxUsers: 4,
    })
    expect(await changed.json()).toMatchObject({
      title: 'Изменено',
      description: 'Новое описание',
      category: 'CAFE',
      icon: null,
      availableSlots: 3,
    })
    await error(
      await request(eventUrl('/participants/me', created.id), 'DELETE'),
      409,
      'ORGANIZER_CANNOT_LEAVE',
    )
    for (let attempt = 0; attempt < 2; attempt++) {
      expect(await (await request(eventUrl('/cancel', created.id), 'POST')).json()).toMatchObject({
        state: 'cancelled_by_organizer',
        participantsCount: 1,
      })
    }
    await error(
      await request(eventUrl('', created.id), 'PATCH', { title: 'Поздно' }),
      409,
      'INVALID_EVENT_STATE',
    )
    expect((await request(eventUrl('/participants/me', created.id), 'PUT')).status).toBe(200)
  })

  it('rejects malformed JSON, unknown fields and invalid create/patch bodies', async () => {
    const malformed = await fetch(`http://localhost${url('/events')}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localAccessToken}`, 'Content-Type': 'application/json' },
      body: '{',
    })
    await error(malformed, 400, 'VALIDATION_ERROR')
    for (const change of [
      { title: ' ' },
      { minUsers: 6 },
      { maxUsers: 51 },
      { latitude: 20 },
      { latitude: 91, longitude: 0 },
      { category: [] },
      { category: 'UNKNOWN' },
      { category: null },
      { categoryIds: ['BOARD_GAMES'] },
      { description: '' },
      { description: ' ' },
      { description: 'я'.repeat(501) },
      { organizer: currentUser },
      { registrationClosesAt: '2000-01-01T00:00:00Z' },
    ]) {
      await error(
        await request(url('/events'), 'POST', { ...newEvent(), ...change }),
        400,
        'VALIDATION_ERROR',
      )
    }
    const missingDescription: Record<string, unknown> = { ...newEvent() }
    delete missingDescription.description
    await error(await request(url('/events'), 'POST', missingDescription), 400, 'VALIDATION_ERROR')
    for (const change of [
      {},
      { title: null },
      { startsAt: newEvent().startsAt },
      { category: 'UNKNOWN' },
      { category: null },
      { categoryIds: [] },
      { description: '' },
      { description: ' ' },
      { description: 'я'.repeat(501) },
    ]) {
      await error(await request(eventUrl('', ownEventId), 'PATCH', change), 400, 'VALIDATION_ERROR')
    }
    await error(await request(eventUrl(), 'PATCH', { title: 'Нельзя' }), 403, 'FORBIDDEN')
    await error(await request(eventUrl('/cancel'), 'POST'), 403, 'FORBIDDEN')
    await error(await request(eventUrl('', currentUser.id)), 404, 'EVENT_NOT_FOUND')
    await error(await request(eventUrl('', 'bad')), 400, 'VALIDATION_ERROR')
  })

  it('accepts enum profile categories but rejects duplicates, unknown codes and legacy fields', async () => {
    const response = await request(url('/users/me'), 'PUT', {
      name: 'Анна',
      categories: ['CAFE', 'RUN'],
      city: 'Москва',
    })
    expect(response.status).toBe(200)
    expect(await response.json()).toMatchObject({ categories: ['CAFE', 'RUN'] })
    for (const change of [
      { categories: ['RUN', 'RUN'] },
      { categories: ['run'] },
      { categories: null },
      { categories: [categories[0]] },
      { categories: [], categoryIds: [] },
    ]) {
      await error(
        await request(url('/users/me'), 'PUT', { name: 'Анна', ...change }),
        400,
        'VALIDATION_ERROR',
      )
    }
  })

  it('accepts 500-character descriptions and returns live counters including finished participation', async () => {
    const response = await request(url('/events'), 'POST', {
      ...newEvent(),
      description: 'я'.repeat(500),
    })
    expect(response.status).toBe(201)
    const created: Schema['Event'] = await response.json()
    expect(
      (await request(eventUrl('', created.id), 'PATCH', { description: 'я'.repeat(500) })).status,
    ).toBe(200)
    expect(await (await request(url('/users/me'))).json()).toMatchObject({
      createdEventsCount: 2,
      visitedEventsCount: 0,
    })
    await request(eventUrl('/participants/me'), 'PUT')
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 80 * 3_600_000)
    const counts = { createdEventsCount: 2, visitedEventsCount: 3 }
    expect(await (await request(url('/users/me'))).json()).toMatchObject(counts)
    expect(await (await request(url(`/users/${currentUser.id}`))).json()).toMatchObject(counts)
    expect(
      (
        await (
          await request(url('/auth/telegram'), 'POST', { initData: localInitData }, null)
        ).json()
      ).user,
    ).toMatchObject(counts)
    expect(await (await request(url(`/users/${otherUser.id}`))).json()).toMatchObject({
      createdEventsCount: 2,
      visitedEventsCount: 2,
    })
    resetMockData()
    expect(await (await request(url('/users/me'))).json()).toEqual(currentUser)
  })

  it('joins/leaves idempotently and paginates participants including the organizer', async () => {
    for (let attempt = 0; attempt < 2; attempt++) {
      expect(await (await request(eventUrl('/participants/me'), 'PUT')).json()).toMatchObject({
        participantsCount: 2,
        availableSlots: 4,
        isParticipant: true,
      })
    }
    expect(await (await request(eventUrl('/participants?limit=1&offset=1'))).json()).toMatchObject({
      total: 2,
      items: [{ id: otherUser.id }],
    })
    for (let attempt = 0; attempt < 2; attempt++) {
      const response = await request(eventUrl('/participants/me'), 'DELETE')
      expect(response.status).toBe(204)
      expect(await response.text()).toBe('')
    }
    expect(await (await request(eventUrl())).json()).toMatchObject({
      participantsCount: 1,
      isParticipant: false,
    })
  })

  it('allows comments only for participants and retains them after leaving', async () => {
    await error(await request(eventUrl('/comments'), 'POST', { text: 'Привет' }), 403, 'FORBIDDEN')
    await request(eventUrl('/participants/me'), 'PUT')
    await error(
      await request(eventUrl('/comments'), 'POST', { text: ' ' }),
      400,
      'VALIDATION_ERROR',
    )
    const response = await request(eventUrl('/comments'), 'POST', { text: 'Привет!' })
    expect(response.status).toBe(201)
    const comment = await response.json()
    expect(comment).toMatchObject({
      id: expect.any(String),
      text: 'Привет!',
      author: { id: currentUser.id },
      createdAt: expect.any(String),
    })
    await request(eventUrl('/participants/me'), 'DELETE')
    expect(await (await request(eventUrl('/comments'))).json()).toMatchObject({
      total: 1,
      items: [comment],
    })
    resetMockData()
    expect((await (await request(eventUrl('/comments'))).json()).total).toBe(0)
  })

  it('applies registration deadlines on demand, preserving cancellation and idempotency', async () => {
    const now = Date.now()
    await request(eventUrl('/participants/me'), 'PUT')
    vi.spyOn(Date, 'now').mockReturnValue(now + 25 * 3_600_000)
    expect(await (await request(eventUrl())).json()).toMatchObject({
      state: 'registration_closed',
      participantsCount: 2,
    })
    expect((await request(eventUrl('/participants/me'), 'PUT')).status).toBe(200)
    await error(await request(eventUrl('/participants/me'), 'DELETE'), 409, 'REGISTRATION_CLOSED')
    vi.spyOn(Date, 'now').mockReturnValue(now + 52 * 3_600_000)
    expect(await (await request(eventUrl())).json()).toMatchObject({ state: 'finished' })
    expect((await request(eventUrl('/comments'), 'POST', { text: 'Спасибо!' })).status).toBe(201)
  })

  it('cancels insufficient attendance at deadline without removing participants', async () => {
    const now = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(now + 25 * 3_600_000)
    expect(await (await request(eventUrl())).json()).toMatchObject({
      state: 'cancelled_by_system',
      participantsCount: 1,
    })
    await error(await request(eventUrl('/participants/me'), 'PUT'), 409, 'INVALID_EVENT_STATE')
    expect((await request(eventUrl('/participants/me'), 'DELETE')).status).toBe(204)
  })
})
