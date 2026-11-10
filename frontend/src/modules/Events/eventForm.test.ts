import { describe, expect, it } from 'vitest'
import { validateEventForm, type EventFormValues } from './eventForm'

const now = new Date('2026-11-10T12:00:00Z')

const valid: EventFormValues = {
  title: 'Вечер настолок',
  description: 'Играем в Каркассон',
  location: 'Кафе на Центральной улице, 10',
  category: 'BOARD_GAMES',
  startsAt: '2026-11-11T18:00:00Z',
  endsAt: '2026-11-11T21:00:00Z',
  registrationClosesAt: '2026-11-11T15:00:00Z',
  minUsers: 3,
  maxUsers: 6,
  icon: '',
}

describe('validateEventForm', () => {
  it('принимает корректную форму', () => {
    expect(validateEventForm(valid, now)).toEqual({})
  })

  it('принимает срок регистрации, равный началу', () => {
    const form = { ...valid, registrationClosesAt: valid.startsAt }
    expect(validateEventForm(form, now)).toEqual({})
  })

  it('принимает событие для одного согласно контракту', () => {
    expect(validateEventForm({ ...valid, minUsers: 1, maxUsers: 1 }, now)).toEqual({})
  })

  it('отклоняет название из пробелов и слишком длинное', () => {
    expect(validateEventForm({ ...valid, title: '   ' }, now).title).toBeDefined()
    expect(validateEventForm({ ...valid, title: 'а'.repeat(101) }, now).title).toBeDefined()
  })

  it('требует описание и отклоняет слишком длинное', () => {
    expect(validateEventForm({ ...valid, description: '   ' }, now).description).toBeDefined()
    expect(
      validateEventForm({ ...valid, description: 'а'.repeat(501) }, now).description,
    ).toBeDefined()
  })

  it('отклоняет пустое место и отсутствие категории', () => {
    const errors = validateEventForm({ ...valid, location: ' ', category: '' }, now)
    expect(errors.location).toBeDefined()
    expect(errors.category).toBeDefined()
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

  it('отклоняет начало дальше одного календарного месяца', () => {
    const tooFar = new Date('2026-12-10T12:01:00Z')
    expect(
      validateEventForm({ ...valid, startsAt: tooFar.toISOString() }, now).startsAt,
    ).toBeDefined()
  })

  it('проверяет лимиты участников', () => {
    expect(validateEventForm({ ...valid, minUsers: 0 }, now).minUsers).toBeDefined()
    expect(validateEventForm({ ...valid, minUsers: 5, maxUsers: 3 }, now).minUsers).toBeDefined()
    expect(validateEventForm({ ...valid, maxUsers: 51 }, now).maxUsers).toBeDefined()
    expect(validateEventForm({ ...valid, minUsers: 2.5 }, now).minUsers).toBeDefined()
  })

  it('принимает пустую иконку и один эмодзи', () => {
    expect(validateEventForm({ ...valid, icon: '' }, now).icon).toBeUndefined()
    expect(validateEventForm({ ...valid, icon: '🎲' }, now).icon).toBeUndefined()
    expect(validateEventForm({ ...valid, icon: '👨‍👩‍👧' }, now).icon).toBeUndefined()
  })

  it('отклоняет иконку не из одного эмодзи', () => {
    expect(validateEventForm({ ...valid, icon: 'abc' }, now).icon).toBe(
      'Укажите один эмодзи или оставьте пустым',
    )
    expect(validateEventForm({ ...valid, icon: '🎲🎲' }, now).icon).toBeDefined()
    expect(validateEventForm({ ...valid, icon: 'a🎲' }, now).icon).toBeDefined()
  })

  it.each(['', '2.0', '2e1', '-2', '2x'])('отклоняет нецифровые лимиты: %s', (value) => {
    expect(validateEventForm({ ...valid, maxUsers: value }, now).maxUsers).toBeDefined()
  })
})
