import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '@/common/api/client'
import { useAuthStore } from './session'
import { authenticateTelegram, getCurrentUser } from './api'

vi.mock('./api', () => ({ authenticateTelegram: vi.fn(), getCurrentUser: vi.fn() }))
const user = {
  id: 'test',
  tgId: 1,
  tgLogin: null,
  name: 'Test',
  photoUrl: null,
  city: null,
  categories: [],
  createdEventsCount: 0,
  visitedEventsCount: 0,
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
  vi.mocked(authenticateTelegram)
    .mockReset()
    .mockResolvedValue({ accessToken: 'test-token', expiresIn: 3600, tokenType: 'Bearer', user })
  vi.mocked(getCurrentUser).mockReset().mockResolvedValue(user)
})
afterEach(() => vi.useRealTimers())

describe('Mini App session', () => {
  it('aborts bootstrap on expiry and does not restore a late user response', async () => {
    let resolveProfile!: (value: typeof user) => void
    vi.mocked(getCurrentUser).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveProfile = resolve
        }),
    )
    const auth = useAuthStore()
    const pending = auth.initialize('fresh')
    await Promise.resolve()
    const signal = vi.mocked(getCurrentUser).mock.calls[0]?.[0]
    auth.expire()
    expect(signal?.aborted).toBe(true)
    resolveProfile(user)
    await pending
    expect(auth.user).toBeNull()
    expect(auth.status).toBe('expired')
  })
  it('does not request login outside Telegram', async () => {
    const auth = useAuthStore()
    await auth.initialize('')
    expect(auth.status).toBe('outside')
    expect(authenticateTelegram).not.toHaveBeenCalled()
  })
  it('exchanges unmodified initData and loads current user', async () => {
    const auth = useAuthStore()
    await auth.initialize('user=%7Btest%7D&hash=123')
    expect(authenticateTelegram).toHaveBeenCalledWith(
      'user=%7Btest%7D&hash=123',
      expect.any(AbortSignal),
    )
    expect(auth.accessToken).toBe('test-token')
    expect(auth.user).toEqual(user)
    expect(auth.status).toBe('authenticated')
    await auth.initialize('duplicate')
    expect(authenticateTelegram).toHaveBeenCalledOnce()
  })
  it('clears session at token expiry', async () => {
    const auth = useAuthStore()
    await auth.initialize('valid')
    vi.advanceTimersByTime(3600000)
    expect(auth.status).toBe('expired')
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toBeNull()
  })
  it('does not retry stale initData automatically', async () => {
    vi.mocked(authenticateTelegram).mockRejectedValue(new ApiError(401, null))
    const auth = useAuthStore()
    await auth.initialize('stale')
    expect(auth.status).toBe('expired')
    expect(authenticateTelegram).toHaveBeenCalledOnce()
  })
  it('allows a manual retry after server failure', async () => {
    vi.mocked(authenticateTelegram).mockRejectedValueOnce(new Error('Сервер недоступен'))
    const auth = useAuthStore()
    await auth.initialize('valid')
    expect(auth.status).toBe('error')
    await auth.initialize('valid')
    expect(auth.status).toBe('authenticated')
  })
  it('re-authenticates after reload without persisted token', async () => {
    await useAuthStore().initialize('fresh')
    setActivePinia(createPinia())
    const reloaded = useAuthStore()
    expect(reloaded.accessToken).toBeNull()
    await reloaded.initialize('fresh')
    expect(authenticateTelegram).toHaveBeenCalledTimes(2)
  })
})
