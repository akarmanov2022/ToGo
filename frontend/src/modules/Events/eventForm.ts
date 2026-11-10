export interface EventFormValues {
  title: string
  description: string
  location: string
  categoryIds: string[]
  startsAt: string
  endsAt: string
  registrationClosesAt: string
  minUsers: number
  maxUsers: number
}

export type EventFormField = keyof EventFormValues
export type EventFormErrors = Partial<Record<EventFormField, string>>

const MAX_TITLE = 100
const MAX_DESCRIPTION = 2000
const MAX_PARTICIPANTS = 50

export function combineDateTime(date: string, time: string): string {
  if (!date || !time) return ''
  const moment = new Date(`${date}T${time}`)
  return Number.isNaN(moment.getTime()) ? '' : moment.toISOString()
}

export function validateEventForm(form: EventFormValues, now: Date): EventFormErrors {
  const errors: EventFormErrors = {}

  if (!form.title.trim()) errors.title = 'Введите название'
  else if (form.title.length > MAX_TITLE) errors.title = `Не длиннее ${MAX_TITLE} символов`

  if (form.description.length > MAX_DESCRIPTION) {
    errors.description = `Не длиннее ${MAX_DESCRIPTION} символов`
  }

  if (!form.location.trim()) errors.location = 'Укажите место'

  if (form.categoryIds.length === 0) errors.categoryIds = 'Выберите хотя бы одну категорию'

  const starts = Date.parse(form.startsAt)
  const ends = Date.parse(form.endsAt)
  const closes = Date.parse(form.registrationClosesAt)

  if (Number.isNaN(starts)) errors.startsAt = 'Укажите время начала'
  if (Number.isNaN(ends)) errors.endsAt = 'Укажите время окончания'
  if (Number.isNaN(closes)) errors.registrationClosesAt = 'Укажите срок регистрации'

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
  if (!Number.isInteger(form.maxUsers) || form.maxUsers > MAX_PARTICIPANTS) {
    errors.maxUsers = `Максимум не больше ${MAX_PARTICIPANTS}`
  }

  return errors
}
