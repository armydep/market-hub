# S10 — Notifications

**Status:** ⬜ not started
**Depends on:** S9 (merged)
**PRD:** F-007 (FR-001/002/003/004)

## Goal

Give a registered user an in-application record of every price alert that has fired, separate from
the alert itself, so clearing a notification and clearing a triggered alert are independent actions
(per `domain-model.md`'s existing rationale for keeping `Notification` a distinct entity).

## PRD traceability

| FR | Requirement | Delivered here |
|---|---|---|
| F007-FR-001 | The system shall create one notification when a price alert is triggered. | Yes |
| F007-FR-002 | The system shall allow a user to list their visible notifications. | Yes |
| F007-FR-003 | Each notification shall identify the cryptocurrency, target price, condition, and trigger time. | Yes |
| F007-FR-004 | The system shall allow the owner to clear a notification from the visible list. | Yes |

## Resolved open questions

None. Unlike S6–S9/S11/S8, this slice has no genuinely undecided product question: `domain-model.md`
already fully specifies the `Notification` entity (denormalized fields, the `UNIQUE(alertId)`
duplicate-suppression guarantee, independent-from-the-alert clearing), and `docs/slices.md`'s S10
outline already fixes the API shape. The one small default this spec sets on its own authority —
list order — is recorded under Architecture decisions below, not as an open question, since nothing
in the PRD or the domain model leaves it ambiguous enough to need a round-trip.

## In scope

- `V8__notifications.sql`: `notifications` — `id` (identity PK), `user_id` (bigint, FK → `users`,
  **ON DELETE CASCADE**), `alert_id` (bigint, FK → `price_alerts`, **ON DELETE CASCADE**, **UNIQUE**
  — the actual duplicate-suppression guarantee per `domain-model.md`, not merely a code-path check),
  `symbol` (varchar(32), matching `price_alerts.symbol`'s width — denormalized so the notification
  stays readable even if the originating alert is later gone), `condition` (varchar, denormalized),
  `target_price` (numeric(30,10), denormalized), `triggered_price` (numeric(30,10)), `triggered_at`
  (timestamptz), `cleared_at` (timestamptz, nullable — set when the owner clears it), `created_at`
  (timestamptz).
- New `notification` feature package: `Notification` entity (static factory `from(PriceAlert alert)`
  copying the four denormalized fields plus `triggeredAt`/`triggeredPrice` at trigger time),
  `NotificationRepository` (`findByUserIdAndClearedAtIsNullOrderByTriggeredAtDesc`,
  `findByIdAndUserId` for the ownership-scoped clear operation), `NotificationResponse` DTO (record,
  static `from(Notification)`), `NotificationService` (`list`, `clear`), `NotificationController`
  (`GET /notifications`, `POST /notifications/{id}/clear`).
- **`AlertEvaluationService` (S9) creates the notification in the same transaction it triggers the
  alert in** — not a second listener, not a second poll-hook. When `alert.trigger(price)` fires,
  immediately `notificationRepository.save(Notification.from(alert))` right there, so a trigger and
  its notification either both commit or neither does (PRD §3.5's "avoid duplicate visible
  notifications" reads naturally as "avoid a trigger with no notification or a notification with no
  trigger" too — a single transaction is what makes that true by construction, not just by writing
  the calls next to each other).
- `clear(id)`: ownership-scoped via `CurrentUser`, and — mirroring `PriceAlert.clear()`'s "wrong
  state is also 404" convention — only a currently-visible (uncleared) notification can be cleared;
  any other case (not found, not yours, already cleared) returns a uniform 404.
- SPA: a Notifications page/list showing symbol, condition, target price, triggered price, and
  triggered time for each visible notification, with a Clear action and an empty state; an
  at-a-glance unread-count indicator in `AppHeader` (a plain badge showing the visible-notification
  count, refreshed on the same query as the list — no separate "mark as read" concept, since Phase 1
  has no read/unread distinction beyond visible/cleared).

## Out of scope

- Any delivery channel beyond in-app storage/display (no email, push, or SMS) — PRD §2.2/§4.3 are
  explicit that Phase 1 notifications are in-application only.
- A "mark as read without clearing" state. The PRD's only lifecycle transition is
  visible → cleared; there is no separate read/unread flag to invent.
- Notifying on anything other than an alert trigger (no system notifications, no admin
  announcements) — F-007's scope is exclusively alert-trigger notifications.
- Re-deriving notification content from the live alert at read time. Every field is denormalized at
  creation specifically so a notification stays fully readable even after its alert's own state (or,
  hypothetically, existence) changes — reading through to the alert would defeat that.
- Pagination on the notification list. Mirrors S9's alerts list: PRD frames Phase 1 as intentionally
  basic scale, and the PRD doesn't ask for it here either.

## Architecture decisions

- **Duplicate suppression is the `UNIQUE(alert_id)` constraint, not control flow** — the same
  discipline `domain-model.md` already calls out. Because notification creation lives inside
  `AlertEvaluationService`'s existing per-alert loop, and each alert only ever transitions
  `active → triggered` once (S9: no re-arm), a second attempt to insert a notification for the same
  `alert_id` is structurally impossible under normal operation; the constraint exists to make a
  retried or overlapping evaluation cycle *fail* to double-insert rather than silently succeed.
- **List order: most-recently-triggered first** (`triggeredAt DESC`). Not specified by the PRD, but
  the natural reading of "list their visible notifications" for something the user checks
  periodically — newest first is what every comparable in-app list in this product already does
  (dashboard defaults to market-cap rank, alerts have no stated order either way, but a
  notification's whole reason to exist is "something just happened", so recency is the obvious
  default here specifically).
- **Notification creation happens inside `AlertEvaluationService`'s existing transaction**, not via
  a second `@EventListener` on a "alert triggered" sub-event. A second listener would reintroduce
  exactly the cross-transaction risk S9's `PollCompletedEvent` hook was designed to avoid — two
  independent commit boundaries for two things (a trigger and its notification) that must never be
  observed independently.
- **`ON DELETE CASCADE` on both `notifications.user_id` and `notifications.alert_id`.** User cascade
  matches every other per-user table (`price_alerts`, `password_reset_tokens`, `user_preferences`).
  Alert cascade is defensible even though S9 currently provides no way to delete a triggered alert
  (only active alerts can be deleted) — the FK still needs a defined behavior, and "a notification
  survives its own alert's row being gone" would leave `alert_id` dangling for no benefit, unlike the
  deliberate symbol-string looseness between alerts and quotes (which exists to survive universe
  churn, a real Phase-1 occurrence — this is not that).
- **The unread-count badge reads the same list query as the page**, no separate count endpoint. The
  full visible-notification list is Phase-1-scale (bounded by how many alerts one user can
  realistically have active), so a second round trip just to get a number would be a premature
  optimization for a number the client already has after the first fetch.

## Acceptance criteria

- [ ] Triggering an alert (via a real poll cycle, as in S9's `AlertEvaluationServiceIT`) creates
      exactly one notification, visible to the owner.
- [ ] The notification identifies the symbol, condition, target price, triggered price, and
      triggered time.
- [ ] A later poll cycle that would otherwise re-satisfy an already-triggered alert's condition
      creates no additional notification (this is already guaranteed by S9's no-re-arm rule, but the
      unique constraint is the belt to that suspenders).
- [ ] A user can list their visible (uncleared) notifications, most-recent-first.
- [ ] A user can clear a notification; it then disappears from the visible list.
- [ ] Clearing a notification does not affect the underlying alert's own triggered/cleared state,
      and clearing a triggered alert (S9) does not affect its notification's visible/cleared state —
      the two are independent.
- [ ] Cross-user access to another user's notification (clear) returns 404.
- [ ] Clearing an already-cleared or nonexistent notification returns 404.

## Test plan

Backend (new `NotificationServiceIT`/`NotificationControllerIT` pair, or combined — implementation
decides — `RestClient` + Testcontainers + `StubProviderConfig`, same style as
`AlertControllerIT`/`AlertEvaluationServiceIT`): a real poll cycle that triggers an alert produces
exactly one notification with the correct denormalized fields; a second poll cycle that would
re-satisfy the same (already-triggered, non-re-arming) alert creates no second notification; list
returns only uncleared notifications, most-recent-first; clear removes it from the visible list;
clearing an active alert's would-be notification is impossible to even attempt (no notification
exists until trigger); ownership isolation on clear (404); clearing an already-cleared notification
(404); clearing a nonexistent id (404); clearing a notification does not change its alert's
`active`/`triggeredAt` fields and vice versa.

Frontend: notification list renders triggered-alert content correctly; clear action removes an item
and the header badge count decrements; empty state when there are no visible notifications; header
badge shows the correct count on load.

## Risks / notes

- This is the last Phase-1 PRD feature slice (F-001 through F-010 are then all delivered); after
  S10 merges, `docs/slices.md`'s status table has no remaining ⬜ rows for Phase 1 proper.
- `docs/slices.md`'s existing S10 outline doesn't name a migration version number; the actual next
  available one is `V8`, since `V7` (`user_preferences`, S8) has since merged to main.
