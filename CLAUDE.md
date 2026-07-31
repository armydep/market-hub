# market-hub

Crypto market-monitoring web app: a Spring Boot backend plus a React SPA. A scheduled poller upserts
a top-N coin universe from CoinMarketCap (behind a `PriceProvider` seam) into Postgres. Everyone —
guests included — sees the same public market dashboard (paginated, sortable, searchable) and asset
details. Registered users (JWT, role-based: `TRADER`/`ADMIN`, with `MODERATOR` reserved) additionally
get persisted visible-column preferences, account management, one-shot in-app price alerts evaluated
after each successful poll, and the notifications those alerts create. Admins list users and
block/unblock them, with an audit record. Guests are the anonymous principal — no rows, state held
client-side. Crypto-only, USD-only in Phase 1.

## Context docs (read these first)
@docs/Market_Hub_PRD_v0.1.md
@docs/domain-model.md
@docs/constraints.md
@docs/slices.md

The PRD is the product source of truth; the other three translate it into the model, the decisions,
and the delivery plan.

## Layout
- `backend/` — Spring Boot Maven module (package root `com.am.market_hub`, package-by-feature).
- `frontend/` — React + Vite + TypeScript SPA (created in S3; see `docs/slices.md`).
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

## Test-runner subagents
Both test-runner agents follow the same contract: report failures only,
cap output at 40 lines, never modify files, never include raw tool output.

## CI parity
Before pushing, both test-runner subagents must pass. They mirror
`.github/workflows/ci.yml` exactly:
- backend: `./mvnw test`
- frontend: `npm run lint && npm run build && npm test`

A subagent pass means CI should pass. If CI fails after both subagents
passed, that's a signal the agent definitions have drifted from the
workflow — fix the agent, not just the immediate failure.

## Conventions
- Schema is Flyway-only (`db/migration/V__*.sql`); JPA `ddl-auto=validate` — never let Hibernate alter schema.
- Constructor injection only; no field injection. DTOs are `record`s. Entities are not returned from controllers.
- Package-by-feature under `com.am.market_hub`, layered inside each feature (`web`/`service`/`repository`/`domain`/`dto`); see the `spring-boot-backend` skill. Features: `auth`, `user`, `market`, `alert`, `notification`, `admin`, plus `common`, `config`.
- All external price access goes through `PriceProvider`; only the poller touches it (never the read path). Transactional email goes through `EmailSender` (password reset only).
- Errors via `ApiException` + the single `GlobalExceptionHandler`; cross-user access returns 404. The catch-all must honor framework status codes, not collapse them to 500.
- API docs via springdoc (OpenAPI 3): spec at `/api/v3/api-docs`, Swagger UI at `/api/swagger-ui.html`; annotate controllers with `@Tag`/`@Operation`.
- Auth’d ownership scoping through the `CurrentUser` helper on every alert/notification/account operation.
- Sorting and searching happen in the query, before pagination — never over an already-paginated slice.
- RBAC: single `User.role` enum + `RoleHierarchy` (`ADMIN>MODERATOR>TRADER`); role is a JWT claim; admin seeded from env.
- Tests use real Postgres (Testcontainers) and a stub `PriceProvider`; no live CMC calls in tests.
- Money as `BigDecimal`/`numeric`, never `double`. Timestamps `timestamptz`, UTC.
- Each slice documents the env vars it introduces in `.env.example` + the README env table (per-slice, not a separate slice).
- Keep decisions in the docs above in sync when behavior changes; they are the source of truth.
