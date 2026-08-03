# S8 — Account management + display preferences

**Status:** ⬜ not started
**Depends on:** S5 (merged), S2 (merged, column catalog)
**PRD:** F-009 (FR-001/002/003/004), the registered-user half of F001-FR-007

## Goal

Let a registered user view their account, change their email and password, and persist which
dashboard columns are visible — the three pieces of self-service account state Phase 1 supports.

## PRD traceability

| FR | Requirement | Delivered here |
|---|---|---|
| F009-FR-001 | The system shall allow a user to view their own account information. | Yes |
| F009-FR-002 | The system shall allow a user to update editable fields in their own account. | Yes — email is the one editable field (see resolved question below) |
| F009-FR-003 | The system shall allow a user to change their password after satisfying the required security check. | Yes — the check is re-entering the current password |
| F009-FR-004 | The system shall persist supported registered-user display preferences. | Yes |
| F001-FR-007 | Registered users shall be able to show or hide supported columns. | Yes, via the preferences endpoint |

## Resolved open questions

| # | Question | Resolution | Basis |
|---|---|---|---|
| 1 | OQ-006 — is email user-editable in Phase 1? | **Yes**, reversing `docs/slices.md`'s prior provisional "not editable" default. `PATCH /api/account` changes it, re-validated for uniqueness (case-insensitive) exactly like registration. | User's explicit choice this round — the domain model has no other editable profile field, so leaving email fixed would mean F009-FR-002 has literally nothing to satisfy. |
| 2 | Does changing email require the current password, the same as changing password does? | **Yes.** Both mutations go through the same "re-enter current password" check. | Not asked separately — treated as a direct consequence of Q1, not a new open question. Email is the identifier a stolen/leaked (but not yet expired) JWT could otherwise redirect: an attacker with only a bearer token could change the account's email to one they control, then later reach the S7 password-reset flow through it. Requiring the current password closes that path the same way F009-FR-003 already requires it for password change; a bearer token alone must not be sufficient to change the account's recovery identity. |

## In scope

- `V7__user_preferences.sql`: `user_preferences` — `user_id` (bigint, **PK and** FK → `users`,
  **ON DELETE CASCADE** — 1:1 with `users`, per `domain-model.md`), `visible_columns_json` (text —
  an ordered JSON array of column keys), `updated_at` (timestamptz).
- New `user` package additions: `UserPreference` entity, `UserPreferenceRepository`,
  `AccountService`, `AccountController`. DTOs: `AccountResponse` (id, email, role, createdAt —
  read-only view; role/blocked/audit fields are never included as editable, per the F-009 business
  rule), `UpdateAccountRequest` (email, currentPassword), `ChangePasswordRequest` (currentPassword,
  newPassword), `PreferencesResponse` (visibleColumns: `List<String>`), `UpdatePreferencesRequest`
  (visibleColumns: `List<String>`).
- `GET /api/account` — the caller's own `AccountResponse`, scoped via `CurrentUser`.
- `PATCH /api/account` — body `{email, currentPassword}`. Verifies `currentPassword` against the
  stored hash (401/400 if wrong — see Architecture decisions for the exact status), lowercases and
  re-checks the new email for uniqueness (case-insensitive) exactly like registration, 409 on
  collision. A no-op update (submitting the same email) is allowed and simply succeeds.
- `POST /api/account/password` — body `{currentPassword, newPassword}`. Verifies the current
  password, re-uses the registration password rule (`@Size(min = 8)`), re-encodes and stores the
  new hash. Does **not** touch S6 lockout state or S7 reset tokens — those are already-correct,
  independent mechanisms this endpoint has no reason to reach into.
- `GET /api/account/preferences` — the caller's persisted visible-column list, or the
  `MarketService`/S2 column-catalog default-visible set if the user has never saved one (no row
  yet).
- `PUT /api/account/preferences` — body `{visibleColumns}`. Every key must exist in the S2
  `CoinColumn` catalog; an unknown key is a 400, not a silent drop. Full replace (not a merge/patch)
  of the visible-column list, upserting the 1:1 `user_preferences` row.
- SPA: an Account page (view email/role/registered-since, an email-change form, a change-password
  form, each requiring the current password) and dashboard column preferences that persist for a
  registered user and survive sign-out/sign-in (guests keep S3's client-side-only Zustand+persist
  behavior — this slice does not touch guest behavior at all).
- `.env.example`/README: no new env var — this slice introduces no new configuration.

## Out of scope

- Any account field beyond email, password, and column preferences — the User entity has no
  display name, avatar, or similar profile field, and the PRD doesn't ask for one.
- Re-issuing a new JWT after an email or password change. Both mutations leave already-issued
  tokens valid until their normal expiry (the same accepted Phase-1 limitation S7 already documents
  for password reset — closing it needs a `passwordChangedAt`/similar JWT claim check, which is a
  separate decision, not a quiet addition here).
- Sorting/page-size/search preferences. `domain-model.md` is explicit that those stay transient UI
  state for everyone in Phase 1; only the visible-column set is persisted.
- Account deletion or deactivation — not in the PRD's Phase-1 feature list.
- Notifying the user by email that their email or password changed. No PRD acceptance criterion
  asks for it, and it would be a second `EmailSender` call path this slice has no mandate to add.

## Architecture decisions

- **Email change requires the current password, same as password change** (resolved Q2). Both
  `AccountService.updateEmail` and `AccountService.changePassword` verify `currentPassword` via
  `PasswordEncoder.matches` before doing anything else, and both reject a wrong one the same way:
  `ApiException.badRequest("Current password is incorrect")` — a 400, not a 401, since the caller
  is already authenticated via a valid bearer token; this failure is about a second, in-band check,
  not about authentication itself.
- **Email uniqueness is re-checked exactly like registration**, including the same
  read-then-insert-and-catch-the-constraint discipline `AuthService.register` already uses
  (`DataIntegrityViolationException` → 409), since two concurrent email changes racing to the same
  new address have the same non-atomicity problem two concurrent registrations do.
- **`ON DELETE CASCADE` on `user_preferences.user_id`**, matching `password_reset_tokens`'s
  reasoning, not `admin_audit_log`'s: a preference row is live per-user state with nothing to
  preserve once the account itself is gone (Phase 1 has no user-delete feature either way, but the
  schema reflects the real relationship, per `domain-model.md`).
- **Preferences validate against the existing S2 `CoinColumn` catalog**, the same enum
  `GET /api/market/columns` already publishes — no second, drifting definition of "supported
  columns."
- **No preference row yet is not an error.** `GET /api/account/preferences` for a user who has
  never saved one returns the application default-visible set (the same one `GET /api/market/columns`
  reports), so the client never has to special-case "no preferences saved" as distinct from
  "preferences equal the default."
- **`PUT`, not `PATCH`, for preferences.** The client always has and sends the complete visible-set
  (it's a small, bounded list rendered as checkboxes), so a full replace has no merge ambiguity to
  worry about, unlike `PATCH /api/account`'s single-field email update.

## Acceptance criteria

- [ ] A signed-in user can view their own email, role, and registration date.
- [ ] A user can change their email to an unused address; the new email is required at the next
      sign-in.
- [ ] Changing email to an address already registered to another account is rejected (409).
- [ ] Changing email or password with the wrong current password is rejected (400) and nothing
      changes.
- [ ] A user can change their password; the old password stops working and the new one signs in.
- [ ] A user's visible-column choices persist across sign-out and sign-in.
- [ ] Saving an unknown column key is rejected (400).
- [ ] A user who has never saved preferences sees the application default-visible columns.
- [ ] Cross-user account/preferences access is impossible by construction (every operation is
      scoped through `CurrentUser`, never a path/body-supplied id) — there is no id-based endpoint
      to even attempt cross-user access against.

## Test plan

Backend (new `AccountControllerIT`, `RestClient` + Testcontainers, same style as
`AuthControllerIT`/`AlertControllerIT`): view own account; change email happy path + duplicate-email
409 + wrong-current-password 400; change password happy path (old password fails, new one signs in)
+ wrong-current-password 400; preferences round-trip (save then read back); unknown column key → 400;
reading preferences before ever saving any returns the default-visible set; a second user's session
never sees or affects the first user's account or preferences (there's no endpoint parameter to even
attempt it against, but the test still confirms each user only ever sees their own data).

Frontend: Account page renders the signed-in user's info; email-change form (success, duplicate-email
error, wrong-password error); password-change form (success, wrong-password error); column
preferences round-trip through a full sign-out/sign-in cycle (mocked); an invalid column key from a
(hypothetical) stale client shows a clear error rather than silently doing nothing.

## Risks / notes

- `docs/slices.md`'s existing S8 outline names the migration `V6__user_preferences.sql`; the actual
  next available number is `V7`, since `V6` was already taken by S9 (`price_alerts`), which merged
  first. That doc will be updated to `V7` when this spec lands.
- This is the first slice to let a user change the value (`email`) that both `AuthService.login`
  and `JwtService` treat as identity, and that S7's password-reset flow delivers a token to. Get the
  uniqueness re-check and the current-password gate right before extending anything else that keys
  off email.
