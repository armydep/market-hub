# S7 — Password reset

**Status:** ⬜ not started
**Depends on:** S5 (merged)
**PRD:** F-004 (FR-004), PRD §4.2 (transactional password-reset delivery)

## Goal

Let a user who forgot their password regain access through a time-limited, single-use reset
process, delivered by the `EmailSender` seam `constraints.md` already reserves for exactly this
purpose.

## PRD traceability

| FR | Requirement | Delivered here |
|---|---|---|
| F004-FR-004 | The system shall allow a user to reset a forgotten password through a time-limited reset process. | Yes |

Business rules from §3.3 (Security) also apply directly: reset tokens must be unpredictable,
time-limited, and single-use.

## Resolved open questions

| # | Question | Resolution | Basis |
|---|---|---|---|
| 1 | Token lifetime | **60 minutes** | User's explicit choice this round — supersedes `docs/slices.md`'s prior provisional default of 30 minutes (tagged OQ-008); that doc will be updated to match. |
| 2 | What happens to an earlier unused, unexpired token when a user requests a new reset? | **Invalidated.** Only the most recently requested token is ever valid. | User's explicit choice this round. |

## In scope

- `V5__password_reset_tokens.sql`: `password_reset_tokens` — `id` (identity PK), `user_id` (bigint,
  FK → `users`, **ON DELETE CASCADE** — unlike `admin_audit_log`, this is per-user operational state,
  not an audit trail, so it should disappear with its subject), `token_hash` (varchar — a hash of the
  token, never the raw value), `expires_at` (timestamptz), `used_at` (timestamptz, nullable —
  single-use marker), `created_at` (timestamptz).
- New `auth` sub-package additions: `PasswordResetToken` entity, `PasswordResetTokenRepository`,
  `PasswordResetService` (or folded into `AuthService` — implementation decides), `EmailSender`
  interface + a logging/no-op default implementation (mirrors `PriceProvider`'s pattern: the app
  boots and the test suite runs with no mail configuration; the concrete provider stays undecided,
  per PRD OQ-007, and must not leak into business logic).
- `POST /api/auth/password-reset/request` — body `{email}`. Always responds identically (same
  status, same generic message) whether or not the email belongs to a registered account — no
  account-enumeration channel. When the account exists: invalidate any prior unused token for that
  user (resolved Q2), generate a new unpredictable token, store only its hash with a 60-minute
  expiry, and hand the raw token to `EmailSender` (which logs it by default).
- `POST /api/auth/password-reset/confirm` — body `{token, newPassword}`. Looks the token up by its
  hash; rejects (400) if not found, already used, or expired. On success: updates the user's
  `passwordHash` (same `PasswordEncoder`/validation rule as registration — minimum 8 characters),
  marks the token `usedAt`, and clears any existing `lockedUntil`/`failedLoginAttempts` (a
  successful reset is at least as strong a proof of ownership as a correct password, so it gets the
  same recovery effect a successful login already has per S6).
- SPA: a "Forgot password" screen (email input, submits the request, shows the same generic
  confirmation regardless of outcome) and a "Reset password" screen (reached via a link containing
  the token — the token is a URL param, never rendered or logged client-side beyond that link),
  with distinct states for an invalid/expired/reused token vs. success.
- `.env.example` / README: whatever config the chosen logging `EmailSender` default needs (likely
  none beyond what already exists — no new required env var, matching `PriceProvider`'s
  boots-with-nothing-configured precedent).

## Out of scope

- Any real email provider/SMTP integration — the default `EmailSender` implementation logs instead
  of sending, exactly like `PriceProvider`'s missing-`CMC_API_KEY` degrade path. PRD OQ-007 (which
  concrete provider) stays open; this slice must not let one leak into business logic.
- Rate limiting or CAPTCHA on the request endpoint. The PRD only asks for login-attempt throttling
  (F004-FR-006/007, delivered in S6); nothing in F-004 asks for reset-request throttling, and
  Phase 1 is intentionally basic. Not adding it now avoids inventing a requirement the PRD doesn't
  state.
- Any interaction with the S6 `blocked` flag beyond what already exists. A blocked account can still
  complete a password reset (changing a password doesn't grant access — `blocked` is still checked
  independently at login, per S6), so there is no new check to add here and no bypass created.
- Notifying the user by any channel that a reset occurred (no "your password was changed" email).
  Not required by the PRD and adds a second `EmailSender` call path for no acceptance criterion.
- Changing password while already authenticated (that's account management, S8's
  "change password after satisfying the required security check" — a different flow with a
  different precondition).

## Architecture decisions

- **Token hashing, not encryption.** Store `SHA-256(token)` (or an equivalent fast, deterministic
  hash), not a `PasswordEncoder`/BCrypt hash. The token itself is already high-entropy random
  (unlike a human-chosen password), so there's nothing for a slow, salted KDF to defend against
  here beyond what a fast cryptographic hash already provides against a stolen database read — and
  a fast hash is what lets confirm() look the token up in one indexed query instead of hashing every
  outstanding token to find a match.
- **Requesting a new reset invalidates the old one** (resolved Q2) by marking the prior unused token
  row's `usedAt` (or deleting it) rather than leaving multiple valid tokens per account — keeps "is
  this token still good" a single unambiguous check instead of a set membership question.
- **`ON DELETE CASCADE` on `password_reset_tokens.user_id`**, unlike `admin_audit_log`'s deliberate
  no-cascade: this table is operational (a live credential-recovery mechanism tied to one account's
  current state), not a permanent audit record — there is nothing to preserve once the user account
  itself is gone. (Phase 1 has no user-delete feature either way, but the schema reflects the real
  relationship.)
- **A successful reset clears the S6 lockout state** (`lockedUntil`/`failedLoginAttempts` reset),
  mirroring the existing "reset to 0 on a successful login" rule — proving account ownership via a
  valid reset token is at least as strong evidence as a correct password.
- **No email-existence oracle.** The request endpoint's response is identical either way (same
  status, same body) — this is the same pattern `AuthService.login()` already uses for the
  wrong-password-vs-unknown-email case, applied to a second endpoint.

## Acceptance criteria

- [ ] A user can request a reset for their own email and, following the link/token delivered via
      `EmailSender` (logged in Phase 1), set a new password.
- [ ] The request endpoint responds identically for a registered and an unregistered email.
- [ ] An expired token is rejected on confirm.
- [ ] A token already used once is rejected on a second confirm attempt.
- [ ] Requesting a second reset invalidates the first token; only the newest one works.
- [ ] After a successful reset, the old password no longer works and the new one does.
- [ ] A successful reset clears any active temporary lockout from S6.
- [ ] Reset tokens (raw or hashed) never appear in application logs beyond the deliberate
      `EmailSender` logging line that stands in for actually sending the email.

## Test plan

Backend (new `PasswordResetControllerIT`, `RestClient` + Testcontainers, same style as
`AuthControllerIT`): full happy path (request → confirm → sign in with the new password); request
for an unknown email returns the same response shape/status as a known one; confirm with an expired
token → 400; confirm with an already-used token → 400; a second request supersedes the first
token (the first token now fails confirm); a successful reset against a previously-locked account
clears the lock (next login isn't rejected as "temporarily locked"); the old password fails after
reset. A focused `EmailSenderTest`/equivalent proving the default implementation never throws and
never requires configuration.

Frontend: forgot-password screen test (submit → generic confirmation shown regardless of a
mocked-known vs. mocked-unknown email); reset-password screen tests for the success, expired-token,
and already-used-token states.

## Risks / notes

- The reset link's token is the one piece of state carried entirely in a URL; the SPA must not log
  it (e.g. via analytics or console logging) even though it's a normal route param.
- `docs/slices.md`'s existing provisional 30-minute default for this slice is now stale and should
  be corrected to 60 minutes when this spec is written into that file's status/summary.
