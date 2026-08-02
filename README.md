# market-hub

Backend service for a crypto price dashboard. See [`CLAUDE.md`](CLAUDE.md) and
[`docs/`](docs/) for the architecture, domain model, constraints, and slice roadmap.

## Prerequisites
- Java 21 and Maven
- Node 22+ and npm (for the `frontend/` SPA)
- Docker (for local Postgres and the Testcontainers-based tests)

## Run locally
1. Start Postgres:
   ```
   docker compose up -d
   ```
2. *(optional)* Configure environment — only needed when you diverge from the
   defaults, which already match `docker-compose.yml`:
   ```
   cp .env.example .env      # edit as needed
   set -a && source .env && set +a
   ```
3. Run the app (from `backend/`):
   ```
   cd backend && mvn spring-boot:run
   ```
4. Verify:
   ```
   curl http://localhost:8080/api/actuator/health   # -> {"status":"UP"}
   ```

## Run the frontend (S3)
The SPA lives in `frontend/` (React + Vite + TypeScript, TanStack Query/Table, Zustand).
It expects the backend running on port 8080 — the Vite dev server proxies `/api`
straight through, which is what stands in for the CORS config the backend
deliberately doesn't have.

```
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Other scripts: `npm run build` (production bundle), `npm run test` (Vitest +
Testing Library + MSW), `npm run lint`.

## Test
From `backend/`:
```
mvn test
```
Testcontainers starts a throwaway Postgres, so Docker must be available.

From `frontend/`:
```
npm run test
```
Runs against a mocked API — never a live backend or provider.

## Environment variables
Each slice documents the variables it introduces in
[`.env.example`](.env.example). Available today (S0):

| Variable | Default | Purpose |
|---|---|---|
| `DB_NAME` | `markethub` | Local Postgres database name |
| `DB_URL` | `jdbc:postgresql://localhost:5432/markethub` | JDBC URL |
| `DB_USERNAME` | `markethub` | Database user |
| `DB_PASSWORD` | `markethub` | Database password |
| `SERVER_PORT` | `8080` | HTTP port (app serves under context path `/api`) |
| `APP_LOG_LEVEL` | `INFO` | Log level for the `com.am.market_hub` package |
| `CMC_API_KEY` | *(empty)* | CoinMarketCap key; empty → empty universe, no crash |
| `CMC_BASE_URL` | `https://pro-api.coinmarketcap.com` | CoinMarketCap base URL |
| `CMC_CONVERT` | `USD` | Quote currency (Phase 1: USD only) |
| `POLLER_ENABLED` | `true` | Enable the scheduled poller |
| `POLLER_COIN_LIMIT` | `100` | Top-N coins fetched per cycle |
| `POLLER_INTERVAL_MS` | `180000` | Poll interval (ms) |
| `POLLER_INITIAL_DELAY_MS` | `5000` | Delay before first poll (ms) |
| `MARKET_DEFAULT_PAGE_SIZE` | `20` | Default dashboard page size (must be in the supported list) |
| `MARKET_SUPPORTED_PAGE_SIZES` | `20,50,100` | Selectable page sizes; any other `size` → 400 |
| `MARKET_DEFAULT_VISIBLE_COLUMNS` | *(all 10 columns)* | Default visible columns; validated against the column catalog at startup |
| `JWT_SECRET` | *(dev-only insecure default)* | HMAC signing key for issued JWTs; always set a real one outside dev |
| `JWT_EXPIRATION_MS` | `86400000` | JWT lifetime (24h) |
| `ADMIN_EMAIL` | *(empty)* | Seeds the one admin account on startup if no admin exists yet; empty → seed skipped |
| `ADMIN_PASSWORD` | *(empty)* | Password for the seeded admin account |
| `AUTH_MAX_FAILED_ATTEMPTS` | `5` | Consecutive failed sign-ins before a temporary lockout |
| `AUTH_LOCKOUT_DURATION_MINUTES` | `15` | How long a temporary lockout lasts |

### API documentation (OpenAPI / Swagger)
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/api/v3/api-docs`

### Market endpoints (S1–S2, public)
- `GET /api/market/coins?page=&size=&sort=<field>&order=asc|desc&q=<term>` — cached
  universe as a page envelope (`content`, `page`, `size`, `totalElements`,
  `totalPages`, `lastUpdatedAt`)
- `GET /api/market/coins/{symbol}` — single coin (404 if absent)
- `GET /api/market/columns` — supported columns, default visible set, and
  selectable page sizes

Sortable fields: `symbol`, `name`, `marketCapRank` (default), `price`,
`pctChange1h`, `pctChange24h`, `pctChange7d`, `marketCap`, `volume24h`,
`circulatingSupply`.

`q` matches a **name or symbol substring**, case-insensitively. Sorting and
searching apply to the complete matching dataset *before* pagination. An
unsupported `size`, a negative `page`, or an unknown `sort`/`order` is a `400`;
a search with no matches is a `200` with an empty page.
