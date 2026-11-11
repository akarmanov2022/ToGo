import type { components } from '@/common/api/schema'
import type { EventScheduleValue } from '@/modules/EventSchedule'
import type { MeetingPlace } from '@/modules/Map'
import { validateEventForm } from './eventForm'
import { isActiveEvent } from './rules'
import { categoryEmoji } from './categories'

export interface CreationDraft {
  title: string
  description: string
  category: string
  minUsers: string
  maxUsers: string
  place: MeetingPlace | null
  schedule: EventScheduleValue
}
export function validateCreation(draft: CreationDraft, now = new Date()) {
  const errors: Record<string, string> = validateEventForm(
    {
      ...draft,
      ...draft.schedule,
      icon: categoryEmoji(draft.category),
      location: draft.place?.address ?? '',
    },
    now,
  )
  if (errors.location) {
    errors.place = 'Выберите место на карте'
    delete errors.location
  }
  return errors
}

type Event = components['schemas']['Event']
export function creationRestrictions(events: Event[], userId: string, startsAt: string): string[] {
  const active = events.filter(isActiveEvent)
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
