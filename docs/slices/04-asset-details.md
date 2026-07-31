# S4 — Asset Details

**Status:** in progress
**Depends on:** S3 (merged)
**PRD:** [`../Market_Hub_PRD_v0.1.md`](../Market_Hub_PRD_v0.1.md) — F-003
**Plan entry:** [`../slices.md`](../slices.md) § S4

## Goal

Give the dashboard's rows somewhere to go. A guest or registered user selects any part of a
cryptocurrency row and reaches a readable detail page for that asset, reading Market Hub's stored
quote — never a live provider call.

**Scope is narrower than S2/S3.** Backend investigation (see "Resolved open questions") found that
`CoinResponse` — the DTO both the list and detail endpoints already return — contains every field
a detail page could plausibly want. This is a **frontend-only slice**: no backend PR.

## PRD traceability

| Requirement | Covered by |
|---|---|
| F003-FR-001 | Detail page renders the full stored quote for the selected symbol |
| F003-FR-002 | Reads via the existing `GET /api/market/coins/{symbol}`, which serves stored data only |
| F003-FR-003 | Grouped layout (header / price / market data / footer) — see Architecture decisions |
| F003-FR-004 | Route added outside any auth guard; page is reachable with no sign-in, same as the dashboard |
| F003-FR-005 | Automatic refresh, same cadence and background-pause as the dashboard |
| F003-FR-006 | Last successful update time displayed, sourced from the coin's own `updatedAt` |
| F003-FR-007 | Unknown/invalid symbol renders a page-scoped not-found state, not a crash or blank page |

Also: PRD §5.2's own browsing-flow diagram draws row-selection as the entry point into Asset
Details, and F001-FR-009 ("selecting a cryptocurrency row shall open its Asset Details page")
originates the requirement from the dashboard side — both are satisfied by the same click handler.

## Resolved open questions

Three genuine open points remained after the backend investigation. They were put to the product
owner; the prompt went unanswered, so **each resolves to its recommended default** and is flagged
here explicitly rather than presented as a confirmed decision — revisit if any pick is wrong.

| Question | Decision | Why |
|---|---|---|
| Naming: PRD says "Asset Details," every existing file says "coin" | **`/coins/:symbol` route, coin-named component/hook** (`useCoin`). The PRD's "Asset Details" is the page's *user-facing label*, not its identifier. | Matches the existing API path 1:1 and S1-S3's domain language (`CoinResponse`, `useCoins`, `CoinGrid`, `/market/coins`) exactly. Phase 2's real asset abstraction (stocks, F-012) is deferred to when it's actually needed rather than anticipated now. |
| OQ-005 — which of `CoinResponse`'s 14 fields does the detail page show, and how organized | **Grouped**: header (name, symbol, rank) · price block (price, 1h/24h/7d % change) · market data block (market cap, 24h volume, circulating supply) · footer (last updated). `cmcId`, `slug`, `assetType`, `convertCurrency` are not shown as raw values. | `cmcId` is an internal provider id with no user meaning; `slug` isn't display text; `assetType` is always `CRYPTO` and `convertCurrency` always `USD` in Phase 1 — showing either as a literal field would be noise, not information. |
| Does this slice touch the backend | **No — frontend-only.** | `CoinResponse` (`backend/src/main/java/com/am/market_hub/market/dto/CoinResponse.java`) already returns the identical 14-field projection for both the list and detail endpoints — confirmed by reading `CoinResponse::from` and `CoinPageResponse.from`, which call the same factory. The old provisional note in `slices.md` ("extend the endpoint if OQ-005 needs more... full catalog plus slug, assetType, convertCurrency, updatedAt") describes exactly what's already there. `MarketControllerIT.getBySymbolIsCaseInsensitive` and `.unknownSymbolReturns404` already cover the two backend behaviors this page depends on. |

## In scope

1. **Route** `/coins/:symbol` rendering a new `CoinDetailPage`.
2. **`useCoin(symbol)` hook** — single-coin fetch, same TanStack Query conventions as `useCoins`
   (`refetchInterval: REFRESH_INTERVAL_MS`, `refetchIntervalInBackground: false`), erroring via the
   existing `ApiError` so 404 is distinguishable from other failures.
3. **Row navigation** from the dashboard grid — the whole row becomes a real link (see Architecture
   decisions), not just one cell.
4. **Not-found state** for a syntactically valid route with no matching symbol, distinct from the
   app's generic "page not found" catch-all.
5. **Last-updated display and manual/automatic refresh**, consistent with the dashboard.

## Out of scope

- **Any backend change** — see resolved question 3.
- **Persisting anything new.** The page is read-only, per F-003's business rules.
- **A link out to CoinMarketCap's own page via `slug`.** No PRD requirement drives it, and `slug`
  isn't otherwise surfaced as text — introducing an external link here would be scope invented
  by this slice, not asked for.
- **A shared page-shell/breadcrumb abstraction.** Two pages exist after this slice; a shared shell
  is exactly the kind of premature abstraction the project avoids — revisit once S5+ adds enough
  pages that the duplication actually hurts.
- **Auth-gating.** The page is public per F003-FR-004, identical to the dashboard — no route guard
  needed yet, since S5 hasn't landed.
- **Historical price data or charting.** Explicitly out of scope for Phase 1 per `constraints.md`.

## Architecture decisions

### Whole-row click, not a single-cell link

The PRD states this as a requirement, not a design choice: F001-FR-009 says selecting a row opens
Asset Details, and F-003's own description repeats it. Implemented as a real `<Link>` wrapping the
row's content (not a bare `onClick` + `useNavigate`) — a raw click handler on a `<tr>` has no
`href`, so it silently drops the browser affordances a real anchor gives for free: right-click →
open in new tab, middle-click, ctrl/cmd-click. Getting this right the naive way would be a visible
regression for anyone used to how links behave everywhere else on the web.

### Refresh cadence matches the dashboard exactly

Not a choice — PRD's F-003 business rules state refresh frequency "follows the web application's
refresh behavior." Reuses `REFRESH_INTERVAL_MS` from `useCoins.ts` rather than defining a second
constant that could drift out of sync with the dashboard's.

### Failed refresh never blanks the page

Same mechanism as the dashboard (S3): TanStack Query retains the last successful `data` while
`isError` is set, so the render path is "show the coin if `data` exists" and, independently, "show
a failure indicator if `isError`" — never an either/or. This satisfies the PRD's failure-behavior
section directly ("the latest successfully stored data remains visible... a basic indication may
tell the user that current data could not be refreshed").

### Not-found is page-scoped, not the router's catch-all

`/coins/DOGE99` is a *syntactically valid* route matching a symbol that doesn't exist — a different
case from `App.tsx`'s existing `<Route path="*">`, which only catches routes that don't match any
pattern at all. Driven off `error instanceof ApiError && error.status === 404` from `useCoin`,
reusing the dashboard's existing `EmptyState` component with asset-specific copy rather than
inventing a second not-found component.

### Dashboard state survives the round trip for free

Clicking into a coin and back doesn't need any special state-preservation logic. S3 already put the
dashboard's page/size/sort/order/search entirely in the URL query string, and a real `<Link>`
navigation is a standard history entry — the browser's back button restores the exact prior URL,
querystring included, the same way it would for any two pages on the web.

## Acceptance criteria

- [ ] Selecting any part of a dashboard row navigates to `/coins/:symbol` for that row's coin.
- [ ] The row is a real link: right-click → open in new tab, and middle/ctrl-click, both work.
- [ ] The detail page renders the header, price block, market data block, and last-updated footer
      for the selected coin, sourced from `GET /api/market/coins/{symbol}`.
- [ ] All monetary values render as USD, consistent with the dashboard's formatting.
- [ ] The page is reachable directly by URL with no authentication.
- [ ] Data refreshes automatically on the same interval as the dashboard, paused while the tab is
      hidden; a manual refresh control is available.
- [ ] A failed refresh leaves the last successfully loaded coin on screen with a failure indicator
      — never a blank page.
- [ ] The last successful update time is visible and reflects the coin's own `updatedAt`.
- [ ] An unknown symbol (valid route, no matching coin) renders a clear not-found state — no crash,
      no blank shell, and distinguishable from the app's generic "page not found" route.
- [ ] Browser back from the detail page returns to the dashboard with its prior page, size, sort,
      order, and search intact.

## Test plan

Vitest + React Testing Library + MSW, matching S2/S3's tooling and the same discipline: every
mechanism-level test verified by break-then-revert (deliberately break the behavior, confirm the
test fails, then restore) given four false-passes have already turned up across the two prior
frontend slices.

Specific cases:
- Clicking a row (via `user.click` on the row, not directly on an inner element) navigates to the
  correct `/coins/:symbol`.
- The rendered row element has a real `href` — asserted directly, not inferred from click behavior,
  since a `role="link"` div with an `onClick` would pass a naive click-based test while still
  failing the "open in new tab" requirement.
- Detail page renders all expected fields for a known symbol from a mocked response.
- An MSW 404 response renders the not-found state, distinguishable in the DOM from the router's
  generic catch-all (different copy/heading).
- A failed refresh (mocked 500 after a successful load) keeps the previously rendered coin visible
  alongside a failure indicator.
- Auto-refresh test using the same fake-timers-installed-before-render technique established in
  S3, to avoid the "timer scheduled under real timers can't be advanced by fakes" trap already hit
  once.
- Navigating to a detail page and back preserves the dashboard's query string, asserted against the
  actual URL/history state, not just that the dashboard re-renders.

## Risks / notes

- **`useCoin`'s query key must not collide with `useCoins`'s.** `useCoins` keys on
  `['coins', params]`; `useCoin` should key on something disjoint (e.g. `['coin', symbol]`) so a
  detail-page fetch and a dashboard fetch never share or evict each other's cache entry.
- **The three resolved open questions above are defaults, not confirmed decisions.** In particular,
  the field grouping (price / market data split) is a UI judgment call with no PRD text dictating
  it — cheap to revise later since nothing downstream depends on the exact grouping.
- **BigDecimal-as-JSON-number precision note from S3 applies here too** — no new risk introduced,
  just inherited from the same API responses the dashboard already renders.
