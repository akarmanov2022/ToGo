const TOKEN_KEY = 'togo.token'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    message: string,
    readonly details: Record<string, string> = {},
  ) {
    super(message)
  }
}

interface TelegramWindow {
  Telegram?: { WebApp?: { initData?: string } }
}

interface ErrorBody {
  code?: string
  message?: string
  details?: Record<string, string>
}

function readToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? import.meta.env.VITE_API_TOKEN ?? null
  } catch {
    return import.meta.env.VITE_API_TOKEN ?? null
  }
}

export function storeAccessToken(token: string): void {
  try {
    sessionStorage.setItem(TOKEN_KEY, token)
  } catch {
    // токен останется только на время этой вкладки
  }
}

export function clearAccessToken(): void {
  try {
    sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // ignore
  }
}

async function loginWithTelegram(): Promise<string | null> {
  const initData = (window as TelegramWindow).Telegram?.WebApp?.initData
  if (!initData) return null
  const response = await fetch('/api/v1/auth/telegram', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ initData }),
  })
  if (!response.ok) return null
  const { accessToken } = (await response.json()) as { accessToken: string }
  storeAccessToken(accessToken)
  return accessToken
}

async function parseFailure(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as ErrorBody | null
  return new ApiError(
    response.status,
    body?.code ?? null,
    body?.message ?? `HTTP ${response.status}`,
    body?.details ?? {},
  )
}

async function request(
  path: string,
  init: RequestInit = {},
  token: string | null = readToken(),
): Promise<Response> {
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return fetch(path, { ...init, headers })
}

async function send(path: string, init: RequestInit = {}): Promise<Response> {
  let response = await request(path, init)
  if (response.status === 401) {
    clearAccessToken()
    const token = await loginWithTelegram()
    if (token) response = await request(path, init, token)
  }
  return response
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await send(path)
  if (!response.ok) throw await parseFailure(response)
  return (await response.json()) as T
}

export async function apiSendJson<T>(
  path: string,
  method: 'POST' | 'PUT' | 'PATCH',
  body?: unknown,
): Promise<T> {
  const response = await send(path, {
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw await parseFailure(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export async function apiDelete(path: string): Promise<void> {
  const response = await send(path, { method: 'DELETE' })
  if (!response.ok) throw await parseFailure(response)
}
