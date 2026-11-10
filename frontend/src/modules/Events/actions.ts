import type { components } from '@/common/api/schema'

type Event = components['schemas']['Event']

export interface CardAction {
  kind: 'cancel' | 'leave' | 'join' | 'unavailable'
  label: string
}

/** Метка состояния события из пяти состояний API. */
export function stateLabel(state: components['schemas']['EventState']): string {
  switch (state) {
    case 'recruiting':
      return 'Идёт набор'
    case 'registration_closed':
      return 'Набор закрыт'
    case 'finished':
      return 'Завершено'
    case 'cancelled_by_organizer':
      return 'Отменено организатором'
    case 'cancelled_by_system':
      return 'Отменено: не набрался минимум'
  }
}

/**
 * Главная страница по обновлённой аналитике: только предстоящие события,
 * без отменённых и завершённых; порядок — по возрастанию начала.
 */
export function isVisibleInFeed(event: Event): boolean {
  return (
    event.state !== 'cancelled_by_organizer' &&
    event.state !== 'cancelled_by_system' &&
    event.state !== 'finished'
  )
}

/**
 * Кнопка карточки события по таблице 3.1 аналитики.
 * Организатор видит «Отменить событие» (отмена — только с подтверждением) и никогда
 * не может присоединиться к своему событию. Участник видит «Отписаться от события».
 * Забитое событие, закрытый набор, отмена и завершение делают кнопку недоступной.
 */
export function cardAction(event: Event, now: Date): CardAction {
  const time = now.getTime()
  const registrationOpen =
    event.state === 'recruiting' && time < Date.parse(event.registrationClosesAt)

  if (event.isOrganizer) {
    const cancellable =
      (event.state === 'recruiting' || event.state === 'registration_closed') &&
      time < Date.parse(event.startsAt)
    return cancellable ? { kind: 'cancel', label: 'Отменить событие' } : unavailableLabel(event)
  }
  if (event.isParticipant) {
    return registrationOpen
      ? { kind: 'leave', label: 'Отписаться от события' }
      : unavailableLabel(event)
  }
  if (!registrationOpen) return unavailableLabel(event)
  return event.availableSlots > 0
    ? { kind: 'join', label: 'Присоединиться' }
    : { kind: 'unavailable', label: 'Забито' }
}

function unavailableLabel(event: Event): CardAction {
  if (event.state === 'cancelled_by_organizer' || event.state === 'cancelled_by_system')
    return { kind: 'unavailable', label: 'Отменено' }
  if (event.state === 'finished') return { kind: 'unavailable', label: 'Завершено' }
  return { kind: 'unavailable', label: 'Набор закрыт' }
}
