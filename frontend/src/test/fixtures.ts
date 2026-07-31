import type { Coin, ColumnCatalog } from '../api/types'

export const CATALOG: ColumnCatalog = {
  supported: [
    'symbol',
    'name',
    'marketCapRank',
    'price',
    'pctChange1h',
    'pctChange24h',
    'pctChange7d',
    'marketCap',
    'volume24h',
    'circulatingSupply',
  ],
  defaultVisible: ['marketCapRank', 'name', 'symbol', 'price', 'pctChange24h'],
  supportedPageSizes: [5, 20, 50],
  defaultPageSize: 20,
}

function coin(rank: number, symbol: string, name: string, price: number): Coin {
  return {
    cmcId: rank,
    symbol,
    name,
    slug: name.toLowerCase(),
    assetType: 'CRYPTO',
    marketCapRank: rank,
    price,
    pctChange1h: 0.1,
    pctChange24h: rank % 2 === 0 ? 1.5 : -1.5,
    pctChange7d: 3,
    marketCap: price * 1_000_000,
    volume24h: price * 10_000,
    circulatingSupply: 21_000_000,
    convertCurrency: 'USD',
    updatedAt: '2026-07-31T10:00:00Z',
  }
}

/**
 * Twelve coins so a page can actually be cut — a fixture that fits on one page
 * cannot distinguish server-side paging from client-side slicing.
 *
 * Names and symbols are deliberately separable: "coin" appears only in names,
 * and no name contains a symbol substring like "TC", so a test asserting
 * name-matching can't accidentally pass via the symbol column or vice versa.
 */
export const COINS: Coin[] = [
  coin(1, 'BTC', 'Bitcoin', 60000),
  coin(2, 'ETH', 'Ethereum', 3000),
  coin(3, 'XRP', 'Ripple', 0.5),
  coin(4, 'ADA', 'Cardano', 0.35),
  coin(5, 'SOL', 'Solana', 150),
  coin(6, 'DOGE', 'Dogecoin', 0.12),
  coin(7, 'DOT', 'Polkadot', 6),
  coin(8, 'MATIC', 'Polygon', 0.8),
  coin(9, 'LTC', 'Litecoin', 80),
  coin(10, 'LINK', 'Chainlink', 14),
  coin(11, 'AVAX', 'Avalanche', 30),
  coin(12, 'ATOM', 'Cosmos', 9),
]

export const LAST_UPDATED = '2026-07-31T10:00:00Z'
