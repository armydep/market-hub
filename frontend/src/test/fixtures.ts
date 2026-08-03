import type { Account, AdminUser, AuthResponse, Coin, ColumnCatalog, PriceAlert } from '../api/types'

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
  // Deliberately NOT 20: 20 is the client's hardcoded fallback, so a
  // fixture using it cannot distinguish 'follows the server' from 'ignores it'.
  supportedPageSizes: [2, 5, 20, 50],
  defaultPageSize: 50,
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
 * Search discrimination: "coin" appears only in names (Bitcoin, Dogecoin,
 * Litecoin) and in no symbol, so a name-matching test can't pass via the symbol
 * column. The reverse case uses "TC" — note "bitcoin" *does* contain "tc", so
 * that test discriminates via LTC, which matches on symbol alone.
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

/**
 * A coin with missing values, for the null-rendering and nulls-last paths.
 * Kept out of COINS so it doesn't perturb every other fixture expectation;
 * tests that need it install their own handler.
 */
export const COIN_WITH_NULLS: Coin = {
  ...coin(13, 'NULLC', 'Nullcoin', 0),
  marketCapRank: null,
  price: null,
  pctChange24h: null,
  marketCap: null,
}

export const LAST_UPDATED = '2026-07-31T10:00:00Z'

/** An already-registered account, for duplicate-email (409) and login-happy-path tests. */
export const REGISTERED_EMAIL = 'existing@example.com'
export const REGISTERED_PASSWORD = 'correct-password'

/** Accounts the login handler always rejects with a distinct 403, regardless of password. */
export const BLOCKED_EMAIL = 'blocked@example.com'
export const LOCKED_EMAIL = 'locked@example.com'

export const AUTH_RESPONSE: AuthResponse = {
  token: 'fixture.jwt.token',
  userId: 1,
  email: REGISTERED_EMAIL,
  role: 'TRADER',
}

/** The signed-in account behind AUTH_RESPONSE, for the S8 account-page tests. */
export const ACCOUNT: Account = {
  id: 1,
  email: REGISTERED_EMAIL,
  role: 'TRADER',
  createdAt: '2026-07-01T10:00:00Z',
}

/** Already registered, for the S8 email-change-conflict (409) test. */
export const OTHER_REGISTERED_EMAIL = 'taken@example.com'

/** A signed-in administrator, for the S11 admin-page tests. */
export const ADMIN_AUTH_RESPONSE: AuthResponse = {
  token: 'fixture.admin.jwt.token',
  userId: 2,
  email: 'admin@example.com',
  role: 'ADMIN',
}

export const ADMIN_USERS: AdminUser[] = [
  { id: 10, email: 'active-user@example.com', role: 'TRADER', blocked: false, createdAt: '2026-07-01T10:00:00Z' },
  { id: 11, email: 'blocked-user@example.com', role: 'TRADER', blocked: true, createdAt: '2026-07-02T10:00:00Z' },
]

/**
 * Fixed regardless of a mocked-known vs. mocked-unknown email, proving the
 * S7 no-enumeration guarantee at the UI level: the client never branches on
 * anything the server didn't already decide.
 */
export const PASSWORD_RESET_REQUEST_MESSAGE =
  'If an account with that email exists, a password reset link has been sent.'
export const PASSWORD_RESET_CONFIRM_MESSAGE = 'Your password has been updated. You can now sign in.'

/** The confirm handler accepts only this token; anything else is rejected as invalid/expired. */
export const VALID_RESET_TOKEN = 'valid-reset-token'
export const INVALID_RESET_TOKEN = 'invalid-reset-token'

/** One active and one triggered alert, for the S9 alerts-page tests. */
export const ACTIVE_ALERT: PriceAlert = {
  id: 100,
  symbol: 'BTC',
  condition: 'ABOVE_OR_EQUAL',
  targetPrice: 70000,
  active: true,
  triggeredAt: null,
  triggeredPrice: null,
  clearedAt: null,
  createdAt: '2026-07-30T10:00:00Z',
}

export const TRIGGERED_ALERT: PriceAlert = {
  id: 101,
  symbol: 'ETH',
  condition: 'BELOW_OR_EQUAL',
  targetPrice: 2500,
  active: false,
  triggeredAt: '2026-07-31T09:00:00Z',
  triggeredPrice: 2400,
  clearedAt: null,
  createdAt: '2026-07-29T10:00:00Z',
}
