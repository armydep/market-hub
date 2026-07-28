# Vertical Slices

Ordered. Each slice is independently shippable and testable and sized for one focused session.
Dependency graph: S0 → {S1, S2}; {S1, S2} → S3; {S1, S2} → S4. S1 and S2 are parallel; S3 and S4 are
parallel once S1+S2 land.

---

## S0 — Skeleton + DB + health  *(no deps)*
Boot a Spring Boot app against Postgres. `docker-compose.yml` for local PG. Flyway baseline
migration. Actuator `health`. Central config (`application.yml`), `ApiException` +
`GlobalExceptionHandler`.
- **Ship:** app starts, `GET /api/actuator/health` = UP.
- **Test:** context loads and health is UP against a Testcontainers Postgres.

## S1 — Market ingestion + read API  *(deps: S0)*
`crypto_quotes` migration. `PriceProvider` interface + `CoinMarketCapProvider` (WebClient →
`/v1/cryptocurrency/listings/latest`, USD, top-N). `@Scheduled` poller upserts the universe. Public
read API: `GET /api/market/coins` (sort params) and `GET /api/market/coins/{symbol}`.
- **Ship:** with `CMC_API_KEY` set, the universe populates and the endpoints serve sorted quotes;
  with no key, endpoints return an empty list and the app stays healthy.
- **Test:** provider parses a captured CMC JSON fixture; poller upserts (insert + update paths);
  endpoint returns quotes in requested sort order; missing-key path degrades (no crash, empty set).

## S2 — Auth + JWT  *(deps: S0; parallel to S1)*
`users` migration. `User` + repo, BCrypt encoder, `JwtService`, `JwtAuthFilter`, `SecurityConfig`
(public: `/auth/**`, `GET /market/**`, `/actuator/**`; everything else authenticated).
`POST /api/auth/register`, `POST /api/auth/login` → `{token,userId,email}`. `CurrentUser` helper.
- **Ship:** register → login → call a protected endpoint with the bearer token.
- **Test:** register+login happy path; duplicate email → 409; bad credentials → 401; protected route
  without/with token → 401/200; token validity (tampered/expired rejected).

## S3 — Boards + exclusions + column/sort  *(deps: S1, S2)*
`boards` + `board_exclusions` migrations. Board CRUD scoped to `CurrentUser`; `name` unique per user;
`columnsJson`/`sortField` validated against the column catalog; `sortDir` enum. Exclusion add/remove.
`GET /api/boards/{id}/view` resolves the board = universe − exclusions, sorted per board config, with
only catalog columns the board selects.
- **Ship:** a user creates boards, sets columns/sort, excludes symbols, and reads a resolved view.
- **Test:** CRUD; ownership isolation (other user's board → 404); duplicate name per user → 409;
  invalid column/sort key → 400; resolved view omits excluded symbols and honors sort.

## S4 — Alerts + evaluation  *(deps: S1, S2)*
`price_alerts` migration. Alert CRUD scoped to `CurrentUser`. Creation rejects an already-satisfied
condition (400) and an unknown symbol (400). `AlertEvaluationService` runs as the post-poll hook →
one-shot fire (sets `triggeredAt`/`triggeredPrice`, `active=false`). `GET /api/alerts`,
`GET /api/alerts/triggered`, `POST /api/alerts/{id}/acknowledge`, re-enable.
- **Ship:** a user creates an alert, the poll cycle fires it once, and it surfaces in `triggered`.
- **Test:** reject-if-satisfied and unknown-symbol at creation; evaluation fires exactly once and sets
  triggered fields; already-fired alerts don't re-fire; acknowledge and re-enable transitions;
  ownership isolation.

---
**Slice test harness:** Testcontainers Postgres for all; a stub `PriceProvider` bean seeds a
deterministic universe for S3/S4 so tests never call CMC.
