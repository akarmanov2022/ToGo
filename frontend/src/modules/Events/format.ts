import type { EventDto } from './api'
import { slotsLabel } from './rules'

const startFormat = new Intl.DateTimeFormat('ru-RU', {
  weekday: 'short',
  day: 'numeric',
  month: 'long',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatStart(iso: string): string {
  return startFormat.format(new Date(iso))
}

export function participantsLine(event: EventDto): string {
  const taken = `${event.participantsCount} из ${event.maxUsers} участников`
  if (event.state !== 'recruiting') return taken
  return event.availableSlots > 0
    ? `${taken} · ещё ${slotsLabel(event.availableSlots)}`
    : `${taken} · мест нет`
}

export function metaLine(event: EventDto, categoryName: string): string {
  return `${categoryName} · ${formatStart(event.startsAt)}`
}
