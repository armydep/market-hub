# 05 · Entity-relationship diagram

All seven persisted tables (`V1`–`V8`). `crypto_quotes` sits deliberately outside this graph — it
is a poller-owned read-model, not user data, and nothing has a foreign key into it.

```mermaid
erDiagram
  users ||--o| user_preferences : "has"
  users ||--o{ price_alerts : "owns"
  users ||--o{ notifications : "owns"
  users ||--o{ password_reset_tokens : "requests"
  users ||--o{ admin_audit_log : "acts as (actor_user_id)"
  users ||--o{ admin_audit_log : "targeted by (target_user_id)"
  price_alerts ||--o| notifications : "triggers"

  users {
    bigint id PK
    varchar email UK "lowercased"
    varchar password_hash
    varchar role "TRADER default"
    boolean blocked
    int failed_login_attempts
    timestamptz locked_until
    timestamptz created_at
  }
  user_preferences {
    bigint user_id PK "FK users.id, CASCADE"
    text visible_columns_json
    timestamptz updated_at
  }
  price_alerts {
    bigint id PK
    bigint user_id FK "CASCADE"
    varchar symbol "no FK — see 04-class-diagram.md"
    varchar condition
    numeric target_price
    boolean active
    timestamptz triggered_at
    numeric triggered_price
    timestamptz cleared_at
    timestamptz created_at
  }
  notifications {
    bigint id PK
    bigint user_id FK "CASCADE"
    bigint alert_id FK "UNIQUE, CASCADE"
    varchar symbol "denormalized"
    varchar condition "denormalized"
    numeric target_price "denormalized"
    numeric triggered_price
    timestamptz triggered_at
    timestamptz cleared_at
    timestamptz created_at
  }
  password_reset_tokens {
    bigint id PK
    bigint user_id FK "CASCADE"
    varchar token_hash UK "SHA-256, never the raw token"
    timestamptz expires_at
    timestamptz used_at
    timestamptz created_at
  }
  admin_audit_log {
    bigint id PK
    bigint actor_user_id FK "no CASCADE"
    varchar action
    bigint target_user_id FK "no CASCADE"
    timestamptz created_at
  }
  crypto_quotes {
    int cmc_id PK "provider id, not generated"
    varchar symbol "indexed, not unique"
    varchar name
    varchar asset_type "always CRYPTO"
    int market_cap_rank
    numeric price
    timestamptz updated_at
  }
```

`notifications.alert_id UNIQUE` is the real duplicate-suppression guarantee for
F006-FR-009/F007-FR-001 — verified by deliberately breaking the no-re-arm logic and watching
Postgres reject the second insert with a genuine constraint violation, not a soft application check.
