import { afterEach, describe, expect, it, vi } from 'vitest'
import { PAGE_SIZE, buildFeedQuery, fetchMyEvents } from './api'

afterEach(() => vi.unstubAllGlobals())

const now = new Date('2026-11-10T12:00:00Z')

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
