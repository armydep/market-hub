import { HttpResponse, http } from 'msw'
import type { Coin } from '../api/types'
import {
  AUTH_RESPONSE,
  BLOCKED_EMAIL,
  CATALOG,
  COINS,
  LAST_UPDATED,
  LOCKED_EMAIL,
  REGISTERED_EMAIL,
  REGISTERED_PASSWORD,
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

    const matches = body.email.toLowerCase() === REGISTERED_EMAIL.toLowerCase()
      && body.password === REGISTERED_PASSWORD
    if (!matches) {
      return HttpResponse.json(
        { timestamp: new Date().toISOString(), status: 401, error: 'Unauthorized',
          message: 'Invalid email or password' },
        { status: 401 },
      )
    }

    return HttpResponse.json(AUTH_RESPONSE)
  }),
]
