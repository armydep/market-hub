# 09 · State diagrams

## 9a · PriceAlert lifecycle

One-shot, no re-arm.

```mermaid
stateDiagram-v2
  [*] --> Active : create (rejects already-satisfied)
  Active --> Triggered : condition met at evaluation
  Triggered --> Cleared : owner clears
  Active --> [*] : owner deletes
  Cleared --> [*]
  note right of Triggered
    No re-arm. A fired alert
    never returns to Active —
    re-arming means a new alert.
  end note
```

Creation itself is refused (400) if the condition is already true — an active alert always means a
future crossing.

## 9b · User security state — two independent axes

Deliberately not one state machine. An admin unblock must never clear a brute-force lock, and an
expiring lock must never restore a blocked account.

### Lockout — self-expiring, checked at sign-in only

```mermaid
stateDiagram-v2
  [*] --> Unlocked
  Unlocked --> Unlocked : failed login, below max
  Unlocked --> Locked : failed login, reaches max
  Locked --> Unlocked : lock elapsed (lazy check)
  Unlocked --> Unlocked : successful login resets counter
```

### Block — admin-controlled, checked at sign-in *and* on every authenticated request

```mermaid
stateDiagram-v2
  [*] --> Unblocked
  Unblocked --> Blocked : admin blocks (audited)
  Blocked --> Unblocked : admin unblocks (audited)
```
