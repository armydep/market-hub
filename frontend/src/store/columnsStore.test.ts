import { describe, expect, it } from 'vitest'
import { resolveVisibleColumns } from './columnsStore'

const SUPPORTED = ['symbol', 'name', 'marketCapRank', 'price', 'pctChange24h']
const DEFAULT_VISIBLE = ['marketCapRank', 'name', 'symbol', 'price']

describe('resolveVisibleColumns', () => {
  it('falls back to the server default when the user has chosen nothing', () => {
    expect(resolveVisibleColumns(null, DEFAULT_VISIBLE, SUPPORTED)).toEqual(DEFAULT_VISIBLE)
  })

  it('follows the defaultVisible order, not the supported order', () => {
    // The bug this guards: ordering by `supported` would render
    // symbol, name, marketCapRank, price — silently rearranging the grid away
    // from the layout the server intended.
    const result = resolveVisibleColumns(null, DEFAULT_VISIBLE, SUPPORTED)
    expect(result).toEqual(['marketCapRank', 'name', 'symbol', 'price'])
    expect(result).not.toEqual(['symbol', 'name', 'marketCapRank', 'price'])
  })

  it('appends extra opted-in columns after the default ones', () => {
    const chosen = [...DEFAULT_VISIBLE, 'pctChange24h']
    expect(resolveVisibleColumns(chosen, DEFAULT_VISIBLE, SUPPORTED)).toEqual([
      'marketCapRank',
      'name',
      'symbol',
      'price',
      'pctChange24h',
    ])
  })

  it('keeps ordering stable when a column is hidden', () => {
    const chosen = ['marketCapRank', 'symbol', 'price']
    expect(resolveVisibleColumns(chosen, DEFAULT_VISIBLE, SUPPORTED)).toEqual([
      'marketCapRank',
      'symbol',
      'price',
    ])
  })

  it('ignores a persisted column the server no longer supports', () => {
    // A stale localStorage entry must not inject an unknown column key into the
    // grid, which would render an empty column with a raw key as its header.
    const chosen = [...DEFAULT_VISIBLE, 'removedColumn']
    expect(resolveVisibleColumns(chosen, DEFAULT_VISIBLE, SUPPORTED)).toEqual(DEFAULT_VISIBLE)
  })
})
