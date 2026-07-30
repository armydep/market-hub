# Domain Model

Aligned to [`Market_Hub_PRD_v0.1.md`](Market_Hub_PRD_v0.1.md). Crypto-only Phase 1. All monetary
values in USD.

Phase 1 is delivered as a **web application**: a Spring Boot backend (`backend/`) plus a React SPA
(`frontend/`). This document covers the persisted model, which lives entirely in the backend; guest
state is client-side only and is never persisted.

## Entities

### User
Registered account. Owns alerts, notifications, and display preferences. Guests are **not**
persisted — no anonymous rows exist.

| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| email | varchar unique | stored lowercased; uniqueness is case-insensitive |
| passwordHash | varchar | BCrypt |
| role | varchar | `TRADER` \| `MODERATOR` \| `ADMIN`; default `TRADER` at registration |
| blocked | boolean | administrative block (F-010); default false |
| failedLoginAttempts | int | consecutive failures; reset to 0 on success (F004-FR-008) |
| lockedUntil | timestamptz? | temporary failed-attempt lock; null when not locked |
| createdAt | timestamptz | |

**Two independent block states.** `blocked` is an administrator action with no expiry; `lockedUntil`
is an automatic, self-expiring consequence of consecutive sign-in failures. They must not be
collapsed into one flag — an admin unblock must not clear a brute-force lock, and a lock expiring
must not restore a blocked account.

**Blocking applies to existing sessions.** Because the role travels in a JWT claim and authorization
is otherwise stateless, `blocked` is the one thing that must be re-checked per request; otherwise a
blocked user keeps full access until their token expires (F004-FR-009).

**Roles / RBAC.** Single `role` per user (enum column, not a join table). A Spring `RoleHierarchy`
grants downward: `ADMIN > MODERATOR > TRADER`, so an admin implicitly holds trader authorities. The
role is carried as a **JWT claim** (stateless authz — no per-request role lookup). Registration
always mints `TRADER`.
- **`GUEST` is not a stored role** — it is the *anonymous/unauthenticated* principal (no user row, no
  token). Public market reads and asset details are the only things a guest can do.
- **`MODERATOR` is reserved but unused in Phase 1** — the PRD grants it no workflow. The value exists
  in the enum + hierarchy so the seam is ready; no endpoint grants or requires it.
- **Roles are not changeable at runtime in Phase 1.** The PRD's admin capability is view +
  block/unblock + audit only. Admins exist solely via the env-provisioned startup seed.

### CryptoQuote — poller-owned read-model (NOT user data)
One row per coin in the tracked top-N universe. Fully re-upserted every poll cycle. This is a cache,
not a system of record; it may be truncated/rebuilt at will.

| field | type | notes |
|---|---|---|
| cmcId | int | PK — CoinMarketCap id, the provider's stable identity |
| symbol | varchar | e.g. `BTC`; the join key used by user data |
| name, slug | varchar | `name` and `symbol` are both searchable (F-002) |
| assetType | varchar | always `CRYPTO` in Phase 1; the extension seam |
| marketCapRank | int | |
| price | numeric(30,10) | USD |
| pctChange1h / 24h / 7d | numeric(18,6) | |
| marketCap, volume24h | numeric(30,2) | |
| circulatingSupply | numeric(30,4) | |
| convertCurrency | varchar | always `USD` in Phase 1 |
| updatedAt | timestamptz | set each upsert |

**Why identity+quote are one row (not split Instrument/Quote):** the whole top-N set is refreshed
every cycle, so there is no slow-vs-fast field split to exploit; splitting would add a join and a
lifecycle with zero Phase-1 benefit. `assetType` + `cmcId`-as-provider-id are the seams that let a
split (or a second provider) happen later without touching user data.

**Symbol is indexed, not unique.** The provider can return duplicate tickers within a large enough
top-N. A unique constraint would fail the whole poll cycle on a collision, which is worse than the
collision itself; lookups instead resolve deterministically to the highest-ranked match.

### UserPreference — persisted display settings
Phase 1's registered-user personalization, and the *only* per-user view state that is persisted
(F001-FR-007, F009-FR-004).

| field | type | notes |
|---|---|---|
| userId | bigint | PK **and** FK → User, ON DELETE CASCADE (1:1) |
| visibleColumnsJson | text | ordered list of visible column keys; validated against the column catalog |
| updatedAt | timestamptz | |

Guests get the application default column set and hold any changes client-side only
(F001-FR-008/023). Sorting, page, and page size are transient UI state and are **not** persisted for
anyone in Phase 1.

### PriceAlert — one-shot, in-app
| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| userId | bigint | FK → User, ON DELETE CASCADE |
| symbol | varchar | must exist in the current universe at creation |
| condition | varchar | `ABOVE_OR_EQUAL` \| `BELOW_OR_EQUAL` |
| targetPrice | numeric(30,10) | > 0 |
| active | boolean | true until it fires |
| triggeredAt | timestamptz? | set on fire |
| triggeredPrice | numeric(30,10)? | price snapshot at fire time |
| clearedAt | timestamptz? | set when the owner clears it from the triggered list |
| createdAt | timestamptz | |

**Lifecycle (PRD F-006):**
`active=true` → condition met at evaluation → set `triggeredAt`, `triggeredPrice`, `active=false` →
owner clears it → `clearedAt` set and it leaves the visible triggered list. An **active** alert can
be updated or deleted by its owner; a **triggered** one can only be cleared. There is **no re-arm, no
cooldown, and no re-enable** — re-arming means creating a new alert.

**Reject-if-already-satisfied:** creation is refused (HTTP 400) when the latest quote already
satisfies the condition (`ABOVE_OR_EQUAL` with price ≥ target, `BELOW_OR_EQUAL` with price ≤ target).
This makes the "already true at creation" case a defined rule rather than an artifact of evaluation
order — so `active` alerts always represent a *future* crossing. Creation is also refused for a
symbol absent from the current universe. *The PRD does not mention this rule; its silence is treated
as "not revisited", not as a reversal.*

### Notification — in-app, one per alert trigger
Separate from the alert so that clearing a notification and clearing a triggered alert are
independent actions (F-007).

| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| userId | bigint | FK → User, ON DELETE CASCADE |
| alertId | bigint | FK → PriceAlert, ON DELETE CASCADE; **unique** — the duplicate-suppression guarantee |
| symbol | varchar | denormalized: the alert may later be deleted, the notification must stay readable |
| condition | varchar | denormalized for the same reason |
| targetPrice | numeric(30,10) | denormalized |
| triggeredPrice | numeric(30,10) | |
| triggeredAt | timestamptz | |
| clearedAt | timestamptz? | set when the owner clears it from the visible list |
| createdAt | timestamptz | |

**Duplicate suppression is a constraint, not a code path.** `UNIQUE(alertId)` is what actually
guarantees "one notification per trigger, no duplicate visible results" (F006-FR-009, F007-FR-001,
PRD §3.5). Control flow alone is not sufficient — a retried or overlapping evaluation must be *unable*
to insert a second row.

**Fields are denormalized on purpose:** F007-FR-003 requires a notification to identify its asset,
target, condition, and trigger time, and the originating alert may be gone by the time it is read.

### AdminAuditLog — administrator action record
| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| actorUserId | bigint | the administrator who performed the action |
| action | varchar | `BLOCK_USER` \| `UNBLOCK_USER` in Phase 1 |
| targetUserId | bigint | the affected account |
| createdAt | timestamptz | |

Written in the **same transaction** as the state change it records, so a block can never succeed
un-audited. Not user-facing. Neither reference cascades on user delete — an audit trail that
disappears with its subject is not an audit trail; retention is OQ-010.

### PasswordResetToken
| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| userId | bigint | FK → User, ON DELETE CASCADE |
| tokenHash | varchar | **hash** of the token; the raw value is emailed and never stored |
| expiresAt | timestamptz | short-lived |
| usedAt | timestamptz? | single-use marker |
| createdAt | timestamptz | |

Storing only a hash means a database read cannot be replayed into an account takeover. Tokens are
single-use, time-limited, and invalidated on password change (PRD §3.3).

## Relationships & cardinalities
- `User 1—1 UserPreference` (cascade), `User 1—* PriceAlert` (cascade),
  `User 1—* Notification` (cascade), `User 1—* PasswordResetToken` (cascade).
- `PriceAlert 1—1 Notification` (cascade, enforced by `UNIQUE(alertId)`).
- `AdminAuditLog` references users **without** cascade (see above).
- **PriceAlert references quotes by `symbol` string — there is no FK to CryptoQuote.**
  Rationale: the top-N universe churns (coins enter/leave) and provider ids are provider-specific;
  loose coupling keeps alerts durable across universe refreshes and a possible provider swap. Cost: a
  symbol may transiently reference a coin not currently in the cache — treated as "not evaluable this
  cycle", never an integrity error.

## Cross-cutting invariants
- Ownership: every alert/notification/account/preference operation is scoped to the authenticated
  `userId`; cross-user access returns 404 (not 403) to avoid id enumeration.
- `visibleColumnsJson` validates against a single server-defined **column catalog** (the quote fields
  exposed to the UI); unknown keys are rejected at write time. The same catalog backs the sortable-
  and searchable-column contract of the market read API.
- Sorting and searching apply to the **complete matching dataset before pagination** — never to an
  already-paginated slice (F001-FR-011).
- Symbol-collision risk across asset types is **accepted** in crypto-only Phase 1 (symbol treated as
  unique within the crypto universe, resolved by rank on collision); revisit when `assetType` gains
  non-CRYPTO values.
- `triggeredPrice` is stored because latest-quote-only retains no price history — it is the only
  record of the firing price.
- Alerts are evaluated **only** after a successful poll that actually stored data; a skipped or
  partial cycle must not trigger evaluation (PRD §3.5).

## Phase 2 (modeled for, not built)

- **Personal dashboards (F-005).** A user-owned, named dashboard containing assets the owner
  **explicitly adds** from an administrator-approved universe. This replaces the earlier Phase-1
  `Board`/`BoardExclusion` "universe minus exclusions" model, which was removed when the PRD moved
  dashboards to Phase 2 — the two are not compatible, and the exclusion model should not be revived.
- **Approved-asset list (F-016).** Assets become available to dashboards only after administrator
  approval, with approval/removal audited through the same `AdminAuditLog` seam.
- **Portfolio (F-008)**, **stocks (F-012)** via `assetType`, and **social auth (F-015)**.
