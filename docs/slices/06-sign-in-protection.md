# S6 — Sign-in protection + account blocking

**Status:** ⬜ not started
**Depends on:** S5 (merged)
**PRD:** F-004 (FR-006, FR-007, FR-008, FR-009)

## Goal

Add brute-force sign-in protection (consecutive failed-attempt counting with temporary lockout) and
enforce administrative account blocking against already-issued tokens — the defensive half of F-004
that S5 deliberately left out.

## PRD traceability

| FR | Requirement | Delivered here |
|---|---|---|
| F004-FR-006 | Track consecutive failed sign-in attempts. | Yes |
| F004-FR-007 | Temporarily reject sign-in after the configured maximum consecutive failures. | Yes |
| F004-FR-008 | Reset the consecutive-failure counter after a successful sign-in. | Yes |
| F004-FR-009 | Reject authenticated access for an administratively blocked account. | Enforcement only — the block/unblock admin endpoints themselves are S11. |

## Resolved open questions

| # | Question | Resolution | Basis |
|---|---|---|---|
| 1 | Max consecutive failed attempts before temporary lockout | **5** | Provisional default already recorded in `docs/slices.md` (tagged OQ-008); not reconfirmed by the user this round, carried forward as provisional pending a real answer. |
| 2 | Temporary lockout duration | **15 minutes** | Same source, same provisional status. |
| 3 | Does `failedLoginAttempts` reset when a lock naturally expires (time passes `lockedUntil`), or stay at the threshold so one more bad password immediately re-locks? | **Resets to 0.** The next login attempt after expiry is evaluated as a fresh cycle. | Not answered by any existing doc — `domain-model.md` only says "reset to 0 on success." Chosen as the simpler, better-precedented option and recorded here as the authoritative answer other slices can rely on. |

Both numeric values are configurable, not hardcoded (`AUTH_MAX_FAILED_ATTEMPTS` /
`AUTH_LOCKOUT_DURATION_MINUTES`), consistent with every other Phase 1 threshold in
`docs/constraints.md`'s configuration catalog.

## In scope

- `AuthService.login()` gains, in this order, ahead of the existing password check:
  1. If `user.blocked` → reject (distinct message, e.g. "Account is blocked").
  2. Else if `user.lockedUntil` is set and still in the future → reject (distinct message, e.g.
     "Account temporarily locked, try again later").
  3. Else if `user.lockedUntil` is set but has passed → clear it and reset
     `failedLoginAttempts` to 0 *before* evaluating this attempt (lazy expiry — see Resolved
     Question 3), then fall through to the normal password check.
- On a failed password check (existing account, wrong password): increment
  `failedLoginAttempts`; if it now reaches the configured maximum, set
  `lockedUntil = now + lockoutDuration`. The response stays the existing generic "Invalid email or
  password" — attempt count is never revealed, preserving the current no-oracle guarantee.
- On a successful password check: reset `failedLoginAttempts` to 0 and clear `lockedUntil`
  (already-existing behavior for the happy path, now also the recovery path out of a lock).
- An unknown email still short-circuits to the existing timing-safe dummy-hash comparison and
  generic 401 — there is no user row to carry a counter, so nothing changes there.
- `JwtAuthFilter` gains a `UserRepository` lookup by the JWT's `userId` claim on every authenticated
  request, and does **not** set an `Authentication` (falls through to the existing
  `JwtAuthenticationEntryPoint`'s 401) when `blocked=true`. This is the "one per-request DB check"
  `constraints.md` already calls out as the accepted exception to otherwise-stateless JWT auth. A
  `lockedUntil` that is still active is **not** re-checked per request — it only gates new sign-ins,
  it does not revoke an already-issued token (see Architecture decisions).
- New config block `app.auth.max-failed-attempts` / `app.auth.lockout-duration-minutes`, bound the
  same way as every other `@Value`-injected setting in this codebase (no
  `@ConfigurationProperties`).
- `.env.example` + README env table: `AUTH_MAX_FAILED_ATTEMPTS`, `AUTH_LOCKOUT_DURATION_MINUTES`.
- SPA `SignInPage`: three distinct error states — invalid credentials, temporarily locked,
  administratively blocked — driven by distinct backend messages/status codes.

## Out of scope

- The admin block/unblock endpoints and their audit log — S11 (`S6 → S11` in the dependency graph).
  This slice's own tests set `blocked=true` by writing directly through `UserRepository`, since no
  admin-facing mechanism to set it exists yet.
- Any manual/early unlock mechanism. `lockedUntil` only ever clears itself by elapsing or by a
  successful login; there is no "admin clears a temporary lock" action, and S11's future unblock
  action touches only `blocked` — it must **not** clear `lockedUntil`, since the two states are
  independent per `domain-model.md`.
- IP-based rate limiting, CAPTCHA, or any bot-mitigation beyond the per-account counter the PRD
  actually asks for.
- Notifying the user of a lockout by email or any other channel.
- Any change to registration.

## Architecture decisions

- **Lazy expiry, no scheduled job.** `lockedUntil` is only ever evaluated at the moment of the next
  login attempt — the same "check at point of use" pattern the poller/alert-evaluation seam already
  follows elsewhere in this codebase. No new scheduled task is introduced.
- **`blocked` and `lockedUntil` remain fully independent**, per `domain-model.md`: locking due to
  failed attempts never touches `blocked`; an eventual admin unblock (S11) never touches
  `lockedUntil`. Neither state's handling branch in `login()` touches the other's fields. Same
  expectation is what S11's tests will need.
- **A temporary lock blocks new sign-ins, not existing sessions.** FR-007's literal text is about
  sign-in, not session validity, so `lockedUntil` is checked only inside `login()`. `blocked` is the
  opposite — it must revoke access mid-session (FR-009), which is why only `blocked` gets a
  per-request check in `JwtAuthFilter`. Conflating the two would either over-invalidate live
  sessions on a transient lockout, or under-enforce an administrative block; keeping them on
  separate enforcement paths avoids both.
- **No new migration.** `V3__users.sql` (S5) already added all three columns this slice needs. This
  corrects `docs/slices.md`'s stale reference to a `V4__user_login_protection.sql` migration, which
  should not be created.

## Acceptance criteria

- [ ] `failedLoginAttempts` increments on each wrong-password attempt against an existing account.
- [ ] Reaching the configured maximum sets `lockedUntil`; further sign-in attempts are rejected with
      a distinct "temporarily locked" message until it elapses.
- [ ] A successful sign-in resets `failedLoginAttempts` to 0 and clears `lockedUntil`.
- [ ] Once `lockedUntil` has elapsed, the next attempt is evaluated fresh rather than immediately
      re-locking on a single subsequent failure.
- [ ] An administratively blocked account cannot sign in, with a message distinct from both invalid
      credentials and temporary lockout.
- [ ] A blocked account is rejected on a protected request even when presenting a previously-issued,
      still-unexpired JWT.
- [ ] Locking via failed attempts never sets `blocked`; an unblocked-but-still-locked account (state
      simulated directly via the repository) remains locked.
- [ ] The SPA sign-in form renders three distinct error states for the three cases above.
- [ ] Both threshold values are configurable via env vars, with the documented defaults.

## Test plan

Backend (new `AccountLockoutIT`, same `RestClient`+Testcontainers style as `AuthControllerIT`):
lockout triggers at exactly the configured threshold, not one attempt before or after; a successful
login mid-sequence resets the counter; a locked account is rejected during the lock window; after
the lock window elapses, the next attempt is evaluated fresh; a `blocked=true` account (set via
`UserRepository` in the test) is rejected at login; a `blocked=true` account with a valid pre-issued
token is rejected on `/test/protected`; the two states don't mask each other (locking never flips
`blocked`; flipping `blocked` back to `false` directly doesn't clear an active `lockedUntil`).

Frontend: `SignInPage` renders the three distinct messages for three differently-shaped mocked error
responses (MSW), matching the existing per-page test style.

## Risks / notes

- `JwtAuthFilter` gains a database dependency it didn't have in S5 (previously fully stateless
  besides signature/expiry checks) — one extra query per authenticated request, which
  `constraints.md` already accepts as the one necessary exception to stateless JWT auth.
- Login now discloses "this specific account is blocked" or "this specific account is locked" to
  anyone who knows the email, which S5's generic-invalid-credentials path deliberately avoids. This
  is accepted because `docs/slices.md` itself requires distinct SPA error states for these cases,
  and neither disclosure reveals whether a guessed password was close to correct.
