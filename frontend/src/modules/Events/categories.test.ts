import { expect, it } from 'vitest'
import { categoryEmoji, eventCategories } from './categories'
import { validateEventForm } from './eventForm'

it('каждой категории назначен собственный валидный эмодзи', () => {
  const icons = eventCategories.map((category) => categoryEmoji(category.code))
  expect(icons).toEqual(['🧺', '🏃', '🏛️', '🎲', '🔎', '🎬', '☕'])
  for (const category of eventCategories) {
    expect(
      validateEventForm(
        {
          title: '',
          description: '',
          category: category.code,
          location: '',
          minUsers: 1,
          maxUsers: 2,
          startsAt: '',
          endsAt: '',
          registrationClosesAt: '',
          icon: categoryEmoji(category.code),
        },
        new Date(),
      ).icon,
    ).toBeUndefined()
  }
})
