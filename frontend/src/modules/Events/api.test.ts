import { describe, expect, it } from 'vitest'
import { PAGE_SIZE, buildFeedQuery } from './api'

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
