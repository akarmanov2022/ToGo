import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { apiRequest, configureApiSession } from '@/common/api/client'
import { currentUserKey, useAuthStore } from '@/modules/Auth'
import { queryClient } from '../queryClient'
import { bindApiSession } from './session'
import type { components } from '@/common/api/schema'

const user: components['schemas']['CurrentUser'] = {
  id: '019a0000-0000-7000-8000-000000000101',
  tgId: 1,
  tgLogin: null,
  name: 'Анна',
  photoUrl: null,
  city: null,
  categories: [],
  createdEventsCount: 0,
  visitedEventsCount: 0,
}
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status })
const login = (token: string) =>
  json({ accessToken: token, tokenType: 'Bearer', expiresIn: 3600, user })
let auth: ReturnType<typeof useAuthStore>
let unsubscribe: () => void

beforeEach(() => {
  setActivePinia(createPinia())
  auth = useAuthStore()
  unsubscribe = bindApiSession(auth, queryClient)
})
afterEach(() => {
  auth.expire()
  unsubscribe()
  queryClient.clear()
  configureApiSession({ getToken: () => null, onUnauthorized: () => {} })
  vi.unstubAllGlobals()
})

describe('API / Auth / Query integration', () => {
  it('seeds the shared profile query from login instead of requesting me again for each screen', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(login('first-token'))
      .mockResolvedValueOnce(json(user))
    vi.stubGlobal('fetch', fetchMock)
    await auth.initialize('fresh-init-data')
    expect(queryClient.getQueryData(currentUserKey(user.id))).toEqual(user)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('clears the token, user and query cache on protected 401 without retry', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(login('first-token'))
      .mockResolvedValueOnce(json(user))
      .mockResolvedValueOnce(
        json({ code: 'UNAUTHORIZED', message: 'Сессия завершена', details: [] }, 401),
      )
    vi.stubGlobal('fetch', fetchMock)
    await auth.initialize('fresh-init-data')
    expect(auth.status).toBe('authenticated')
    queryClient.setQueryData(['profile-private'], user)
    await expect(
      queryClient.fetchQuery({
        queryKey: ['protected'],
        queryFn: () => apiRequest('/api/v1/events'),
      }),
    ).rejects.toBeDefined()
    expect(auth.status).toBe('expired')
    expect(auth.accessToken).toBeNull()
    expect(auth.user).toBeNull()
    expect(queryClient.getQueryData(['profile-private'])).toBeUndefined()
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('does not let an old request invalidate a new authenticated session', async () => {
    let rejectOld!: (response: Response) => void
    const oldResponse = new Promise<Response>((resolve) => {
      rejectOld = resolve
    })
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(login('old-token'))
        .mockResolvedValueOnce(json(user))
        .mockReturnValueOnce(oldResponse)
        .mockResolvedValueOnce(login('new-token'))
        .mockResolvedValueOnce(json(user)),
    )
    await auth.initialize('first-launch')
    const oldRequest = apiRequest('/api/v1/events')
    auth.expire()
    await auth.initialize('second-launch')
    rejectOld(json({ code: 'UNAUTHORIZED', message: 'Старая сессия', details: [] }, 401))
    await expect(oldRequest).rejects.toMatchObject({ status: 401 })
    expect(auth.status).toBe('authenticated')
    expect(auth.accessToken).toBe('new-token')
  })
})
