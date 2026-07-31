import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

export interface DashboardParams {
  page: number
  size: number
  sort: string
  order: 'asc' | 'desc'
  q: string
}

export const DEFAULT_SORT = 'marketCapRank'
export const DEFAULT_ORDER: 'asc' | 'desc' = 'asc'

/**
 * The URL query string is the single source of truth for the dashboard view.
 *
 * No component holds an independent copy: two sources of truth for the same
 * value is how "refresh preserved four of the five things" bugs happen. (The
 * one deliberate exception is the toolbar's search input, which mirrors `q`
 * into local state purely so typing feels immediate while the URL update is
 * debounced — the URL still wins on every render.) It also
 * makes F001-FR-018 structural — a refetch never touches the URL, so page, size,
 * sort, order and search survive by construction — and makes a view shareable
 * by link.
 *
 * Visible columns deliberately live elsewhere (see columnsStore): they're a user
 * preference, not part of the view's address.
 */
export function useDashboardParams(defaultSize: number, supportedSizes?: number[]) {
  const [searchParams, setSearchParams] = useSearchParams()

  const params = useMemo<DashboardParams>(() => {
    const rawPage = Number(searchParams.get('page'))
    const rawSize = Number(searchParams.get('size'))
    const order = searchParams.get('order')
    // The URL is deliberately shareable, so `size` is attacker/typo-reachable.
    // An unsupported value would be a server 400 *and* would leave the
    // page-size <select> showing a value matching none of its options, so fall
    // back to the default rather than forwarding it.
    const sizeIsUsable =
      Number.isInteger(rawSize) &&
      rawSize > 0 &&
      (supportedSizes === undefined || supportedSizes.includes(rawSize))
    return {
      page: Number.isInteger(rawPage) && rawPage > 0 ? rawPage : 0,
      size: sizeIsUsable ? rawSize : defaultSize,
      sort: searchParams.get('sort') ?? DEFAULT_SORT,
      order: order === 'desc' ? 'desc' : DEFAULT_ORDER,
      q: searchParams.get('q') ?? '',
    }
  }, [searchParams, defaultSize, supportedSizes])

  const update = useCallback(
    (patch: Partial<DashboardParams>) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(patch)) {
            if (value === '' || value === undefined || value === null) {
              next.delete(key)
            } else {
              next.set(key, String(value))
            }
          }
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  /** Any change to what's being listed must return to the first page. */
  const updateAndResetPage = useCallback(
    (patch: Partial<DashboardParams>) => update({ ...patch, page: 0 }),
    [update],
  )

  return { params, update, updateAndResetPage }
}
