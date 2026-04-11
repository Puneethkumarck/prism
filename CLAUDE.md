# CLAUDE.md — Prism (Solana Real-Time Transaction Indexer)

## Project Overview

High-performance real-time Solana transaction indexer. Streams confirmed transactions via Yellowstone gRPC (paid) or WebSocket blockSubscribe (free), persists to PostgreSQL via COPY protocol, exposes paginated REST API.

**Stack**: Java 25, Helidon 4 SE, Virtual Threads, pgjdbc, HikariCP, Avaje Inject — **NO Spring Boot**.

## Mandatory Reading

| Task | Read first |
|------|-----------|
| Any production code | [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) |
| Any test code | [docs/TESTING_STANDARDS.md](docs/TESTING_STANDARDS.md) |
| Story details | `gh issue view <number>` — always fetch from GitHub, never from local files |
| Architecture & phases | [docs/implementation-plan.md](docs/implementation-plan.md) |
| Functional requirements | [docs/functional-spec.md](docs/functional-spec.md) |

These docs are the single source of truth. Do not guess conventions — look them up.

## Build Commands

```bash
./gradlew build              # compile + Spotless + unit + integration + ArchUnit
./gradlew test               # unit tests only
./gradlew integrationTest    # integration tests (requires Docker for Testcontainers)
./gradlew spotlessApply      # auto-format before committing
make help                    # list all available targets
```

## Tech Stack

| Component | Choice | Version |
|-----------|--------|---------|
| Runtime | Java 25 + Virtual Threads | JDK 25 LTS |
| HTTP server | Helidon 4 SE | 4.4.0 |
| gRPC client | Helidon 4 SE gRPC | 4.4.0 |
| DI | Avaje Inject | latest |
| DB driver | pgjdbc | 42.7+ |
| Connection pool | HikariCP x2 (write + read) | 7.x |
| JSON | Jackson | 2.18+ |
| Migrations | Flyway (standalone) | 12.x |
| Resilience | Resilience4j | 2.3+ |
| Metrics | Micrometer + Prometheus | 1.14+ |
| Mapping | MapStruct (`componentModel = "jsr330"`) | 1.6.3 |
| Architecture | ArchUnit | 1.4.1 |
| Testing | JUnit 5 + Mockito + AssertJ + Testcontainers | — |
| Build | Gradle 9 + Kotlin DSL | 9.0.0 |

### What We Explicitly Avoid

| Avoided | Replacement |
|---------|-------------|
| Spring Boot | Helidon 4 SE + `main()` |
| Spring Data JPA | Raw pgjdbc + CopyManager |
| `@Autowired` | Avaje `@Singleton` + Lombok `@RequiredArgsConstructor` |
| `@ConfigurationProperties` | `IndexerConfig` record + `System.getenv()` |
| `@RestController` | Helidon SE `HttpService` functional routing |
| `synchronized` | `ReentrantLock` (avoids VT carrier pinning) |

## Module Structure

```
prism/                           (root — convention plugins only)
├── prism/                       (main service — Helidon 4 SE)
├── prism-api/                   (shared DTOs — java-library)
└── buildSrc/                    (convention plugins: prism.service + prism.library)
```

**Base package**: `com.stablebridge.prism`

## Architecture

```
application → domain ← infrastructure
```

- `domain/` — models, ports, services. ZERO framework imports (only Lombok + `java.*`)
- `infrastructure/` — implements domain ports (JDBC, gRPC, WebSocket, metrics)
- `application/` — Helidon SE routes, lifecycle, config, MapStruct mappers

Full layer rules, ArchUnit enforcement, and directory layout: see [CODING_STANDARDS.md §1](docs/CODING_STANDARDS.md#1-architecture-hexagonal-ports-and-adapters).

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| COPY FROM STDIN | pgjdbc CopyManager | 5-10x faster than INSERT for transaction table |
| Dual HikariCP pools | Write (20) + Read (20) | API latency independent of ingest load |
| Unbounded tx queue | `LinkedTransferQueue` | Prevents gRPC/WS backpressure disconnection |
| Bounded acct queue | `ArrayBlockingQueue(10K)` | Drop if full — accounts less critical |
| 200 tx / 100ms batch | Dual trigger | ~200x fewer DB round-trips, < 200ms max latency |
| 200 acct / 2s batch | Slower cadence | Less latency-sensitive, dedup by pubkey |

Style rules, coding conventions, and DDD patterns: see [CODING_STANDARDS.md](docs/CODING_STANDARDS.md).
Testing conventions: see [TESTING_STANDARDS.md](docs/TESTING_STANDARDS.md).

## Configuration

| Variable | Default | Required |
|----------|---------|----------|
| `STREAM_MODE` | `websocket` | No |
| `RPC_WS_ENDPOINT` | `wss://api.mainnet-beta.solana.com` | If mode=websocket |
| `GRPC_ENDPOINT` | — | If mode=grpc |
| `DATABASE_URL` | — | Yes |
| `X_TOKEN` | — | No (gRPC auth) |
| `API_PORT` | `3000` | No |
| `CONSOLE_LOG` | `true` | No |
| `BENCH_LOG` | `benchmark.log` | No |

## Data Flow

```
Solana RPC/gRPC → TransactionStream adapter → parse protobuf/JSON
  → LinkedTransferQueue (unbounded)
    → TransactionBatchService (200 tx / 100ms)
      → TransactionProcessor (parallel writes to 4 tables)
        → CopyTransactionRepository (COPY FROM STDIN)
        → JdbcFailedTransactionRepository (batch INSERT)
        → JdbcMemoRepository (batch INSERT)
        → JdbcTransferRepository (batch INSERT)

Fee Payer → ArrayBlockingQueue (10K, bounded)
  → AccountBatchService (200 acct / 2s, dedup by pubkey)
    → JdbcAccountRepository (UPSERT ON CONFLICT)

Helidon SE WebServer → 8 REST endpoints → read pool → paginated JSON
```

## Pre-Commit Checklist

Full checklist: [CODING_STANDARDS.md §10](docs/CODING_STANDARDS.md#10-quick-reference-checklist). Key gates:

- [ ] `./gradlew build` passes (compile + Spotless + unit + integration + ArchUnit)
- [ ] Domain layer: zero framework imports, compact constructor validation, value objects, `BigDecimal`
- [ ] Tests follow [TESTING_STANDARDS.md](docs/TESTING_STANDARDS.md)
- [ ] `ReentrantLock` used, no `synchronized`
- [ ] Functional style, `var` for locals, `@Slf4j` for logging, no comments

## Conflict Resolution Order

1. Direct user instruction → 2. This file → 3. `docs/CODING_STANDARDS.md` → 4. `docs/TESTING_STANDARDS.md` → 5. `docs/implementation-plan.md` → 6. Generic best practices
