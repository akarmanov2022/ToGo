interface TelegramWebApp {
  initData: string
  ready: () => void
  expand: () => void
}
declare global {
  interface Window {
    Telegram?: { WebApp?: TelegramWebApp }
  }
}

export function getTelegramInitData(): string {
  const app = window.Telegram?.WebApp
  if (!app) return ''
  app.ready()
  app.expand()
  return app.initData
}
