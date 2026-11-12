import { validateSchedule } from '@/modules/EventSchedule'
import { eventCategories } from './categories'

export interface EventFields {
  title: string
  description: string
  category: string
  minUsers: number | string
  maxUsers: number | string
  icon: string
}

export interface EventFormValues extends EventFields {
  location: string
  startsAt: string
  endsAt: string
  registrationClosesAt: string
}

export type EventFormField = keyof EventFormValues
export type EventFormErrors = Partial<Record<EventFormField, string>>

const MAX_TITLE = 100
const MAX_DESCRIPTION = 500
const MAX_PARTICIPANTS = 50
const ICON_ERROR = 'Укажите один эмодзи или оставьте пустым'
const graphemeSegmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' })

export function isSingleEmoji(value: string): boolean {
  const segments = [...graphemeSegmenter.segment(value)]
  return segments.length === 1 && /\p{Extended_Pictographic}/u.test(segments[0].segment)
}

function participantCount(value: string | number): number {
  if (typeof value === 'string' && !/^\d+$/.test(value)) return NaN
  return Number(value)
}

export function validateEventFields(
  form: EventFields,
  options: { original?: Pick<EventFields, 'title' | 'icon'>; participantsCount?: number } = {},
): EventFormErrors {
  const errors: EventFormErrors = {}

  if (!form.title.trim()) errors.title = 'Введите название'
  else if (form.title.length > MAX_TITLE) errors.title = `Не длиннее ${MAX_TITLE} символов`
  else if (form.title !== options.original?.title && !/^[\p{L}\p{N}\s]+$/u.test(form.title))
    errors.title = 'Название: буквы, цифры и пробелы'

  if (!form.description.trim()) errors.description = 'Введите описание'
  else if (form.description.length > MAX_DESCRIPTION) {
    errors.description = `Не длиннее ${MAX_DESCRIPTION} символов`
  }

  if (!eventCategories.some((category) => category.code === form.category))
    errors.category = 'Выберите категорию из перечня'

  const minimum = participantCount(form.minUsers)
  const maximum = participantCount(form.maxUsers)
  if (!Number.isInteger(minimum) || minimum < 1 || minimum > MAX_PARTICIPANTS) {
    errors.minUsers = `Минимум от 1 до ${MAX_PARTICIPANTS}`
  } else if (minimum > maximum) {
    errors.minUsers = 'Минимум не больше максимума'
  }
  if (!Number.isInteger(maximum) || maximum < 1 || maximum > MAX_PARTICIPANTS) {
    errors.maxUsers = `Максимум от 1 до ${MAX_PARTICIPANTS}`
  } else if (maximum < Math.max(minimum || 0, options.participantsCount ?? 0)) {
    errors.maxUsers = 'Максимум не может быть меньше минимума или числа записавшихся участников'
  }

  const icon = form.icon.trim()
  if (icon && form.icon !== options.original?.icon && !isSingleEmoji(icon)) errors.icon = ICON_ERROR

  return errors
}

export function validateEventForm(form: EventFormValues, now: Date): EventFormErrors {
  const errors = { ...validateEventFields(form), ...validateSchedule(form, now) }
  if (!form.location.trim()) errors.location = 'Укажите место'
  return errors
}
