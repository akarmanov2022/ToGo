import type { components } from '@/common/api/schema'

/** Резервный справочник из контракта; при доступном API используется ответ сервера. */
export const eventCategories = [
  { code: 'PICNIC', name: 'Пикник' },
  { code: 'RUN', name: 'Пробежка' },
  { code: 'EXCURSION', name: 'Поход на экскурсию' },
  { code: 'BOARD_GAMES', name: 'Настолки' },
  { code: 'QUEST', name: 'Квест' },
  { code: 'CINEMA', name: 'Кино' },
  { code: 'CAFE', name: 'Кафе/Ресторан' },
] as const satisfies readonly components['schemas']['Category'][]
