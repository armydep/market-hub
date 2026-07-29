# market-hub

Backend service for a crypto price dashboard. See [`CLAUDE.md`](CLAUDE.md) and
[`docs/`](docs/) for the architecture, domain model, constraints, and slice roadmap.

## Prerequisites
- Java 21 and Maven
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

## Test
From `backend/`:
```
mvn test
```
Testcontainers starts a throwaway Postgres, so Docker must be available.

## Environment variables
Each slice documents the variables it introduces in
[`.env.example`](.env.example). Available today (S0):

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/markethub` | JDBC URL |
| `DB_USERNAME` | `markethub` | Database user |
| `DB_PASSWORD` | `markethub` | Database password |
| `SERVER_PORT` | `8080` | HTTP port (app serves under context path `/api`) |
| `CMC_API_KEY` | *(empty)* | CoinMarketCap key; empty → empty universe, no crash |
| `CMC_BASE_URL` | `https://pro-api.coinmarketcap.com` | CoinMarketCap base URL |
| `CMC_CONVERT` | `USD` | Quote currency (Phase 1: USD only) |
| `POLLER_ENABLED` | `true` | Enable the scheduled poller |
| `POLLER_COIN_LIMIT` | `100` | Top-N coins fetched per cycle |
| `POLLER_INTERVAL_MS` | `180000` | Poll interval (ms) |
| `POLLER_INITIAL_DELAY_MS` | `5000` | Delay before first poll (ms) |

### Market endpoints (S1, public)
- `GET /api/market/coins?sort=<field>&order=asc|desc` — cached universe, sorted
- `GET /api/market/coins/{symbol}` — single coin (404 if absent)

Sortable fields: `symbol`, `name`, `marketCapRank` (default), `price`,
`pctChange1h`, `pctChange24h`, `pctChange7d`, `marketCap`, `volume24h`,
`circulatingSupply`.
