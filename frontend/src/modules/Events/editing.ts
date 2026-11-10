import type { EventDto, UpdateEventBody } from './api'
import { isSingleEmoji } from './eventForm'
import { creationCategories } from './creation'

export interface EditDraft {
  title: string
  description: string
  icon: string
  category: EventDto['category']
  minUsers: string
  maxUsers: string
  registrationClosesAt: string
}
export function editDraft(event: EventDto): EditDraft {
  return {
    title: event.title,
    description: event.description,
    icon: event.icon ?? '',
    category: event.category,
    minUsers: String(event.minUsers),
    maxUsers: String(event.maxUsers),
    registrationClosesAt: event.registrationClosesAt,
  }
}
export function validateEdit(draft: EditDraft, event: EventDto, now: Date): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!creationCategories.some((category) => category.code === draft.category))
    errors.category = 'Выберите категорию из перечня'
  if (
    !draft.title.trim() ||
    draft.title.length > 100 ||
    (draft.title !== event.title && !/^[\p{L}\p{N}\s]+$/u.test(draft.title))
  )
    errors.title = 'Название: буквы, цифры и пробелы, от 1 до 100 символов'
  if (!draft.description.trim() || draft.description.length > 500)
    errors.description = 'Описание: от 1 до 500 символов'
  if (draft.icon.trim() && draft.icon !== (event.icon ?? '') && !isSingleEmoji(draft.icon.trim()))
    errors.icon = 'Укажите один эмодзи или оставьте поле пустым'
  if (!/^\d+$/.test(draft.minUsers) || Number(draft.minUsers) < 1 || Number(draft.minUsers) > 50)
    errors.minUsers = 'Минимум: целое число от 1 до 50'
  if (!/^\d+$/.test(draft.maxUsers) || Number(draft.maxUsers) < 1 || Number(draft.maxUsers) > 50)
    errors.maxUsers = 'Максимум: целое число от 1 до 50'
  else if (Number(draft.maxUsers) < Math.max(Number(draft.minUsers), event.participantsCount))
    errors.maxUsers = 'Максимум не может быть меньше минимума или числа записавшихся участников'
  const deadline = Date.parse(draft.registrationClosesAt)
  if (
    !Number.isFinite(deadline) ||
    deadline <= now.getTime() ||
    deadline > Date.parse(event.startsAt)
  )
    errors.registrationClosesAt =
      'Закрытие регистрации должно быть позже текущего времени и не позже начала'
  return errors
}
export function editPatch(draft: EditDraft, event: EventDto): UpdateEventBody {
  const patch: UpdateEventBody = {}
  if (draft.title.trim() !== event.title) patch.title = draft.title.trim()
  if (draft.description.trim() !== event.description) patch.description = draft.description.trim()
  if (draft.icon.trim() !== (event.icon ?? '')) patch.icon = draft.icon.trim()
  if (draft.category !== event.category) patch.category = draft.category
  if (Number(draft.minUsers) !== event.minUsers) patch.minUsers = Number(draft.minUsers)
  if (Number(draft.maxUsers) !== event.maxUsers) patch.maxUsers = Number(draft.maxUsers)
  if (Date.parse(draft.registrationClosesAt) !== Date.parse(event.registrationClosesAt))
    patch.registrationClosesAt = draft.registrationClosesAt
  return patch
}
