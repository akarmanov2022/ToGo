# ToGo frontend

Vue 3 + TypeScript + Vite, TanStack Query и Pinia. Экранные данные пока демонстрационные: вход через Telegram, создание события и присоединение к нему не подключены.

```sh
npm install
npm run dev
npm run build
npm run lint
npm run format:check
```

Для автоматического форматирования файлов: `npm run format`.

Экраны: `/` — события, `/login` — вход, `/create` — создание, `/profile` — профиль. В разработке Vite проксирует `/api` на `http://localhost:8080`.
