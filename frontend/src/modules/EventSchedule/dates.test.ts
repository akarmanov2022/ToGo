import { describe, expect, it } from 'vitest'
import {
  addCalendarMonth,
  emptySchedule,
  localDate,
  parseScheduleTimestamp,
  toLocalDateTime,
  toScheduleTimestamp,
  validateSchedule,
  earliestEventStart,
} from './dates'

const now = new Date('2026-11-10T12:00:00+00:00')
const valid = {
  startsAt: '2026-11-11T18:00:00+00:00',
  endsAt: '2026-11-11T21:00:00+00:00',
  registrationClosesAt: '2026-11-11T15:00:00+00:00',
}

describe('schedule validation', () => {
  it('temporarily allows start from tomorrow midnight, not only after 24 hours', () => {
    const clock = new Date(2026, 10, 10, 23, 30)
    const tomorrow = earliestEventStart(clock)
    expect([tomorrow.getDate(), tomorrow.getHours(), tomorrow.getMinutes()]).toEqual([11, 0, 0])
    const value = {
      startsAt: tomorrow.toISOString(),
      endsAt: new Date(+tomorrow + 3600000).toISOString(),
      registrationClosesAt: tomorrow.toISOString(),
    }
    expect(validateSchedule(value, clock)).toEqual({})
    expect(
      validateSchedule({ ...value, startsAt: new Date(+tomorrow - 1).toISOString() }, clock),
    ).toHaveProperty('startsAt')
  })
  it('requires three independent values, including registration', () => {
    expect(Object.keys(validateSchedule(emptySchedule(), now))).toHaveLength(3)
    expect(validateSchedule({ ...valid, registrationClosesAt: '' }, now)).toEqual({
      registrationClosesAt: 'Укажите дату и время',
    })
  })

  it('accepts registration exactly at start but not exactly now', () => {
    expect(validateSchedule({ ...valid, registrationClosesAt: valid.startsAt }, now)).toEqual({})
    expect(
      validateSchedule({ ...valid, registrationClosesAt: now.toISOString() }, now),
    ).toHaveProperty('registrationClosesAt')
  })

  it('rejects reversed registration and equal or reversed end', () => {
    expect(validateSchedule({ ...valid, registrationClosesAt: valid.endsAt }, now)).toHaveProperty(
      'registrationClosesAt',
    )
    for (const endsAt of [valid.startsAt, valid.registrationClosesAt]) {
      expect(validateSchedule({ ...valid, endsAt }, now)).toHaveProperty('endsAt')
    }
  })

  it('rejects past start and end', () => {
    const past = '2026-11-09T10:00:00+00:00'
    expect(validateSchedule({ ...valid, startsAt: past, endsAt: past }, now)).toMatchObject({
      startsAt: expect.any(String),
      endsAt: expect.any(String),
    })
  })

  it('accepts the exact month boundary and rejects one millisecond later', () => {
    const boundary = addCalendarMonth(now)
    const value = {
      ...valid,
      startsAt: boundary.toISOString(),
      endsAt: new Date(boundary.getTime() + 60_000).toISOString(),
    }
    expect(validateSchedule(value, now)).toEqual({})
    expect(
      validateSchedule({ ...value, startsAt: new Date(+boundary + 1).toISOString() }, now),
    ).toHaveProperty('startsAt')
  })

  it('does not impose a month limit on the end', () => {
    expect(validateSchedule({ ...valid, endsAt: '2027-01-01T12:00:00Z' }, now)).toEqual({})
  })

  it('compares instants rather than textual wall times', () => {
    expect(
      validateSchedule(
        {
          startsAt: '2026-11-11T18:00:00+03:00',
          endsAt: '2026-11-11T16:00:00Z',
          registrationClosesAt: '2026-11-11T17:00:00+02:00',
        },
        now,
      ),
    ).toEqual({})
  })
})

describe('calendar and timezone conversion', () => {
  it.skipIf(Intl.DateTimeFormat().resolvedOptions().timeZone !== 'Europe/Berlin')(
    'rejects DST gaps and uses the selected date offset across DST',
    () => {
      expect(toScheduleTimestamp({ date: '2026-03-29', time: '02:30' })).toBe('')
      expect(toScheduleTimestamp({ date: '2026-01-11', time: '18:00' })).toBe(
        '2026-01-11T18:00:00+01:00',
      )
      expect(toScheduleTimestamp({ date: '2026-07-11', time: '18:00' })).toBe(
        '2026-07-11T18:00:00+02:00',
      )
      expect(toScheduleTimestamp({ date: '2026-10-25', time: '02:30' })).toBe(
        '2026-10-25T02:30:00+02:00',
      )
    },
  )

  it.each([
    [2026, 0, 31, '2026-02-28'],
    [2028, 0, 31, '2028-02-29'],
    [2026, 7, 31, '2026-09-30'],
    [2026, 11, 31, '2027-01-31'],
  ])('clamps %i-%i-%i to %s', (year, month, day, expected) => {
    const date = new Date(year, month, day, 12, 34, 56)
    const result = addCalendarMonth(date)
    expect(localDate(result)).toBe(expected)
    expect([result.getHours(), result.getMinutes(), result.getSeconds()]).toEqual([12, 34, 56])
    expect(date.getDate()).toBe(day)
  })

  it.each([
    '',
    '2026-02-30T12:00:00Z',
    '2026-11-11T25:00:00Z',
    '2026-11-11T12:60:00Z',
    '2026-11-11T12:00:00',
    '2026-11-11T12:00:00+24:00',
    'not a date',
    '2026-11-11T12:00:00+02:60',
  ])('rejects invalid or offset-less timestamps: %s', (value) => {
    expect(parseScheduleTimestamp(value)).toBeNull()
    expect(validateSchedule({ ...valid, startsAt: value }, now)).toHaveProperty('startsAt')
  })

  it.each([
    { date: '', time: '' },
    { date: '2026-11-11', time: '' },
    { date: '2026-02-30', time: '12:00' },
    { date: '2026-11-11', time: '24:00' },
    { date: '2026-11-11', time: '12:60' },
    { date: '2026-1-1', time: '12:00' },
  ])('does not normalize invalid local input: %o', (value) => {
    expect(toScheduleTimestamp(value)).toBe('')
  })

  it('serializes local input with its explicit offset and round trips it', () => {
    const input = { date: '2026-11-11', time: '18:35' }
    const timestamp = toScheduleTimestamp(input)
    expect(timestamp).toMatch(/^2026-11-11T18:35:00[+-]\d{2}:\d{2}$/)
    expect(toLocalDateTime(timestamp)).toEqual(input)
    expect(+parseScheduleTimestamp(timestamp)!).toBe(+new Date(2026, 10, 11, 18, 35))
  })

  it('displays UTC and nonzero offsets in the user timezone', () => {
    const instant = new Date('2026-11-11T00:30:00+05:30')
    expect(toLocalDateTime('2026-11-11T00:30:00+05:30')).toEqual({
      date: localDate(instant),
      time: `${String(instant.getHours()).padStart(2, '0')}:${String(instant.getMinutes()).padStart(2, '0')}`,
    })
    expect(toLocalDateTime(instant.toISOString())).toEqual(
      toLocalDateTime('2026-11-11T00:30:00+05:30'),
    )
  })
})
