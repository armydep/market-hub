import { QueryClient } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import {
  formatCompactUsd,
  formatNumber,
  formatPercent,
  formatTimestamp,
  formatUsd,
} from '../format'
import { REFRESH_INTERVAL_MS } from '../hooks/useCoins'
import { COINS } from '../test/fixtures'
import { failNextCoinDetailRequests, lastSymbolRequest, recordedSymbolRequests } from '../test/handlers'
import { renderApp } from '../test/renderApp'
import { server } from '../test/setup'

const BTC = COINS.find((c) => c.symbol === 'BTC')!

describe('Asset Details', () => {
  it('renders the header, price, market data and footer for a known symbol', async () => {
    renderApp('/coins/BTC')

    expect(await screen.findByRole('heading', { name: /bitcoin/i })).toBeInTheDocument()
    expect(screen.getByText('BTC')).toBeInTheDocument()
    expect(screen.getByText(`#${BTC.marketCapRank}`)).toBeInTheDocument()

    expect(screen.getByText(formatUsd(BTC.price))).toBeInTheDocument()
    // Plain strings, not regexes: formatPercent's output starts with "+" or "-",
    // both of which are regex metacharacters that would otherwise throw.
    expect(screen.getByText(`1h ${formatPercent(BTC.pctChange1h)}`)).toBeInTheDocument()
    expect(screen.getByText(`24h ${formatPercent(BTC.pctChange24h)}`)).toBeInTheDocument()
    expect(screen.getByText(`7d ${formatPercent(BTC.pctChange7d)}`)).toBeInTheDocument()

    expect(screen.getByText(formatCompactUsd(BTC.marketCap))).toBeInTheDocument()
    expect(screen.getByText(formatCompactUsd(BTC.volume24h))).toBeInTheDocument()
    expect(screen.getByText(formatNumber(BTC.circulatingSupply))).toBeInTheDocument()

    const stamp = await screen.findByTestId('last-updated')
    expect(stamp).toHaveTextContent(formatTimestamp(BTC.updatedAt))
  })

  it('requests the routed symbol, not a hardcoded one', async () => {
    renderApp('/coins/ETH')
    await screen.findByRole('heading', { name: /ethereum/i })

    expect(lastSymbolRequest().pathname).toBe('/api/market/coins/ETH')
  })

  it('renders an asset-specific not-found state for an unknown symbol, distinct from the router catch-all', async () => {
    renderApp('/coins/NOPE')

    expect(await screen.findByText(/asset not found/i)).toBeInTheDocument()
    expect(screen.queryByText(/page not found/i)).not.toBeInTheDocument()
  })

  it('keeps the last good coin on screen when a refresh fails', async () => {
    const { user } = renderApp('/coins/BTC')
    await screen.findByRole('heading', { name: /bitcoin/i })

    failNextCoinDetailRequests(1)
    await user.click(screen.getByRole('button', { name: /^refresh$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't refresh/i)
    // The previously loaded coin is still on screen (F001-FR-020's detail-page
    // equivalent), not replaced by a blank or error-only view.
    expect(screen.getByRole('heading', { name: /bitcoin/i })).toBeInTheDocument()
    expect(screen.getByText(formatUsd(BTC.price))).toBeInTheDocument()
  })

  it('keeps the last good coin on screen when a later refresh 404s (coin left the top-N universe)', async () => {
    // A 404 on a *refresh* is not the same as a 404 on first load: the coin
    // can transiently drop out of the tracked universe between polls
    // (domain-model.md's "not evaluable this cycle" case), and that must be a
    // recoverable, banner-only failure — not replace an already-loaded asset
    // with the "not found" state.
    const { user } = renderApp('/coins/BTC')
    await screen.findByRole('heading', { name: /bitcoin/i })

    server.use(
      http.get('/api/market/coins/:symbol', () =>
        HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 404,
            error: 'Not Found',
            message: 'Unknown symbol: BTC',
          },
          { status: 404 },
        ),
      ),
    )
    await user.click(screen.getByRole('button', { name: /^refresh$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't refresh/i)
    expect(screen.getByRole('heading', { name: /bitcoin/i })).toBeInTheDocument()
    expect(screen.queryByText(/asset not found/i)).not.toBeInTheDocument()
  })

  it('refetches automatically on the configured interval', async () => {
    // Fake timers must be installed before render, same reason as the
    // dashboard's equivalent test: the interval is scheduled at mount time.
    vi.useFakeTimers()
    try {
      const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
      })
      const { unmount } = render(
        <MemoryRouter initialEntries={['/coins/BTC']}>
          <App queryClient={queryClient} />
        </MemoryRouter>,
      )

      await vi.advanceTimersByTimeAsync(100)
      const before = recordedSymbolRequests.length
      expect(before).toBeGreaterThan(0)

      await vi.advanceTimersByTimeAsync(REFRESH_INTERVAL_MS + 100)

      expect(recordedSymbolRequests.length).toBeGreaterThan(before)
      unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('renders a fatal error with retry for a non-404 failure with no prior data', async () => {
    server.use(
      http.get('/api/market/coins/:symbol', () =>
        HttpResponse.json(
          {
            timestamp: new Date().toISOString(),
            status: 500,
            error: 'Internal Server Error',
            message: 'Upstream unavailable',
          },
          { status: 500 },
        ),
      ),
    )
    renderApp('/coins/BTC')

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/something went wrong/i))
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })
})
