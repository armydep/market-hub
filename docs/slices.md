# Vertical Slices

Derived from [`Market_Hub_PRD_v0.1.md`](Market_Hub_PRD_v0.1.md). Each slice is independently
shippable and testable, sized for one focused session, and — from S3 onward — delivers a PRD feature
**end-to-end through both the backend and the SPA**, so every slice is demoable.

> **Renumbering notice.** S0 and S1 keep their original meaning and are already merged; commit
> messages referencing `feat(s0)` / `feat(s1)` still line up. **S2–S5 have been reassigned** — the
> pre-PRD plan had S2=auth, S3=boards, S4=alerts, S5=admin. Boards left Phase 1 entirely (the PRD
> moves personal dashboards to Phase 2 as F-005), so the old S3 has no successor here.

## Status

| Slice | Feature | PRD | Status |
|---|---|---|---|
| S0 | Skeleton + DB + health | — | ✅ merged |
| S1 | Market ingestion + read API | F-001 (partial) | ✅ merged |
| S2 | Market read API completion | F-001, F-002, F-003 | ✅ merged |
| S3 | Frontend foundation + Public Market Dashboard | F-001, F-002 | 🔄 in review |
| S4 | Asset Details | F-003 | ⬜ next |
| S5 | Auth core (register / sign in / sign out) | F-004 | ⬜ |
| S6 | Sign-in protection + account blocking | F-004 | ⬜ |
| S7 | Password reset | F-004 | ⬜ |
| S8 | Account management + display preferences | F-009, F-001 | ⬜ |
| S9 | Price alerts + evaluation | F-006 | ⬜ |
| S10 | Notifications | F-007 | ⬜ |
| S11 | Admin user management + audit | F-010 | ⬜ |

**Dependency graph**

```
S0 → S1 → S2 → S3 → S4
                S3 → S5 → {S6, S7, S8, S9}
                          S6 → S11
                          S9 → S10
S2 → S8   (column catalog)
S1 → S9   (symbol validation + post-poll hook)
```

Parallel opportunities: S4 ∥ S5 once S3 lands; S6/S7/S8/S9 are largely parallel once S5 lands.

---

## S0 — Skeleton + DB + health ✅ *(shipped)*
Spring Boot app on Postgres, `docker-compose.yml`, Flyway baseline (`V1__baseline.sql`), actuator
health, `ApiException` + `GlobalExceptionHandler`, springdoc/OpenAPI, `application.yml`.
- **Shipped:** `GET /api/actuator/health` = UP against a Testcontainers Postgres.

## S1 — Market ingestion + read API ✅ *(shipped)*
`V2__crypto_quotes.sql`. `PriceProvider` seam + `CoinMarketCapProvider` (WebClient →
`/v1/cryptocurrency/listings/latest`, USD, top-N). `@Scheduled` poller upserts the universe and
publishes `PollCompletedEvent`. `CoinColumn` catalog enum. Public reads:
`GET /api/market/coins?sort=&order=`, `GET /api/market/coins/{symbol}`.
- **Shipped:** universe populates with a key; empty universe and a healthy app without one.
- **Post-review hardening also merged:** the poll transaction no longer spans the HTTP call (+
  WebClient timeouts); framework status codes no longer collapse to 500; nulls sort last in both
  directions; duplicate symbols resolve deterministically; actuator/swagger excluded from request
  logging; `Persistable` id fix; partial-response guard bounded by `min(count, coinLimit)`.

---

## S2 — Market read API completion  *(deps: S1)*
Bring the S1 read API up to what F-001/F-002/F-003 actually require. Backend-only — the SPA that
consumes it arrives in S3.

- Pagination on `GET /api/market/coins` (`page`, `size`) returning a page envelope
  (`content`, `page`, `size`, `totalElements`, `totalPages`).
- Search (`q`) matching **name or symbol**, case-insensitive, substring.
- **Sort and search apply to the full dataset before pagination** (F001-FR-011, and the F-002
  business rule) — push both into the query; never filter or sort an already-paginated slice.
- Column-catalog endpoint `GET /api/market/columns` → supported columns plus the application default
  visible set, so the client never hardcodes them (F001-FR-005/006).
- Last successful market-data update time exposed for F001-FR-019 / F003-FR-006 (a `lastUpdatedAt`
  field on the list response, sourced from the poll cycle rather than a per-row `updatedAt`).
- Reject unsupported `size` values against the supported list rather than honoring arbitrary ones.

**Provisional defaults** (OQ-002/003/004 — confirm before or during this slice):
- Top-N = **100** (already the configured `POLLER_COIN_LIMIT` default).
- Supported columns = the existing `CoinColumn` catalog.
- Default visible = `marketCapRank, name, symbol, price, pctChange24h, marketCap, volume24h`.
- Supported page sizes = **20** (PRD-fixed default), 50, 100.

- **Ship:** the API can back a paginated, sorted, searchable grid and report its own freshness.
- **Test:** page boundaries; sort applied across the whole dataset, not per-page; case-insensitive
  name and symbol search; empty search result is `200` with an empty page, not 404; unsupported
  `size` → 400; unknown sort field → 400; `lastUpdatedAt` present and stable between polls.

## S3 — Frontend foundation + Public Market Dashboard  *(deps: S2)*
First slice with a client. Creates the `frontend/` module (sibling to `backend/`) on the stack
recorded in `constraints.md`: React + Vite + TypeScript, TanStack Query, TanStack Table, Zustand +
persist.

- Module scaffolding, dev server proxying `/api` → backend, production build, lint/format, test
  wiring.
- App shell: routing, layout, error boundary, loading and empty states.
- Public Market Dashboard as the default landing route (F-001) — grid, pagination, page-size picker,
  sortable columns, column show/hide (guest = default set, held client-side in Zustand + persist),
  automatic refresh, manual refresh control.
- Grid search (F-002) with a `No results found` empty state; clearing restores the full dataset.
- Refresh preserves page, size, sort, search, and visible columns (F001-FR-018); a failed refresh
  keeps the last good data on screen behind a basic failure indicator (F001-FR-020).
- Last successful update time displayed.

- **Ship:** a guest opens the app and browses, searches, sorts, and pages the live universe.
- **Test:** component/integration tests against a mocked API — sort and search hit the server rather
  than filtering the current page; refresh preserves state; failed refresh retains prior rows;
  page-size change resets to page 1; guest column choices survive a reload.

## S4 — Asset Details  *(deps: S3)*
Per-asset page reached by selecting any part of a row (F-003, and the PRD's explicit "selecting any
cryptocurrency row opens Asset Details" decision).

- Route + page rendering the stored quote for one asset, read from Market Hub's cache — never a live
  provider call.
- Unknown or absent symbol → clear not-found state backed by the existing 404 contract (F003-FR-007).
- Automatic refresh and last-successful-update time, consistent with S3.
- Backend: extend `GET /api/market/coins/{symbol}` only if the detail field set (OQ-005) needs more
  than the list projection. **Provisional:** full catalog plus `slug`, `assetType`,
  `convertCurrency`, `updatedAt`.

- **Ship:** clicking a row opens a readable detail page; a bad identifier shows a not-found state.
- **Test:** row click routes to the right asset; unknown symbol renders not-found (no crash, no empty
  shell); displayed values match the cached quote.

## S5 — Auth core  *(deps: S3)*
Registration, sign-in, sign-out, and protected access, end-to-end (F-004: FR-001/002/003/005/010).

- `V3__users.sql`: `users` with `email` (unique, lowercased), `password_hash`, `role` (default
  `TRADER`), `created_at`.
- `User` + repository, BCrypt encoder, `JwtService` (role as a claim), `JwtAuthFilter`
  (claim → authorities), `RoleHierarchy` (`ADMIN > MODERATOR > TRADER`), `SecurityConfig`
  (public: `/auth/**`, `GET /market/**`, `/actuator/**`, springdoc paths; everything else
  authenticated), `CurrentUser` helper.
- `POST /api/auth/register`, `POST /api/auth/login` → `{token,userId,email,role}`.
- Env-provisioned admin seeded on startup from `ADMIN_EMAIL`/`ADMIN_PASSWORD` when no admin exists.
- SPA: register / sign-in / sign-out screens, auth state in Zustand + persist, authenticated request
  wiring, redirect-to-sign-in when a guest requests a protected route, sign-out clears client state.
- **Security exception contract:** add an `AuthenticationEntryPoint` + `AccessDeniedHandler` emitting
  the same `{timestamp,status,error,message}` body, and stop the catch-all advice from turning
  `AccessDeniedException` into a 500 — a trap already flagged in review of the current handler.

**Provisional defaults** (OQ-008): password ≥ 8 characters; JWT lifetime 24h.

- **Ship:** register → sign in → reach a protected endpoint with the bearer token; the seeded admin
  can sign in.
- **Test:** register + login happy path; duplicate email → 409; bad credentials → 401; protected route
  without/with token → 401/200; tampered and expired tokens rejected; role claim → authorities; admin
  seed idempotent; registration cannot self-assign a non-`TRADER` role; a 403 really is a 403.

## S6 — Sign-in protection + account blocking  *(deps: S5)*
The defensive half of F-004 (FR-006/007/008/009), and the `blocked` flag S11 operates on.

- `V4__user_login_protection.sql`: `failed_login_attempts`, `locked_until`, `blocked` on `users`.
- Consecutive-failure counter; temporary rejection once the configured maximum is reached; counter
  resets on success (FR-006/007/008).
- Administratively blocked accounts are refused authentication **and** rejected on protected
  requests — blocking must take effect for an already-issued token, not only at sign-in (FR-009).
- Temporary failed-attempt blocking and administrative blocking are independent states.
- SPA: distinct error states for invalid credentials, temporarily blocked, and administratively
  blocked.

**Provisional defaults** (OQ-008): max 5 consecutive failures; 15-minute temporary block. Both
configurable.

- **Ship:** repeated bad passwords lock sign-in temporarily; a blocked account cannot use the app.
- **Test:** lockout triggers at exactly the configured threshold; a successful sign-in resets the
  counter; the lock expires; a blocked account is rejected at sign-in *and* on a protected call with a
  previously valid token; the two block types don't mask each other.

## S7 — Password reset  *(deps: S5)*
F-004-FR-004 plus the PRD's transactional-email integration (§4.2).

- `V5__password_reset_tokens.sql`: single-use, time-limited, unpredictable tokens (store a hash, not
  the token itself).
- `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm`.
- The request endpoint responds identically whether or not the email exists — no account enumeration.
- `EmailSender` seam mirroring `PriceProvider`: a logging/no-op implementation is the default, so the
  app boots and tests run with no mail configuration and OQ-007 stays open without blocking the slice.
- Token invalidated on use and on password change; a successful reset clears any failed-attempt lock.
- SPA: forgot-password and reset-password screens.

**Provisional default** (OQ-008): 30-minute single-use token.

- **Ship:** a user completes the full reset flow and signs in with the new password.
- **Test:** happy path; expired token rejected; reused token rejected; unknown email gives the same
  response as a known one; the old password stops working; tokens never appear in logs.

## S8 — Account management + display preferences  *(deps: S5, S2)*
F-009, and the registered-user half of F001-FR-007.

- `V6__user_preferences.sql`: per-user visible-column selection (1:1 with `users`).
- `GET /api/account`, `PATCH /api/account`, `POST /api/account/password`.
- `GET`/`PUT /api/account/preferences` — visible columns validated against the S2 column catalog;
  unknown keys rejected at write time.
- Password change requires the current password.
- Role, block status, and audit fields are never user-editable (F-009 business rule).
- SPA: account view/edit, change-password form, and dashboard column preferences that persist for
  registered users and survive re-login (guests keep the client-side-only behavior from S3).

**Provisional default** (OQ-006): email is **not** user-editable in Phase 1; treat the account as
display preferences plus password until confirmed.

- **Ship:** a registered user edits their account, changes their password, and their column choices
  survive sign-out and sign-in.
- **Test:** read/update own account; password change requires the correct current password;
  preferences round-trip; invalid column key → 400; cross-user account access → 404.

## S9 — Price alerts + evaluation  *(deps: S5, S1)*
F-006, end-to-end.

- `V7__price_alerts.sql`. Alert CRUD scoped to `CurrentUser`.
- Condition values are **`ABOVE_OR_EQUAL` / `BELOW_OR_EQUAL`** (PRD naming; the ≥ / ≤ semantics are
  unchanged from the original model).
- Creation rejects an unknown symbol (400) and — **retained deliberately** — a condition the latest
  quote already satisfies (400), so an active alert always means a *future* crossing. The PRD does not
  mention this rule; its silence is treated as "not revisited", not as a reversal.
- `AlertEvaluationService` runs as the post-poll hook on `PollCompletedEvent`; fires once, setting
  `triggeredAt`/`triggeredPrice` and `active=false`. Evaluation runs only against a successfully
  stored poll (PRD §3.5), which the poller's existing skip-on-empty and partial-response guards
  already provide.
- `GET /api/alerts`, `GET /api/alerts/triggered`, `POST /api/alerts`, `PATCH /api/alerts/{id}`,
  `DELETE /api/alerts/{id}`, `POST /api/alerts/{id}/clear`.
- **No re-enable / re-arm** — the PRD lifecycle replaces the earlier acknowledge-and-re-enable model.
- SPA: alert list (active + triggered), create/edit/delete, clear a triggered alert, empty states.

- **Ship:** a user creates an alert, a poll cycle fires it once, and it appears under triggered.
- **Test:** reject-if-satisfied and unknown-symbol at creation; fires exactly once and sets the
  triggered fields; an already-fired alert never re-fires on later cycles; update and delete are
  restricted to active alerts; clear removes it from the triggered list; ownership isolation → 404; a
  symbol that left the universe is "not evaluable", not an error.

## S10 — Notifications  *(deps: S9)*
F-007. A separate entity and lifecycle from the alert that spawned it.

- `V8__notifications.sql`: owner, originating alert, symbol, target price, condition, triggered price,
  triggered time.
- Exactly **one** notification per alert trigger; later evaluation cycles must not produce duplicates
  (F006-FR-009, F007-FR-001, PRD §3.5) — enforce at the data layer, not only by control flow.
- `GET /api/notifications`, `POST /api/notifications/{id}/clear`.
- SPA: notification list with an at-a-glance indicator in the shell, clear action, empty state.

- **Ship:** an alert fires and the owner sees exactly one notification describing it.
- **Test:** one trigger → one notification; repeated polls satisfying the same condition add none;
  content identifies asset, condition, target, and trigger time; clear hides it; ownership isolation.

## S11 — Admin user management + audit  *(deps: S6)*
F-010 and PRD §3.7.

- `V9__admin_audit_log.sql`: actor, action, target user, timestamp.
- Admin-only API guarded by `hasRole('ADMIN')` via the hierarchy: `GET /api/admin/users` (paged),
  `POST /api/admin/users/{id}/block`, `POST /api/admin/users/{id}/unblock`.
- Every block/unblock writes an audit record in the same transaction as the state change.
- **Role management is deliberately out of Phase 1** — the PRD's admin capability is view +
  block/unblock + audit only, and its role matrix has no "change role" row. Admins come solely from
  the `ADMIN_EMAIL`/`ADMIN_PASSWORD` seed. (This drops `PATCH /admin/users/{id}/role` and its
  last-admin lockout guard from the pre-PRD plan; reintroduce only via a new decision.)
- SPA: admin user list with block/unblock controls, admin-only navigation, 403 handling.

**Provisional default** (OQ-012): administration lives inside the main SPA under `/admin` routes, not
a separate application.

- **Ship:** the seeded admin lists users and blocks/unblocks one; the blocked user loses access.
- **Test:** non-admin → 403 on every `/admin/**` route; block/unblock round-trip; blocking takes
  effect for an existing session; audit rows carry actor/action/target/time; audit records are not
  user-facing.

---

## Not slices

Explicitly **not** scheduled here:

- **Phase 2 features** — F-005 personal dashboards, F-008 portfolio, F-011 Android, F-012 stocks,
  F-015 social auth, F-016 asset administration, F-017 runtime dashboard configuration.
- **Future** — F-013 news, F-014 AI assistant, alert delivery channels beyond in-app.
- **Non-functional work with no agreed target yet** — OQ-009 (latency, concurrency, availability) and
  OQ-010 (retention). Both need numbers before they can become a slice; neither blocks S2–S11.

## Slice test harness

- Backend: Testcontainers Postgres for every integration test; a stub `PriceProvider` seeds a
  deterministic universe so no test ever calls CoinMarketCap. Surefire pins `app.poller.enabled=false`
  and an empty API key so an exported `CMC_API_KEY` can't leak into a run.
- Frontend: component/integration tests against a mocked API — no live backend, no live provider.
- Delegate suite runs to the **test-runner** subagent; delegate a completed slice to the
  **code-reviewer** subagent and resolve every CRITICAL before pushing.
