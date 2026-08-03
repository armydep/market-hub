import {
  ApiError,
  type Account,
  type AccountPreferences,
  type AdminUser,
  type AdminUserPage,
  type AlertCondition,
  type ApiErrorBody,
  type AuthResponse,
  type Coin,
  type CoinPage,
  type ColumnCatalog,
  type PasswordResetResponse,
  type PriceAlert,
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

export async function patchJson<T>(
  path: string,
  body: unknown,
  signal?: AbortSignal,
  token?: string,
): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'PATCH',
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

export async function putJson<T>(
  path: string,
  body: unknown,
  signal?: AbortSignal,
  token?: string,
): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'PUT',
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

/**
 * For a 204-with-no-body response — `postJson`/`getJson` always call
 * `response.json()`, which fails on an empty body, so DELETE needs its own
 * helper rather than reusing them.
 */
export async function deleteRequest(path: string, signal?: AbortSignal, token?: string): Promise<void> {
  const response = await fetch(`${BASE}${path}`, {
    method: 'DELETE',
    signal,
    headers: { Accept: 'application/json', ...authHeader(token) },
  })
  await throwIfError(response)
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

export function fetchAdminUsers(page: number, token: string, signal?: AbortSignal): Promise<AdminUserPage> {
  return getJson<AdminUserPage>(`/admin/users?page=${page}`, signal, token)
}

export function blockUser(id: number, token: string, signal?: AbortSignal): Promise<AdminUser> {
  return postJson<AdminUser>(`/admin/users/${id}/block`, {}, signal, token)
}

export function unblockUser(id: number, token: string, signal?: AbortSignal): Promise<AdminUser> {
  return postJson<AdminUser>(`/admin/users/${id}/unblock`, {}, signal, token)
}

export function requestPasswordReset(email: string, signal?: AbortSignal): Promise<PasswordResetResponse> {
  return postJson<PasswordResetResponse>('/auth/password-reset/request', { email }, signal)
}

export function confirmPasswordReset(
  token: string,
  newPassword: string,
  signal?: AbortSignal,
): Promise<PasswordResetResponse> {
  return postJson<PasswordResetResponse>('/auth/password-reset/confirm', { token, newPassword }, signal)
}

export interface AlertInput {
  symbol: string
  condition: AlertCondition
  targetPrice: number
}

export interface AlertUpdateInput {
  condition: AlertCondition
  targetPrice: number
}

export function fetchActiveAlerts(token: string, signal?: AbortSignal): Promise<PriceAlert[]> {
  return getJson<PriceAlert[]>('/alerts', signal, token)
}

export function fetchTriggeredAlerts(token: string, signal?: AbortSignal): Promise<PriceAlert[]> {
  return getJson<PriceAlert[]>('/alerts/triggered', signal, token)
}

export function createAlert(body: AlertInput, token: string, signal?: AbortSignal): Promise<PriceAlert> {
  return postJson<PriceAlert>('/alerts', body, signal, token)
}

export function updateAlert(
  id: number,
  body: AlertUpdateInput,
  token: string,
  signal?: AbortSignal,
): Promise<PriceAlert> {
  return patchJson<PriceAlert>(`/alerts/${id}`, body, signal, token)
}

export function deleteAlert(id: number, token: string, signal?: AbortSignal): Promise<void> {
  return deleteRequest(`/alerts/${id}`, signal, token)
}

export function clearAlert(id: number, token: string, signal?: AbortSignal): Promise<PriceAlert> {
  return postJson<PriceAlert>(`/alerts/${id}/clear`, {}, signal, token)
}

export function fetchAccount(token: string, signal?: AbortSignal): Promise<Account> {
  return getJson<Account>('/account', signal, token)
}

export interface UpdateAccountInput {
  email: string
  currentPassword: string
}

export function updateAccount(body: UpdateAccountInput, token: string, signal?: AbortSignal): Promise<Account> {
  return patchJson<Account>('/account', body, signal, token)
}

export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

export async function changePassword(
  body: ChangePasswordInput,
  token: string,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${BASE}/account/password`, {
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
}

export function fetchAccountPreferences(token: string, signal?: AbortSignal): Promise<AccountPreferences> {
  return getJson<AccountPreferences>('/account/preferences', signal, token)
}

export function updateAccountPreferences(
  visibleColumns: string[],
  token: string,
  signal?: AbortSignal,
): Promise<AccountPreferences> {
  return putJson<AccountPreferences>('/account/preferences', { visibleColumns }, signal, token)
}
