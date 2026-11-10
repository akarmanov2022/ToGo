import type { components } from '@/common/api/schema'
import { availableActions, isActiveEvent, isRegistrationOpen } from './rules'

type Event = components['schemas']['Event']

export interface CardAction {
  kind: 'cancel' | 'leave' | 'join' | 'unavailable'
  label: string
}

/**
 * Главная страница по обновлённой аналитике: только предстоящие события,
 * без отменённых и завершённых; порядок — по возрастанию начала.
 */
export function isVisibleInFeed(event: Event): boolean {
  return isActiveEvent(event)
}

/**
 * Кнопка карточки события по таблице 3.1 аналитики.
 * Организатор видит «Отменить событие» (отмена — только с подтверждением) и никогда
 * не может присоединиться к своему событию. Участник видит «Отписаться от события».
 * Забитое событие, закрытый набор, отмена и завершение делают кнопку недоступной.
 */
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
