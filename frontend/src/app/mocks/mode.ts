export interface MockModeInput {
  dev: boolean
  envFlag: string | undefined
  hasInitData: boolean
  demo?: boolean
}

/**
 * Пока авторизация через Telegram недоступна, локальная разработка без `initData`
 * предлагает кнопку входа через MSW-моки (демо-вход с баннером тестового режима).
 * Отдельная сборка --mode demo разрешает этот же сценарий на опубликованном сайте.
 * Явный `VITE_MSW_ENABLED=false` отключает автодовход и оставляет запросы настоящему
 * бэкенду, `true` включает моки даже внутри Telegram.
 */
export function resolveMockMode({ dev, envFlag, hasInitData, demo }: MockModeInput): boolean {
  if (demo) return true
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
  })
}
