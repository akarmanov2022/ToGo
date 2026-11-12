# Tasks

## 1. Контракт и схема данных

- [x] 1.1 Дополнить `api/openapi.yaml` по решению 1: тег `Ratings`, `PUT /api/v1/events/{eventId}/rating`, схемы `RateEventRequest` и `Rating`, необязательные nullable-поля `Event.rating`, `Event.myRating`, `CurrentUser.organizerRating` и `UserProfile.organizerRating`, не меняя существующие поля и коды; проверить, что `./backend/mvnw -f backend/pom.xml compile` генерирует `RatingsApiService`, а `npm run api:generate` во `frontend/` обновляет `schema.ts` без ошибок `npm run build`
- [x] 1.2 Добавить миграцию `V5__event_ratings.sql` по решению 2; проверить в `MigrationTest` через `Rows` отказ при оценке 0 и 6, отказ при второй строке той же пары событие и пользователь и отказ при неизвестном событии
- [x] 1.3 Добавить JPA-сущность оценки и репозиторий с upsert `ON CONFLICT` и выборкой оценок зрителя по списку событий; проверить в `@DataJpaTest`, что повторный upsert заменяет оценку и не создаёт вторую строку

## 2. Правило оценки в домене

- [x] 2.1 Добавить `Meeting.rate(now)` по решению 3; проверить юнит-тестами с фиксированным временем принятие в `FINISHED`, отказ `INVALID_EVENT_STATE` в `RECRUITING`, `REGISTRATION_CLOSED` до `endsAt` и обоих отменённых состояниях, и принятие для `REGISTRATION_CLOSED` после `endsAt` с переходом в `FINISHED`

## 3. Сервис оценки и средние значения

- [x] 3.1 Добавить `@Formula` средней оценки и числа оценок в `Event` и поле `rating` в карточку, а `myRating` передавать в `card` аргументом и загружать одним запросом на страницу ленты по решению 4; проверить в `EventDiscoveryServiceTest` среднее 4.3 для оценок 5, 4 и 4, `null` без оценок и `myRating` только у оценившего
- [x] 3.2 Реализовать `RatingsService.rateEvent` в порядке из решения 3; проверить в `RatingsServiceTest` ответ с `myRating`, замену оценки, 403 FORBIDDEN для организатора, постороннего и вышедшего участника, 409 INVALID_EVENT_STATE для незавершённого и отменённого события, 404 EVENT_NOT_FOUND и сохранение перехода в `finished` при запоздавшем фоновом переходе, в том числе когда оценка отклонена
- [x] 3.3 Проверить в `@WebMvcTest` запрос оценки: 400 VALIDATION_ERROR с полем `score` для 0, 6, дробного числа и отсутствующего поля, 400 для неизвестного поля и 401 без токена
- [ ] 3.4 Добавить `@Formula` рейтинга организатора в `User` и поле `organizerRating` в `CurrentUser` и `UserProfile`; проверить в `UsersServiceTest` среднее 4.0 из трёх оценок двух событий, `null` без оценок и пересчёт после изменения оценки
- [x] 3.5 Обновить раздел «Backend architecture» в `AGENTS.md` строкой о `RatingsService` и вычисляемых средних; проверить, что `./backend/mvnw -f backend/pom.xml verify` проходит с покрытием не ниже 80%, detekt и ktlint без нарушений

## 4. Фронтенд

- [ ] 4.1 Добавить компонент `EventRating` в `EventDetail` по решению 5; проверить тестами Vitest показ средней оценки и числа оценок, звёзды только у участника не организатора в `finished`, отправку `PUT` с выбранной оценкой, обновление карточки ответом сервера и показ сообщения `ApiError` при ошибке
- [ ] 4.2 Показать `organizerRating` в своём и чужом профиле `UserProfile`; проверить тестами Vitest показ «4.3» и числа 12 и отсутствие блока при `null`, затем `npm run lint`, `npm run format:check` и `npm run build`

## 5. Сквозная проверка

- [ ] 5.1 Пройти на запущенном бэкенде с двумя аккаунтами сценарий: завершённое событие с ранним `endsAt`, оценка участником из карточки, отсутствие звёзд у организатора, изменение оценки и рейтинг организатора в его профиле

## Workflow follow-up

- Заархивировать изменение через `/opsx:archive` после `togo-hackathon-mvp`, чтобы его спецификации профиля и событий
  уже были в `openspec/specs/`.
