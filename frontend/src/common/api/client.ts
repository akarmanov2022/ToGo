import type { components } from './schema'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: components['schemas']['ApiError'] | null,
  ) {
    super(body?.message ?? `Ошибка сервера (${status})`)
    this.name = 'ApiError'
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Не удалось связаться с сервером. Проверьте подключение.')
    this.name = 'NetworkError'
  }
}

// Access token exists only in memory. Auth supplies the session hooks during bootstrap.
let getToken: () => string | null = () => null
let onUnauthorized: () => void = () => {}
export function configureApiSession(options: {
  getToken: () => string | null
  onUnauthorized: () => void
}) {
  getToken = options.getToken
  onUnauthorized = options.onUnauthorized
}

export async function apiRequest<T>(
  path: string,
  options: Omit<RequestInit, 'body'> & { body?: unknown; anonymous?: boolean } = {},
): Promise<T> {
  const { body, anonymous = false, ...init } = options
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (body !== undefined) headers.set('Content-Type', 'application/json')
  const token = anonymous ? null : getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let response: Response
  try {
    response = await fetch(path, {
      ...init,
      headers,
      credentials: 'omit',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (error) {
    if (error && typeof error === 'object' && 'name' in error && error.name === 'AbortError')
      throw error
    throw new NetworkError()
  }
  if (!response.ok) {
    const data: unknown = await response.json().catch((error: unknown) => {
      if (error && typeof error === 'object' && 'name' in error && error.name === 'AbortError')
        throw error
      return null
    })
    const errorBody = isApiError(data) ? data : null
    // Do not let a delayed 401 from an old session invalidate a newer session.
    if (response.status === 401 && !anonymous && token === getToken()) onUnauthorized()
    throw new ApiError(response.status, errorBody)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function isApiError(value: unknown): value is components['schemas']['ApiError'] {
  if (!value || typeof value !== 'object') return false
  return (
    'code' in value &&
    typeof value.code === 'string' &&
    'message' in value &&
    typeof value.message === 'string' &&
    'details' in value &&
    Array.isArray(value.details)
  )
}
