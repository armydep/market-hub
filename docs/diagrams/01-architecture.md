# 01 · System architecture

Guests and registered users share one public read path; only the poller ever talks to
CoinMarketCap, and user-facing requests always read Market Hub's own stored data
(`constraints.md`: "user-facing market reads shall use Market Hub's stored data and shall not wait
for a live CoinMarketCap request").

```mermaid
flowchart TB
  subgraph People["People"]
    direction LR
    Guest["Guest<br/>anonymous, no server state"]
    Trader["Registered user<br/>role: TRADER"]
    Admin["Administrator<br/>role: ADMIN"]
  end

  subgraph Client["Browser — React SPA"]
    SPA["TanStack Query cache<br/>Zustand stores (auth, guest columns)<br/>REST client"]
  end

  subgraph Server["Spring Boot — context path /api"]
    Sec["JwtAuthFilter + SecurityConfig<br/>(re-checks 'blocked' every request)"]
    Ctrl["Controllers<br/>auth · account · market · alerts · notifications · admin"]
    Poller["CryptoPoller<br/>@Scheduled — exactly one instance"]
  end

  DB[("PostgreSQL 16<br/>Flyway-owned schema")]
  CMC["CoinMarketCap<br/>/v1/cryptocurrency/listings/latest"]
  Mail["EmailSender<br/>logging stub in Phase 1 — no SMTP configured"]

  Guest -->|"public GET"| SPA
  Trader -->|"JWT bearer"| SPA
  Admin -->|"JWT bearer"| SPA
  SPA -->|"HTTPS / JSON"| Sec --> Ctrl
  Ctrl --> DB
  Poller -->|"PriceProvider seam"| CMC
  Poller --> DB
  Poller -.->|"PollCompletedEvent<br/>after commit"| Ctrl
  Ctrl -.->|"password reset only"| Mail
```

The poller is the only component with outbound network access to a third party. Every user-facing
read is served from Postgres, never a live provider call — "stored market data" is a product
principle, not an implementation detail.

**Provider independence.** `PriceProvider` is the single seam over CoinMarketCap; only the poller
depends on it. Swapping providers (CoinGecko, Binance) touches one adapter, never the read path or
alert evaluation.
