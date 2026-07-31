/**
 * Display formatting. Phase 1 is USD-only (F001-FR-022).
 *
 * Note on precision: prices are numeric(30,10) server-side and arrive as JSON
 * numbers, so a very small altcoin price can already have lost low-order digits
 * before it reaches here. Formatting therefore deliberately caps decimals rather
 * than implying more precision than survived the wire.
 */

const PLACEHOLDER = '—'

export function formatUsd(value: number | null): string {
  if (value === null || Number.isNaN(value)) return PLACEHOLDER
  // Sub-dollar assets need more decimals to be meaningful at all; large ones
  // would just be noise.
  const fractionDigits = Math.abs(value) < 1 ? 6 : 2
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: fractionDigits,
  }).format(value)
}

export function formatCompactUsd(value: number | null): string {
  if (value === null || Number.isNaN(value)) return PLACEHOLDER
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 2,
  }).format(value)
}

export function formatPercent(value: number | null): string {
  if (value === null || Number.isNaN(value)) return PLACEHOLDER
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
}

export function formatNumber(value: number | null): string {
  if (value === null || Number.isNaN(value)) return PLACEHOLDER
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(value)
}

export function formatTimestamp(iso: string | null): string {
  if (!iso) return PLACEHOLDER
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return PLACEHOLDER
  return date.toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'medium' })
}

/** Human label for a catalog column key. */
const COLUMN_LABELS: Record<string, string> = {
  marketCapRank: '#',
  name: 'Name',
  symbol: 'Symbol',
  price: 'Price',
  pctChange1h: '1h %',
  pctChange24h: '24h %',
  pctChange7d: '7d %',
  marketCap: 'Market Cap',
  volume24h: 'Volume (24h)',
  circulatingSupply: 'Circulating Supply',
}

export function columnLabel(key: string): string {
  return COLUMN_LABELS[key] ?? key
}
