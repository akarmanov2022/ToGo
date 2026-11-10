import type { components } from '@/common/api/schema'

type Event = components['schemas']['Event']

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

/** Строка о местах: «3 из 5 участников · ещё 2 места» либо «… · мест нет». */
export function placesLine(event: Event): string {
  const taken = `${event.participantsCount} из ${event.maxUsers} участников`
  if (event.state !== 'recruiting') return taken
  return event.availableSlots > 0
    ? `${taken} · ещё ${slotsLabel(event.availableSlots)}`
    : `${taken} · мест нет`
}
