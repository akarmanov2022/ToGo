export interface EventScheduleValue {
  startsAt: string
  endsAt: string
  registrationClosesAt: string
}

export type ScheduleField = keyof EventScheduleValue
export type ScheduleErrors = Partial<Record<ScheduleField, string>>
export interface LocalDateTime {
  date: string
  time: string
}

export const emptySchedule = (): EventScheduleValue => ({
  startsAt: '',
  endsAt: '',
  registrationClosesAt: '',
})

const pad = (value: number) => String(value).padStart(2, '0')

export function earliestEventStart(now: Date): Date {
  const result = new Date(now)
  result.setDate(result.getDate() + 1)
  result.setHours(0, 0, 0, 0)
  return result
}

export function localDate(value: Date): string {
  return `${String(value.getFullYear()).padStart(4, '0')}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
}

export function addCalendarMonth(value: Date): Date {
  const result = new Date(value)
  const day = result.getDate()
  result.setDate(1)
  result.setMonth(result.getMonth() + 1)
  const lastDay = new Date(result.getFullYear(), result.getMonth() + 1, 0).getDate()
  result.setDate(Math.min(day, lastDay))
  return result
}

function validDateParts(year: number, month: number, day: number): boolean {
  const date = new Date(0)
  date.setUTCFullYear(year, month - 1, day)
  return (
    year >= 1 &&
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  )
}

export function parseScheduleTimestamp(value: string): Date | null {
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})$/.exec(value)
  if (!match) return null
  const [, y, m, d, h, minute, second, offset] = match
  if (
    !validDateParts(Number(y), Number(m), Number(d)) ||
    Number(h) > 23 ||
    Number(minute) > 59 ||
    Number(second) > 59 ||
    (offset !== 'Z' && (Number(offset!.slice(1, 3)) > 23 || Number(offset!.slice(4)) > 59))
  )
    return null
  const parsed = new Date(value)
  return Number.isFinite(parsed.getTime()) ? parsed : null
}

export function toLocalDateTime(value: string): LocalDateTime {
  const parsed = parseScheduleTimestamp(value)
  return parsed
    ? { date: localDate(parsed), time: `${pad(parsed.getHours())}:${pad(parsed.getMinutes())}` }
    : { date: '', time: '' }
}

export function toScheduleTimestamp({ date, time }: LocalDateTime): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) return ''
  const [year, month, day] = date.split('-').map(Number) as [number, number, number]
  const [hour, minute] = time.split(':').map(Number) as [number, number]
  if (!validDateParts(year, month, day) || hour > 23 || minute > 59) return ''
  const parsed = new Date(0)
  parsed.setFullYear(year, month - 1, day)
  parsed.setHours(hour, minute, 0, 0)
  if (localDate(parsed) !== date || parsed.getHours() !== hour || parsed.getMinutes() !== minute)
    return ''
  const offset = -parsed.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  return `${date}T${time}:00${sign}${pad(Math.floor(Math.abs(offset) / 60))}:${pad(Math.abs(offset) % 60)}`
}

export function validateSchedule(value: EventScheduleValue, now = new Date()): ScheduleErrors {
  const errors: ScheduleErrors = {}
  const dates = {} as Record<ScheduleField, Date | null>
  for (const field of ['startsAt', 'endsAt', 'registrationClosesAt'] as const) {
    dates[field] = parseScheduleTimestamp(value[field])
    if (!value[field]) errors[field] = 'Укажите дату и время'
    else if (!dates[field]) errors[field] = 'Укажите корректные дату и время с часовым поясом'
  }
  const { startsAt: start, endsAt: end, registrationClosesAt: registration } = dates
  if (start && start < earliestEventStart(now))
    errors.startsAt = 'Начало доступно с завтрашнего дня'
  if (start && start > addCalendarMonth(now))
    errors.startsAt = 'Начало не может быть позже чем через календарный месяц'
  if (end && end <= now) errors.endsAt = 'Окончание должно быть позже текущего времени'
  if (start && end && end <= start) errors.endsAt = 'Окончание должно быть позже начала'
  if (registration && registration <= now)
    errors.registrationClosesAt = 'Срок регистрации должен быть позже текущего времени'
  else if (registration && start && registration > start)
    errors.registrationClosesAt = 'Регистрация должна закрыться не позже начала'
  return errors
}
