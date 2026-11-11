import { expect, it } from 'vitest'
import { validateCreation, type CreationDraft } from './creation'
import { toScheduleTimestamp } from '@/modules/EventSchedule'

it('разрешает одного участника, включая организатора, согласно OpenAPI', () => {
  const draft: CreationDraft = {
    title: 'Пробежка',
    description: 'Бегаем в парке',
    category: 'RUN',
    minUsers: '1',
    maxUsers: '1',
    place: {
      id: 'park',
      name: 'Парк',
      address: 'Санкт-Петербург',
      latitude: 59.9,
      longitude: 30.3,
      category: 'RUN',
    },
    schedule: {
      registrationClosesAt: toScheduleTimestamp({ date: '2026-11-11', time: '15:00' }),
      startsAt: toScheduleTimestamp({ date: '2026-11-11', time: '18:00' }),
      endsAt: toScheduleTimestamp({ date: '2026-11-11', time: '21:00' }),
    },
  }
  expect(validateCreation(draft, new Date(2026, 10, 10, 12))).toEqual({})
})
