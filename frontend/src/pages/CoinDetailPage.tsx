import { useParams } from 'react-router-dom'
import { ApiError } from '../api/types'
import { EmptyState, FatalError, LoadingState, RefreshFailureBanner } from '../components/States'
import { formatCompactUsd, formatNumber, formatPercent, formatTimestamp, formatUsd } from '../format'
import { useCoin } from '../hooks/useCoin'
import styles from './CoinDetailPage.module.css'

function percentClass(value: number | null): string | undefined {
  if (value === null) return undefined
  return value >= 0 ? styles.up : styles.down
}

export function CoinDetailPage() {
  const { symbol = '' } = useParams<{ symbol: string }>()
  const { data: coin, isLoading, isError, error, isFetching, refetch } = useCoin(symbol)

  if (isLoading) {
    return <LoadingState label="Loading asset…" />
  }

  if (isError && error instanceof ApiError && error.status === 404) {
    return (
      <EmptyState
        title="Asset not found"
        hint={`No cryptocurrency matches "${symbol}".`}
      />
    )
  }

  if (isError && !coin) {
    return (
      <FatalError
        message={(error as Error)?.message ?? 'Could not load this asset.'}
        onRetry={() => refetch()}
      />
    )
  }

  if (!coin) {
    return null
  }

  return (
    <section className={styles.page}>
      {/* Rendered above the content, never instead of it: a failed refresh
          must keep the last good data on screen (F001-FR-020). */}
      {isError && <RefreshFailureBanner message={(error as Error)?.message ?? ''} />}

      <header className={styles.header}>
        <span className={styles.rank}>
          {coin.marketCapRank !== null ? `#${coin.marketCapRank}` : '—'}
        </span>
        <h1 className={styles.title}>{coin.name}</h1>
        <span className={styles.symbol}>{coin.symbol}</span>
      </header>

      <div className={styles.priceBlock}>
        <div className={styles.price}>{formatUsd(coin.price)}</div>
        <div className={styles.changes}>
          <span className={percentClass(coin.pctChange1h)}>1h {formatPercent(coin.pctChange1h)}</span>
          <span className={percentClass(coin.pctChange24h)}>
            24h {formatPercent(coin.pctChange24h)}
          </span>
          <span className={percentClass(coin.pctChange7d)}>7d {formatPercent(coin.pctChange7d)}</span>
        </div>
      </div>

      <dl className={styles.marketData}>
        <dt>Market Cap</dt>
        <dd>{formatCompactUsd(coin.marketCap)}</dd>
        <dt>Volume (24h)</dt>
        <dd>{formatCompactUsd(coin.volume24h)}</dd>
        <dt>Circulating Supply</dt>
        <dd>{formatNumber(coin.circulatingSupply)}</dd>
      </dl>

      <footer className={styles.footer}>
        <span>
          Last updated <span data-testid="last-updated">{formatTimestamp(coin.updatedAt)}</span>
        </span>
        <button
          type="button"
          className={styles.refresh}
          onClick={() => refetch()}
          disabled={isFetching}
        >
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </button>
      </footer>
    </section>
  )
}
