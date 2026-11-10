# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Project

ToGo is a Telegram Mini App for finding and joining local events (bot: `@togo_devdays_bot`), built during a hackathon.
The repository holds three parts that share one API contract:

- `api/openapi.yaml` is the single source of truth for the HTTP API.
- `backend/` is Spring Boot 4.1 on Kotlin 2.2 and Java 21, built with Maven, backed by PostgreSQL 18 and Flyway.
- `frontend/` is Vue 3 with TypeScript on Vite, using TanStack Query for server state, Pinia for client state, and Leaflet for maps.

Planning lives in OpenSpec under `openspec/changes/togo-hackathon-mvp/`.
Read `design.md` there before implementing a feature and tick the matching item in `tasks.md` when it is done.
OpenSpec artifacts are written in Russian, keeping the parser markup and SHALL/MUST keywords.
User-facing strings, including `ApiError` messages, are in Russian.

## Commands

Backend (run from the repository root; tests need a running Docker daemon for Testcontainers):

```sh
docker compose -f infra/docker-compose.yml up -d    # local PostgreSQL on 5432 (db togo, user admin)
./backend/mvnw -f backend/pom.xml compile            # regenerates API interfaces from api/openapi.yaml
./backend/mvnw -f backend/pom.xml verify             # tests, Kover coverage, detekt and ktlint, the check before declaring work done
./backend/mvnw -f backend/pom.xml ktlint:format      # fixes Kotlin formatting in place
./backend/mvnw -f backend/pom.xml test -Dtest=MigrationTest
TELEGRAM_BOT_TOKEN=<token> ./backend/mvnw -f backend/pom.xml spring-boot:run    # serves on :8080
```

Frontend (run inside `frontend/`):

```sh
npm install
npm run dev            # Vite proxies /api to http://localhost:8080
npm run build          # vue-tsc type check, then vite build
npm run lint
npm run format:check   # npm run format rewrites files
```

GitHub Actions runs `.github/workflows/ci.yml` on every pull request and every push to `main`: the same backend `verify`, which attaches the coverage, analyzer, and test reports to the run, and the frontend `npm ci` and `npm run build`.
Deployment is planned in `tasks.md` section 2.

## Backend architecture

The contract drives the server.
On `generate-sources`, `openapi-generator` (`kotlin-spring`, `serviceInterface=true`, `useTags=true`) writes controllers and one `*ApiService` interface per tag into `itmo.goto.generated.api` and DTOs into `itmo.goto.generated.model`, under `target/generated-sources/openapi`.
Never edit generated code; change `api/openapi.yaml` first, then implement the regenerated interface.
Hand-written classes in `itmo.goto.service` (such as `EventsService`) implement those interfaces; most methods still throw `501 NOT_IMPLEMENTED`.
Contract changes must stay additive: existing fields and endpoints do not change, new ones are optional.

Errors always use the contract's `ApiError { code, message, details }`, never problem details.
Services signal refusals by throwing `itmo.goto.web.ApiFailure` with an `ApiError.Code`; the code alone decides the HTTP status.
`ApiErrorHandler` turns validation, unreadable JSON, unknown fields (`fail-on-unknown-properties=true`), and unexpected exceptions into `ApiError`, hiding internal details on 500.

Planned layering from `design.md` decision 4:
event rules (participant limits, the five event states, join, leave, cancel) go into a framework-free `itmo.goto.domain` package that returns a new state or a typed refusal.
Services load JPA entities, call the domain, and persist the result.
Time always comes from the injected `java.time.Clock` bean (`ClockConfiguration`, UTC), so tests fix the clock instead of waiting.
Join and leave lock the event row with `@Lock(PESSIMISTIC_WRITE)`, read the time after acquiring the lock, and keep the denormalized `events.participants_count` in sync (decision 6).

Authentication lives in `itmo.goto.auth`.
`TelegramLaunch` is a framework-free check of Mini App `initData` against `TELEGRAM_BOT_TOKEN` and the `Clock`.
`AccessTokens` issues one-hour HS256 JWTs whose key is derived from the bot token, and `SecurityConfiguration` accepts only those Bearer tokens.
Services learn the caller through the `Identity` bean, never through `SecurityContextHolder` directly.

The schema is defined only by Flyway migrations in `backend/src/main/resources/db/migration`; primary keys use PostgreSQL 18 `uuidv7()`.
JPA entities live in `itmo.goto.model` and Spring Data repositories in `itmo.goto.repository`.

## Backend tests

- Repository and migration tests use `@DataJpaTest` with `@Import(PostgresConfiguration::class)`, a real PostgreSQL 18 container shared through the cached Spring context. H2 is deliberately absent because row locks and `uuidv7()` behave differently.
- `Rows` inserts raw rows through `JdbcTemplate` to probe schema constraints without entities.
- Web tests are `@WebMvcTest` slices; `web/ProbeController` and `web/MethodProbeController` are test-only controllers that trigger each failure kind for `ApiErrorHandler`.
- `auth/SignedLaunch` signs launch data the way Telegram does, so tests can send genuine, stale, or tampered `initData`.
- `DemoProfileTest` is the only full-context test: it starts the whole application to prove the `demo` profile cannot bypass Telegram login.
- Tests follow one style: backtick sentence names, one AssertJ assertion per test passed through a negative `withFailMessage`, random inputs from `kotlin.random.Random`, and `@TestBean` to replace the `Clock`.
- Logging is turned off in `src/test/resources/logback-test.xml`.
- `verify` fails when Kover reports less than 80% line or branch coverage of hand-written code; only `itmo.goto.generated` and `main` are excluded. Never lower the threshold or add exclusions to pass it.
- Each service method that still answers 501 has a stub test; replace it with behavior tests when the method is implemented.
- detekt (`backend/detekt.yml`, on top of the default rules) and ktlint (`.editorconfig`, `intellij_idea` style) check main and test code with zero tolerance and no baselines. Fix violations instead of suppressing them; every exception in `detekt.yml` carries a comment with its reason.

## Frontend architecture

The frontend follows FEOD (Fractal Entity Oriented Design), described by the `.agents/skills/feod` skill.
Layers import in one direction only: `common → modules → pages → app`.
Each module under `src/modules/<Name>/` exposes its public API through `index.ts`, and other layers import only from that entry.
`@` aliases `src/`.
Screen data is still demo data in `modules/Events/data.ts`; Telegram login, event creation, and joining are not connected to the backend yet.
Planned work adds `openapi-typescript` types and MSW mocks generated from the same `api/openapi.yaml`.

## Workflow

There is no Jira in this project; the repository lives on GitHub.
Name branches after the OpenSpec change (such as `togo-hackathon-mvp-block3`) and write commit messages without issue keys.
OpenSpec work goes through the `openspec-*` skills in `.agents/skills/` (Claude Code also exposes them as `/opsx:*` commands in `.claude/commands/opsx/`).
