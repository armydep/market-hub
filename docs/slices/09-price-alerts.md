# S9 — Price alerts + evaluation

**Status:** ⬜ not started
**Depends on:** S5 (merged), S1 (merged)
**PRD:** F-006 (FR-001–FR-009)

## Goal

Let a registered user create a one-time above/below price alert for a supported cryptocurrency,
evaluated automatically after each successful poll cycle, firing exactly once.

## PRD traceability

| FR | Requirement | Delivered here |
|---|---|---|
| F006-FR-001 | The system shall allow an authenticated user to create a price alert. | Yes |
| F006-FR-002 | An alert shall identify a supported cryptocurrency, a positive target USD price, and an `ABOVE_OR_EQUAL` or `BELOW_OR_EQUAL` condition. | Yes |
| F006-FR-003 | The system shall allow a user to list their active alerts. | Yes |
| F006-FR-004 | The system shall allow a user to update or delete an active alert. | Yes |
| F006-FR-005 | The system shall evaluate active alerts after each successful market-data poll. | Yes |
| F006-FR-006 | A satisfied alert shall be marked triggered and shall no longer be active. | Yes |
| F006-FR-007 | A triggered alert shall be visible to its owner. | Yes |
| F006-FR-008 | The system shall allow the owner to clear a triggered alert from the visible triggered-alert list. | Yes |
| F006-FR-009 | Triggering an alert and creating its notification shall not produce duplicate visible results for the same evaluation. | Half — the "no duplicate trigger" half is delivered here (an alert firing flips `active` to `false`, making it permanently unreachable by later evaluations); the "notification" half is S10's `Notification` entity and its own `UNIQUE(alertId)` guarantee. |

## Resolved open questions

| # | Question | Resolution | Basis |
|---|---|---|---|
| 1 | Can `PATCH /api/alerts/{id}` change an active alert's symbol? | **No** — only `condition`/`targetPrice`. Symbol is fixed at creation. | User's explicit choice this round. |
| 2 | Does an update re-validate "reject if already satisfied by the latest quote" the same way creation does? | **Yes.** | User's explicit choice this round. |

## In scope

- `V6__price_alerts.sql`: `price_alerts` — `id` (identity PK), `user_id` (bigint, FK → `users`,
  **ON DELETE CASCADE**), `symbol` (varchar — no FK to `crypto_quotes`, see Architecture decisions),
  `condition` (varchar: `ABOVE_OR_EQUAL` \| `BELOW_OR_EQUAL`), `target_price` (numeric(30,10), > 0),
  `active` (boolean, default true), `triggered_at`/`triggered_price` (nullable, set on fire),
  `cleared_at` (nullable, set when the owner clears a triggered alert), `created_at`.
- New `alert` feature package (`domain`/`repository`/`service`/`web`/`dto`, matching `auth`/`admin`):
  `PriceAlert` entity, `AlertCondition` enum, `AlertRepository`, `AlertService`,
  `AlertEvaluationService`, `AlertController`.
- `AlertService.create(...)`: validates the symbol exists in the current universe
  (`CryptoQuoteRepository.findFirstBySymbolIgnoreCaseOrderByMarketCapRankAsc`, the same lookup
  `MarketService` already uses) and rejects (400) a symbol not currently tracked; rejects (400) a
  condition the latest quote already satisfies (`ABOVE_OR_EQUAL` with price ≥ target,
  `BELOW_OR_EQUAL` with price ≤ target) — retained deliberately, an active alert always means a
  *future* crossing.
- `AlertService.update(...)`: active alerts only; `condition`/`targetPrice` only (resolved Q1);
  re-runs the same reject-if-already-satisfied check against the latest quote (resolved Q2).
- `AlertService.delete(...)`: active alerts only.
- `AlertService.clear(...)`: only an alert that is both owned by the caller and currently
  triggered-and-uncleared; any other state (active, already cleared, not owned) → 404 (see
  Architecture decisions).
- `AlertEvaluationService`: `@EventListener` on `PollCompletedEvent` (the existing seam, already
  documented as "no-op until" this slice) — for every active alert, look up the latest quote by
  symbol; a symbol no longer in the universe is skipped ("not evaluable this cycle", not an error);
  when found and the condition is met, set `triggeredAt`/`triggeredPrice` and `active = false`.
- `AlertController`: `GET /api/alerts` (active only), `GET /api/alerts/triggered` (triggered,
  uncleared), `POST /api/alerts`, `PATCH /api/alerts/{id}`, `DELETE /api/alerts/{id}`,
  `POST /api/alerts/{id}/clear`. Every operation scoped to `CurrentUser`; cross-user access → 404
  (existing convention).
- DTOs: `CreateAlertRequest(symbol, condition, targetPrice)`,
  `UpdateAlertRequest(condition, targetPrice)` — no `symbol` field, per resolved Q1 — and
  `AlertResponse(id, symbol, condition, targetPrice, active, triggeredAt, triggeredPrice, clearedAt, createdAt)`.
- SPA: an alerts page with active and triggered sections, a create form, edit/delete on active
  alerts, a clear action on triggered ones, and empty states for "no active alerts"/"no triggered
  alerts".

## Out of scope

- The `Notification` entity, its endpoints, and its own duplicate-suppression guarantee — S10.
  This slice only needs an alert to become reliably unreachable by future evaluations once it
  fires; the notification-side half of F006-FR-009 belongs to S10.
- Re-enable/re-arm of a triggered alert — explicitly dropped per `constraints.md`; re-arming means
  creating a new alert, not reviving an old one.
- Any limit on how many alerts a single user may create — not required by the PRD; Phase 1 doesn't
  cap this.
- Any condition beyond `ABOVE_OR_EQUAL`/`BELOW_OR_EQUAL`, and any multi-leg or trailing-stop alert
  shape.
- Editing a triggered alert. `PATCH`/`DELETE` are explicitly active-only per F006-FR-004's literal
  wording; a triggered alert can only be cleared.

## Architecture decisions

- **Reject-if-already-satisfied applies at both create and update** (resolved Q2) — the domain
  invariant "an active alert always represents a future crossing" must hold at every point it
  could be violated, not only at creation; otherwise a user could edit around the creation-time
  check.
- **`PATCH` never touches `symbol`** (resolved Q1) — changing what coin an alert watches is
  modeled as delete-and-recreate, not edit, keeping the update surface small and its validation
  identical in shape to creation's.
- **Evaluation is a hook on `PollCompletedEvent`, not its own scheduled job.** Already decided in
  `constraints.md`: "quotes and alert checks stay consistent and evaluation can never run against
  a half-written or skipped universe." `PollCompletedEvent` is published only after the poller's
  own upsert transaction commits (see `CryptoPoller.pollOnce()`), so alert evaluation naturally
  never sees a partial poll.
- **No FK from `PriceAlert` to `CryptoQuote`.** Per `domain-model.md`'s existing rationale: the
  top-N universe churns and provider ids are provider-specific, so loose (string) coupling keeps
  alerts durable across universe refreshes. The accepted cost — a symbol that transiently
  references a coin not currently cached — is treated as "not evaluable this cycle," never an
  integrity error.
- **Clearing is scoped to (owned AND triggered-and-uncleared), not just (owned).** Attempting to
  clear an active, already-cleared, or not-owned alert all return the same 404 — extending the
  existing cross-user-404 convention to state mismatches too, so there's no separate signal that
  would let a caller distinguish "not yours" from "not currently clearable."
- **`ON DELETE CASCADE` on `price_alerts.user_id`**, matching `PasswordResetToken`'s reasoning
  (live per-account state, not an audit record) rather than `AdminAuditLog`'s no-cascade choice.

## Acceptance criteria

- [ ] A user can create a valid alert for a symbol in the current universe.
- [ ] Creation is rejected (400) for an unknown symbol, and for a condition the latest quote
      already satisfies.
- [ ] A user can list their active alerts and, separately, their triggered-uncleared alerts.
- [ ] A user can update an active alert's condition/target price, but not its symbol; the update is
      rejected under the same already-satisfied check as creation.
- [ ] A user can delete an active alert.
- [ ] An alert fires exactly once when its condition is met after a successful poll, setting
      `triggeredAt`/`triggeredPrice` and `active = false`.
- [ ] An already-fired alert is never re-evaluated or re-fired by a later poll cycle.
- [ ] A user can clear a triggered alert, removing it from the visible triggered list.
- [ ] Cross-user access to another user's alert (read, update, delete, or clear) returns 404.
- [ ] A symbol that has left the current universe is skipped during evaluation, not treated as an
      error.

## Test plan

Backend: `AlertControllerIT` (`RestClient` + Testcontainers + the existing stub `PriceProvider`,
same style as `MarketControllerIT`/`AdminUserControllerIT`) covering creation validation
(unknown symbol, already-satisfied condition), the active/triggered list split, update (allowed
fields, rejected symbol change, rejected already-satisfied update), delete, ownership isolation
(404) across every operation including clear, and clear's state-scoping (active or
already-cleared → 404). A separate `AlertEvaluationServiceIT` (or a `CryptoPoller`-adjacent test)
publishing/triggering `PollCompletedEvent` against a seeded universe and asserting: a matching
alert fires exactly once and stays fired on a second cycle; an alert whose symbol isn't in the
universe is left untouched (no error, no crash); multiple alerts on the same symbol all evaluate
correctly in one cycle.

Frontend: alerts page tests for create/edit/delete on the active list, clear on the triggered list,
and the two empty states.

## Risks / notes

- Evaluation runs synchronously in the same thread that publishes `PollCompletedEvent`, consistent
  with the single-instance poller constraint already documented in `constraints.md` — no new
  concurrency surface is introduced.
- A user with a very large number of active alerts makes each poll cycle's evaluation proportionally
  more expensive; Phase 1 accepts this since there's no alert-count cap (see Out of scope) and the
  expected scale is small.
