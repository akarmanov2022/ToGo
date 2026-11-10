import { describe, expect, it } from 'vitest'
import { combineDateTime, validateEventForm, type EventFormValues } from './eventForm'

const now = new Date('2026-11-10T12:00:00Z')

const valid: EventFormValues = {
  title: 'Вечер настолок',
  description: '',
  location: 'Кафе на Центральной улице, 10',
  categoryIds: ['category-1'],
  startsAt: '2026-11-11T18:00:00Z',
  endsAt: '2026-11-11T21:00:00Z',
  registrationClosesAt: '2026-11-11T15:00:00Z',
  minUsers: 3,
  maxUsers: 6,
}

describe('validateEventForm', () => {
  it('принимает корректную форму', () => {
    expect(validateEventForm(valid, now)).toEqual({})
  })

  it('принимает срок регистрации, равный началу', () => {
    const form = { ...valid, registrationClosesAt: valid.startsAt }
    expect(validateEventForm(form, now)).toEqual({})
  })

  it('принимает событие для одного', () => {
    expect(validateEventForm({ ...valid, minUsers: 1, maxUsers: 1 }, now)).toEqual({})
  })

  it('отклоняет название из пробелов и слишком длинное', () => {
    expect(validateEventForm({ ...valid, title: '   ' }, now).title).toBeDefined()
    expect(validateEventForm({ ...valid, title: 'а'.repeat(101) }, now).title).toBeDefined()
  })

  it('отклоняет слишком длинное описание', () => {
    expect(
      validateEventForm({ ...valid, description: 'а'.repeat(2001) }, now).description,
    ).toBeDefined()
  })

  it('отклоняет пустое место и отсутствие категорий', () => {
    const errors = validateEventForm({ ...valid, location: ' ', categoryIds: [] }, now)
    expect(errors.location).toBeDefined()
    expect(errors.categoryIds).toBeDefined()
  })

  it('отклоняет срок регистрации в прошлом и ровно сейчас', () => {
    const past = { ...valid, registrationClosesAt: '2026-11-10T11:00:00Z' }
    const exactlyNow = { ...valid, registrationClosesAt: now.toISOString() }
    expect(validateEventForm(past, now).registrationClosesAt).toBeDefined()
    expect(validateEventForm(exactlyNow, now).registrationClosesAt).toBeDefined()
  })

  it('отклоняет срок регистрации позже начала', () => {
    const form = { ...valid, registrationClosesAt: '2026-11-11T18:00:01Z' }
    expect(validateEventForm(form, now).registrationClosesAt).toBeDefined()
  })

  it('отклоняет окончание не позже начала', () => {
    expect(validateEventForm({ ...valid, endsAt: valid.startsAt }, now).endsAt).toBeDefined()
  })

  it('требует все три даты', () => {
    const errors = validateEventForm(
      { ...valid, startsAt: '', endsAt: '', registrationClosesAt: '' },
      now,
    )
    expect(errors.startsAt).toBeDefined()
    expect(errors.endsAt).toBeDefined()
    expect(errors.registrationClosesAt).toBeDefined()
  })

  it('проверяет лимиты участников', () => {
    expect(validateEventForm({ ...valid, minUsers: 0 }, now).minUsers).toBeDefined()
    expect(validateEventForm({ ...valid, minUsers: 5, maxUsers: 3 }, now).minUsers).toBeDefined()
    expect(validateEventForm({ ...valid, maxUsers: 51 }, now).maxUsers).toBeDefined()
    expect(validateEventForm({ ...valid, minUsers: 2.5 }, now).minUsers).toBeDefined()
  })
})

describe('combineDateTime', () => {
  it('собирает дату и время в ISO-строку с часовым поясом', () => {
    const result = combineDateTime('2026-11-11', '18:00')
    expect(Date.parse(result)).toBe(new Date('2026-11-11T18:00').getTime())
    expect(result).toMatch(/Z$/)
  })

  it('возвращает пустую строку, если дата или время не заданы', () => {
    expect(combineDateTime('', '18:00')).toBe('')
    expect(combineDateTime('2026-11-11', '')).toBe('')
  })
})
