import type { components } from '@/common/api/schema'
import { validateSchedule, type EventScheduleValue } from '@/modules/EventSchedule'
import type { MeetingPlace } from '@/modules/Map'
import { isSingleEmoji } from './eventForm'

export interface CreationDraft {
  title: string
  description: string
  icon?: string
  category: string
  minUsers: string
  maxUsers: string
  place: MeetingPlace | null
  schedule: EventScheduleValue
}
export const creationCategories = [
  { code: 'PICNIC', name: 'Пикник' },
  { code: 'RUN', name: 'Пробежка' },
  { code: 'EXCURSION', name: 'Поход на экскурсию' },
  { code: 'BOARD_GAMES', name: 'Настолки' },
  { code: 'QUEST', name: 'Квест' },
  { code: 'CINEMA', name: 'Кино' },
  { code: 'CAFE', name: 'Кафе/Ресторан' },
] as const

export function validateCreation(draft: CreationDraft, now = new Date()) {
  const errors: Record<string, string> = { ...validateSchedule(draft.schedule, now) }
  if (!draft.title.trim()) errors.title = 'Укажите название'
  else if (draft.title.length > 100 || !/^[\p{L}\p{N}\s]+$/u.test(draft.title))
    errors.title = 'Название: буквы, цифры и пробелы, до 100 символов'
  if (!draft.description.trim() || draft.description.length > 500)
    errors.description = 'Укажите описание от 1 до 500 символов'
  if (!creationCategories.some((item) => item.code === draft.category))
    errors.category = 'Выберите категорию'
  if (!draft.place) errors.place = 'Выберите место на карте'
  if (draft.icon?.trim() && !isSingleEmoji(draft.icon.trim()))
    errors.icon = 'Укажите один эмодзи или оставьте пустым'
  if (!/^\d+$/.test(draft.minUsers) || Number(draft.minUsers) < 1 || Number(draft.minUsers) > 50)
    errors.minUsers = 'Минимум: целое число от 1 до 50'
  if (!/^\d+$/.test(draft.maxUsers) || Number(draft.maxUsers) < 1 || Number(draft.maxUsers) > 50)
    errors.maxUsers = 'Максимум: целое число от 1 до 50'
  else if (!errors.minUsers && Number(draft.minUsers) > Number(draft.maxUsers))
    errors.maxUsers = 'Максимум не может быть меньше минимума'
  return errors
}

type Event = components['schemas']['Event']
export function creationRestrictions(events: Event[], userId: string, startsAt: string): string[] {
  const active = events.filter(
    (event) => event.state === 'recruiting' || event.state === 'registration_closed',
  )
  const reasons: string[] = []
  if (active.filter((event) => event.isParticipant || event.organizer.id === userId).length >= 5)
    reasons.push('У вас уже 5 активных событий — созданных или с вашим участием')
  const start = Date.parse(startsAt)
  if (
    Number.isFinite(start) &&
    active.some((event) => Math.abs(Date.parse(event.startsAt) - start) < 3 * 60 * 60 * 1000)
  )
    reasons.push('У вас уже есть встреча менее чем за 3 часа до или после выбранного начала')
  return reasons
}
