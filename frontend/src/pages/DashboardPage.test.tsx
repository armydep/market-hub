import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/renderApp'
import { failNextCoinRequests, lastRequest, recordedRequests } from '../test/handlers'

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
    expect(lastRequest().searchParams.get('size')).toBe('20')
  })

  it('shows the last successful update time', async () => {
    renderApp()
    const stamp = await screen.findByTestId('last-updated')
    await waitFor(() => expect(stamp.textContent).not.toBe('—'))
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
      await waitFor(async () =>
        expect(await rowSymbols()).toEqual(expect.arrayContaining(['BTC', 'DOGE', 'LTC'])),
      )
    })

    it('matches on symbol too', async () => {
      const { user } = renderApp()
      await screen.findByRole('table')

      // "TC" appears in BTC and LTC as symbols; no fixture name contains it.
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
      expect(options.map((o) => o.textContent)).toEqual(['5', '20', '50'])
    })
  })

  describe('refresh', () => {
    it('preserves page, size, sort and search across a manual refresh', async () => {
      // q=o matches 9 fixture coins, so page 1 at size 5 genuinely has rows —
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

      // Remount against the same localStorage, standing in for a reload.
      unmount()
      renderApp()

      await screen.findByRole('table')
      await waitFor(() => expect(screen.getAllByRole('columnheader')).toHaveLength(4))
    })
  })
})
