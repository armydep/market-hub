# S11 — Admin user management + audit

**Status:** ⬜ not started
**Depends on:** S6 (merged)
**PRD:** F-010 (FR-001–FR-005), PRD §3.7 (Audit)

## Goal

Give administrators a basic view of registered users and the ability to block/unblock accounts,
with every state-changing action audited — completing F-010, the last remaining Phase-1 feature
area.

## PRD traceability

| FR | Requirement | Delivered here |
|---|---|---|
| F010-FR-001 | The system shall allow an administrator to view registered users. | Yes |
| F010-FR-002 | The system shall allow an administrator to block an unblocked user account. | Yes |
| F010-FR-003 | The system shall allow an administrator to unblock a blocked user account. | Yes |
| F010-FR-004 | Protected access by a blocked user shall be rejected. | Already delivered in S6; this slice adds the endpoint that *sets* `blocked`, not new enforcement. |
| F010-FR-005 | The system shall record block and unblock actions for audit purposes. | Yes |

## Resolved open questions

| # | Question | Resolution | Basis |
|---|---|---|---|
| 1 | What happens when an admin blocks an already-blocked user, or unblocks an already-unblocked one? | **Idempotent success** — 200, no state change, no new audit row. | User's explicit choice this round. |
| 2 | Should there be a safeguard against an admin action leaving the system with no usable admin access (self-block, or blocking the last unblocked admin)? | **No safeguard.** Phase 1 keeps it basic; recovery from that state is an operational/DB-level fix. | User's explicit choice this round, consistent with the PRD's own "intentionally basic" framing for admin (§2.12). |

## In scope

- Migration `V10__admin_audit_log.sql`: `admin_audit_log` — `id` (identity PK), `actor_user_id`
  (bigint, FK → `users`, **no cascade**), `action` (varchar: `BLOCK_USER` \| `UNBLOCK_USER`),
  `target_user_id` (bigint, FK → `users`, **no cascade**), `created_at` (timestamptz).
- New `admin` feature package (`domain`/`repository`/`service`/`web`, matching every other
  feature's layering): `AdminAuditLog` entity, `AdminAuditLogRepository`, `AdminUserService`,
  `AdminUserController`.
- `GET /api/admin/users` — paged list of registered users (`id`, `email`, `role`, `blocked`,
  `createdAt` — never `passwordHash`), gated by `hasRole('ADMIN')` via the existing `RoleHierarchy`
  bean. Same page-envelope shape as `GET /api/market/coins` (`content`/`page`/`size`/
  `totalElements`/`totalPages`) for consistency; **no search or sort** — plain pagination ordered
  by `id` ascending (see Architecture decisions).
- `POST /api/admin/users/{id}/block` — sets `blocked=true` via `User.block()` (already added in
  S6) unless already blocked (resolved Q1: no-op success, no audit row). Writes an
  `AdminAuditLog(BLOCK_USER)` row in the same transaction as the state change, only when a state
  change actually occurs.
- `POST /api/admin/users/{id}/unblock` — mirror, `User.unblock()`, `UNBLOCK_USER`.
- Both endpoints resolve the target by path id: an unknown id → 404 (this is a genuine
  "no such resource," not the cross-user-access 404 mask used elsewhere — see Architecture
  decisions).
- SPA: an `/admin/users` route — a table (email, role, blocked, createdAt) with block/unblock
  controls, an admin-only nav link (rendered from the `role` already in `authStore`), and a
  client-side redirect/403 fallback for a non-admin who navigates there directly (defense in
  depth only — real enforcement is server-side, per `constraints.md`: "UI hiding is not an
  authorization control").

## Out of scope

- Role changes. Deliberately dropped from Phase 1 per `docs/slices.md`'s own note: "This drops
  `PATCH /admin/users/{id}/role` and its last-admin lockout guard from the pre-PRD plan; reintroduce
  only via a new decision." Admins exist solely via the `ADMIN_EMAIL`/`ADMIN_PASSWORD` startup seed.
- Runtime asset approval/removal and public-dashboard configuration (F-016/F-017, Phase 2).
- Search or sort on the admin user list.
- Any safeguard against an admin action removing all admin access (resolved Q2 — none).
- Audit-log retention policy (PRD OQ-010 — already flagged in `docs/slices.md` as open and
  non-blocking).
- Deleting users, or any admin write besides block/unblock.
- Viewing another user's alerts, notifications, or preferences — outside the PRD's admin scope
  entirely (§7.2's role matrix gives admin no such capability).

## Architecture decisions

- **Idempotent block/unblock, silently.** (Resolved Q1.) A block on an already-blocked account, or
  an unblock on an already-unblocked one, returns 200 with no state change and no audit row — an
  audit entry exists only when something actually changed, keeping the log a true action history
  rather than a click log.
- **No admin-lockout safeguard.** (Resolved Q2.) If an admin blocks themselves or the last
  unblocked admin, recovery is an operational/DB-level fix — the same tier as, say, a lost
  `ADMIN_PASSWORD`: a rare mistake with no self-service undo, which Phase 1 accepts.
- **Audit log has no FK cascade on user delete**, per `domain-model.md`: "an audit trail that
  disappears with its subject is not an audit trail." (Phase 1 has no user-delete feature at all,
  but the schema is written for that invariant regardless.)
- **Audit write is same-transaction, not best-effort.** `AdminUserService`'s block/unblock methods
  write the `User` mutation and the `AdminAuditLog` row in one `@Transactional` method — a block can
  never succeed un-audited (`domain-model.md`'s explicit requirement).
- **Plain pagination, no search/sort**, for `GET /api/admin/users` — matches the PRD's explicit
  "intentionally basic" framing for F-010 (§2.12), and no acceptance criterion asks for it. Reuses
  the market API's existing page-envelope DTO shape rather than inventing a second convention.
- **A real 404 for an unknown target id, not the cross-user-access 404 mask.** This endpoint is
  already admin-only, so there's no id-enumeration concern to hide by uniformly returning 404 for
  both "doesn't exist" and "exists but isn't yours" — there is no "yours" here, only a genuine
  not-found case.
- **Replaces `TestProtectedController`'s `/test/admin-only`.** That test-only scaffolding was added
  in S5 specifically because "S11 is the first slice with a real admin-only endpoint" — now that
  one exists, the scaffold's job is done (see Test plan).

## Acceptance criteria

- [ ] An authenticated `ADMIN` can list registered users (paged).
- [ ] A non-admin (including an unauthenticated guest) gets 403/401 on every `/admin/**` route.
- [ ] An admin can block an unblocked user; the target is rejected at sign-in and on any protected
      request using a previously-issued token (S6 enforcement, now exercised end-to-end through a
      real endpoint for the first time).
- [ ] An admin can unblock a blocked user; the target can sign in again afterward.
- [ ] Blocking an already-blocked user (or unblocking an already-unblocked one) succeeds
      idempotently with no new audit row.
- [ ] Each real block/unblock action creates exactly one audit row identifying the actor, action,
      target, and timestamp.
- [ ] Audit records are never exposed through any user-facing endpoint.
- [ ] An unknown target user id returns 404.
- [ ] The SPA admin page lists users and offers block/unblock controls, gated behind the admin role.

## Test plan

Backend (`AdminUserControllerIT`, same `RestClient` + Testcontainers style as `AuthControllerIT`/
`AccountLockoutIT`): a non-admin (trader token, and no token at all) gets 403/401 on every
`/admin/users/**` route; an admin lists users and sees a newly registered one appear; a block
round-trip (target then rejected at login with S6's distinct "blocked" message, and rejected on a
protected route using a pre-issued token); an unblock round-trip (target can sign in again);
blocking the same user twice writes exactly one audit row (verified via the repository, since there
is no audit-read endpoint); unblocking an already-unblocked user is a no-op; an unknown target id →
404; the seeded admin can perform all of the above. `TestProtectedController`/`/test/admin-only` is
retired, and `AuthControllerIT`'s existing role-hierarchy proof (`aTraderTokenCannotReachAnAdminOnlyEndpoint`
/ `theSeededAdminCanSignInAndReachAnAdminOnlyEndpoint`) is repointed at the new real endpoint.

Frontend: an `/admin/users` page test asserting a non-admin session is redirected/blocked, an admin
session renders the user list, and clicking block/unblock updates the row (MSW-mocked).

## Risks / notes

- Retiring `TestProtectedController` touches `AuthControllerIT`, which currently asserts against
  `/test/admin-only` for its role-hierarchy proof. This is an intentional, in-scope cleanup for this
  slice — the S5 spec that introduced the scaffold said explicitly it exists only until a real
  admin-only endpoint arrives — not unrelated scope creep.
- The admin user list returns every registered user with no upper bound beyond pagination. Fine for
  Phase 1's expected user counts and consistent with the PRD's "intentionally basic" framing, but
  worth flagging as a scale assumption if usage ever grows unexpectedly.
