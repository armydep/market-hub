import {
  ApiError,
  type ApiErrorBody,
  type AuthResponse,
  type Coin,
  type CoinPage,
  type ColumnCatalog,
} from './types'

/**
 * Relative base: in development Vite proxies /api to the backend, and in a
 * production build the bundle is served alongside it. No configurable base URL
 * until there's a deployment story that needs one.
 */
const BASE = '/api'

function authHeader(token?: string): Record<string, string> {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * Every backend error carries the {timestamp,status,error,message} envelope,
 * but a proxy or gateway failure might not — fall back rather than throwing
 * a parse error that hides the real status. Shared by every request helper
 * below so the error shape never drifts between GET and POST.
 */
async function throwIfError(response: Response): Promise<void> {
  if (response.ok) return
  let body: ApiErrorBody | undefined
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    body = undefined
  }
  throw new ApiError(response.status, body?.message ?? `Request failed (${response.status})`, body)
}

export async function getJson<T>(path: string, signal?: AbortSignal, token?: string): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    signal,
    headers: { Accept: 'application/json', ...authHeader(token) },
  })
  await throwIfError(response)
  return (await response.json()) as T
}

export async function postJson<T>(
  path: string,
  body: unknown,
  signal?: AbortSignal,
  token?: string,
): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'POST',
    signal,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...authHeader(token),
    },
    body: JSON.stringify(body),
  })
  await throwIfError(response)
  return (await response.json()) as T
}

export interface CoinQuery {
  page: number
  size: number
  sort: string
  order: 'asc' | 'desc'
  q: string
}

/**
 * Build the query string for the coin list. Only non-default, meaningful values
 * are sent, which keeps the URL the server sees close to the one the user sees.
 */
export function coinsPath(query: CoinQuery): string {
  const params = new URLSearchParams()
  params.set('page', String(query.page))
  params.set('size', String(query.size))
  params.set('sort', query.sort)
  params.set('order', query.order)
  if (query.q.trim() !== '') {
    params.set('q', query.q.trim())
  }
  return `/market/coins?${params.toString()}`
}

export function fetchCoins(query: CoinQuery, signal?: AbortSignal): Promise<CoinPage> {
  return getJson<CoinPage>(coinsPath(query), signal)
}

export function fetchColumnCatalog(signal?: AbortSignal): Promise<ColumnCatalog> {
  return getJson<ColumnCatalog>('/market/columns', signal)
}

export function fetchCoin(symbol: string, signal?: AbortSignal): Promise<Coin> {
  return getJson<Coin>(`/market/coins/${encodeURIComponent(symbol)}`, signal)
}

export interface Credentials {
  email: string
  password: string
}

export function registerUser(credentials: Credentials, signal?: AbortSignal): Promise<AuthResponse> {
  return postJson<AuthResponse>('/auth/register', credentials, signal)
}

export function login(credentials: Credentials, signal?: AbortSignal): Promise<AuthResponse> {
  return postJson<AuthResponse>('/auth/login', credentials, signal)
}
