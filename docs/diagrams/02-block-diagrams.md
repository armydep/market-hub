# 02 · Block diagrams

Backend is feature-first, layered inside each feature
(`web → service → {repository, provider} → domain → dto`); dependencies point inward and
cross-feature reuse is explicit, never a reach into another feature's repository.

## 2a · Backend package layout

```mermaid
flowchart LR
  subgraph auth["auth"]
    AuthWeb["web"] --> AuthSvc["service<br/>AuthService · PasswordResetService"]
    AuthSvc --> AuthSec["security<br/>JwtService · JwtAuthFilter · CurrentUser"]
    AuthSvc --> AuthEmail["email<br/>EmailSender seam"]
  end
  subgraph user["user"]
    UserWeb["web"] --> UserSvc["service<br/>AccountService"] --> UserRepo["repository"] --> UserDom["domain<br/>User · UserPreference"]
  end
  subgraph market["market"]
    MktWeb["web"] --> MktSvc["service<br/>MarketService · CryptoPoller"] --> MktRepo["repository"] --> MktDom["domain<br/>CryptoQuote · CoinColumn"]
    MktSvc --> MktProv["provider<br/>PriceProvider seam"]
  end
  subgraph alert["alert"]
    AlertWeb["web"] --> AlertSvc["service<br/>AlertService · AlertEvaluationService · AlertTriggerService"] --> AlertRepo["repository"] --> AlertDom["domain<br/>PriceAlert"]
  end
  subgraph notification["notification"]
    NotifWeb["web"] --> NotifSvc["service"] --> NotifRepo["repository"] --> NotifDom["domain<br/>Notification"]
  end
  subgraph admin["admin"]
    AdminWeb["web"] --> AdminSvc["service"] --> AdminRepo["repository"] --> AdminDom["domain<br/>AdminAuditLog"]
  end
  Common["common<br/>ApiException · GlobalExceptionHandler"]

  AuthSec -.->|"blocked-check"| UserRepo
  AdminSvc -.->|"row lock on target"| UserRepo
  AlertSvc -.->|"symbol validation"| MktRepo
  AlertSvc -.->|"post-poll hook"| MktDom
  NotifDom -.->|"denormalized from"| AlertDom
  UserSvc -.->|"column catalog"| MktDom

  auth -.-> Common
  user -.-> Common
  market -.-> Common
  alert -.-> Common
  notification -.-> Common
  admin -.-> Common
```

Solid arrows are the fixed layer order within a feature. Dashed arrows are the only sanctioned
cross-feature reuse — each one is a specific, named dependency, not a general "everything can see
everything."

## 2b · Frontend module layout

```mermaid
flowchart TB
  Pages["Pages<br/>Dashboard · CoinDetail · Register · SignIn<br/>ForgotPassword · ResetPassword · Account<br/>Alerts · Notifications · AdminUsers"]
  Comp["Shared components<br/>AppHeader · RequireAuth · CoinGrid<br/>DashboardToolbar · Pagination · States"]
  Hooks["Hooks — TanStack Query<br/>one per resource: useCoins, useAccount,<br/>useActiveAlerts, useNotifications, …"]
  Client["api/client.ts<br/>fetch wrappers, throwIfError, ApiError"]
  Stores["Zustand + persist<br/>authStore (JWT) · columnsStore (guest-only)"]
  API[("Spring Boot API")]

  Pages --> Comp
  Pages --> Hooks
  Comp --> Stores
  Hooks --> Client
  Hooks --> Stores
  Client -->|"Authorization: Bearer"| API
```

One query hook per server resource, no shared "god store" for server state — TanStack Query's
cache *is* the server-state layer. Zustand is reserved for two genuinely client-only things: the
JWT/identity and a guest's unsaved column choice.
