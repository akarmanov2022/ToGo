export interface MockModeInput {
  dev: boolean
  envFlag: string | undefined
  hasInitData: boolean
  inTelegram?: boolean
  demo?: boolean
}

/**
 * В Telegram всегда используется настоящий API, даже в demo-сборке.
 * Моки разрешены только вне Telegram для разработки и отдельной demo-сборки.
 * Явный false отключает их; production не включает их ни при каком флаге.
 */
export function resolveMockMode({
  dev,
  envFlag,
  hasInitData,
  inTelegram,
  demo,
}: MockModeInput): boolean {
  if (hasInitData || inTelegram || envFlag === 'false') return false
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
    inTelegram: Boolean(
      window.Telegram?.WebApp?.platform && window.Telegram.WebApp.platform !== 'unknown',
    ),
  })
}
