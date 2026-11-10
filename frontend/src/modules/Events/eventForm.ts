import type { EventCategory } from './api'

export interface EventFormValues {
  title: string
  description: string
  location: string
  category: EventCategory | ''
  startsAt: string
  endsAt: string
  registrationClosesAt: string
  minUsers: number
  maxUsers: number
  icon: string
}

export type EventFormField = keyof EventFormValues
export type EventFormErrors = Partial<Record<EventFormField, string>>

const MAX_TITLE = 100
const MAX_DESCRIPTION = 500
const MAX_PARTICIPANTS = 50
const ICON_ERROR = 'Укажите один эмодзи или оставьте пустым'
const graphemeSegmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' })

function isSingleEmoji(value: string): boolean {
  const segments = [...graphemeSegmenter.segment(value)]
  return segments.length === 1 && /\p{Extended_Pictographic}/u.test(segments[0].segment)
}

export function combineDateTime(date: string, time: string): string {
  if (!date || !time) return ''
  const moment = new Date(`${date}T${time}`)
  return Number.isNaN(moment.getTime()) ? '' : moment.toISOString()
}

export function addCalendarMonths(date: Date, months: number): Date {
  const result = new Date(date.getTime())
  const day = result.getDate()
  result.setMonth(result.getMonth() + months)
  if (result.getDate() < day) result.setDate(0)
  return result
}

export function toDateInputValue(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function validateEventForm(form: EventFormValues, now: Date): EventFormErrors {
  const errors: EventFormErrors = {}

  if (!form.title.trim()) errors.title = 'Введите название'
  else if (form.title.length > MAX_TITLE) errors.title = `Не длиннее ${MAX_TITLE} символов`

  if (!form.description.trim()) errors.description = 'Введите описание'
  else if (form.description.length > MAX_DESCRIPTION) {
    errors.description = `Не длиннее ${MAX_DESCRIPTION} символов`
  }

  if (!form.location.trim()) errors.location = 'Укажите место'

  if (!form.category) errors.category = 'Выберите категорию'

  const starts = Date.parse(form.startsAt)
  const ends = Date.parse(form.endsAt)
  const closes = Date.parse(form.registrationClosesAt)
  const monthLimit = addCalendarMonths(now, 1).getTime()

  if (Number.isNaN(starts)) errors.startsAt = 'Укажите время начала'
  if (Number.isNaN(ends)) errors.endsAt = 'Укажите время окончания'
  if (Number.isNaN(closes)) errors.registrationClosesAt = 'Укажите срок регистрации'

  if (!errors.startsAt && starts <= now.getTime()) {
    errors.startsAt = 'Начало должно быть в будущем'
  }
  if (!errors.startsAt && starts > monthLimit) {
    errors.startsAt = 'Начало не дальше одного календарного месяца'
  }

  if (!errors.registrationClosesAt && closes <= now.getTime()) {
    errors.registrationClosesAt = 'Срок регистрации должен быть в будущем'
  }
  if (!errors.registrationClosesAt && !errors.startsAt && closes > starts) {
    errors.registrationClosesAt = 'Срок регистрации не может быть позже начала'
  }
  if (!errors.endsAt && !errors.startsAt && ends <= starts) {
    errors.endsAt = 'Окончание должно быть позже начала'
  }

  if (!Number.isInteger(form.minUsers) || form.minUsers < 1) {
    errors.minUsers = 'Минимум не меньше 1'
  } else if (form.minUsers > form.maxUsers) {
    errors.minUsers = 'Минимум не больше максимума'
  }
  if (!Number.isInteger(form.maxUsers) || form.maxUsers < 1 || form.maxUsers > MAX_PARTICIPANTS) {
    errors.maxUsers = `Максимум от 1 до ${MAX_PARTICIPANTS}`
  }

  const icon = form.icon.trim()
  if (icon && !isSingleEmoji(icon)) errors.icon = ICON_ERROR

  return errors
}

export function hasRequiredGaps(errors: EventFormErrors): boolean {
  return Boolean(
    errors.title ||
      errors.description ||
      errors.location ||
      errors.category ||
      errors.startsAt ||
      errors.endsAt ||
      errors.registrationClosesAt ||
      errors.minUsers ||
      errors.maxUsers,
  )
}
