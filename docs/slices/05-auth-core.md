# S5 — Auth core (register / sign in / sign out)

**Status:** in progress
**Depends on:** S3 (merged)
**PRD:** [`../Market_Hub_PRD_v0.1.md`](../Market_Hub_PRD_v0.1.md) — F-004 (partial: FR-001/002/003/005/010)
**Plan entry:** [`../slices.md`](../slices.md) § S5

## Goal

Give the app its first notion of an identity. A visitor can register with an email and password,
sign in, and sign out; an authenticated request carries a JWT the backend can verify statelessly;
and the env-provisioned admin exists and can sign in. This is the seam every later Phase 1
personal feature (alerts, notifications, account, admin) is built on — nothing in this slice is
user-facing on its own yet, since no protected feature exists until S6+.

**The defensive half of F-004 is deliberately excluded.** FR-006/007/008/009 (consecutive-failure
lockout, administrative blocking) are S6's scope per `docs/slices.md`'s dependency graph. This
slice's migration creates the `blocked`, `failed_login_attempts`, and `locked_until` columns
because the table needs their final shape now (adding columns later would be a second migration
touching the same table for no reason) — but no code in this slice reads or writes them. Treat
their presence in the schema as "reserved," not as unfinished work.

## PRD traceability

| Requirement | Covered by |
|---|---|
| F004-FR-001 | `POST /api/auth/register` — email + password, always mints `TRADER` |
| F004-FR-002 | `POST /api/auth/login` — registered, unblocked user signs in (blocking itself is S6; nothing is blocked yet, so this is trivially satisfied until S6 gives it teeth) |
| F004-FR-003 | Sign-out — client-side only: clear the persisted auth state. There is no server-side session to invalidate (JWTs are stateless), matching the PRD's "authenticated session used to access protected features" model without inventing a token-revocation list Phase 1 doesn't ask for |
| F004-FR-005 | `SecurityConfig` restricts everything except the public surface (`/auth/**`, `GET /market/**`, `/actuator/**`, springdoc paths) to authenticated requests; role is enforced via the `RoleHierarchy` seam for when role-gated endpoints arrive |
| F004-FR-010 | Passwords hashed with BCrypt before storage; the raw password is never persisted or logged |

**Explicitly not claimed here:** FR-004 (password reset — S7), FR-006/007/008/009 (lockout,
administrative blocking — S6). Silence on these in this slice's acceptance criteria is
intentional, not an oversight.

## Resolved open questions

Put to the product owner and answered before implementation.

| Question | Decision | Why |
|---|---|---|
| Password rule (OQ-008) | **Minimum 8 characters, no composition requirements** (no forced digit/symbol/case mix) | Matches current NIST/OWASP guidance that length matters more than forced complexity, and matches the provisional default already recorded in `docs/slices.md` |
| JWT lifetime (OQ-008) | **24 hours** | Matches the existing provisional default; reasonable for a Phase 1 app with no refresh-token flow — a expired token just means signing in again |
| Does `POST /api/auth/register` return a token? | **Yes — identical response shape to login**, `{token,userId,email,role}` from both endpoints | One fewer round trip for the SPA (register lands the user on an authenticated view immediately, no forced extra sign-in step); the simpler and more common pattern for this kind of app |

## In scope

### Backend
1. **`V3__users.sql`** — the `users` table exactly per `domain-model.md`'s field table: `id` (bigint
   identity, PK), `email` (unique, stored lowercased — uniqueness is case-insensitive), `password_hash`,
   `role` (default `TRADER`), `blocked` (default `false`), `failed_login_attempts` (default `0`),
   `locked_until` (nullable), `created_at`.
2. **`User` entity + `UserRepository`** in a new `auth`/`user` package pair, mirroring the `market`
   package's layering 1:1 (`web`/`service`/`repository`/`domain`/`dto` — no `provider/`, since
   neither feature calls an outbound integration). `UserRepository` needs at minimum
   `findByEmailIgnoreCase`.
3. **BCrypt `PasswordEncoder` bean.**
4. **`JwtService`** — issues and parses JWTs; role travels as a claim; 24h expiry per the resolved
   decision.
5. **`JwtAuthFilter`** — maps the JWT's role claim to Spring Security authorities per request.
6. **`RoleHierarchy`** — `ADMIN > MODERATOR > TRADER`, so an admin implicitly holds trader
   authorities without a second grant.
7. **`SecurityConfig`** — public: `/auth/**`, `GET /market/**`, `/actuator/**`, springdoc paths;
   everything else requires authentication.
8. **`CurrentUser` helper** — thin accessor for the authenticated principal, consumed by later
   slices' ownership-scoping (alerts, notifications, account); introduced now so those slices don't
   each reinvent it.
9. **`POST /api/auth/register`** and **`POST /api/auth/login`** — both return
   `{token,userId,email,role}` per the resolved decision. Registration always mints `TRADER`
   (cannot self-elevate); duplicate email → 409.
10. **Env-provisioned admin seed** — on startup, if no `ADMIN` role exists yet, seed one from
    `ADMIN_EMAIL`/`ADMIN_PASSWORD` (BCrypt-hashed). Idempotent — safe across restarts, and the
    *only* way to become an admin in Phase 1 (no self-elevation, no runtime role change).
11. **`AuthenticationEntryPoint` + `AccessDeniedHandler`** — emit the same
    `{timestamp,status,error,message}` body `GlobalExceptionHandler` already produces. Needed
    because Spring Security's own rejections happen in the filter chain, before any controller
    method runs, so they never reach `@RestControllerAdvice` — today's catch-all
    (`@ExceptionHandler(Exception.class)`) would otherwise turn an `AccessDeniedException` into a
    plain 500, which this slice fixes as part of introducing the filter chain that can produce one.

### Frontend
12. **Register / sign-in / sign-out screens.**
13. **Auth state in Zustand + persist**, mirroring the existing guest-view-state pattern from S3
    (`localStorage`-backed, same middleware already in use).
14. **Authenticated request wiring** — attach the bearer token to requests once signed in.
15. **Redirect-to-sign-in seam** for a guest hitting a protected route. No protected route actually
    exists yet (alerts/notifications/account/admin all arrive in S6+), so this is infrastructure
    landing ahead of its first real caller, not a feature with nothing to protect.
16. **Sign-out clears client auth state** and the app treats the user as a guest again.

### Env / docs (per-slice convention)
17. Add `APP_JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` to `.env.example`'s already-earmarked S5
    section, and to the README's env-var table, matching the existing row format.

## Out of scope

- **Failed-login lockout and administrative blocking (S6).** The `blocked`/`failed_login_attempts`/
  `locked_until` columns exist after this slice's migration but are inert — nothing reads or writes
  them until S6.
- **Password reset (S7).**
- **Account management / display preferences (S8).**
- **Any role-change capability.** The PRD's admin capability is view + block/unblock + audit only;
  `constraints.md` is explicit that admins exist solely via the env seed. No `PATCH .../role`
  endpoint, no admin UI for it.
- **Protecting any specific feature route.** There is nothing to protect yet beyond the seam
  itself — alerts, notifications, account, and admin endpoints don't exist until later slices.
- **Refresh tokens / token revocation.** A 24h-expired token means a full re-login; no mechanism to
  extend or revoke a token early is in scope.
- **Social / OAuth login (Phase 2, F-015).**

## Architecture decisions

### `auth`/`user` packages mirror `market`'s layering exactly

`market/web/MarketController.java` (thin `@RestController`, constructor injection, delegates
straight to the service), `market/service/MarketService.java` (`@Service`, `@Transactional`,
throws `ApiException` for expected failures), `market/repository/CryptoQuoteRepository.java` (plain
`JpaRepository<Entity, Id>`), `market/domain/CryptoQuote.java` (no-arg protected constructor, no
Lombok, explicit `@Column(name = "...")` mapping), and `market/dto/CoinResponse.java` (plain
`record` with a static `from(Entity)` mapper, never the entity itself returned from a controller)
are the concrete pattern the new `auth`/`user` code follows layer-for-layer. `User` doesn't need
`Persistable` the way `CryptoQuote` does — it gets a normal generated `bigint identity` id, not an
externally-assigned one.

### Role as a JWT claim, not a per-request lookup

Already a settled decision in `constraints.md` (RBAC via a single role + hierarchy, role travels as
a claim so authorization is stateless) — restated here, not reopened. The one per-request DB check
this project accepts is `blocked` (S6), because blocking must take effect on an already-issued
token; role itself never needs a lookup.

### A new entry point/handler, not an extension of `GlobalExceptionHandler`

Spring Security's filter chain rejects unauthenticated/forbidden requests *before* Spring MVC's
`DispatcherServlet` ever routes to a controller, so `@RestControllerAdvice` — which only intercepts
exceptions thrown *inside* controller method execution — never sees them. An
`AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) are the correct extension points,
and they're written to produce byte-for-byte the same JSON shape `GlobalExceptionHandler` already
produces, so a client never has to special-case "auth-layer error" vs. "everything else."

### Admin seed idempotency

The seed only runs when no user with the `ADMIN` role exists yet, so it's safe to run on every
startup without creating duplicate or conflicting admin accounts, and safe in a fresh environment
with no manual DB step required.

## Acceptance criteria

- [ ] A new user can register with a unique, valid email and a password of at least 8 characters;
      the response is 201 with `{token,userId,email,role:"TRADER"}`.
- [ ] Registering with an email that's already registered (case-insensitively) → 409, matching the
      standard `{timestamp,status,error,message}` envelope.
- [ ] A registered user can sign in with correct credentials → 200 with
      `{token,userId,email,role}`.
- [ ] Signing in with a wrong password → 401.
- [ ] A protected route requested with no token → 401, via the new `AuthenticationEntryPoint` (not
      a 500).
- [ ] A protected route requested with a valid token → 200.
- [ ] A tampered or expired token is rejected.
- [ ] The JWT's role claim correctly maps to Spring Security authorities through the role
      hierarchy (an admin token can reach a trader-level check).
- [ ] The seeded admin (from `ADMIN_EMAIL`/`ADMIN_PASSWORD`) can sign in on a fresh environment
      with no manual DB step.
- [ ] Registration cannot self-assign a role other than `TRADER` (a role field in the request body,
      if sent, is ignored).
- [ ] A genuine 403 (authenticated but forbidden) is actually reported as 403, not collapsed to 500
      or reported as 401.
- [ ] Signing out clears the client's persisted auth state; the app behaves as a guest again
      (no bearer token sent on subsequent requests).
- [ ] `APP_JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` are documented in both `.env.example` and
      the README's env-var table.

## Test plan

**Backend** — Testcontainers Postgres integration tests, following `MarketControllerIT`'s existing
style: register + login happy path; duplicate email → 409; bad credentials → 401; protected route
without/with token → 401/200; tampered and expired tokens rejected; role claim → authorities via
the hierarchy; admin seed is idempotent across two application-context starts; registration cannot
self-assign a non-`TRADER` role; and explicitly, a genuine 403 is verified to still be a 403 after
the new `AccessDeniedHandler` is wired in — this is the exact trap already flagged against the
current catch-all handler, and the regression this slice must not reintroduce.

**Frontend** — Vitest + React Testing Library + MSW, per the S3/S4 convention (`renderApp` helper,
MSW handlers for `/auth/register` and `/auth/login`, request-assertion over DOM-only assertion
where it matters). Covers: register → redirected to an authenticated view with the returned token
stored; login happy path and bad-credentials error state; sign-out clears persisted state and
subsequent requests carry no bearer token; a guest hitting a protected route is redirected to sign
in. Every mechanism-level test verified by break-then-revert, continuing the discipline established
since two false-passes surfaced in S2/S3 and one more was caught in S4's code review.

## Risks / notes

- **Largest backend slice since S1/S2** — a whole new security layer (dependency, config, filter
  chain, two new packages) rather than an incremental extension of existing code.
- **The `blocked`/lockout columns existing-but-unused until S6 is deliberate sequencing, not an
  oversight.** A future reader (or reviewer) seeing unused columns in this slice's migration should
  read this note rather than conclude S5 left work unfinished.
- **No refresh token in Phase 1** — a 24h-expired token means a full re-login. Acceptable per the
  PRD's Phase 1 scope; nothing requires more than this today.
- **Protected-route redirect-to-sign-in has no real caller yet.** It's being built ahead of S6+'s
  first protected feature deliberately, since the auth seam and the routing seam belong together —
  but it can't be demoed against a real protected page until later slices exist.
