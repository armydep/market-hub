import { HttpResponse, http } from 'msw'
import type { Account, AdminUser, AlertCondition, Coin, PriceAlert } from '../api/types'
import {
  ACCOUNT,
  ACTIVE_ALERT,
  ADMIN_USERS,
  AUTH_RESPONSE,
  BLOCKED_EMAIL,
  CATALOG,
  COINS,
  LAST_UPDATED,
  LOCKED_EMAIL,
  OTHER_REGISTERED_EMAIL,
  PASSWORD_RESET_CONFIRM_MESSAGE,
  PASSWORD_RESET_REQUEST_MESSAGE,
  REGISTERED_EMAIL,
  REGISTERED_PASSWORD,
  TRIGGERED_ALERT,
  VALID_RESET_TOKEN,
} from './fixtures'

/**
 * Every /market/coins request the app made, in order. Tests assert against
 * these rather than only against the DOM: rendered output alone cannot
 * distinguish "the server sorted" from "the client re-sorted the page it
 * already had", which is exactly the F001-FR-011 claim.
 */
export const recordedRequests: URL[] = []

export function lastRequest(): URL {
  const url = recordedRequests.at(-1)
  if (!url) throw new Error('No /market/coins request was made')
  return url
}

export function resetRecordedRequests() {
  recordedRequests.length = 0
}

/** Fails the next N coin requests, so a refresh failure can be simulated. */
let failuresRemaining = 0
export function failNextCoinRequests(count: number) {
  failuresRemaining = count
}
export function resetFailures() {
  failuresRemaining = 0
}

/** Every /market/coins/:symbol request the app made, in order. */
export const recordedSymbolRequests: URL[] = []

export function lastSymbolRequest(): URL {
  const url = recordedSymbolRequests.at(-1)
  if (!url) throw new Error('No /market/coins/:symbol request was made')
  return url
}

export function resetRecordedSymbolRequests() {
  recordedSymbolRequests.length = 0
}

/** Fails the next N single-coin requests, so a detail-page refresh failure can be simulated. */
let symbolFailuresRemaining = 0
export function failNextCoinDetailRequests(count: number) {
  symbolFailuresRemaining = count
}
export function resetSymbolFailures() {
  symbolFailuresRemaining = 0
}

/** Every /auth/register request the app made, in order. */
export const recordedAuthRegisterRequests: unknown[] = []
export function lastAuthRegisterRequest(): unknown {
  const body = recordedAuthRegisterRequests.at(-1)
  if (!body) throw new Error('No /auth/register request was made')
  return body
}
export function resetRecordedAuthRegisterRequests() {
  recordedAuthRegisterRequests.length = 0
}

let authRegisterFailuresRemaining = 0
export function failNextAuthRegisterRequests(count: number) {
  authRegisterFailuresRemaining = count
}
export function resetAuthRegisterFailures() {
  authRegisterFailuresRemaining = 0
}

/** Every /auth/login request the app made, in order. */
export const recordedAuthLoginRequests: unknown[] = []
export function lastAuthLoginRequest(): unknown {
  const body = recordedAuthLoginRequests.at(-1)
  if (!body) throw new Error('No /auth/login request was made')
  return body
}
export function resetRecordedAuthLoginRequests() {
  recordedAuthLoginRequests.length = 0
}

let authLoginFailuresRemaining = 0
export function failNextAuthLoginRequests(count: number) {
  authLoginFailuresRemaining = count
}
export function resetAuthLoginFailures() {
  authLoginFailuresRemaining = 0
}

/**
 * A mutable working copy of ADMIN_USERS: block/unblock handlers below flip
 * `blocked` on this array so a test can see the row actually change, without
 * mutating the shared fixture itself.
 */
let adminUsers: AdminUser[] = ADMIN_USERS.map((u) => ({ ...u }))
export function resetAdminUsers() {
  adminUsers = ADMIN_USERS.map((u) => ({ ...u }))
}

/**
 * A mutable working copy of the alert fixtures: create/update/delete/clear
 * handlers below mutate this array so a test can see the row actually change,
 * without mutating the shared fixtures themselves.
 */
let alerts: PriceAlert[] = [{ ...ACTIVE_ALERT }, { ...TRIGGERED_ALERT }]
let nextAlertId = 200
export function resetAlerts() {
  alerts = [{ ...ACTIVE_ALERT }, { ...TRIGGERED_ALERT }]
  nextAlertId = 200
}

/**
 * A mutable working copy of ACCOUNT plus a "current password" the account/
 * password handlers check against, mirroring the admin-users mutable-array
 * pattern: a test needs to see the row actually change without mutating the
 * shared fixture itself.
 */
let account: Account = { ...ACCOUNT }
let currentPassword = REGISTERED_PASSWORD
/** null = never saved; falls back to CATALOG.defaultVisible, mirroring the real endpoint. */
let savedPreferences: string[] | null = null
export function resetAccount() {
  account = { ...ACCOUNT }
  currentPassword = REGISTERED_PASSWORD
  savedPreferences = null
}

/** Mirrors com.am.market_hub.alert.domain.AlertCondition#isSatisfiedBy. */
function isSatisfiedBy(condition: AlertCondition, price: number, target: number): boolean {
  return condition === 'ABOVE_OR_EQUAL' ? price >= target : price <= target
}

function compare(a: Coin, b: Coin, field: string): number {
  const left = a[field as keyof Coin]
  const right = b[field as keyof Coin]
  if (typeof left === 'number' && typeof right === 'number') return left - right
  return String(left).localeCompare(String(right))
}

/**
 * Sort with nulls pinned last in BOTH directions, matching the server's
 * explicit `.nullsLast()`. Null handling has to sit outside the direction flip:
 * negating a comparator that already pushed nulls down would surface them
 * first on `desc`, which is the exact bug the server-side hardening fixed.
 */
function sortCoins(coins: Coin[], field: string, order: string): Coin[] {
  const descending = order === 'desc'
  return coins.sort((a, b) => {
    const left = a[field as keyof Coin]
    const right = b[field as keyof Coin]
    if (left === null && right === null) return 0
    if (left === null) return 1
    if (right === null) return -1
    const result = compare(a, b, field)
    return descending ? -result : result
  })
}

/** Mirrors the real endpoint: filter, then sort, then page — in that order. */
function buildPage(url: URL) {
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? CATALOG.defaultPageSize)
  const sort = url.searchParams.get('sort') ?? 'marketCapRank'
  const order = url.searchParams.get('order') ?? 'asc'
  const q = (url.searchParams.get('q') ?? '').toLowerCase()

  const filtered = q
    ? COINS.filter(
        (c) => c.name.toLowerCase().includes(q) || c.symbol.toLowerCase().includes(q),
      )
    : [...COINS]

  const sorted = sortCoins(filtered, sort, order)

  const start = page * size
  return {
    content: sorted.slice(start, start + size),
    page,
    size,
    totalElements: sorted.length,
    totalPages: Math.ceil(sorted.length / size),
    // Freshness is a property of the last poll, not of the current query: the
    // real server reads MAX(updated_at) over the whole universe, so a search
    // that matches nothing still carries a timestamp. Tying this to the
    // filtered count would teach the client the wrong contract.
    lastUpdatedAt: COINS.length > 0 ? LAST_UPDATED : null,
  }
}

export const handlers = [
  http.get('/api/market/columns', () => HttpResponse.json(CATALOG)),

  http.get('/api/market/coins', ({ request }) => {
    const url = new URL(request.url)
    recordedRequests.push(url)

    if (failuresRemaining > 0) {
      failuresRemaining -= 1
      return HttpResponse.json(
        {
          timestamp: new Date().toISOString(),
          status: 500,
          error: 'Internal Server Error',
          message: 'Upstream unavailable',
        },
        { status: 500 },
      )
    }

    return HttpResponse.json(buildPage(url))
  }),

  http.get('/api/market/coins/:symbol', ({ request, params }) => {
    const url = new URL(request.url)
    recordedSymbolRequests.push(url)

    if (symbolFailuresRemaining > 0) {
      symbolFailuresRemaining -= 1
      return HttpResponse.json(
        {
          timestamp: new Date().toISOString(),
          status: 500,
          error: 'Internal Server Error',
          message: 'Upstream unavailable',
        },
        { status: 500 },
      )
    }

    // Case-insensitive, mirroring the backend's confirmed
    // getBySymbolIsCaseInsensitive behavior.
    const requested = String(params.symbol).toUpperCase()
    const coin = COINS.find((c) => c.symbol.toUpperCase() === requested)
    if (!coin) {
      return HttpResponse.json(
        {
          timestamp: new Date().toISOString(),
          status: 404,
          error: 'Not Found',
          message: `Unknown symbol: ${params.symbol}`,
        },
        { status: 404 },
      )
    }

    return HttpResponse.json(coin)
  }),

  http.post('/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string }
    recordedAuthRegisterRequests.push(body)

    if (authRegisterFailuresRemaining > 0) {
      authRegisterFailuresRemaining -= 1
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 500, error: 'Internal Server Error',
          message: 'Upstream unavailable' },
        { status: 500 },
      )
    }

    if (body.email.toLowerCase() === REGISTERED_EMAIL.toLowerCase()) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 409, error: 'Conflict',
          message: 'Email already registered' },
        { status: 409 },
      )
    }

    return HttpResponse.json(
      { ...AUTH_RESPONSE, email: body.email.toLowerCase() },
      { status: 201 },
    )
  }),

  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string }
    recordedAuthLoginRequests.push(body)

    if (authLoginFailuresRemaining > 0) {
      authLoginFailuresRemaining -= 1
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 500, error: 'Internal Server Error',
          message: 'Upstream unavailable' },
        { status: 500 },
      )
    }

    if (body.email.toLowerCase() === BLOCKED_EMAIL.toLowerCase()) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 403, error: 'Forbidden',
          message: 'Account is blocked' },
        { status: 403 },
      )
    }

    if (body.email.toLowerCase() === LOCKED_EMAIL.toLowerCase()) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 403, error: 'Forbidden',
          message: 'Account temporarily locked, try again later' },
        { status: 403 },
      )
    }

    // Checked against the mutable account/currentPassword state, not the fixed
    // constants, so the S8 email/password-change handlers below can be
    // verified end-to-end by logging in again afterward.
    const matches = body.email.toLowerCase() === account.email.toLowerCase()
      && body.password === currentPassword
    if (!matches) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 401, error: 'Unauthorized',
          message: 'Invalid email or password' },
        { status: 401 },
      )
    }

    return HttpResponse.json(AUTH_RESPONSE)
  }),

  http.get('/api/admin/users', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? 0)
    const size = 20
    const start = page * size
    return HttpResponse.json({
      content: adminUsers.slice(start, start + size),
      page,
      size,
      totalElements: adminUsers.length,
      totalPages: Math.max(1, Math.ceil(adminUsers.length / size)),
    })
  }),

  http.post('/api/admin/users/:id/block', ({ params }) => {
    const user = adminUsers.find((u) => u.id === Number(params.id))
    if (!user) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 404, error: 'Not Found',
          message: `Unknown user: ${params.id}` },
        { status: 404 },
      )
    }
    user.blocked = true
    return HttpResponse.json(user)
  }),

  http.post('/api/admin/users/:id/unblock', ({ params }) => {
    const user = adminUsers.find((u) => u.id === Number(params.id))
    if (!user) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 404, error: 'Not Found',
          message: `Unknown user: ${params.id}` },
        { status: 404 },
      )
    }
    user.blocked = false
    return HttpResponse.json(user)
  }),

  http.post('/api/auth/password-reset/request', () => HttpResponse.json({ message: PASSWORD_RESET_REQUEST_MESSAGE })),

  http.post('/api/auth/password-reset/confirm', async ({ request }) => {
    const body = (await request.json()) as { token: string; newPassword: string }
    if (body.token !== VALID_RESET_TOKEN) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: 'Invalid or expired token' },
        { status: 400 },
      )
    }
    return HttpResponse.json({ message: PASSWORD_RESET_CONFIRM_MESSAGE })
  }),

  http.get('/api/alerts', () => HttpResponse.json(alerts.filter((a) => a.active))),

  http.get('/api/alerts/triggered', () =>
    HttpResponse.json(alerts.filter((a) => !a.active && !a.clearedAt)),
  ),

  http.post('/api/alerts', async ({ request }) => {
    const body = (await request.json()) as {
      symbol: string
      condition: AlertCondition
      targetPrice: number
    }

    const coin = COINS.find((c) => c.symbol.toUpperCase() === body.symbol.toUpperCase())
    if (!coin || coin.price === null) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: `Unknown symbol: ${body.symbol}` },
        { status: 400 },
      )
    }

    if (isSatisfiedBy(body.condition, coin.price, body.targetPrice)) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: 'Condition is already satisfied by the current price' },
        { status: 400 },
      )
    }

    const created: PriceAlert = {
      id: nextAlertId++,
      symbol: coin.symbol,
      condition: body.condition,
      targetPrice: body.targetPrice,
      active: true,
      triggeredAt: null,
      triggeredPrice: null,
      clearedAt: null,
      createdAt: new Date().toISOString(),
    }
    alerts.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.patch('/api/alerts/:id', async ({ request, params }) => {
    const body = (await request.json()) as { condition: AlertCondition; targetPrice: number }
    const alert = alerts.find((a) => a.id === Number(params.id))
    if (!alert || !alert.active) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 404, error: 'Not Found',
          message: `Unknown alert: ${params.id}` },
        { status: 404 },
      )
    }

    const coin = COINS.find((c) => c.symbol.toUpperCase() === alert.symbol.toUpperCase())
    if (coin && coin.price !== null && isSatisfiedBy(body.condition, coin.price, body.targetPrice)) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: 'Condition is already satisfied by the current price' },
        { status: 400 },
      )
    }

    alert.condition = body.condition
    alert.targetPrice = body.targetPrice
    return HttpResponse.json(alert)
  }),

  http.delete('/api/alerts/:id', ({ params }) => {
    const index = alerts.findIndex((a) => a.id === Number(params.id) && a.active)
    if (index === -1) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 404, error: 'Not Found',
          message: `Unknown alert: ${params.id}` },
        { status: 404 },
      )
    }
    alerts.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/alerts/:id/clear', ({ params }) => {
    const alert = alerts.find((a) => a.id === Number(params.id))
    if (!alert || alert.active || alert.clearedAt) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 404, error: 'Not Found',
          message: `Unknown alert: ${params.id}` },
        { status: 404 },
      )
    }
    alert.clearedAt = new Date().toISOString()
    return HttpResponse.json(alert)
  }),

  http.get('/api/account', () => HttpResponse.json(account)),

  http.patch('/api/account', async ({ request }) => {
    const body = (await request.json()) as { email: string; currentPassword: string }
    if (body.currentPassword !== currentPassword) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: 'Current password is incorrect' },
        { status: 400 },
      )
    }
    if (body.email.toLowerCase() === OTHER_REGISTERED_EMAIL.toLowerCase()) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 409, error: 'Conflict',
          message: 'Email already registered' },
        { status: 409 },
      )
    }
    account = { ...account, email: body.email.toLowerCase() }
    return HttpResponse.json(account)
  }),

  http.post('/api/account/password', async ({ request }) => {
    const body = (await request.json()) as { currentPassword: string; newPassword: string }
    if (body.currentPassword !== currentPassword) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: 'Current password is incorrect' },
        { status: 400 },
      )
    }
    currentPassword = body.newPassword
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/account/preferences', () =>
    HttpResponse.json({ visibleColumns: savedPreferences ?? CATALOG.defaultVisible }),
  ),

  http.put('/api/account/preferences', async ({ request }) => {
    const body = (await request.json()) as { visibleColumns: string[] }
    const unknown = body.visibleColumns.filter((key) => !CATALOG.supported.includes(key))
    if (unknown.length > 0) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 400, error: 'Bad Request',
          message: `Unknown column key(s): ${unknown.join(', ')}` },
        { status: 400 },
      )
    }
    savedPreferences = body.visibleColumns
    return HttpResponse.json({ visibleColumns: savedPreferences })
  }),
]
