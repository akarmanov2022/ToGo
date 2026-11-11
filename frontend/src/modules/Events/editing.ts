import type { EventDto, UpdateEventBody } from './api'
import { validateEventFields } from './eventForm'
import { categoryEmoji } from './categories'

export interface EditDraft {
  title: string
  description: string
  category: EventDto['category']
  minUsers: string
  maxUsers: string
  registrationClosesAt: string
}
export function editDraft(event: EventDto): EditDraft {
  return {
    title: event.title,
    description: event.description,
    category: event.category,
    minUsers: String(event.minUsers),
    maxUsers: String(event.maxUsers),
    registrationClosesAt: event.registrationClosesAt,
  }
}
export function validateEdit(draft: EditDraft, event: EventDto, now: Date): Record<string, string> {
  const errors: Record<string, string> = validateEventFields(
    { ...draft, icon: categoryEmoji(draft.category) },
    {
      original: { title: event.title, icon: event.icon ?? '' },
      participantsCount: event.participantsCount,
    },
  )
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
  const icon = categoryEmoji(draft.category)
  if (icon !== event.icon) patch.icon = icon
  if (draft.category !== event.category) patch.category = draft.category
  if (Number(draft.minUsers) !== event.minUsers) patch.minUsers = Number(draft.minUsers)
  if (Number(draft.maxUsers) !== event.maxUsers) patch.maxUsers = Number(draft.maxUsers)
  if (Date.parse(draft.registrationClosesAt) !== Date.parse(event.registrationClosesAt))
    patch.registrationClosesAt = draft.registrationClosesAt
  return patch
}
