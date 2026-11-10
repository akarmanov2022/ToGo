import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getTelegramInitData } from './telegram'

describe('Telegram write access', () => {
  beforeEach(() => {
    sessionStorage.clear()
    delete window.Telegram
  })

  it('asks once when a real Mini App launch is available', () => {
    const requestWriteAccess = vi.fn()
    window.Telegram = {
      WebApp: {
        initData: 'signed-launch',
        platform: 'android',
        ready: vi.fn(),
        expand: vi.fn(),
        requestWriteAccess,
      },
    }

    getTelegramInitData()
    getTelegramInitData()

    expect(requestWriteAccess).toHaveBeenCalledTimes(1)
  })

  it('does not ask without initData', () => {
    const requestWriteAccess = vi.fn()
    window.Telegram = {
      WebApp: {
        initData: '',
        platform: 'unknown',
        ready: vi.fn(),
        expand: vi.fn(),
        requestWriteAccess,
      },
    }

    getTelegramInitData()

    expect(requestWriteAccess).not.toHaveBeenCalled()
  })
})
