# 08 · Sequence diagrams

Four flows chosen for what's non-obvious about them, not for coverage's sake.

## 8a · Register → JWT issuance

```mermaid
sequenceDiagram
  actor U as Guest
  participant SPA as React SPA
  participant API as AuthController
  participant Svc as AuthService
  participant DB as Postgres (users)

  U->>SPA: submit email + password
  SPA->>API: POST /auth/register
  API->>Svc: register(request)
  Svc->>DB: findByEmail(lowercased)
  DB-->>Svc: none
  Svc->>Svc: BCrypt.encode(password)
  Svc->>DB: INSERT users (role = TRADER)
  DB-->>Svc: saved
  Svc->>Svc: JwtService.issue(id, email, TRADER)
  Svc-->>API: AuthResponse
  API-->>SPA: 201 Created
  SPA->>SPA: authStore.signIn(response)
```

A submitted `role` field is silently dropped — registration structurally cannot self-elevate.

## 8b · Protected request

```mermaid
sequenceDiagram
  participant SPA as React SPA
  participant Filter as JwtAuthFilter
  participant DB as Postgres (users)
  participant Ctrl as Controller

  SPA->>Filter: request + Authorization: Bearer JWT
  Filter->>Filter: JwtService.parse(token)
  alt invalid or expired
    Filter-->>SPA: 401 (JwtAuthenticationEntryPoint)
  else valid
    Filter->>DB: findById(userId) — re-checked every request
    alt user.blocked
      Filter-->>SPA: 401/403 (blocked)
    else not blocked
      Filter->>Filter: set Authentication(userId, ROLE_role)
      Filter->>Ctrl: forward request
      Ctrl->>Ctrl: RoleHierarchy check
      alt insufficient role
        Ctrl-->>SPA: 403 (JwtAccessDeniedHandler)
      else authorized
        Ctrl-->>SPA: 200 + body
      end
    end
  end
```

`blocked` is the one per-request DB check in an otherwise fully stateless authz model — a blocked
account loses access immediately, not at token expiry.

## 8c · Poll cycle → evaluate → trigger → notify

```mermaid
sequenceDiagram
  participant Sched as @Scheduled
  participant Poller as CryptoPoller
  participant Prov as PriceProvider
  participant DB as Postgres
  participant Bus as EventPublisher
  participant Eval as AlertEvaluationService
  participant Trig as AlertTriggerService

  Sched->>Poller: pollOnce()
  Poller->>Prov: fetchTopCoins(limit, USD)
  Prov-->>Poller: quotes (or empty)
  alt empty, or under half the expected size
    Poller->>Poller: log + skip cycle
  else plausible result
    Poller->>DB: TransactionTemplate — upsert + delete stale
    DB-->>Poller: committed
    Poller->>Bus: publish PollCompletedEvent
    Bus->>Eval: onPollCompleted() [readOnly]
    Eval->>DB: findByActiveTrue()
    loop each active alert
      Eval->>DB: find quote by symbol
      alt condition satisfied
        Eval->>Trig: trigger(alertId, price) [REQUIRES_NEW]
        Trig->>DB: alert.trigger(price) + INSERT notifications
        alt UNIQUE(alert_id) violation
          DB-->>Trig: constraint error
          Trig-->>Eval: rolled back — this alert only
          Eval->>Eval: log warning, continue loop
        else success
          DB-->>Trig: committed
        end
      else not satisfied, or symbol left the universe
        Eval->>Eval: skip — not evaluable this cycle
      end
    end
  end
```

Each alert's trigger + notification commits in its own transaction. One alert's constraint
violation costs only that alert a cycle — it can never roll back another alert that legitimately
fired in the same batch (verified by a break-then-revert test).

## 8d · Password reset

```mermaid
sequenceDiagram
  actor U as User
  participant SPA as React SPA
  participant API as PasswordResetController
  participant Svc as PasswordResetService
  participant DB as Postgres
  participant Mail as EmailSender

  U->>SPA: enter email
  SPA->>API: POST /auth/password-reset/request
  API->>Svc: requestReset(email)
  alt account exists
    Svc->>DB: invalidate prior unused token
    Svc->>Svc: generate token, SHA-256 hash
    Svc->>DB: INSERT password_reset_tokens
  else no account
    Svc->>Svc: equivalent-cost no-op (timing guard)
  end
  Svc-->>API: identical generic response either way
  API->>Mail: send(rawToken) — after the DB transaction commits
  API-->>SPA: 200 generic message
  U->>SPA: open link, set new password
  SPA->>API: POST /auth/password-reset/confirm
  API->>Svc: confirmReset(token, newPassword)
  Svc->>DB: find by hash(token); reject if expired/used
  Svc->>DB: update passwordHash, mark token used, clear lockout
  Svc-->>API: 200
```

The raw token is emailed and hashed on arrival — a stolen database read can never be replayed into
an account takeover.
