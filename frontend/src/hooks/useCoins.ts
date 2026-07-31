import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchCoins } from '../api/client'
import type { DashboardParams } from './useDashboardParams'

/** 60s: comfortably inside the backend's 180s poll, without pointless traffic. */
export const REFRESH_INTERVAL_MS = 60_000

/**
 * The coin list query.
 *
 * `keepPreviousData` keeps the previous page rendered while a new one loads, so
 * paging doesn't flash an empty grid. Separately, TanStack Query retains `data`
 * from the last successful fetch even when a later refetch errors — that, not
 * any bookkeeping of our own, is what satisfies F001-FR-020: callers render rows
 * whenever `data` exists and render the failure indicator independently when
 * `isError` is set. The two are never an either/or.
 */
export function useCoins(params: DashboardParams, enabled = true) {
  return useQuery({
    queryKey: ['coins', params],
    queryFn: ({ signal }) => fetchCoins(params, signal),
    // Gated on the catalog: without it `size` would fall back to a hardcoded
    // default and the very first request could carry a page size the server
    // doesn't support (MARKET_SUPPORTED_PAGE_SIZES is configurable), producing
    // a 400 before the catalog-driven retry succeeds.
    enabled,
    refetchInterval: REFRESH_INTERVAL_MS,
    // Don't poll a tab nobody is looking at.
    refetchIntervalInBackground: false,
    placeholderData: keepPreviousData,
  })
}
