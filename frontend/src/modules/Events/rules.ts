import type { components } from '@/common/api/schema'
export type EventState = components['schemas']['EventState']

export interface EventActionsInput {
  state: EventState
  startsAt: string
  registrationClosesAt: string
  availableSlots: number
  isParticipant: boolean
  isOrganizer: boolean
}

export interface EventActions {
  canJoin: boolean
  canLeave: boolean
  canCancel: boolean
  canEdit: boolean
}

const stateLabels: Record<EventState, string> = {
  recruiting: 'Идёт набор',
  registration_closed: 'Набор закрыт',
  finished: 'Завершено',
  cancelled_by_organizer: 'Отменено организатором',
  cancelled_by_system: 'Отменено: не набрался минимум',
}

export function stateLabel(state: EventState): string {
  return stateLabels[state]
}

export function availableActions(event: EventActionsInput, now: Date): EventActions {
  const time = now.getTime()
  const registrationOpen = isRegistrationOpen(event, now)
  const beforeStart = time < Date.parse(event.startsAt)
  const cancellable = isActiveEvent(event)

  return {
    canJoin:
      registrationOpen && event.availableSlots > 0 && !event.isParticipant && !event.isOrganizer,
    canLeave: registrationOpen && event.isParticipant && !event.isOrganizer,
    canCancel: event.isOrganizer && cancellable && beforeStart,
    canEdit: event.isOrganizer && registrationOpen,
  }
}

export function isRegistrationOpen(
  event: Pick<EventActionsInput, 'state' | 'registrationClosesAt'>,
  now: Date,
): boolean {
  return event.state === 'recruiting' && now.getTime() < Date.parse(event.registrationClosesAt)
}

export function isActiveEvent(event: Pick<EventActionsInput, 'state'>): boolean {
  return event.state === 'recruiting' || event.state === 'registration_closed'
}

export function slotsLabel(count: number): string {
  const lastTwo = count % 100
  const last = count % 10
  const noun =
    lastTwo >= 11 && lastTwo <= 14
      ? 'мест'
      : last === 1
        ? 'место'
        : last >= 2 && last <= 4
          ? 'места'
          : 'мест'
  return `${count} ${noun}`
}
