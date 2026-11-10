import { describe, expect, it } from 'vitest'
import type { components } from '@/common/api/schema'
import { cardAction, isVisibleInFeed, stateLabel } from './actions'

type Event = components['schemas']['Event']

const now = new Date('2026-11-10T12:00:00Z')

function fixture(overrides: Partial<Event>): Event {
  return {
    id: 'e1',
    title: 'Кино и попкорн',
    description: 'Описание',
    location: 'Кинотеатр «Аврора»',
    startsAt: '2026-11-11T19:00:00Z',
    endsAt: '2026-11-11T22:00:00Z',
    registrationClosesAt: '2026-11-11T15:00:00Z',
    state: 'recruiting',
    icon: null,
    latitude: null,
    longitude: null,
    minUsers: 2,
    maxUsers: 5,
    organizer: { id: 'u1', tgLogin: 'anna' },
    category: 'CINEMA',
    participantsCount: 2,
    availableSlots: 3,
    isParticipant: false,
    isOrganizer: false,
    ...overrides,
  } as Event
}

describe('cardAction', () => {
  it('организатор видит «Отменить событие», а не «Присоединиться»', () => {
    const own = fixture({ isOrganizer: true })
    expect(cardAction(own, now)).toEqual({ kind: 'cancel', label: 'Отменить событие' })
  })

  it('организатор не может присоединиться к своему событию и после начала видит недоступную кнопку', () => {
    const own = fixture({ isOrganizer: true })
    const started = new Date('2026-11-11T20:00:00Z')
    expect(cardAction(own, started).kind).toBe('unavailable')
    expect(['join', 'leave']).not.toContain(cardAction(own, started).kind)
  })

  it('участник видит «Отписаться от события», пока открыта регистрация', () => {
    expect(cardAction(fixture({ isParticipant: true }), now)).toEqual({
      kind: 'leave',
      label: 'Отписаться от события',
    })
  })

  it('участник после срока регистрации видит недоступную кнопку «Набор закрыт»', () => {
    const late = new Date('2026-11-11T16:00:00Z')
    expect(cardAction(fixture({ isParticipant: true }), late)).toEqual({
      kind: 'unavailable',
      label: 'Набор закрыт',
    })
  })

  it('свободное место даёт «Присоединиться», занятое — недоступную «Забито»', () => {
    expect(cardAction(fixture({}), now)).toEqual({ kind: 'join', label: 'Присоединиться' })
    expect(cardAction(fixture({ availableSlots: 0 }), now)).toEqual({
      kind: 'unavailable',
      label: 'Забито',
    })
  })

  it('закрытый набор, отмена и завершение делают кнопку недоступной', () => {
    expect(cardAction(fixture({ state: 'registration_closed' }), now).label).toBe('Набор закрыт')
    expect(cardAction(fixture({ state: 'cancelled_by_organizer' }), now).label).toBe('Отменено')
    expect(cardAction(fixture({ state: 'cancelled_by_system' }), now).label).toBe('Отменено')
    expect(cardAction(fixture({ state: 'finished' }), now).label).toBe('Завершено')
  })

  it('организатор может отменить событие с закрытым набором до начала', () => {
    const own = fixture({ state: 'registration_closed', isOrganizer: true })
    expect(cardAction(own, now).kind).toBe('cancel')
  })
})

describe('isVisibleInFeed', () => {
  it('главная страница прячет отменённые и завершённые, оставляя предстоящие', () => {
    expect(isVisibleInFeed(fixture({}))).toBe(true)
    expect(isVisibleInFeed(fixture({ state: 'registration_closed' }))).toBe(true)
    expect(isVisibleInFeed(fixture({ state: 'cancelled_by_organizer' }))).toBe(false)
    expect(isVisibleInFeed(fixture({ state: 'cancelled_by_system' }))).toBe(false)
    expect(isVisibleInFeed(fixture({ state: 'finished' }))).toBe(false)
  })
})

describe('stateLabel', () => {
  it('даёт русское название для каждого из пяти состояний', () => {
    expect(stateLabel('recruiting')).toBe('Идёт набор')
    expect(stateLabel('registration_closed')).toBe('Набор закрыт')
    expect(stateLabel('finished')).toBe('Завершено')
    expect(stateLabel('cancelled_by_organizer')).toBe('Отменено организатором')
    expect(stateLabel('cancelled_by_system')).toBe('Отменено: не набрался минимум')
  })
})
