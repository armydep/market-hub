# S3 — Frontend foundation + Public Market Dashboard

**Status:** in progress
**Depends on:** S2 (merged)
**PRD:** [`../Market_Hub_PRD_v0.1.md`](../Market_Hub_PRD_v0.1.md) — F-001, F-002
**Plan entry:** [`../slices.md`](../slices.md) § S3

## Goal

Create the `frontend/` module and ship the Public Market Dashboard as the default landing route, so
a guest can open the app and browse, search, sort and page the live universe.

This is the first slice with a client, and the first whose result is visible to someone who doesn't
read Java. S2 completed the API; nothing consumes it yet.

**No backend changes.** S3 is purely additive.

## PRD traceability

| Requirement | Covered by |
|---|---|
| F001-FR-003/004 | tabular grid, one row per cryptocurrency |
| F001-FR-006 | default visible columns taken from `GET /api/market/columns` |
| F001-FR-007 | column show/hide control |
| F001-FR-008/023 | guest preferences are client-side only (`localStorage`), never persisted server-side |
| F001-FR-010 | sortable columns |
| F001-FR-011 | sorting requests the server; the client never re-sorts the fetched page |
| F001-FR-013/014 | pagination, 20 rows by default |
| F001-FR-015 | page-size picker restricted to the server's supported sizes |
| F001-FR-016 | automatic refresh (60s, paused while the tab is hidden) |
| F001-FR-017 | manual refresh control |
| F001-FR-018 | refresh preserves page, size, sort, search and visible columns |
| F001-FR-019 | last successful market-data update time displayed |
| F001-FR-020 | a failed refresh keeps the last good data on screen behind a failure indicator |
| F001-FR-021 | reachable with no authentication |
| F001-FR-022 | monetary values rendered as USD |
| F002-FR-001/002/003 | search box drives the server's `q` (name or symbol, case-insensitive) |
| F002-FR-004/005 | matching rows shown; `No results found` empty state |
| F002-FR-006 | clearing the search restores the full dataset |

## Resolved open questions

Decided with the product owner before implementation.

| Question | Decision | Why |
|---|---|---|
| Where view state lives | **URL query string** (`page`, `size`, `sort`, `order`, `q`) | Deep-linkable and shareable, back/forward works, and F001-FR-018 falls out for free since a refetch never touches the URL |
| Where column visibility lives | **Zustand + persist** (`localStorage`) | It's a user preference, not a view address; keeps guest state client-side per F001-FR-008 |
| Automatic refresh interval | **60s**, paused when the tab is hidden | Comfortably inside the backend's 180s poll so new data appears within a cycle, without pointless traffic against stored data |
| Styling | **Plain CSS + CSS Modules** | No extra dependency or build plugin; this is a grid, a toolbar and a few states, not a design system |
| Test tooling | **Vitest + React Testing Library + MSW** | MSW intercepts at the network layer, so a test can assert the request actually carried `sort=`/`q=` — which is the F001-FR-011 claim itself |

## In scope

1. **Module scaffolding** — Vite + React + TypeScript at `frontend/`, dev server proxying `/api` to
   the backend, production build, lint/format, test wiring.
2. **App shell** — routing, layout, error boundary, loading and empty states.
3. **Public Market Dashboard** at `/` — grid, pagination, page-size picker, sortable columns,
   column show/hide, automatic refresh, manual refresh, last-updated display.
4. **Grid search** — server-driven, with a `No results found` state.
5. **Failure behavior** — a failed refresh retains the last good rows behind an indicator.

## Out of scope

- **Asset Details and row navigation** — S4. Rows are not clickable in this slice; wiring a route to
  a page that doesn't exist would be dead code.
- **Any authentication UI** — S5. No sign-in, no protected routes, no auth state.
- **Server-persisted column preferences** — S8. Guests hold theirs in `localStorage`; a registered
  user's saved set needs the account API that doesn't exist yet.
- **Any backend change.** The S2 API is sufficient; if this slice finds it isn't, that's a finding to
  record, not to silently patch.
- **Production deployment / static-serving story.** The dev proxy covers development. How the built
  bundle is served in production is undecided and deliberately not invented here.
- **Playwright / browser tests.** The plan scopes testing to component/integration against a mocked
  API; Chromium is available but a browser suite is not part of this slice.
- **i18n, theming, dark mode, accessibility audit** beyond semantic markup and labelled controls.

## Architecture decisions

### Sorting, searching and paging are server-side, structurally

TanStack Table is configured with `manualPagination`, `manualSorting` and `manualFiltering` all
`true`. That is what guarantees the table never re-sorts or re-filters the fetched page locally: a
header click only rewrites the URL, the URL is the query key, and the query refetches.

This matters because the client is where F001-FR-011 is easiest to violate by accident — a default
TanStack Table configuration would happily sort the 20 rows it currently holds and look correct on
page 1 while being wrong on every other page. That is precisely the bug S2 exists to prevent on the
server side.

### A failed refresh is an overlay, not a state

TanStack Query retains `data` from the last successful fetch while `isError` is true. The render path
is therefore "render rows if `data` exists" and, independently, "render a failure banner if
`isError`" — never an either/or. `placeholderData: keepPreviousData` extends the same behavior to
parameter changes, so paging doesn't flash an empty grid.

### The URL is the single source of truth for the view

`page`, `size`, `sort`, `order` and `q` live in the query string, and no component holds an
independent copy — two sources of truth for the same value is how "refresh preserved four of the
five things" bugs happen. The one deliberate exception is the toolbar's search input, which mirrors
`q` into local state so typing feels immediate while the URL update is debounced; the URL still wins
on every render.

An unsupported `size` arriving from a shared or hand-edited URL is clamped to the server default
rather than forwarded: the deep-linkable design makes that value externally reachable, and passing
it through would be a server 400 *and* would leave the page-size control showing a value matching
none of its options.

### Refresh never reaches the provider

Both automatic and manual refresh re-read Market Hub's stored data. Neither triggers an upstream
CoinMarketCap call — that is a PRD business rule (F-001) and a hard constraint, not an optimization.

## Acceptance criteria

**Module**
- [ ] `npm run dev` serves the app and proxies `/api` to the backend on 8080.
- [ ] `npm run build` produces a production bundle.
- [ ] `npm run test` passes; `npm run lint` is clean.

**Grid (F-001)**
- [ ] One row per cryptocurrency, columns from the server's catalog, monetary values in USD.
- [ ] 20 rows by default; page navigation works; totals reflect the whole dataset.
- [ ] The page-size picker offers exactly the server's supported sizes.
- [ ] Changing page size returns to the first page.
- [ ] Clicking a sortable header issues a **new request** carrying `sort` and `order`.
- [ ] Column show/hide works, and the choice survives a reload.

**Search (F-002)**
- [ ] Typing a term issues a request carrying `q`; the grid shows matching rows.
- [ ] A term with no matches renders `No results found`, not an error.
- [ ] Clearing the search restores the full dataset.

**Refresh and failure**
- [ ] Data refreshes automatically about every 60s, and not while the tab is hidden.
- [ ] A manual refresh control re-reads stored data.
- [ ] Refresh preserves page, size, sort, search and visible columns.
- [ ] When a refresh fails, the previously loaded rows stay on screen and a failure indicator appears.
- [ ] The last successful update time is displayed; an empty universe renders a sensible empty state.

**Access**
- [ ] The dashboard is reachable with no authentication.

## Test plan

Vitest + React Testing Library, with MSW serving a fixture universe.

The assertions that carry real weight are the ones made against the **intercepted request**, not the
rendered output — a test that only checks the DOM cannot tell server-side sorting from client-side
sorting of the same fixture:

- sort header click → intercepted request carries `sort=price&order=desc`;
- search input → intercepted request carries `q=`;
- page-size change → intercepted request carries `page=0`;
- MSW returns 500 on refetch → prior rows still in the DOM **and** a failure indicator present;
- column toggle survives a remount sharing the same `localStorage`.

Two S2 tests passed for the wrong reason (a URI-encoding artifact, and an assertion comparing a
component against itself). Each mechanism-level test above will therefore be verified by
break-then-revert: deliberately break the mechanism, confirm the test fails, restore.

## Risks / notes

- **Largest slice so far** — new module, new toolchain, eleven acceptance criteria. If it runs long,
  the natural cut line is the column show/hide picker, which could move to S8 where per-user column
  persistence already lives.
- **`BigDecimal` arrives as a JSON number.** Prices are `numeric(30,10)` server-side; JavaScript
  numbers carry ~15–17 significant digits, so a very small altcoin price can lose low-order digits in
  transit. Acceptable for display, but formatting must not imply more precision than survived.
  Recorded here rather than solved; revisit if Phase 2 adds anything that computes on these values.
- **No production serving story**, by design (above).
- **Still no CI.** `.github/workflows/**` is denied in `.claude/settings.json`, so the frontend suite
  runs only locally — the same gap that already applies to the backend, now across two modules.
