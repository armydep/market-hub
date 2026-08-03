# 04 · Class diagram — domain model

JPA entities only (no Lombok, protected no-arg constructors, static factories). The one deliberate
non-relationship: `PriceAlert.symbol` is a plain string, never a foreign key into `CryptoQuote` —
see the note below.

```mermaid
classDiagram
  class User {
    +Long id
    +String email
    +String passwordHash
    +Role role
    +boolean blocked
    +int failedLoginAttempts
    +Instant lockedUntil
    +Instant createdAt
    +registerFailedLogin()
    +registerSuccessfulLogin()
    +block() unblock()
    +changePassword() changeEmail()
  }
  class Role {
    <<enumeration>>
    TRADER
    MODERATOR
    ADMIN
  }
  class UserPreference {
    +Long userId
    +String visibleColumnsJson
    +Instant updatedAt
  }
  class PriceAlert {
    +Long id
    +Long userId
    +String symbol
    +AlertCondition condition
    +BigDecimal targetPrice
    +boolean active
    +Instant triggeredAt
    +BigDecimal triggeredPrice
    +Instant clearedAt
    +trigger(price) clear()
  }
  class AlertCondition {
    <<enumeration>>
    ABOVE_OR_EQUAL
    BELOW_OR_EQUAL
  }
  class Notification {
    +Long id
    +Long userId
    +Long alertId
    +String symbol
    +AlertCondition condition
    +BigDecimal targetPrice
    +BigDecimal triggeredPrice
    +Instant triggeredAt
    +Instant clearedAt
    +clear()
  }
  class PasswordResetToken {
    +Long id
    +Long userId
    +String tokenHash
    +Instant expiresAt
    +Instant usedAt
  }
  class AdminAuditLog {
    +Long id
    +Long actorUserId
    +AdminAction action
    +Long targetUserId
    +Instant createdAt
  }
  class AdminAction {
    <<enumeration>>
    BLOCK_USER
    UNBLOCK_USER
  }
  class CryptoQuote {
    +Integer cmcId
    +String symbol
    +String name
    +Integer marketCapRank
    +BigDecimal price
    +Instant updatedAt
  }

  User "1" --> "0..1" UserPreference
  User "1" --> "*" PriceAlert
  User "1" --> "*" Notification
  User "1" --> "*" PasswordResetToken
  PriceAlert "1" --> "0..1" Notification : triggers, UNIQUE(alertId)
  PriceAlert ..> AlertCondition
  Notification ..> AlertCondition
  User ..> Role
  AdminAuditLog ..> AdminAction
  AdminAuditLog ..> User : actorUserId
  AdminAuditLog ..> User : targetUserId
  PriceAlert ..> CryptoQuote : symbol string, no FK
```

`AdminAuditLog` is the only entity with no `ON DELETE CASCADE` in either direction — an audit
trail that disappears with its subject isn't an audit trail.

## Why symbol, not a foreign key

The top-N universe churns every poll cycle and provider ids are provider-specific. Coupling
`PriceAlert` to `CryptoQuote` by symbol string keeps alerts durable across universe refreshes (and
a possible provider swap); the accepted cost is a symbol that transiently matches nothing — treated
as "not evaluable this cycle," never an integrity error.
