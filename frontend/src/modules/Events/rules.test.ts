import { describe, expect, it } from 'vitest'
import { availableActions, slotsLabel, stateLabel, type EventActionsInput } from './rules'

const now = new Date('2026-11-10T12:00:00Z')

const base: EventActionsInput = {
  state: 'recruiting',
  startsAt: '2026-11-10T19:00:00Z',
  registrationClosesAt: '2026-11-10T17:00:00Z',
  availableSlots: 2,
  isParticipant: false,
  isOrganizer: false,
}

describe('availableActions', () => {
  it('позволяет присоединиться к открытому набору со свободным местом', () => {
    expect(availableActions(base, now)).toEqual({
      canJoin: true,
      canLeave: false,
      canCancel: false,
      canEdit: false,
    })
  })

  it('не позволяет присоединиться без свободных мест', () => {
    expect(availableActions({ ...base, availableSlots: 0 }, now).canJoin).toBe(false)
  })

  it('не позволяет присоединиться повторно', () => {
    expect(availableActions({ ...base, isParticipant: true }, now).canJoin).toBe(false)
  })

  it('не позволяет присоединиться после срока регистрации, даже если место есть', () => {
    const late = new Date('2026-11-10T17:00:00Z')
    expect(availableActions(base, late).canJoin).toBe(false)
  })

  it('не позволяет присоединиться при закрытом наборе', () => {
    expect(availableActions({ ...base, state: 'registration_closed' }, now).canJoin).toBe(false)
  })

  it('позволяет участнику выйти до срока регистрации', () => {
    expect(availableActions({ ...base, isParticipant: true }, now).canLeave).toBe(true)
  })

  it('не позволяет организатору выйти из своего события', () => {
    const organizer = { ...base, isParticipant: true, isOrganizer: true }
    expect(availableActions(organizer, now).canLeave).toBe(false)
  })

  it('не позволяет выйти после срока регистрации', () => {
    const closed = { ...base, isParticipant: true }
    expect(availableActions(closed, new Date('2026-11-10T17:30:00Z')).canLeave).toBe(false)
  })

  it('позволяет организатору изменить и отменить событие в открытом наборе', () => {
    const organizer = { ...base, isParticipant: true, isOrganizer: true }
    const actions = availableActions(organizer, now)
    expect(actions.canEdit).toBe(true)
    expect(actions.canCancel).toBe(true)
  })

  it('позволяет организатору отменить событие после закрытия набора до начала', () => {
    const organizer = { ...base, state: 'registration_closed' as const, isOrganizer: true }
    const actions = availableActions(organizer, new Date('2026-11-10T18:00:00Z'))
    expect(actions.canCancel).toBe(true)
    expect(actions.canEdit).toBe(false)
  })

  it('не позволяет организатору отменить начавшееся событие', () => {
    const organizer = { ...base, state: 'registration_closed' as const, isOrganizer: true }
    expect(availableActions(organizer, new Date('2026-11-10T19:00:00Z')).canCancel).toBe(false)
  })

  it('не позволяет организатору отменить событие, отменённое системой', () => {
    const organizer = { ...base, state: 'cancelled_by_system' as const, isOrganizer: true }
    expect(availableActions(organizer, now).canCancel).toBe(false)
  })

  it('не даёт действий в завершённом событии', () => {
    const finished = { ...base, state: 'finished' as const, isParticipant: true, isOrganizer: true }
    expect(availableActions(finished, now)).toEqual({
      canJoin: false,
      canLeave: false,
      canCancel: false,
      canEdit: false,
    })
  })
})

describe('stateLabel', () => {
  it('даёт русское название для каждого состояния', () => {
    expect(stateLabel('recruiting')).toBe('Идёт набор')
    expect(stateLabel('registration_closed')).toBe('Набор закрыт')
    expect(stateLabel('finished')).toBe('Завершено')
    expect(stateLabel('cancelled_by_organizer')).toBe('Отменено организатором')
    expect(stateLabel('cancelled_by_system')).toBe('Отменено: не набрался минимум')
  })
})

describe('slotsLabel', () => {
  it.each([
    [0, '0 мест'],
    [1, '1 место'],
    [2, '2 места'],
    [4, '4 места'],
    [5, '5 мест'],
    [11, '11 мест'],
    [12, '12 мест'],
    [21, '21 место'],
    [22, '22 места'],
  ])('склоняет %i', (count, expected) => {
    expect(slotsLabel(count)).toBe(expected)
  })
})
