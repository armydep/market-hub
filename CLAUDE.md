# market-hub

Spring Boot backend for a crypto price dashboard: a scheduled poller upserts a top-N coin universe
from CoinMarketCap (behind a `PriceProvider` seam) into Postgres; registered users (JWT, role-based:
`TRADER`/`ADMIN`, with `MODERATOR` reserved) manage multiple boards that view the universe minus
per-board exclusions with custom columns/sort, plus one-shot in-app price alerts evaluated after each
poll. Guests are the anonymous principal (public market reads only, state held client-side). A React
SPA (out of scope here) is the client. Crypto-only, USD-only in Phase 1.

## Context docs (read these first)
@docs/domain-model.md
@docs/constraints.md
@docs/slices.md

## Layout
- `backend/` — Spring Boot Maven module (package root `com.am.market_hub`, package-by-feature).
- `docker-compose.yml` — local Postgres 16.

## Build / test / run
Run from `backend/`:
- Start Postgres: `docker compose up -d` (repo root).
- Run app: `mvn spring-boot:run` (needs `CMC_API_KEY` for real data; boots empty without it).
- Test command for the test-runner subagent: `./mvnw -B test` from `backend/` (Testcontainers spins up Postgres; Docker must be available).
- Single test: `mvn -Dtest=ClassName#method test`.
- Package: `mvn -DskipTests package`.

## Workflow
- Never run `./mvnw test` in the main session — delegate to the test-runner subagent.
- Before pushing a completed slice, delegate to the code-reviewer subagent and resolve every CRITICAL finding.

## Conventions
- Schema is Flyway-only (`db/migration/V__*.sql`); JPA `ddl-auto=validate` — never let Hibernate alter schema.
- Constructor injection only; no field injection. DTOs are `record`s. Entities are not returned from controllers.
- Package-by-feature under `com.am.market_hub`, layered inside each feature (`web`/`service`/`repository`/`domain`/`dto`); see the `spring-boot-backend` skill. Features: `auth`, `user`, `market`, `board`, `alert`, plus `common`, `config`.
- All external price access goes through `PriceProvider`; only the poller touches it (never the read path).
- Errors via `ApiException` + the single `GlobalExceptionHandler`; cross-user access returns 404.
- API docs via springdoc (OpenAPI 3): spec at `/api/v3/api-docs`, Swagger UI at `/api/swagger-ui.html`; annotate controllers with `@Tag`/`@Operation`.
- Auth’d ownership scoping through the `CurrentUser` helper on every board/alert operation.
- RBAC: single `User.role` enum + `RoleHierarchy` (`ADMIN>MODERATOR>TRADER`); role is a JWT claim; admin seeded from env.
- Tests use real Postgres (Testcontainers) and a stub `PriceProvider`; no live CMC calls in tests.
- Money as `BigDecimal`/`numeric`, never `double`. Timestamps `timestamptz`, UTC.
- Each slice documents the env vars it introduces in `.env.example` + the README env table (per-slice, not a separate slice).
- Keep decisions in the docs above in sync when behavior changes; they are the source of truth.
