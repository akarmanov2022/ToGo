import { afterEach, expect, it, vi } from 'vitest'
import { getTelegramInitData, requestTelegramWriteAccess } from './telegram'
afterEach(() => vi.unstubAllGlobals())
it('передаёт исходную строку initData без пересборки и подготавливает Mini App', () => {
  const ready = vi.fn(),
    expand = vi.fn()
  vi.stubGlobal('Telegram', {
    WebApp: { initData: 'query_id=raw%2Bvalue&hash=signed', ready, expand },
  })
  expect(getTelegramInitData()).toBe('query_id=raw%2Bvalue&hash=signed')
  expect(ready).toHaveBeenCalledOnce()
  expect(expand).toHaveBeenCalledOnce()
})
it('спрашивает разрешение сообщений и допускает отказ без ошибки авторизации', async () => {
  const request = vi.fn((callback: (granted: boolean) => void) => callback(false))
  vi.stubGlobal('Telegram', { WebApp: { initData: 'signed-data', requestWriteAccess: request } })
  await expect(requestTelegramWriteAccess()).resolves.toBe(false)
  expect(request).toHaveBeenCalledOnce()
})
it('не вызывает Telegram API в обычном браузере или старом клиенте', async () => {
  vi.stubGlobal('Telegram', undefined)
  await expect(requestTelegramWriteAccess()).resolves.toBeNull()
  const request = vi.fn()
  vi.stubGlobal('Telegram', {
    WebApp: { initData: 'signed-data', requestWriteAccess: request, isVersionAtLeast: () => false },
  })
  await expect(requestTelegramWriteAccess()).resolves.toBeNull()
  expect(request).not.toHaveBeenCalled()
})
