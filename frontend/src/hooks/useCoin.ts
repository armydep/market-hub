import { useQuery } from '@tanstack/react-query'
import { fetchCoin } from '../api/client'
import { REFRESH_INTERVAL_MS } from './useCoins'

/**
 * The single-coin detail query. Keyed disjointly from `useCoins`'s
 * `['coins', params]` (different first element) so the two caches can never
 * collide with or evict each other.
 *
 * No `keepPreviousData`: that option smooths a paged dataset changing shape
 * across a key change. Here the key only changes across a full page
 * navigation (the page unmounts), and "a failed refresh keeps the last coin
 * on screen" already comes from TanStack Query retaining `data` across a
 * failed refetch regardless of this flag — the same mechanism `useCoins`
 * relies on for F001-FR-020.
 */
export function useCoin(symbol: string) {
  return useQuery({
    queryKey: ['coin', symbol],
    queryFn: ({ signal }) => fetchCoin(symbol, signal),
    enabled: symbol.trim() !== '',
    refetchInterval: REFRESH_INTERVAL_MS,
    refetchIntervalInBackground: false,
  })
}
