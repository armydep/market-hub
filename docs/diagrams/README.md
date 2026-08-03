# Architecture diagrams

Visual companion to [`domain-model.md`](../domain-model.md), [`constraints.md`](../constraints.md),
and [`slices.md`](../slices.md) — these documents remain the source of truth; this set illustrates
their decisions rather than restating them. Generated from the shipped Phase 1 codebase (slices
S0–S11, migrations `V1`–`V8`).

| # | Diagram | Covers |
|---|---|---|
| 01 | [Architecture](01-architecture.md) | System context: actors, SPA, API, Postgres, CoinMarketCap, poller |
| 02 | [Block diagrams](02-block-diagrams.md) | Backend feature-package layout + cross-cutting reuse; frontend module layout |
| 03 | [Deployment & runtime](03-deployment-runtime.md) | Runtime topology, the single-poller-instance constraint, config surface |
| 04 | [Class diagram](04-class-diagram.md) | Domain entities and their relationships |
| 05 | [ERD](05-erd.md) | Full schema, `V1`–`V8` |
| 06 | [API outline](06-api-outline.md) | Every REST endpoint, grouped by feature |
| 07 | [Frontend routes](07-frontend-routes.md) | SPA route map and guards |
| 08 | [Sequence diagrams](08-sequence-diagrams.md) | Registration, a protected request, the poll→evaluate→trigger→notify pipeline, password reset |
| 09 | [State diagrams](09-state-diagrams.md) | `PriceAlert` lifecycle; the two independent user security axes |
| 10 | [Security & authorization model](10-security-model.md) | Role hierarchy, JWT claims, access matrix |

Diagrams are Mermaid, rendered natively by GitHub. If a diagram and the code disagree, the code
(and the docs above) win — open a PR updating this set.
