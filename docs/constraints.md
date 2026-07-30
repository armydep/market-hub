# Constraints & Decisions

Aligned to [`Market_Hub_PRD_v0.1.md`](Market_Hub_PRD_v0.1.md). Phase 1 is delivered as a **web
application**: a Spring Boot backend (`backend/`) and a React SPA (`frontend/`).

## Stack / versions
- Java 21, Spring Boot 4.1.x (Spring Framework 7 / Spring Security 7), Maven (module root `backend/`, groupId `com.am`, base package `com.am.market_hub`).
- PostgreSQL 16. Schema owned by **Flyway**; JPA `ddl-auto=validate` (Hibernate never mutates schema).
- Spring Security + JWT (`io.jsonwebtoken jjwt` 0.12.x). BCrypt password hashing.
- HTTP client to the price provider: Spring `WebClient` (webflux on the classpath, app is otherwise MVC/servlet).
- Caffeine available for in-process caching if a read-path cache is wanted; the DB read-model is the primary cache.
- Backend tests: JUnit 5 + Spring Boot Test + **Testcontainers** (real Postgres); no H2 (Flyway/PG-specific SQL).
- Frontend module `frontend/`: React + Vite + TypeScript, TanStack Query (server state), TanStack
  Table (grid: columns/sort/pagination), Zustand + persist (auth + guest view state in
  `localStorage`). Frontend tests run against a mocked API — never a live backend or provider.

## Architectural decisions (with rationale)
- **`PriceProvider` interface is the single seam over the price source.** `CoinMarketCapProvider` is
  the Phase-1 impl and the default. Poller and alert evaluation depend on the interface, never on CMC
  types. Rationale: CMC free tier is a demo-scale liability (see below); the seam lets us swap to
  CoinGecko/Binance without touching business logic. (Abstraction was adopted; CMC-as-default was a
  deliberate user override of the recommendation to switch the default.)
- **`EmailSender` is the same kind of seam over transactional email** (password reset only). The
  default implementation logs instead of sending, so the app boots and the suite runs with no mail
  configuration — mirroring the missing-API-key rule below. The concrete provider is deliberately
  undecided (PRD OQ-007) and must not leak into business logic.
- **Scheduled poller, single instance, plain `@Scheduled`.** No distributed lock / leader election.
  Running two instances would double-poll and double-fire alerts — **operational constraint: run
  exactly one instance** until ShedLock (or similar) is added. The scheduled work is isolated behind
  a service so a lock can be introduced without reshaping callers.
- **The poll transaction never spans the provider call.** The HTTP fetch runs outside any transaction
  (`TransactionTemplate` wraps only the upsert) and the `WebClient` carries connect/response timeouts.
  A hung provider must not pin a DB connection or wedge the single-threaded scheduler.
- **A poll that returns nothing, or implausibly little, is skipped rather than applied.** An empty
  result, or one below half of `min(current universe size, coinLimit)`, preserves the last-known
  universe instead of mass-deleting it. Bounding by `coinLimit` as well as the live count matters:
  comparing to the count alone lets a *lowered* `POLLER_COIN_LIMIT` wedge the universe permanently.
- **Alert evaluation is a hook after each successful poll cycle** (not its own timer): quotes and
  alert checks stay consistent and evaluation can never run against a half-written or skipped
  universe (PRD §3.5).
- **Top-N universe only.** One `listings/latest` call per cycle covers the whole tracked set. Users
  cannot track/alert on coins outside top-N. Keeps CMC credit usage flat and the poller a single call.
- **Latest-quote-only.** The poller upserts; no historical snapshot table. Alert firing price is
  preserved only via `PriceAlert.triggeredPrice`.
- **Guests are client-side only.** The backend exposes public GET market reads and asset details;
  everything else requires auth. No anonymous rows, no guest session tokens — zero server state for
  guests. Guest column choices live in `localStorage` (F001-FR-008/023).
- **Sorting and searching happen in the query, before pagination** — never over an already-paginated
  slice (F001-FR-011). This is a correctness rule, not a performance preference.
- **Symbol-based coupling** between user data and quotes (see domain-model.md) — durable across
  universe churn; the accepted cost is transient dangling symbols handled as "not evaluable".
- **Error contract:** a single `@RestControllerAdvice` maps `ApiException`(status,msg) and validation
  failures to a consistent JSON body `{timestamp,status,error,message,details?}`. The catch-all must
  honor framework-supplied status codes (`ErrorResponse`) rather than collapsing everything to 500,
  and — once Security lands — Spring Security's filter-chain rejections need an
  `AuthenticationEntryPoint`/`AccessDeniedHandler` emitting that same body.
- **Cross-user access → 404**, not 403 (no id enumeration).
- **RBAC via a single role + role hierarchy.** `User.role` is one enum (`TRADER|MODERATOR|ADMIN`),
  not a join table; a Spring `RoleHierarchy` (`ADMIN > MODERATOR > TRADER`) grants downward. Role
  travels as a **JWT claim** so authorization is stateless; the `JwtAuthFilter` maps the claim to
  authorities. `GUEST` is the anonymous principal, never a stored role. Registration always mints
  `TRADER`. Rationale: 4 roles with a natural superset structure don't need orthogonal multi-role
  bookkeeping.
- **Account blocking is the one per-request DB check.** Everything else about authorization is
  stateless, but a blocked account must lose access immediately rather than when its token expires
  (F004-FR-009), so `blocked` is re-checked per authenticated request.
- **Admin bootstrap is env-provisioned, and is the *only* way to become an admin in Phase 1.** On
  startup, if no admin exists, seed one from `ADMIN_EMAIL` / `ADMIN_PASSWORD` (BCrypt-hashed). No
  admin credential is committed to VCS and fresh environments get a working admin without manual DB
  edits. Registration cannot self-elevate.

## Non-inferable constraints (would not be obvious from code later)
- **CoinMarketCap free/basic is credit-metered** (~10k calls/month; credits weighted by coins-per-call)
  and returns cached quotes, not a stream. Do not add per-request CMC calls on the read path; only the
  poller talks to CMC. `CMC_API_KEY` is required for real data; **absence must degrade gracefully**
  (log a warning, serve an empty universe) rather than crash — the app must boot and pass tests with
  no key. A user-facing "refresh" reloads *stored* data and must never force an upstream call
  (F-001 business rules, PRD §4.1).
- **USD is the only convert currency** in Phase 1; `convertCurrency` exists on the row for future
  multi-fiat but is always `USD`.
- **`reject-if-already-satisfied`** is intentional (not a missing feature): active alerts always mean
  a future crossing. Do not "fix" it into fire-immediately. The PRD does not restate this rule; its
  silence was reviewed and treated as "not revisited", not as a reversal.
- **Alerts do not re-arm.** The PRD lifecycle is create → trigger once → clear. The earlier
  acknowledge-and-re-enable model was **superseded** and should not be reintroduced; re-arming means
  creating a new alert.
- **Personal dashboards are Phase 2, and are an explicit-include model.** The earlier Phase-1
  `Board` = *universe minus exclusions* design was **removed**, not deferred: the PRD's Phase-2
  dashboard starts empty and its owner explicitly adds approved assets. The two models are
  incompatible — do not revive exclusions. Phase 1's only persisted personalization is a per-user
  visible-column set.
- **One notification per alert trigger is enforced by a unique constraint**, not by control flow
  (F006-FR-009, PRD §3.5). A retried or overlapping evaluation must be unable to insert a duplicate.
- **`MODERATOR` is a reserved, unimplemented role** in Phase 1 — the PRD's role matrix grants it
  nothing. It exists in the enum + hierarchy only; do not add moderator-gated endpoints until a
  moderation domain is introduced by a new decision.
- **Several PRD open questions are answered provisionally** so slices aren't blocked (top-N, default
  and supported columns, page sizes, password rules, token/session lifetimes, lockout thresholds).
  Each is recorded on its slice in `slices.md` as provisional; treat them as defaults awaiting
  confirmation, not as settled decisions.

## Non-goals (Phase 1)
- Personal dashboards and portfolio management (both Phase 2).
- Android/iOS clients (Phase 2+).
- Stock-market support and any non-crypto asset type — modeled-for via `assetType`, not implemented.
- Runtime asset approval/removal and runtime public-dashboard configuration (Phase 2).
- Social / OAuth login (Phase 2).
- Arbitrary-coin tracking beyond top-N.
- Multi-instance operation / distributed scheduling (ShedLock).
- Historical price storage / charting.
- Multi-fiat or crypto-to-crypto conversion.
- Alert delivery channels beyond in-app (no market-alert email, no webhooks, no push). Transactional
  email exists **only** for password reset.
- Auto re-arm / cooldown alerts, server-side guest sessions.
- Financial news, AI assistant, trading/wallet/exchange integration, social features, multi-language UI.
