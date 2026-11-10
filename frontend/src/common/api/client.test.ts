import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError, apiRequest, configureApiSession } from './client'

afterEach(() => {
  vi.unstubAllGlobals()
  configureApiSession({ getToken: () => null, onUnauthorized: () => {} })
})
describe('API client', () => {
  it('preserves cancellation while reading an error response', async () => {
    const expire = vi.fn()
    configureApiSession({ getToken: () => 'token', onUnauthorized: expire })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: () => Promise.reject(new DOMException('aborted', 'AbortError')),
      }),
    )
    await expect(apiRequest('/api/test')).rejects.toMatchObject({ name: 'AbortError' })
    expect(expire).not.toHaveBeenCalled()
  })
  it('sends bearer token without cookies and JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}'))
    vi.stubGlobal('fetch', fetchMock)
    configureApiSession({ getToken: () => 'test-token', onUnauthorized: vi.fn() })
    await apiRequest('/api/test', { method: 'POST', body: { title: 'test' } })
    const init = fetchMock.mock.calls[0]![1]
    expect(init.headers.get('Authorization')).toBe('Bearer test-token')
    expect(init.credentials).toBe('omit')
    expect(init.body).toBe('{"title":"test"}')
  })
  it('preserves field errors and invalidates session once on 401 without retry', async () => {
    const body = { code: 'UNAUTHORIZED', message: 'Войдите снова', details: [] }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 401 }))
    const expire = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    configureApiSession({ getToken: () => 'token', onUnauthorized: expire })
    await expect(apiRequest('/api/test')).rejects.toMatchObject({ status: 401, body })
    expect(expire).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledOnce()
  })
  it('does not invalidate session on rejected login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))
    const expire = vi.fn()
    configureApiSession({ getToken: () => 'token', onUnauthorized: expire })
    await expect(apiRequest('/api/test', { anonymous: true })).rejects.toBeInstanceOf(ApiError)
    expect(expire).not.toHaveBeenCalled()
  })
  it('handles empty 204 and non-JSON server errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
        .mockResolvedValueOnce(new Response('bad gateway', { status: 502 })),
    )
    await expect(apiRequest('/api/test')).resolves.toBeUndefined()
    await expect(apiRequest('/api/test')).rejects.toMatchObject({ status: 502, body: null })
  })
  it('distinguishes network failure and cancellation', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockRejectedValueOnce(new TypeError('offline'))
        .mockRejectedValueOnce(new DOMException('aborted', 'AbortError')),
    )
    await expect(apiRequest('/api/test')).rejects.toBeInstanceOf(NetworkError)
    await expect(apiRequest('/api/test')).rejects.toMatchObject({ name: 'AbortError' })
  })
})
