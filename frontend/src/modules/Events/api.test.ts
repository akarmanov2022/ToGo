import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureApiSession } from '@/common/api/client'
import {
  PAGE_SIZE,
  buildFeedQuery,
  fetchFeedMap,
  fetchMyEvents,
  fetchOrganizerEvents,
  rateEvent,
  fetchCategories,
  fetchEvent,
  fetchParticipants,
  joinEvent,
  leaveEvent,
  cancelEvent,
  createEvent,
  updateEvent,
  validateFeedDates,
  type CreateEventBody,
} from './api'

afterEach(() => {
  vi.unstubAllGlobals()
  configureApiSession({ getToken: () => null, onUnauthorized: () => {} })
})

const now = new Date('2026-11-10T12:00:00Z')

it('читает публичные события со всех страниц с отменой, отбирая только нужного организатора', async () => {
  const mine = { id: 'mine', organizer: { id: 'target' } }
  const next = { id: 'next', organizer: { id: 'target' } }
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ items: [{ id: 'other', organizer: { id: 'someone' } }, mine], total: 3 }),
      ),
    )
    .mockResolvedValueOnce(new Response(JSON.stringify({ items: [next], total: 3 })))
  vi.stubGlobal('fetch', fetch)
  const signal = new AbortController().signal
  expect(await fetchOrganizerEvents('target', signal)).toEqual([mine, next])
  expect(fetch.mock.calls.map(([url]) => url)).toEqual([
    '/api/v1/events?limit=100&offset=0',
    '/api/v1/events?limit=100&offset=2',
  ])
  expect(fetch.mock.calls.every(([, options]) => options.signal === signal)).toBe(true)
})

describe('подключение к API событий через общий клиент', () => {
  const operations = [
    {
      name: 'оценка',
      path: '/api/v1/events/event/rating',
      method: 'PUT',
      run: () => rateEvent('event', 4),
    },
    { name: 'категории', path: '/api/v1/categories', method: 'GET', run: () => fetchCategories() },
    {
      name: 'карточка',
      path: '/api/v1/events/event',
      method: 'GET',
      run: () => fetchEvent('event'),
    },
    {
      name: 'участники',
      path: '/api/v1/events/event/participants?limit=100&offset=0',
      method: 'GET',
      run: () => fetchParticipants('event'),
    },
    {
      name: 'вступление',
      path: '/api/v1/events/event/participants/me',
      method: 'PUT',
      run: () => joinEvent('event'),
    },
    {
      name: 'выход',
      path: '/api/v1/events/event/participants/me',
      method: 'DELETE',
      run: () => leaveEvent('event'),
    },
    {
      name: 'отмена',
      path: '/api/v1/events/event/cancel',
      method: 'POST',
      run: () => cancelEvent('event'),
    },
  ]
  it.each(operations)(
    '$name: отправляет запрос с настоящим Bearer-токеном и без cookie',
    async ({ path, method, run }) => {
      const fetchMock = vi
        .fn()
        .mockResolvedValue(
          method === 'DELETE'
            ? new Response(null, { status: 204 })
            : new Response(JSON.stringify({})),
        )
      vi.stubGlobal('fetch', fetchMock)
      configureApiSession({ getToken: () => 'test-session', onUnauthorized: () => {} })
      await run()
      expect(fetchMock).toHaveBeenCalledTimes(1)
      const [url, options] = fetchMock.mock.calls[0]!
      expect(url).toBe(path)
      expect(options.method ?? 'GET').toBe(method)
      expect(options.credentials).toBe('omit')
      expect(options.headers.get('Authorization')).toBe('Bearer test-session')
    },
  )

  it('создаёт и изменяет событие с JSON-телом по контракту', async () => {
    const fetchMock = vi
      .fn()
      .mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ id: 'created' }))))
    vi.stubGlobal('fetch', fetchMock)
    const body: CreateEventBody = {
      title: 'Встреча',
      description: 'Играем вместе',
      category: 'BOARD_GAMES',
      location: 'Парк',
      startsAt: '2026-11-11T18:00:00+03:00',
      endsAt: '2026-11-11T21:00:00+03:00',
      registrationClosesAt: '2026-11-11T15:00:00+03:00',
      minUsers: 1,
      maxUsers: 5,
      latitude: 59.93,
      longitude: 30.33,
    }
    await createEvent(body)
    await updateEvent('created', { description: 'Новое описание' })
    expect(fetchMock.mock.calls[0]![0]).toBe('/api/v1/events')
    expect(fetchMock.mock.calls[0]![1].method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[0]![1].body)).toEqual(body)
    expect(fetchMock.mock.calls[1]![0]).toBe('/api/v1/events/created')
    expect(fetchMock.mock.calls[1]![1].method).toBe('PATCH')
    expect(JSON.parse(fetchMock.mock.calls[1]![1].body)).toEqual({ description: 'Новое описание' })
  })
})

describe('buildFeedQuery', () => {
  it('для «Все» не передаёт категорию и запрашивает будущие события', () => {
    const params = new URLSearchParams(buildFeedQuery({ category: null, query: '' }, 0, now))
    expect(params.get('startsFrom')).toBe('2026-11-10T12:00:00.000Z')
    expect(params.has('category')).toBe(false)
    expect(params.has('query')).toBe(false)
    expect(params.get('limit')).toBe(String(PAGE_SIZE))
    expect(params.get('offset')).toBe('0')
  })

  it('передаёт код категории и обрезанный поисковый запрос', () => {
    const params = new URLSearchParams(
      buildFeedQuery({ category: 'CINEMA', query: '  кино ' }, 24, now),
    )
    expect(params.get('category')).toBe('CINEMA')
    expect(params.get('query')).toBe('кино')
    expect(params.get('offset')).toBe('24')
  })

  it('фиксирует размер страницы в 12 карточек', () => {
    expect(PAGE_SIZE).toBe(12)
  })

  it('передаёт диапазон дат включительно и признак свободных мест', () => {
    const params = new URLSearchParams(
      buildFeedQuery(
        {
          category: null,
          query: '',
          dateFrom: '2026-11-12',
          dateTo: '2026-11-13',
          hasFreePlaces: true,
        },
        0,
        now,
      ),
    )
    expect(new Date(params.get('startsFrom')!).toISOString()).toBe(
      new Date(2026, 10, 12).toISOString(),
    )
    expect(new Date(params.get('startsTo')!).toISOString()).toBe(
      new Date(2026, 10, 14).toISOString(),
    )
    expect(params.get('hasFreePlaces')).toBe('true')
  })

  it('не даёт диапазону начинаться раньше текущего момента', () => {
    const params = new URLSearchParams(
      buildFeedQuery({ category: null, query: '', dateFrom: '2026-11-10' }, 0, now),
    )
    expect(params.get('startsFrom')).toBe('2026-11-10T12:00:00.000Z')
    expect(params.has('startsTo')).toBe(false)
    expect(params.has('hasFreePlaces')).toBe(false)
  })
})

describe('validateFeedDates', () => {
  it('отклоняет несуществующие и перевёрнутые даты диапазона', () => {
    expect(validateFeedDates({ dateFrom: '2026-02-30' })).toBe('Укажите корректные даты')
    expect(validateFeedDates({ dateFrom: '2026-11-13', dateTo: '2026-11-12' })).toBe(
      'Дата окончания диапазона не может быть раньше начала',
    )
    expect(validateFeedDates({ dateFrom: '2026-11-12', dateTo: '2026-11-12' })).toBe('')
  })
})

it('карта ленты собирает все страницы выборки и прячет отменённые события', async () => {
  const makePage = (items: unknown[], total: number) =>
    new Response(JSON.stringify({ items, total, limit: 12, offset: 0 }))
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      makePage(
        [
          { id: 'open', state: 'recruiting', startsAt: '2026-11-12T10:00:00Z' },
          { id: 'gone', state: 'cancelled_by_organizer', startsAt: '2026-11-12T11:00:00Z' },
        ],
        3,
      ),
    )
    .mockResolvedValueOnce(
      makePage(
        [{ id: 'closed', state: 'registration_closed', startsAt: '2026-11-13T10:00:00Z' }],
        3,
      ),
    )
  vi.stubGlobal('fetch', fetchMock)
  const events = await fetchFeedMap({ category: null, query: '' }, now)
  expect(events.map((event) => event.id)).toEqual(['open', 'closed'])
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('собирает все страницы архива до сортировки, не отправляя отсутствующий в контракте order', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          items: [{ id: 'old', startsAt: '2026-11-01T12:00:00Z' }],
          total: 2,
          limit: 100,
          offset: 0,
        }),
      ),
    )
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          items: [{ id: 'new', startsAt: '2026-11-09T12:00:00Z' }],
          total: 2,
          limit: 100,
          offset: 1,
        }),
      ),
    )
  vi.stubGlobal('fetch', fetchMock)
  const page = await fetchMyEvents({ state: 'finished', order: 'desc' })
  expect(page.items.map((event) => event.id)).toEqual(['new', 'old'])
  expect(page.total).toBe(2)
  const urls = fetchMock.mock.calls.map(([url]) => new URL(url, 'http://localhost'))
  expect(urls.map((url) => url.searchParams.get('offset'))).toEqual(['0', '1'])
  expect(
    urls.every(
      (url) => !url.searchParams.has('order') && url.searchParams.get('participating') === 'true',
    ),
  ).toBe(true)
})
