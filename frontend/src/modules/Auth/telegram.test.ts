import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { getTelegramInitData, requestTelegramWriteAccess } from './telegram'

beforeEach(() => sessionStorage.clear())
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

it('спрашивает разрешение сообщений один раз за сессию и допускает отказ', async () => {
  const request = vi.fn((callback: (granted: boolean) => void) => callback(false))
  vi.stubGlobal('Telegram', { WebApp: { initData: 'signed-data', requestWriteAccess: request } })
  await expect(requestTelegramWriteAccess()).resolves.toBe(false)
  await expect(requestTelegramWriteAccess()).resolves.toBeNull()
  expect(request).toHaveBeenCalledOnce()
})

it('не вызывает Telegram API без initData или в старом клиенте', async () => {
  const request = vi.fn()
  vi.stubGlobal('Telegram', { WebApp: { initData: '', requestWriteAccess: request } })
  await expect(requestTelegramWriteAccess()).resolves.toBeNull()
  vi.stubGlobal('Telegram', {
    WebApp: { initData: 'signed-data', requestWriteAccess: request, isVersionAtLeast: () => false },
  })
  await expect(requestTelegramWriteAccess()).resolves.toBeNull()
  expect(request).not.toHaveBeenCalled()
})
