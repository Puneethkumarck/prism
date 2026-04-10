# CLAUDE.md — Prism (Solana Real-Time Transaction Indexer)

## Project Overview

High-performance real-time Solana transaction indexer. Streams confirmed transactions via Yellowstone gRPC (paid) or WebSocket blockSubscribe (free), persists to PostgreSQL via COPY protocol, exposes paginated REST API.

**Stack**: Java 25, Helidon 4 SE, Virtual Threads, pgjdbc, HikariCP, Avaje Inject — **NO Spring Boot**.

## Mandatory Reading

| Task | Read first |
|------|-----------|
| Any production code | [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) |
| Any test code | [docs/TESTING_STANDARDS.md](docs/TESTING_STANDARDS.md) |
| Story details | [docs/github-issues.md](docs/github-issues.md) |
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
| Connection pool | HikariCP x2 (write + read) | 6.x |
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

```
domain/                            ZERO framework imports (only Lombok + java.*)
├── model/                         SolanaTransaction, Account, BatchResult, etc.
├── port/                          TransactionStream, TransactionRepository, MetricsRecorder, etc.
└── service/                       TransactionProcessor, TransactionBatchService, AccountBatchService

infrastructure/                    Implements domain ports
├── grpc/                          YellowstoneTransactionStream (paid), TransactionParser
├── websocket/                     WebSocketTransactionStream (free), BlockNotificationParser
├── persistence/                   CopyTransactionRepository, JdbcXxxRepository, DataSourceFactory
├── metrics/                       MicrometerMetricsRecorder, BenchmarkLogReporter
└── console/                       ConsoleOutputFormatter

application/                       Helidon SE routes + lifecycle
├── IndexerApplication.java        main() — wires everything
├── IndexerConfig.java             Env var parsing (like Rust Config::from_env)
├── routing/                       TransactionRoutes, TransferRoutes, HealthRoutes, etc.
└── mapper/                        MapStruct: domain → API response
```

## ArchUnit Rules (5 Non-Negotiable, Build-Time)

| # | Rule |
|---|------|
| 1 | `domain..` must NOT depend on `infrastructure..` |
| 2 | `domain..` must NOT depend on `application..` |
| 3 | `domain..` must NOT import `io.helidon..`, `jakarta..`, `io.avaje..` |
| 4 | `domain..` must NOT import `java.sql..` |
| 5 | `infrastructure..` must NOT depend on `application.routing..` |

## Style Rules (always applied)

- No comments or Javadoc — code must be self-documenting
- No `@Autowired` — use `@RequiredArgsConstructor` with `private final` fields
- No `System.out`/`System.err` — use `@Slf4j`
- Use `var` for local variables when type is obvious
- `@Builder(toBuilder = true)` on all records (3+ fields)
- Functional style: streams over loops, Optional pipelines over null checks
- `ReentrantLock` over `synchronized` everywhere (VT safety)
- AssertJ only — no JUnit `assertEquals`/`assertTrue`
- BDD Mockito only — `given()`/`then()`, never `when()`/`verify()`
- No generic matchers (`any()`, `anyString()`, `eq()`) — use actual values
- `eqIgnoringTimestamps()` and `eqIgnoring()` from `TestUtils` for mock verification

## Testing Conventions

| Rule | Detail |
|------|--------|
| Golden rule | Build expected object → single `assertThat(actual).usingRecursiveComparison().isEqualTo(expected)` |
| Naming | `should<Action><Condition>` |
| Structure | `// given`, `// when`, `// then` comments in every test |
| Mocking | BDDMockito only. No generic matchers. Pass actual values. |
| Fixtures | `SOME_*` constants + `<concept>Builder()` factories in `src/testFixtures/fixtures/` |
| Unit tests | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` |
| Integration tests | Testcontainers PostgreSQL + direct JDBC (no Spring context) |
| Source sets | `src/test/` (unit), `src/integration-test/` (Testcontainers), `src/testFixtures/` (shared) |

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Helidon 4 SE | No Spring Boot | +291% VT throughput on JDK 25, < 7ms p99.999, sub-50MB, < 100ms startup |
| COPY FROM STDIN | pgjdbc CopyManager | 5-10x faster than INSERT for transaction table |
| Dual HikariCP pools | Write (20) + Read (20) | API latency independent of ingest load |
| Unbounded tx queue | `LinkedTransferQueue` | Prevents gRPC/WS backpressure disconnection |
| Bounded acct queue | `ArrayBlockingQueue(10K)` | Drop if full — accounts less critical than transactions |
| 200 tx / 100ms batch | Dual trigger | ~200x fewer DB round-trips, < 200ms max latency |
| 200 acct / 2s batch | Slower cadence | Accounts are less latency-sensitive, dedup by pubkey |
| WebSocket default | `STREAM_MODE=websocket` | Free — works with any public Solana RPC endpoint |
| gRPC opt-in | `STREAM_MODE=grpc` | Paid ($300-500/mo) but lower latency |
| `ReentrantLock` | No `synchronized` | `synchronized` pins VT carrier threads |
| Avaje Inject | Compile-time DI | Zero reflection, zero runtime overhead, JSR-330 compatible |
| MapStruct jsr330 | `componentModel = "jsr330"` | Compatible with Avaje Inject, compile-time mapping |

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

- [ ] Domain models: ZERO framework imports (only Lombok + `java.*`)
- [ ] Domain ports: plain interfaces, no annotations
- [ ] `ReentrantLock` used, no `synchronized`
- [ ] All mapping: MapStruct `componentModel = "jsr330"`
- [ ] Read methods use read pool, write methods use write pool
- [ ] SQL is parameterized — no string concatenation
- [ ] Tests: single-assert pattern with recursive comparison
- [ ] Tests: BDDMockito, no generic matchers
- [ ] Tests: `// given`, `// when`, `// then` comments
- [ ] Tests: fixtures in `src/testFixtures/fixtures/`, not private methods
- [ ] Functional style: streams over loops, Optional over null checks
- [ ] `var` for local variables, `@Slf4j` for logging
- [ ] No comments, no Javadoc
- [ ] `./gradlew build` passes
- [ ] Spotless formatting applied

## Conflict Resolution Order

1. Direct user instruction → 2. This file → 3. `docs/CODING_STANDARDS.md` → 4. `docs/TESTING_STANDARDS.md` → 5. `docs/implementation-plan.md` → 6. Generic best practices
