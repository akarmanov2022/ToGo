import { describe, expect, it } from 'vitest'
import { editDraft, editPatch, validateEdit } from './editing'
import type { EventDto } from './api'

const now = new Date('2026-11-10T12:00:00Z')
const event: EventDto = {
  id: 'event',
  title: 'Настолки',
  description: 'Играем',
  location: 'Парк',
  icon: '🎲',
  category: 'BOARD_GAMES',
  minUsers: 1,
  maxUsers: 5,
  participantsCount: 3,
  availableSlots: 2,
  startsAt: '2026-11-11T18:00:00Z',
  endsAt: '2026-11-11T21:00:00Z',
  registrationClosesAt: '2026-11-11T15:00:00Z',
  latitude: 59.9,
  longitude: 30.3,
  organizer: { id: 'u', tgLogin: null },
  isOrganizer: true,
  isParticipant: true,
  state: 'recruiting',
}

describe('редактор события', () => {
  it('при смене категории автоматически отправляет её эмодзи', () => {
    const draft = editDraft(event)
    draft.maxUsers = '6'
    draft.category = 'CINEMA'
    expect(editPatch(draft, event)).toEqual({ icon: '🎬', maxUsers: 6, category: 'CINEMA' })
    expect(Object.keys(editPatch(draft, event))).not.toEqual(
      expect.arrayContaining(['startsAt', 'endsAt', 'location', 'latitude', 'longitude']),
    )
  })
  it('не отправляет пустое изменение и эквивалентное время с другим смещением', () => {
    const draft = editDraft(event)
    draft.registrationClosesAt = '2026-11-11T18:00:00+03:00'
    expect(editPatch(draft, event)).toEqual({})
    expect(validateEdit(draft, event, now)).toEqual({})
  })
  it('не разрешает уменьшить вместимость ниже состава или минимума', () => {
    const draft = { ...editDraft(event), maxUsers: '2' }
    expect(validateEdit(draft, event, now)).toHaveProperty('maxUsers')
    expect(validateEdit({ ...draft, maxUsers: '5', minUsers: '6' }, event, now)).toHaveProperty(
      'maxUsers',
    )
  })
  it('проверяет новый срок регистрации и описание', () => {
    expect(
      validateEdit(
        { ...editDraft(event), registrationClosesAt: now.toISOString(), description: ' ' },
        event,
        now,
      ),
    ).toHaveProperty('registrationClosesAt')
    expect(
      validateEdit(
        { ...editDraft(event), registrationClosesAt: '2026-11-11T19:00:00Z' },
        event,
        now,
      ),
    ).toHaveProperty('registrationClosesAt')
    expect(
      validateEdit({ ...editDraft(event), description: 'x'.repeat(501) }, event, now),
    ).toHaveProperty('description')
  })

  it('не заставляет исправлять прежние название и иконку при изменении других полей', () => {
    const original = { ...event, title: 'Настолки: вечер!', icon: 'legacy' }
    expect(validateEdit(editDraft(original), original, now)).toEqual({})
    expect(validateEdit({ ...editDraft(original), title: 'Новое!' }, original, now)).toHaveProperty(
      'title',
    )
    expect(editPatch(editDraft(original), original)).toEqual({ icon: '🎲' })
  })
})
