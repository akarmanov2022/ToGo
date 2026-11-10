interface TelegramWebApp {
  initData: string
  platform: string
  ready: () => void
  expand: () => void
  requestWriteAccess?: (callback?: (granted: boolean) => void) => void
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
  if (
    app.initData &&
    app.requestWriteAccess &&
    !sessionStorage.getItem('togo-write-access-requested')
  ) {
    sessionStorage.setItem('togo-write-access-requested', '1')
    app.requestWriteAccess()
  }
  return app.initData
}
