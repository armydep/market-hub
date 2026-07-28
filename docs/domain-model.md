# Domain Model

Backend service only. Crypto-only Phase 1. All monetary values in USD.

## Entities

### User
Registered account. Owns boards and alerts. Guests are **not** persisted — no anonymous rows exist.

| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| email | varchar unique | stored lowercased; uniqueness is case-insensitive |
| passwordHash | varchar | BCrypt |
| role | varchar | `TRADER` \| `MODERATOR` \| `ADMIN`; default `TRADER` at registration |
| createdAt | timestamptz | |

**Roles / RBAC.** Single `role` per user (enum column, not a join table). A Spring `RoleHierarchy`
grants downward: `ADMIN > MODERATOR > TRADER`, so an admin implicitly holds trader authorities.
The role is carried as a **JWT claim** (stateless authz — no per-request DB lookup). Only an admin
may change another user's role; registration always mints `TRADER`.
- **`GUEST` is not a stored role** — it is the *anonymous/unauthenticated* principal (no user row,
  no token), consistent with "guests are not persisted". Public GET market reads are the only thing
  a guest can do.
- **`MODERATOR` is reserved but unused in Phase 1** — no user-generated content exists to moderate.
  The value exists in the enum + hierarchy so the seam is ready; no endpoint grants or requires it yet.

### CryptoQuote — poller-owned read-model (NOT user data)
One row per coin in the tracked top-N universe. Fully re-upserted every poll cycle. This is a cache,
not a system of record; it may be truncated/rebuilt at will.

| field | type | notes |
|---|---|---|
| cmcId | int | PK — CoinMarketCap id, the provider's stable identity |
| symbol | varchar | e.g. `BTC`; the join key used by user data |
| name, slug | varchar | |
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

### Board — user's customizable view over the universe
Semantics: **universe minus exclusions**. A board renders the entire tracked universe except its
excluded symbols, with its own visible-column set and sort. Boards do **not** hold a curated coin
list (watchlist model was explicitly rejected).

| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| userId | bigint | FK → User, ON DELETE CASCADE |
| name | varchar | non-blank; **unique per user** |
| position | int | user-defined board ordering |
| columnsJson | text | ordered list of visible column keys; validated against the column catalog |
| sortField | varchar | must be in the column catalog |
| sortDir | varchar | `ASC` \| `DESC` |
| createdAt | timestamptz | |

### BoardExclusion
Symbols hidden from one board.

| field | type | notes |
|---|---|---|
| boardId | bigint | FK → Board, ON DELETE CASCADE |
| symbol | varchar | |
| — | | PK = (boardId, symbol) |

### PriceAlert — one-shot, in-app
| field | type | notes |
|---|---|---|
| id | bigint identity | PK |
| userId | bigint | FK → User, ON DELETE CASCADE |
| symbol | varchar | must exist in the current universe at creation |
| direction | varchar | `ABOVE` \| `BELOW` |
| targetPrice | numeric(30,10) | > 0 |
| active | boolean | true until it fires |
| acknowledged | boolean | |
| triggeredAt | timestamptz? | set on fire |
| triggeredPrice | numeric(30,10)? | price snapshot at fire time |
| createdAt | timestamptz | |

**One-shot lifecycle:**
`active=true` → condition met at evaluation → set `triggeredAt`,`triggeredPrice`, `active=false`,
`acknowledged=false`. User acknowledges → `acknowledged=true`. User re-enables → `active=true`,
clear `triggeredAt`/`triggeredPrice`. No auto re-arm, no cooldown.

**Reject-if-already-satisfied:** creation is refused (HTTP 400) when the latest quote already
satisfies the condition (`ABOVE` with price ≥ target, `BELOW` with price ≤ target). This makes the
"already true at creation" case a defined rule, not an artifact of evaluation order — so `active`
alerts always represent a *future* crossing. Creation is also refused for a symbol absent from the
current universe.

## Relationships & cardinalities
- `User 1—* Board` (cascade), `Board 1—* BoardExclusion` (cascade), `User 1—* PriceAlert` (cascade).
- **Board and PriceAlert reference quotes by `symbol` string — there is no FK to CryptoQuote.**
  Rationale: the top-N universe churns (coins enter/leave) and provider ids are provider-specific;
  loose coupling keeps exclusions and alerts durable across universe refreshes and a possible
  provider swap. Cost: a symbol may transiently reference a coin not currently in the cache — treated
  as "not shown"/"not evaluable this cycle", never an integrity error.

## Cross-cutting invariants
- Ownership: every Board/PriceAlert operation is scoped to the authenticated `userId`; cross-user
  access returns 404 (not 403) to avoid id enumeration.
- `columnsJson` and `sortField` validate against a single server-defined **column catalog** (the
  quote fields exposed to the UI); unknown keys are rejected at write time.
- Symbol-collision risk across asset types is **accepted** in crypto-only Phase 1 (symbol treated as
  unique within the crypto universe); revisit when `assetType` gains non-CRYPTO values.
- `triggeredPrice` is stored because latest-quote-only retains no price history — it is the only
  record of the firing price.
