---
feod-profile: 1
---

# Профиль FEOD

Подтверждён пользователем: сохраняем существующие слои и ручную маршрутизацию Vue SPA.
Настройки проверок — только в `feod.config.json`; ESLint читает этот файл, не дублирует параметры.

| Parameter                | Value                                 |
| ------------------------ | ------------------------------------- |
| modification             | base                                  |
| multiapp                 | no                                    |
| framework                | vue (spa)                             |
| srcRoot                  | src                                   |
| alias                    | @                                     |
| layers                   | app, pages, modules, common, global   |
| pages                    | keep                                  |
| modulePages              | no                                    |
| fileBasedRouting         | no                                    |
| common.allowIndex        | false                                 |
| modules.allowDeepImports | false                                 |
| singleFileModules        | no                                    |
| transitModules           | none                                  |
| layouts                  | app only                              |
| lint                     | eslint, подключён в npm run lint и CI |

## Договорённости

- Межмодульные импорты — только через `@/modules/<Name>`; локальные — относительные.
- Прямые зависимости между модулями разрешены через публичный API без циклов;
  при сильной взаимной связи применяется IoC с подключением в `app/init`.
- `common` содержит отдельные UI-примитивы, транспорт API и универсальные composable;
  общий поповер не принадлежит доменному модулю расписания. Полноценный UI-kit выносится в модуль.
- В `pages` только маршрут и композиция, в `app` — запуск, layout и интеграции.
- Правила доступных действий событий — в `Events/rules.ts`, полей — в `Events/eventForm.ts`.
- GET текущего пользователя и его query key принадлежат `Auth`; `Profile` использует этот API,
  а не создаёт второй кеш. Токен остаётся только в памяти Pinia.
- Типы HTTP-контракта — из генерируемого `common/api/schema.ts`; UI-draft и результаты валидации
  могут иметь собственные типы. Сгенерированный файл исключён из линтинга, но не из typecheck.
