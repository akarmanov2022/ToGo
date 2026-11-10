# EventSchedule

Изолированный ввод трёх обязательных моментов времени, без HTTP и зависимостей от других модулей.
Импортировать только из `@/modules/EventSchedule`.

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { EventSchedule, emptySchedule, validateSchedule } from '@/modules/EventSchedule'

const schedule = ref(emptySchedule())
const submitted = ref(false)
function submit() {
  submitted.value = true
  const errors = validateSchedule(schedule.value, new Date())
  if (Object.keys(errors).length) return
  // Родитель объединяет schedule.value с остальными полями формы.
}
</script>

<template>
  <form @submit.prevent="submit">
    <EventSchedule v-model="schedule" :show-errors="submitted" />
    <button type="submit">Создать встречу</button>
  </form>
</template>
```

- `v-model`: `EventScheduleValue { startsAt: string; endsAt: string; registrationClosesAt: string }`.
  Пустой или неполный ввод — `''`, не `null`. По умолчанию всё пусто; срок регистрации не подставляется.
  Частично введённые дата/время остаются в локальном черновике до заполнения второй части.
- Полные значения — RFC3339 с явным смещением (`2026-11-11T18:00:00+03:00`).
  Представление использует локальный часовой пояс браузера, включая переход даты для входящих UTC-значений;
  смещение определяется для выбранной даты, с учётом DST. Несуществующее локальное время при переводе часов
  отклоняется; при повторяющемся времени используется более ранний момент, как в JavaScript Date.
  -- Нативные поля `type=date`/`type=time` дополнены собственным календарём `CalendarPicker` и ползунками
  часов/минут, оформленными в стиле приложения. Календарь **начала** допускает даты от завтрашнего дня
  (временное решение команды: завтра с 00:00, а не через 24 часа — сосредоточено в `earliestEventStart`)
  и до одного календарного месяца вперёд. Конец месяца обрезается (31 января → 28/29 февраля).
  Для срока регистрации и окончания прошлое запрещено, месячного предела нет; секунды нового ввода — 00.
- `validateSchedule(value, now?)` возвращает ошибки по именам полей на русском и проверяет
  `now < registrationClosesAt <= startsAt < endsAt`, начало не раньше завтрашнего дня
  и месячный предел начала.
  Компонент посылает `validation` с тем же объектом, включая первоначальные ошибки.
  Ошибки видны после изменения поля или при `show-errors=true`.
- `now?: () => Date` позволяет внедрить часы для тестов. При открытом компоненте ограничения обновляются
  каждую секунду; перед отправкой родителю следует повторно вызвать helper с актуальным временем.
  Серверное время и серверная валидация остаются окончательным источником истины.
- Общая ошибка незаполненной формы под кнопкой, отправка и обработка серверных ошибок — ответственность родителя.

Проверки из `frontend/`:

```sh
npx vitest run src/modules/EventSchedule
npx eslint src/modules/EventSchedule
npx prettier --check src/modules/EventSchedule
```
