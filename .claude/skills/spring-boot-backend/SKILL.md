---
name: spring-boot-backend
description: >
  Structure and conventions for the market-hub Spring Boot backend. Use whenever
  creating or modifying backend code under backend/ — adding a controller,
  service, repository, entity, DTO, migration, config, or test — so new code
  follows the project's feature-first + layered package layout, naming, error
  handling, config/env, OpenAPI, and testing conventions. Also read before
  starting a new vertical slice (S2+).
---

# market-hub backend conventions

Spring Boot 4.1 (Spring Framework 7 / Security 7), Java 21, Maven, PostgreSQL 16,
Flyway, JWT, Testcontainers. Module root: `backend/`. Base package:
`com.am.market_hub`. Keep `docs/domain-model.md`, `docs/constraints.md`,
`docs/slices.md` authoritative; this skill is the *how we build it* layer.

## Package layout — feature-first, layered inside each feature

Top level is **by feature** (the approved decision); inside each feature, split
by **layer**. This gives explicit controller/service/repository separation
*and* feature cohesion (no scattering across global `controller/`, `service/`).

```
com.am.market_hub
├── MarketHubApplication.java
├── config/                     cross-cutting @Configuration (Security, WebClient, OpenApi, ...)
├── common/                     shared cross-feature code
│   └── exception/              ApiException, GlobalExceptionHandler
└── <feature>/                  market, auth, user, board, alert
    ├── web/                    @RestController + request/response mapping only
    ├── service/                @Service business logic, @Transactional boundaries, @Scheduled
    ├── provider/               outbound integrations / ports+adapters (e.g. PriceProvider, CMC)
    ├── repository/             Spring Data JPA repositories
    ├── domain/                 @Entity, domain enums/value objects, domain events
    └── dto/                    request/response records (never expose entities)
```

Rules:
- A feature owns its sub-packages; only create the layers it needs (a feature
  without outbound calls has no `provider/`).
- **Dependencies point inward:** web → service → {repository, provider} → domain.
  Never let `domain`/`repository` import `web`/`service`. Controllers must not
  touch repositories directly.
- Cross-feature reuse goes through `common/` or a service API, not by reaching
  into another feature's `repository`/`domain`.

## Layer responsibilities
- **web:** thin. Map HTTP ↔ DTOs, validate input (`@Valid`), delegate to a
  service. No business logic, no entities in signatures. Annotate with
  `@Tag`/`@Operation` for OpenAPI.
- **service:** business logic and transaction boundaries. `@Transactional` on
  writes, `@Transactional(readOnly = true)` on reads. Throw `ApiException` for
  expected failures. Owns `@Scheduled` work (behind a service so a lock can be
  added later).
- **provider:** the single seam over an external source (`PriceProvider`). Only
  services/pollers depend on it; the read path never does. Must degrade
  gracefully (return empty, never throw) — see `CoinMarketCapProvider`.
- **repository:** Spring Data interfaces. Derived queries or `@Query`; bulk
  modifiers use `@Modifying(flushAutomatically, clearAutomatically)`.
- **domain:** JPA entities (no-arg `protected` ctor, no Lombok), catalog enums
  (`CoinColumn`), domain events (`PollCompletedEvent`).
- **dto:** Java `record`s. Provide `static from(Entity)` mappers.

## Naming
- Controllers `*Controller`, services `*Service`, repositories `*Repository`,
  responses `*Response`, requests `*Request`.
- Tests: unit `*Test`, integration (Spring context / Testcontainers) `*IT`, in a
  package **mirroring** the class under test (`market/service/CryptoPollerIT`).

## Configuration & env
- `application.yml` (not `.properties`). Externalize every environment-specific
  value as `${ENV_VAR:default}`; defaults must match `docker-compose.yml` so the
  app runs locally with no `.env`.
- Schema is **Flyway-only** (`db/migration/V__*.sql`), `ddl-auto=validate` —
  Hibernate never mutates schema. One migration per slice; never edit an applied
  migration.
- **Each slice documents the env vars it introduces** in `.env.example` and the
  README env table (per-slice convention, not a separate slice).
- Money = `BigDecimal`/`numeric`; timestamps `timestamptz`, UTC.
- Constructor injection only (no field injection). No entity returned from a
  controller.

## Errors
Single contract: throw `ApiException(status, msg)` (static helpers
`notFound/badRequest/conflict/unauthorized`); `GlobalExceptionHandler`
(`@RestControllerAdvice`) maps it and validation failures to
`{timestamp,status,error,message,details?}`. Cross-user access → **404**, not 403.

## API docs (OpenAPI / Swagger)
- springdoc `springdoc-openapi-starter-webmvc-ui` (3.x line for Boot 4). Metadata
  in `config/OpenApiConfig`.
- Under context-path `/api`: spec at `/api/v3/api-docs`, UI at
  `/api/swagger-ui.html`.
- Annotate controllers with `@Tag`; operations with `@Operation` and, where
  useful, `@ApiResponse`.
- **When S2 adds Security, `SecurityConfig` must `permitAll` the springdoc
  paths:** `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` (paths are
  relative to the context path).

## Testing
- Real Postgres via **Testcontainers** (`support/TestcontainersConfig`,
  `@ServiceConnection`) — no H2. `@SpringBootTest` + `@Import(TestcontainersConfig.class)`.
- A stub `PriceProvider` (`support/StubPriceProvider`, `@Primary` via
  `support/StubProviderConfig`) seeds a deterministic universe — tests never call
  CMC.
- Prefer a pure unit test for parsing/logic (no Spring); use `*IT` for anything
  needing the context or DB.
- Web ITs: `webEnvironment = RANDOM_PORT` + `RestClient` against
  `http://localhost:{port}/api`.

## Build / run
From `backend/`: `mvn spring-boot:run` (needs `CMC_API_KEY` for live data),
`mvn test`, `mvn -Dtest=Class#method test`, `mvn -DskipTests package`. Local
Postgres: `docker compose up -d` (repo root).

## Version-specific gotchas (Boot 4 / new stack — easy to trip on)
- **Auto-config is split into per-feature modules.** `flyway-core` alone won't
  migrate — add `org.springframework.boot:spring-boot-flyway`. Same pattern for
  other features.
- **Jackson 3**: `databind` moved to package `tools.jackson.databind`
  (`JsonNode`, `ObjectMapper`), not `com.fasterxml.jackson.databind`.
- **Testcontainers 2.x**: modules are `testcontainers-<name>` (e.g.
  `testcontainers-postgresql`, `testcontainers-junit-jupiter`);
  `PostgreSQLContainer` moved to `org.testcontainers.postgresql` and is
  **non-generic** (no `<>`).
- **`TestRestTemplate` was removed** — use `RestClient` + `@LocalServerPort`.
  `LocalServerPort` is at `org.springframework.boot.test.web.server.LocalServerPort`.
- **springdoc**: use the **3.x** line for Boot 4 (2.x targets Boot 3).
