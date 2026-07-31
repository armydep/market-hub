import { QueryClient } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { App } from '../App'
import { HttpResponse, http } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { formatTimestamp } from '../format'
import { REFRESH_INTERVAL_MS } from '../hooks/useCoins'
import { useColumnsStore } from '../store/columnsStore'
import { LAST_UPDATED } from '../test/fixtures'
import { failNextCoinRequests, lastRequest, recordedRequests } from '../test/handlers'
import { renderApp } from '../test/renderApp'
import { server } from '../test/setup'

async function rowSymbols(): Promise<string[]> {
  const table = await screen.findByRole('table')
  const rows = within(table).getAllByRole('row').slice(1) // drop the header row
  return rows.map((row) => within(row).getAllByRole('cell')[2].textContent ?? '')
}

describe('Public Market Dashboard', () => {
  it('renders the universe with the server default columns and page size', async () => {
    renderApp()

    expect(await screen.findByRole('table')).toBeInTheDocument()
    // Catalog default is 5 columns; the grid must follow it, not invent its own.
    const headers = screen.getAllByRole('columnheader')
    expect(headers).toHaveLength(5)
    expect(await rowSymbols()).toHaveLength(12)
    // 50 is the fixture catalog's default; 20 is the client's fallback.
    // Asserting 50 is what proves the client follows the server.
    expect(lastRequest().searchParams.get('size')).toBe('50')
  })

  it('shows the last successful update time', async () => {
    renderApp()
    const stamp = await screen.findByTestId('last-updated')
    // Asserting the actual formatted value, not merely "not the placeholder":
    // 'Invalid Date', 'undefined' and a raw ISO string would all clear that bar.
    await waitFor(() => expect(stamp).toHaveTextContent(formatTimestamp(LAST_UPDATED)))
  })

  it('renders monetary values as USD (F001-FR-022)', async () => {
    renderApp('/?size=5')
    const table = await screen.findByRole('table')

    const firstRow = within(table).getAllByRole('row')[1]
    const cells = within(firstRow).getAllByRole('cell')
    // Column order is rank, name, symbol, price, 24h% — price is index 3.
    expect(cells[3]).toHaveTextContent('$60,000.00')
  })

  it('reports totals for the whole dataset, not the current page', async () => {
    renderApp('/?size=5')
    await screen.findByRole('table')

    expect(await screen.findByTestId('page-summary')).toHaveTextContent('12 coins')
  })

  it('renders an empty-universe state distinct from the no-results state', async () => {
    server.use(
      http.get('/api/market/coins', () =>
        HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          lastUpdatedAt: null,
        }),
      ),
    )
    renderApp()

    expect(await screen.findByText(/no market data yet/i)).toBeInTheDocument()
    // The search-empty wording must not leak into the never-polled case.
    expect(screen.queryByText(/no results found/i)).not.toBeInTheDocument()
  })

  describe('sorting', () => {
    it('asks the server to sort rather than reordering the current page', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')

      await user.click(screen.getByRole('button', { name: /sort by price/i }))

      // The request is the assertion that matters: a client-side sort of the
      // already-fetched rows would leave the query string untouched.
      await waitFor(() => {
        expect(lastRequest().searchParams.get('sort')).toBe('price')
        expect(lastRequest().searchParams.get('order')).toBe('asc')
      })
      // Sort state must reach assistive tech, on the columnheader itself.
      await waitFor(() =>
        expect(screen.getByRole('columnheader', { name: /price/i })).toHaveAttribute(
          'aria-sort',
          'ascending',
        ),
      )
    })

    it('toggles to descending on a second click and reflects the server order', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')

      const priceHeader = screen.getByRole('button', { name: /sort by price/i })
      await user.click(priceHeader)
      await waitFor(() => expect(lastRequest().searchParams.get('order')).toBe('asc'))
      await user.click(priceHeader)

      await waitFor(() => expect(lastRequest().searchParams.get('order')).toBe('desc'))
      // BTC is the priciest coin in the fixture, so it must lead a desc sort.
      await waitFor(async () => expect((await rowSymbols())[0]).toBe('BTC'))
    })

    it('sorts the whole dataset, not just the rows already on screen', async () => {
      // The discriminating case for F001-FR-011. Page 0 sorted by rank holds
      // BTC, ETH, XRP, ADA, SOL. Sorting *those five* by price ascending gives
      // ADA first; sorting the whole 12-coin dataset gives DOGE (the globally
      // cheapest, which isn't on page 0 at all). Only a server-side sort can
      // produce DOGE, so this fails if the grid ever sorts locally.
      const { user } = renderApp('/?size=5')
      await screen.findByRole('table')
      expect(await rowSymbols()).toEqual(['BTC', 'ETH', 'XRP', 'ADA', 'SOL'])

      await user.click(screen.getByRole('button', { name: /sort by price/i }))

      // Globally price-ascending page 0. Sorting only the five rows already on
      // screen would instead yield ADA, XRP, SOL, ETH, BTC.
      await waitFor(async () =>
        expect(await rowSymbols()).toEqual(['DOGE', 'ADA', 'XRP', 'MATIC', 'DOT']),
      )
    })

    it('returns to the first page when the sort changes', async () => {
      const { user } = renderApp('/?page=1&size=5')
      await screen.findByRole('table')

      await user.click(screen.getByRole('button', { name: /sort by price/i }))

      await waitFor(() => expect(lastRequest().searchParams.get('page')).toBe('0'))
    })
  })

  describe('search', () => {
    it('sends the term to the server and shows matching rows', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')

      // "coin" appears only in names (Bitcoin, Dogecoin, Litecoin), never in a
      // symbol, so a pass here can only come from name matching.
      await user.type(screen.getByLabelText(/search/i), 'coin')

      await waitFor(() => expect(lastRequest().searchParams.get('q')).toBe('coin'))
      // Exact set, not arrayContaining: the loose form would also pass if the
      // server ignored `q` and returned all twelve coins.
      await waitFor(async () =>
        expect((await rowSymbols()).sort()).toEqual(['BTC', 'DOGE', 'LTC']),
      )
    })

    it('matches on symbol too', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')

      // Discriminates via LTC: "bitcoin" does contain "tc", so BTC could match
      // on name, but no fixture name contains "ltc" — only the symbol does.
      await user.type(screen.getByLabelText(/search/i), 'TC')

      await waitFor(() => expect(lastRequest().searchParams.get('q')).toBe('TC'))
      await waitFor(async () => expect((await rowSymbols()).sort()).toEqual(['BTC', 'LTC']))
    })

    it('renders "No results found" for a term that matches nothing', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')

      await user.type(screen.getByLabelText(/search/i), 'zzzznotacoin')

      expect(await screen.findByText(/no results found/i)).toBeInTheDocument()
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
    })

    it('restores the full dataset when the search is cleared', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')
      const input = screen.getByLabelText(/search/i)

      await user.type(input, 'zzzznotacoin')
      await screen.findByText(/no results found/i)
      await user.clear(input)

      await waitFor(async () => expect(await rowSymbols()).toHaveLength(12))
      expect(lastRequest().searchParams.has('q')).toBe(false)
    })
  })

  describe('pagination', () => {
    it('pages through the dataset server-side', async () => {
      const { user } = renderApp('/?size=5')
      await screen.findByRole('table')
      expect(await rowSymbols()).toHaveLength(5)

      await user.click(screen.getByRole('button', { name: /next/i }))

      await waitFor(() => expect(lastRequest().searchParams.get('page')).toBe('1'))
      await waitFor(async () => expect((await rowSymbols())[0]).toBe('DOGE'))
    })

    it('returns to the first page when the page size changes', async () => {
      const { user } = renderApp('/?page=1&size=5')
      await screen.findByRole('table')

      await user.selectOptions(screen.getByLabelText(/rows/i), '20')

      await waitFor(() => {
        expect(lastRequest().searchParams.get('size')).toBe('20')
        expect(lastRequest().searchParams.get('page')).toBe('0')
      })
    })

    it('offers exactly the page sizes the server supports', async () => {
      renderApp()
      await screen.findByRole('table')

      const options = within(screen.getByLabelText(/rows/i)).getAllByRole('option')
      expect(options.map((o) => o.textContent)).toEqual(['2', '5', '20', '50'])
    })
  })

  describe('refresh', () => {
    it('preserves page, size, sort and search across a manual refresh', async () => {
      // q=o matches 8 fixture coins, so page 1 at size 5 genuinely has rows —
      // a narrower term would leave an empty page and test nothing.
      const { user } = renderApp('/?page=1&size=5&sort=price&order=desc&q=o')
      await screen.findByRole('table')
      const before = recordedRequests.length

      await user.click(screen.getByRole('button', { name: /^refresh$/i }))

      await waitFor(() => expect(recordedRequests.length).toBeGreaterThan(before))
      const params = lastRequest().searchParams
      expect(params.get('page')).toBe('1')
      expect(params.get('size')).toBe('5')
      expect(params.get('sort')).toBe('price')
      expect(params.get('order')).toBe('desc')
      expect(params.get('q')).toBe('o')
    })

    it('refetches automatically on the configured interval', async () => {
      // Fake timers must be installed *before* render: React Query schedules
      // the interval when the query mounts, and a timer created under real
      // timers can't be advanced by fakes installed afterwards.
      vi.useFakeTimers()
      try {
        const queryClient = new QueryClient({
          defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
        })
        const { unmount } = render(
          <MemoryRouter initialEntries={['/']}>
            <App queryClient={queryClient} />
          </MemoryRouter>,
        )

        // Drive the catalog fetch and the first coin fetch to completion.
        await vi.advanceTimersByTimeAsync(100)
        const before = recordedRequests.length
        expect(before).toBeGreaterThan(0)

        await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL_MS + 100)

        expect(recordedRequests.length).toBeGreaterThan(before)
        unmount()
      } finally {
        vi.useRealTimers()
      }
    })

    it('keeps the last good rows on screen when a refresh fails', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')
      const before = await rowSymbols()
      expect(before.length).toBe(12)

      failNextCoinRequests(1)
      await user.click(screen.getByRole('button', { name: /^refresh$/i }))

      // The failure is reported...
      expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't refresh/i)
      // ...and the previously loaded data is still there (F001-FR-020).
      expect(await rowSymbols()).toEqual(before)
    })
  })

  describe('column visibility', () => {
    it('hides a column and keeps the choice across a remount', async () => {
      const { user, unmount } = renderApp()
      await screen.findByRole('table')
      expect(screen.getAllByRole('columnheader')).toHaveLength(5)

      await user.click(screen.getByRole('button', { name: /5 of 10/i }))
      await user.click(screen.getByRole('checkbox', { name: /24h %/i }))

      await waitFor(() => expect(screen.getAllByRole('columnheader')).toHaveLength(4))

      // The choice must have reached localStorage, not just component state.
      // If `persist` were removed this is null and the test fails here — which
      // is the point: the previous version of this test passed with `persist`
      // deleted entirely, because it only ever re-read the in-memory store.
      const persisted = localStorage.getItem('market-hub.columns')
      expect(persisted).toContain('marketCapRank')
      expect(persisted).not.toContain('pctChange24h')

      // Simulate a reload. Unmounting alone isn't enough — the store lives at
      // module scope, so a remount reads the same object. Clearing the
      // in-memory state also writes null back to storage (persist subscribes to
      // every change), so the snapshot has to be put back to stand in for what
      // a fresh page load would actually find.
      unmount()
      useColumnsStore.setState({ visibleColumns: null })
      localStorage.setItem('market-hub.columns', persisted!)
      await useColumnsStore.persist.rehydrate()

      renderApp()
      await screen.findByRole('table')
      await waitFor(() => expect(screen.getAllByRole('columnheader')).toHaveLength(4))
    })
  })
})
