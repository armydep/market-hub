import { describe, expect, it } from 'vitest'
import {
  columnLabel,
  formatCompactUsd,
  formatNumber,
  formatPercent,
  formatTimestamp,
  formatUsd,
} from './format'

describe('formatUsd', () => {
  it('renders USD, satisfying F001-FR-022', () => {
    expect(formatUsd(60000)).toBe('$60,000.00')
    expect(formatUsd(3000.5)).toBe('$3,000.50')
  })

  it('keeps more decimals for sub-dollar assets, which would otherwise read as $0.00', () => {
    expect(formatUsd(0.12)).toBe('$0.12')
    expect(formatUsd(0.000123)).toBe('$0.000123')
  })

  it('renders a placeholder rather than NaN or $0 for a missing value', () => {
    expect(formatUsd(null)).toBe('—')
    expect(formatUsd(Number.NaN)).toBe('—')
  })
})

describe('formatCompactUsd', () => {
  it('abbreviates large magnitudes', () => {
    expect(formatCompactUsd(1_180_000_000_000)).toBe('$1.18T')
    expect(formatCompactUsd(25_000_000_000)).toBe('$25B')
  })

  it('placeholders a missing value', () => {
    expect(formatCompactUsd(null)).toBe('—')
  })
})

describe('formatPercent', () => {
  it('signs the value explicitly so direction is unambiguous', () => {
    expect(formatPercent(1.5)).toBe('+1.50%')
    expect(formatPercent(-3.4)).toBe('-3.40%')
    expect(formatPercent(0)).toBe('+0.00%')
  })

  it('placeholders a missing value', () => {
    expect(formatPercent(null)).toBe('—')
  })
})

describe('formatNumber', () => {
  it('groups thousands and drops fractions', () => {
    expect(formatNumber(19_700_000)).toBe('19,700,000')
  })

  it('placeholders a missing value', () => {
    expect(formatNumber(null)).toBe('—')
  })
})

describe('formatTimestamp', () => {
  it('renders a readable date for a valid ISO string', () => {
    expect(formatTimestamp('2026-07-31T10:00:00Z')).toMatch(/Jul 31, 2026/)
  })

  it('placeholders null and unparseable input rather than showing "Invalid Date"', () => {
    expect(formatTimestamp(null)).toBe('—')
    expect(formatTimestamp('not-a-date')).toBe('—')
  })
})

describe('columnLabel', () => {
  it('maps catalog keys to human labels', () => {
    expect(columnLabel('marketCapRank')).toBe('#')
    expect(columnLabel('pctChange24h')).toBe('24h %')
  })

  it('falls back to the raw key so a new server column is visible, not blank', () => {
    expect(columnLabel('somethingNew')).toBe('somethingNew')
  })
})
