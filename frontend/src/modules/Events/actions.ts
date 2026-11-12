import type { components } from '@/common/api/schema'
import { availableActions, isActiveEvent, isRegistrationOpen } from './rules'

type Event = components['schemas']['Event']

export interface CardAction {
  kind: 'cancel' | 'leave' | 'join' | 'unavailable'
  label: string
}

export function isVisibleInFeed(event: Event): boolean {
  return isActiveEvent(event)
}

export function cardAction(event: Event, now: Date): CardAction {
  const actions = availableActions(event, now)
  if (actions.canCancel) return { kind: 'cancel', label: 'Отменить событие' }
  if (actions.canLeave) return { kind: 'leave', label: 'Отписаться от события' }
  if (actions.canJoin) return { kind: 'join', label: 'Присоединиться' }
  if (!event.isOrganizer && !event.isParticipant && isRegistrationOpen(event, now))
    return { kind: 'unavailable', label: 'Присоединиться' }
  return unavailableLabel(event)
}

function unavailableLabel(event: Event): CardAction {
  if (event.state === 'cancelled_by_organizer' || event.state === 'cancelled_by_system')
    return { kind: 'unavailable', label: 'Отменено' }
  if (event.state === 'finished') return { kind: 'unavailable', label: 'Завершено' }
  return { kind: 'unavailable', label: 'Набор закрыт' }
}
