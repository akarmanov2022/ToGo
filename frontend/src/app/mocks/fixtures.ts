import type { components } from '@/common/api/schema'

type Category = components['schemas']['Category']
type CurrentUser = components['schemas']['CurrentUser']
type Event = components['schemas']['Event']
type UserProfile = components['schemas']['UserProfile']

export const localInitData = 'local-msw-init-data'
export const localAccessToken = 'local-msw-token'

export const categories: Category[] = [
  { code: 'PICNIC', name: 'Пикник' },
  { code: 'RUN', name: 'Пробежка' },
  { code: 'EXCURSION', name: 'Поход на экскурсию' },
  { code: 'BOARD_GAMES', name: 'Настолки' },
  { code: 'QUEST', name: 'Квест' },
  { code: 'CINEMA', name: 'Кино' },
  { code: 'CAFE', name: 'Кафе/Ресторан' },
]

export const currentUser: CurrentUser = {
  id: '019a0000-0000-7000-8000-000000000101',
  tgId: 100001,
  tgLogin: 'local_demo',
  name: 'Алексей',
  photoUrl: null,
  city: 'Санкт-Петербург',
  categories: ['BOARD_GAMES'],
  createdEventsCount: 1,
  visitedEventsCount: 0,
}

export const otherUser: UserProfile = {
  id: '019a0000-0000-7000-8000-000000000102',
  tgLogin: 'local_organizer',
  name: 'Мария',
  photoUrl: null,
  city: 'Санкт-Петербург',
  categories: ['EXCURSION'],
  createdEventsCount: 2,
  visitedEventsCount: 1,
}

/** Dates are relative to reset time so the local demo does not expire on disk. */
export function createEventFixtures(now = Date.now()): Event[] {
  const date = (hours: number) => new Date(now + hours * 3_600_000).toISOString()
  const base: Event = {
    id: '019a0000-0000-7000-8000-000000000201',
    title: 'Вечер настолок',
    description: 'Локальные тестовые данные: играем и знакомимся.',
    location: 'Санкт-Петербург, Невский проспект, 28',
    startsAt: date(48),
    endsAt: date(51),
    registrationClosesAt: date(24),
    state: 'recruiting',
    icon: '🎲',
    latitude: 59.935,
    longitude: 30.325,
    minUsers: 2,
    maxUsers: 6,
    organizer: { id: otherUser.id, tgLogin: otherUser.tgLogin },
    category: 'BOARD_GAMES',
    participantsCount: 1,
    availableSlots: 5,
    isParticipant: false,
    isOrganizer: false,
  }
  return [
    base,
    {
      ...base,
      id: '019a0000-0000-7000-8000-000000000202',
      title: 'Прогулка по городу',
      category: 'EXCURSION',
      icon: '🚶',
      startsAt: date(72),
      endsAt: date(75),
      registrationClosesAt: date(60),
      organizer: { id: currentUser.id, tgLogin: currentUser.tgLogin },
      minUsers: 1,
      isParticipant: true,
      isOrganizer: true,
    },
    {
      ...base,
      id: '019a0000-0000-7000-8000-000000000203',
      title: 'Утренняя пробежка',
      category: 'RUN',
      icon: '🏃',
      startsAt: date(-48),
      endsAt: date(-46),
      registrationClosesAt: date(-50),
      state: 'finished',
      minUsers: 1,
    },
  ]
}
