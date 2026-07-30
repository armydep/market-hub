# S2 — Market read API completion

**Status:** in progress
**Depends on:** S1 (merged)
**PRD:** [`../Market_Hub_PRD_v0.1.md`](../Market_Hub_PRD_v0.1.md) — F-001, F-002, F-003
**Plan entry:** [`../slices.md`](../slices.md) § S2

## Goal

Bring the S1 read API up to what the Public Market Dashboard actually requires, so S3 can build the
grid against a stable contract. Backend-only: there is no SPA yet.

S1 shipped an unpaginated, unsearchable list. F-001 needs pagination, search, a server-owned column
catalog, and a freshness timestamp — none of which exist today.

## PRD traceability

| Requirement | Covered by |
|---|---|
| F001-FR-005 | `GET /api/market/columns` returns the supported field set |
| F001-FR-006 | same endpoint returns the application default visible set |
| F001-FR-010 | `sort` + `order` on the list endpoint (S1, retained) |
| F001-FR-011 | sort and search execute in the query, before pagination |
| F001-FR-013 | `page` / `size` with a page envelope |
| F001-FR-014 | default page size 20 |
| F001-FR-015 | `size` restricted to a configured supported list |
| F001-FR-019 | `lastUpdatedAt` on the list response |
| F002-FR-001/002/003 | `q` matches name or symbol, case-insensitive |
| F002-FR-004/005 | matching rows returned; no match ⇒ empty page, HTTP 200 |
| F002-FR-006 | absent/blank `q` returns the full dataset |
| F003-FR-006 | `lastUpdatedAt` also available for the detail page (via the list contract) |

PRD §3.2 and §7.3 additionally require that page sizes, default page size, and default visible
columns be configuration rather than constants — so all three are externalized.

## Resolved open questions

Decided before implementation; recorded here because `slices.md` carried them as provisional.

| PRD OQ | Question | Decision |
|---|---|---|
| OQ-003 | Default visible columns | **All 10 supported columns.** Default set == supported set for now; they stay separately modelled so S8 can diverge without a contract change. |
| OQ-004 | Supported page sizes | **20, 50, 100.** 20 is the PRD-fixed default. At top-N=100, `size=100` is a deliberate "whole universe" option. |
| OQ-002 | Top-N | Unchanged at **100** (`POLLER_COIN_LIMIT`). Not re-litigated by this slice. |
| — | `lastUpdatedAt` source | **`MAX(updated_at)` across quotes.** Every successful poll rewrites every row, so the max *is* the last successful cycle. No new table, no restart-sensitive state. `null` on an empty universe. |
| — | Search semantics | **Substring, case-insensitive, `name` OR `symbol`.** `q=coin` matches Bitcoin; `q=eth` matches both ETH and Ethereum. |

## In scope

1. **Pagination** on `GET /api/market/coins` via `page` (0-based) and `size`.
2. **Search** via `q`, substring and case-insensitive, against `name` or `symbol`.
3. **Sort and search pushed into the query.** Both apply to the complete matching dataset *before*
   the page is cut. This is a correctness rule (F001-FR-011), not an optimization.
4. **Column catalog endpoint** `GET /api/market/columns`, so the client never hardcodes field names.
5. **`lastUpdatedAt`** on the list response.
6. **Configuration** for default page size, supported page sizes, and default visible columns,
   validated at startup.

## Out of scope

- Any frontend. The grid, page-size picker, search box, and refresh control are S3.
- Persisting a user's column choice — that is S8 (`UserPreference`); this slice only *publishes* the
  catalog and the application default.
- Changing the detail endpoint's field set. OQ-005 is S4's problem; `GET /market/coins/{symbol}`
  keeps its current projection.
- Sorting or filtering on any field outside the existing `CoinColumn` catalog.
- Cursor/keyset pagination, multi-field sort, fuzzy or tokenized search, relevance ranking.
- A `poll_run` table or any other new persisted poller state.
- Caching. The DB read-model is already the cache (see `constraints.md`).

## API contract

### `GET /api/market/coins`

| Param | Type | Default | Rules |
|---|---|---|---|
| `page` | int | `0` | 0-based; negative ⇒ 400 |
| `size` | int | `20` | must be in the supported list ⇒ else 400 |
| `sort` | string | `marketCapRank` | must be a catalog key ⇒ else 400 |
| `order` | string | `asc` | `asc` \| `desc` (case-insensitive) ⇒ else 400 |
| `q` | string | *(none)* | blank/absent ⇒ no filter |

```json
{
  "content": [ { "cmcId": 1, "symbol": "BTC", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "lastUpdatedAt": "2026-07-30T22:00:00Z"
}
```

- Nulls sort last in both directions (retained from S1 hardening).
- A page past the end is **200** with empty `content` and correct totals — not 404.
- `lastUpdatedAt` is `null` when the universe is empty.

### `GET /api/market/columns`

```json
{
  "supported": ["symbol", "name", "marketCapRank", "..."],
  "defaultVisible": ["symbol", "name", "marketCapRank", "..."],
  "supportedPageSizes": [20, 50, 100],
  "defaultPageSize": 20
}
```

Page-size metadata rides on this endpoint so the client can render the picker (F001-FR-015) without
a second call or hardcoded values.

### `GET /api/market/coins/{symbol}`

Unchanged.

## Acceptance criteria

**Pagination**
- [ ] Default request returns at most 20 rows with `page=0`, `size=20`, and totals for the full set.
- [ ] `page=1&size=20` returns rows 21–40 and no row present on page 0.
- [ ] A page beyond the last returns 200, empty `content`, unchanged `totalElements`/`totalPages`.
- [ ] `size` values 50 and 100 are accepted; `size=25` returns 400; `size=0` and `size=-1` return 400.
- [ ] `page=-1` returns 400.

**Sort before pagination (F001-FR-011)**
- [ ] With more rows than one page, `sort=price&order=desc&size=<n>` puts the globally highest-priced
      coin on page 0 — proving the sort ran across the whole dataset, not within a page.
- [ ] Rows with a null value in the sorted column appear last in both `asc` and `desc`.
- [ ] `sort=bogus` returns 400; `order=sideways` returns 400.

**Search (F-002)**
- [ ] `q` matching a name substring returns that coin (`q=coin` ⇒ Bitcoin).
- [ ] `q` matching a symbol substring returns that coin (`q=TC` ⇒ BTC).
- [ ] Search is case-insensitive in both directions (`q=btc` and `q=BTC` are equivalent).
- [ ] A non-matching `q` returns 200 with empty `content` and `totalElements: 0` — never 404.
- [ ] Blank or absent `q` returns the full dataset.
- [ ] Search and sort compose: results are filtered first, then globally sorted, then paginated.
- [ ] LIKE metacharacters are literal — `q=%` does not match everything, `q=_` does not wildcard.

**Column catalog**
- [ ] `GET /api/market/columns` returns all 10 catalog keys under `supported`.
- [ ] `defaultVisible` matches the configured default set.
- [ ] `supportedPageSizes` and `defaultPageSize` reflect configuration.
- [ ] The endpoint is public (no auth), consistent with the other market reads.
- [ ] A configured default-visible column outside the catalog fails application startup.

**Freshness**
- [ ] `lastUpdatedAt` is present and non-null once a poll has stored quotes.
- [ ] It equals the newest `updated_at` across the universe.
- [ ] It is `null` when the universe is empty.

## Test plan

Backend integration tests (`MarketControllerIT`) over a Testcontainers Postgres with the stub
provider, seeding **more coins than one page** so pagination and global sort are genuinely exercised
— a single-page fixture would let a per-page sort bug pass. Plus a startup test for the
invalid-configured-column case.

## Notes / risks

- **Breaking response shape.** `GET /api/market/coins` changes from a bare JSON array to an envelope.
  There is no client yet (the SPA arrives in S3), so this is the cheapest possible moment to change
  it. Existing S1 tests asserting `CoinResponse[]` will be updated as part of this slice.
- **Default visible == supported today.** Making them one field would be simpler now but would force
  a contract change in S8, when a user's saved subset has to be distinguished from the catalog.
- **`q` is escaped before it reaches LIKE**, otherwise `%` and `_` from user input silently become
  wildcards.
