interface TelegramWebApp {
  initData: string
  platform?: string
  ready: () => void
  expand: () => void
  requestWriteAccess?: (callback: (granted: boolean) => void) => void
  isVersionAtLeast?: (version: string) => boolean
  initDataUnsafe?: { user?: { allows_write_to_pm?: boolean } }
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

/** Разрешение сообщений — не способ входа и не влияет на успех авторизации. */
export function requestTelegramWriteAccess(): Promise<boolean | null> {
  const app = window.Telegram?.WebApp
  if (
    !app?.initData ||
    !app.requestWriteAccess ||
    (app.isVersionAtLeast && !app.isVersionAtLeast('6.9'))
  )
    return Promise.resolve(null)
  if (app.initDataUnsafe?.user?.allows_write_to_pm) return Promise.resolve(true)
  return new Promise((resolve) => {
    try {
      app.requestWriteAccess!((granted) => resolve(granted))
    } catch {
      resolve(null)
    }
  })
}
