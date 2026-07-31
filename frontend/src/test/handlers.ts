import { HttpResponse, http } from 'msw'
import type { Coin } from '../api/types'
import { CATALOG, COINS, LAST_UPDATED } from './fixtures'

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

function compare(a: Coin, b: Coin, field: string): number {
  const left = a[field as keyof Coin]
  const right = b[field as keyof Coin]
  if (left === null) return 1 // nulls last, matching the server
  if (right === null) return -1
  if (typeof left === 'number' && typeof right === 'number') return left - right
  return String(left).localeCompare(String(right))
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

  const sorted = filtered.sort((a, b) => {
    const result = compare(a, b, sort)
    return order === 'desc' ? -result : result
  })

  const start = page * size
  return {
    content: sorted.slice(start, start + size),
    page,
    size,
    totalElements: sorted.length,
    totalPages: Math.ceil(sorted.length / size),
    lastUpdatedAt: sorted.length > 0 ? LAST_UPDATED : null,
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
]
