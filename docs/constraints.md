# Constraints & Decisions

## Stack / versions
- Java 21, Spring Boot 4.1.x (Spring Framework 7 / Spring Security 7), Maven (module root `backend/`, groupId `com.am`, base package `com.am.market_hub`).
- PostgreSQL 16. Schema owned by **Flyway**; JPA `ddl-auto=validate` (Hibernate never mutates schema).
- Spring Security + JWT (`io.jsonwebtoken jjwt` 0.12.x). BCrypt password hashing.
- HTTP client to the price provider: Spring `WebClient` (webflux on the classpath, app is otherwise MVC/servlet).
- Caffeine available for in-process caching if a read-path cache is wanted; the DB read-model is the primary cache.
- Tests: JUnit 5 + Spring Boot Test + **Testcontainers** (real Postgres); no H2 (Flyway/PG-specific SQL).

## Architectural decisions (with rationale)
- **`PriceProvider` interface is the single seam over the price source.** `CoinMarketCapProvider` is
  the Phase-1 impl and the default. Poller and alert evaluation depend on the interface, never on CMC
  types. Rationale: CMC free tier is a demo-scale liability (see below); the seam lets us swap to
  CoinGecko/Binance without touching business logic. (Abstraction was adopted; CMC-as-default was a
  deliberate user override of the recommendation to switch the default.)
- **Scheduled poller, single instance, plain `@Scheduled`.** No distributed lock / leader election.
  Running two instances would double-poll and double-fire alerts — **operational constraint: run
  exactly one instance** until ShedLock (or similar) is added. The scheduled work is isolated behind
  a service so a lock can be introduced without reshaping callers.
- **Alert evaluation is a hook after each successful poll cycle** (not its own timer): quotes and
  alert checks stay consistent and evaluation can never run against a half-written universe.
- **Top-N universe only.** One `listings/latest` call per cycle covers the whole tracked set. Users
  cannot track/alert on coins outside top-N. Keeps CMC credit usage flat and the poller a single call.
- **Latest-quote-only.** The poller upserts; no historical snapshot table. Alert firing price is
  preserved only via `PriceAlert.triggeredPrice`.
- **Guests are client-side only.** Backend exposes public GET market reads; boards/alerts require
  auth. No anonymous rows, no guest session tokens — zero server state for guests.
- **Symbol-based coupling** between user data and quotes (see domain-model.md) — durable across
  universe churn; the accepted cost is transient dangling symbols handled as "not evaluable".
- **Error contract:** a single `@RestControllerAdvice` maps `ApiException`(status,msg) and validation
  failures to a consistent JSON body `{timestamp,status,error,message,details?}`.
- **Cross-user access → 404**, not 403 (no id enumeration).
- **RBAC via a single role + role hierarchy.** `User.role` is one enum (`TRADER|MODERATOR|ADMIN`),
  not a join table; a Spring `RoleHierarchy` (`ADMIN > MODERATOR > TRADER`) grants downward. Role
  travels as a **JWT claim** so authorization is stateless; the `JwtAuthFilter` maps the claim to
  authorities (replaces the earlier hardcoded single authority). `GUEST` is the anonymous principal,
  never a stored role. Registration always mints `TRADER`; only an admin changes roles. Rationale:
  4 roles with a natural superset structure don't need orthogonal multi-role bookkeeping.
- **Admin bootstrap is env-provisioned.** On startup, if no admin exists, seed one from
  `ADMIN_EMAIL` / `ADMIN_PASSWORD` (BCrypt-hashed). No admin credential is committed to VCS and
  fresh environments get a working admin without manual DB edits. Registration cannot self-elevate.

## Non-inferable constraints (would not be obvious from code later)
- **CoinMarketCap free/basic is credit-metered** (~10k calls/month; credits weighted by coins-per-call)
  and returns cached quotes, not a stream. Do not add per-request CMC calls on the read path; only the
  poller talks to CMC. `CMC_API_KEY` is required for real data; **absence must degrade gracefully**
  (log a warning, serve an empty universe) rather than crash — the app must boot and pass tests with
  no key.
- **USD is the only convert currency** in Phase 1; `convertCurrency` exists on the row for future
  multi-fiat but is always `USD`.
- **`reject-if-already-satisfied`** is intentional (not a missing feature): active alerts always mean
  a future crossing. Do not "fix" it into fire-immediately.
- Board semantics are **universe-minus-exclusions**, deliberately (watchlist/include-set was
  considered and rejected). Do not reintroduce a curated per-board coin list without a new decision.
- `MODERATOR` is a **reserved, unimplemented role** in Phase 1 (no content to moderate). It exists in
  the enum + hierarchy only; do not add moderator-gated endpoints until a moderation domain (e.g.
  shared/public boards) is introduced by a new decision.
- **Frontend is a React SPA (client only).** Chosen stack for the future client: React + Vite +
  TypeScript, TanStack Query (polls `/market/coins`), TanStack Table (custom columns/sort/exclusions),
  Zustand + persist (guest state in `localStorage`). This is recorded for continuity but the SPA is
  **not built or documented as a slice here** — the backend docs stay backend-only (see Non-goals).

## Non-goals (Phase 1)
- Frontend / SPA implementation (a React client consumes the API; not built or documented here beyond
  the API contract each slice defines).
- Arbitrary-coin tracking beyond top-N.
- Multi-instance operation / distributed scheduling (ShedLock).
- Historical price storage / charting.
- Multi-fiat or crypto-to-crypto conversion.
- Non-crypto asset types (stocks, etc.) — modeled-for via `assetType`, not implemented.
- Alert delivery channels beyond in-app (no email, no webhooks, no push).
- Auto re-arm / cooldown alerts, OAuth/social login, server-side guest sessions.
