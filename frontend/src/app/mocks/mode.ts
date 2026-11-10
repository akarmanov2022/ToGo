export interface MockModeInput {
  dev: boolean
  envFlag: string | undefined
  hasInitData: boolean
  inTelegram?: boolean
  demo?: boolean
}

/**
 * Локальная разработка без `initData` предлагает вход через MSW-моки.
 * Демо-сборка разрешает его и на опубликованном сайте, но при запуске из Telegram
 * всегда использует настоящий бэкенд и проверенные данные запуска.
 * Явный `VITE_MSW_ENABLED=false` отключает автодовход и оставляет запросы настоящему
 * бэкенду, `true` включает моки в локальной разработке.
 */
export function resolveMockMode({ dev, envFlag, hasInitData, inTelegram, demo }: MockModeInput): boolean {
  if (demo) return !hasInitData && !inTelegram
  if (!dev) return false
  if (envFlag === 'true') return true
  if (envFlag === 'false') return false
  return !hasInitData
}

/** initData для локального демо-входа; MSW принимает только это значение. */
export const MOCK_INIT_DATA = 'local-msw-init-data'

export function shouldUseMocks(): boolean {
  return resolveMockMode({
    dev: import.meta.env.DEV,
    demo: import.meta.env.MODE === 'demo',
    envFlag: import.meta.env.VITE_MSW_ENABLED,
    hasInitData: Boolean(window.Telegram?.WebApp?.initData),
    inTelegram: Boolean(window.Telegram?.WebApp?.platform && window.Telegram.WebApp.platform !== 'unknown'),
  })
}
