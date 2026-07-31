/**
 * Mirrors the backend DTOs. Kept in one place so a server-side field rename
 * surfaces as a TypeScript error here rather than as a silently blank column.
 *
 * Source of truth: com.am.market_hub.market.dto.{CoinResponse,
 * CoinPageResponse, ColumnCatalogResponse}.
 */

/**
 * One cached quote. Monetary values are BigDecimal server-side and arrive as
 * JSON numbers; see the precision note in docs/slices/03-*.md before doing
 * anything with these beyond display.
 */
export interface Coin {
  cmcId: number
  symbol: string
  name: string
  slug: string | null
  assetType: string
  marketCapRank: number | null
  price: number | null
  pctChange1h: number | null
  pctChange24h: number | null
  pctChange7d: number | null
  marketCap: number | null
  volume24h: number | null
  circulatingSupply: number | null
  convertCurrency: string
  updatedAt: string
}

export interface CoinPage {
  content: Coin[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  /** Time of the last successful poll; null when the universe is empty. */
  lastUpdatedAt: string | null
}

export interface ColumnCatalog {
  supported: string[]
  defaultVisible: string[]
  supportedPageSizes: number[]
  defaultPageSize: number
}

/** The body GlobalExceptionHandler emits for every error. */
export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  details?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly body?: ApiErrorBody

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}
