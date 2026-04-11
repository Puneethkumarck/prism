# Solana Real-Time Indexer — Implementation Plan

> Target stack: Java 25 · Helidon 4 SE · Virtual Threads · pgjdbc · PostgreSQL 16 · Hexagonal Architecture
> No Spring Boot, No JPA, No CDI — lightweight, high-performance stack
> Testing conventions: Follow `stablebridge-tx-recovery` patterns (BDDMockito, single-assert, fixtures)
> Functional spec: [`docs/functional-spec.md`](functional-spec.md)

---

## Tech Stack

| Component | Choice | Version | Why |
|-----------|--------|---------|-----|
| **Runtime** | Java 25 + Virtual Threads | JDK 25 LTS | Scoped Values finalized, +291% VT throughput vs JDK 21 |
| **HTTP server** | Helidon 4 SE | 4.4.0 | Built on VTs from ground up, < 7ms p99.999, sub-50MB RSS, < 100ms startup, no CDI/reflection |
| **gRPC client** | Helidon 4 SE gRPC | 4.4.0 | Built-in HTTP/2 engine, no external grpc-java needed, VT-native |
| **DI** | Avaje Inject | latest | Compile-time source generation, zero reflection, JSR-330 (`@Singleton`, `@Inject`) |
| **DB driver** | pgjdbc | 42.7+ | COPY FROM STDIN via CopyManager, `reWriteBatchedInserts=true` for batch INSERT |
| **Connection pool** | HikariCP x2 | 6.x | Write pool (20) + Read pool (20), proven with virtual threads |
| **JSON** | Jackson | 2.18+ | Helidon 4 SE uses Jackson natively for media support |
| **Migrations** | Flyway | 12.x | Standalone mode (no Spring integration) |
| **Resilience** | Resilience4j | 2.3+ | Exponential backoff for gRPC reconnect |
| **Metrics** | Micrometer + Prometheus | 1.14+ | Helidon 4 SE has built-in Micrometer integration |
| **Mapping** | MapStruct | 1.6.3 | Compile-time, zero reflection, `componentModel = "jsr330"` for Avaje compatibility |
| **Architecture** | ArchUnit | 1.4.1 | Hexagonal enforcement at build time |
| **Testing** | JUnit 5 + Mockito + AssertJ + Testcontainers | — | No Spring test context — direct JDBC + Testcontainers |
| **Build** | Gradle 9 + Kotlin DSL | 9.0.0 | Convention plugins in `buildSrc/` |
| **Protobuf** | protobuf-java + protoc | 4.x | Compile Yellowstone Geyser .proto definitions |
| **Base58** | bitcoinj or custom | — | Solana signature/address encoding |
| **Logging** | SLF4J + Logback | — | Structured JSON logging via Logstash encoder |

### What We Explicitly Avoid

| Avoided | Replacement | Why |
|---------|-------------|-----|
| Spring Boot | Helidon 4 SE + `main()` | No reflection, no classpath scanning, < 100ms startup |
| Spring Data JPA | Raw pgjdbc + CopyManager | COPY FROM STDIN is 5-10x faster than JPA `saveAll()` |
| Spring DI (`@Autowired`) | Avaje Inject (`@Singleton`) | Compile-time code generation, zero runtime overhead |
| `@ConfigurationProperties` | `Config` record + `System.getenv()` | Direct env var parsing like the Rust `Config::from_env()` |
| `SmartLifecycle` | `main()` + shutdown hook | Explicit lifecycle control, no framework magic |
| `@RestController` | Helidon SE `HttpRouting` | Functional route definitions, VT-native |
| `synchronized` | `ReentrantLock` | Avoids pinning virtual thread carrier threads |

---

## Module Structure

```
prism/                          (root — convention plugins only)
├── prism/                      (main service — Helidon 4 SE)
├── prism-api/                  (shared DTOs — java-library)
└── buildSrc/                           (convention plugins)
```

**Base package**: `com.stablebridge.prism`

---

## Hexagonal Architecture

```
application → domain ← infrastructure
```

```
domain/                                ZERO framework imports — only Lombok + java.* 
├── model/
│   ├── SolanaTransaction.java          Aggregate root: Signature, slot, BigDecimal amount, failed, memo, Pubkey from/to
│   ├── Account.java                    Record: Pubkey pubkey, lamports, slot, executable, rentEpoch
│   ├── LargeTransfer.java             Projection: Signature, slot, BigDecimal amount
│   ├── Memo.java                       Projection: Signature, memoText
│   ├── FailedTransaction.java         Projection: Signature, slot, error
│   ├── Signature.java                  Value object: wraps String (max 88 chars, Base58 ed25519)
│   ├── Pubkey.java                     Value object: wraps String (max 44 chars, Base58 ed25519)
│   ├── Slot.java                       Value object: wraps long (non-negative)
│   ├── BatchResult.java               Record: written, failed, memos, transfers (all non-negative)
│   └── IndexerStats.java              Record: totalTransactions, totalFailed, totalTransfers, totalMemos, totalAccounts
├── port/
│   ├── TransactionStream.java          Interface: subscribe(consumer), close()
│   ├── TransactionRepository.java      Interface: bulkInsert(batch), findBySignature(Signature), findBySlot, countAll, countBySuccess
│   ├── FailedTransactionRepository.java Interface: bulkInsert(batch)
│   ├── TransferRepository.java         Interface: bulkInsert(transfers), findByMinAmount(BigDecimal, limit, offset), countByMinAmount
│   ├── MemoRepository.java             Interface: bulkInsert(memos), findAll(limit, offset), countAll
│   ├── AccountRepository.java          Interface: batchUpsert(accounts), findByPubkey(Pubkey)
│   ├── StatsRepository.java            Interface: getStats() → IndexerStats
│   └── MetricsRecorder.java            Interface: recordBatch(result), recordSlot(), incrementReceived()
├── service/
│   ├── TransactionBatchService.java    Dual-trigger accumulation (200 tx / 100ms), uses ReentrantLock
│   ├── AccountBatchService.java        Dual-trigger accumulation (200 accts / 2s), dedup by pubkey
│   ├── TransactionProcessor.java       Classify batch → parallel writes to 4 repos
│   └── LargeTransferFilter.java        Pure function: BigDecimal amount > 1.0 SOL
└── event/
    └── SlotReceivedEvent.java          Record: slot, parentSlot, timestamp

infrastructure/                        Framework adapters — implements domain ports
├── grpc/
│   ├── YellowstoneTransactionStream.java  Implements TransactionStream via Helidon gRPC client (paid)
│   ├── GeyserSubscriptionConfig.java      Filter: vote=false, commitment=Confirmed
│   ├── GrpcChannelFactory.java            Helidon gRPC channel: TLS, 8MB window, 10s keepalive
│   ├── GrpcReconnectHandler.java          Resilience4j: 4s→8s→16s→30s cap, 60s reset
│   └── TransactionParser.java             Protobuf → domain: bs58 sig, balance diffs, memo v1/v2
├── websocket/
│   ├── WebSocketTransactionStream.java    Implements TransactionStream via blockSubscribe (free)
│   ├── WebSocketClientFactory.java        WSS connection to Solana RPC endpoint
│   ├── WebSocketReconnectHandler.java     Reuses same backoff logic as gRPC
│   └── BlockNotificationParser.java       JSON blockNotification → domain SolanaTransaction + Account
├── persistence/
│   ├── CopyTransactionRepository.java     pgjdbc CopyManager: COPY FROM STDIN + staging merge
│   ├── JdbcFailedTransactionRepository.java  pgjdbc batch INSERT (reWriteBatchedInserts=true)
│   ├── JdbcTransferRepository.java        pgjdbc batch INSERT + parameterized read queries
│   ├── JdbcMemoRepository.java            pgjdbc batch INSERT + parameterized read queries
│   ├── JdbcAccountRepository.java         pgjdbc batch INSERT ON CONFLICT DO UPDATE
│   ├── JdbcStatsRepository.java           pg_stat_user_tables native query
│   ├── DataSourceFactory.java             HikariCP x2: write pool (20) + read pool (20)
│   └── FlywayMigrator.java               Flyway standalone: migrate on startup
├── metrics/
│   ├── MicrometerMetricsRecorder.java     Implements MetricsRecorder: 8 Prometheus counters
│   └── BenchmarkLogReporter.java          5-min interval file reporter (Rust-compatible format)
└── console/
    └── ConsoleOutputFormatter.java        [SLOT] cyan, [TX] white/red, [MEMO] magenta, [TRANSFER] yellow

application/                           Helidon SE routing + lifecycle wiring
├── IndexerApplication.java            main(): wire everything, start Helidon server, shutdown hook
├── IndexerConfig.java                 Record parsed from env vars (like Rust Config::from_env)
├── routing/
│   ├── TransactionRoutes.java         GET /api/transactions, /api/transactions/{sig}, /api/slots/{slot}
│   ├── TransferRoutes.java            GET /api/transfers?min_amount=N
│   ├── MemoRoutes.java                GET /api/memos
│   ├── AccountRoutes.java             GET /api/accounts/{pubkey}
│   ├── StatsRoutes.java               GET /api/stats
│   ├── HealthRoutes.java              GET /health
│   └── ErrorHandler.java              Global error mapping → JSON error responses
├── mapper/
│   ├── TransactionResponseMapper.java MapStruct: domain → API response
│   ├── TransferResponseMapper.java
│   ├── MemoResponseMapper.java
│   └── AccountResponseMapper.java
└── dto/                               (or in prism-api module)
    ├── Page.java                      Generic: data, total, limit, offset
    ├── TransactionResponse.java
    ├── TransferResponse.java
    ├── MemoResponse.java
    ├── AccountResponse.java
    ├── StatsResponse.java
    └── HealthResponse.java
```

---

## Phased Implementation

### Phase 0: Project Scaffolding

| Story | Title | Layer | Description |
|-------|-------|-------|-------------|
| **SOL-1** | Initialize multi-module Gradle project | — | Root `build.gradle.kts`, `settings.gradle.kts`, `buildSrc/` convention plugins (service + library). `libs.versions.toml` with Java 25, Helidon 4 SE, Avaje Inject, pgjdbc, HikariCP, Jackson, MapStruct 1.6.3, Flyway, Resilience4j, Micrometer, ArchUnit, Testcontainers, protobuf-java. `.editorconfig`, `.gitignore`, `Makefile` |
| **SOL-2** | Create API module with shared DTOs | — | `prism-api/`: `Page<T>`, `TransactionResponse`, `TransferResponse`, `MemoResponse`, `AccountResponse`, `StatsResponse`, `HealthResponse`, `ErrorResponse`. All records with `@Builder(toBuilder = true)` |
| **SOL-3** | Create Flyway migrations | — | `V1__create_tables.sql` (5 tables), `V2__create_staging_table.sql` (staging_transactions), `V3__create_indexes.sql` (7 indexes with `CREATE INDEX CONCURRENTLY` — requires `-- flyway:executeInTransaction=false` directive since CONCURRENTLY cannot run inside a transaction) — exact schema from functional spec section 4 |
| **SOL-4** | Add ArchUnit rules | — | 5 rules: domain !→ infrastructure, domain !→ application, domain no Helidon/Jakarta imports (only Lombok + java.*), domain no java.sql, infrastructure !→ application.routing |
| **SOL-5** | Add docker-compose.yml | — | PostgreSQL 16, Prometheus, Grafana. Indexer service (built via Jib, `--profile app` optional). `.env.example` with `STREAM_MODE` (default `websocket`), `GRPC_ENDPOINT`, `RPC_WS_ENDPOINT` (default `wss://api.mainnet-beta.solana.com`), `DATABASE_URL`, `X_TOKEN`, `API_PORT`, `CONSOLE_LOG`, `BENCH_LOG`. Add `make docker-build` (Jib), `make up` (infra + app), `make infra-up` (infra only) targets to Makefile. |
| **SOL-6** | Add test infrastructure | — | `PostgresExtension` (Testcontainers JUnit 5 extension, provides `DataSource`), `TestDataSourceFactory` (HikariCP over Testcontainers), `TestUtils` (eqIgnoring, eqIgnoringTimestamps), fixture base package in `src/testFixtures/` |

```
Batch 1 (parallel): SOL-1, SOL-2
Batch 2 (needs SOL-1): SOL-3, SOL-4, SOL-5, SOL-6
```

---

### Phase 1: Domain Layer

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-7** | Domain models | Domain | `SolanaTransaction`, `Account`, `LargeTransfer`, `Memo`, `FailedTransaction`, `Slot`, `BatchResult`, `IndexerStats`. All Java records with `@Builder(toBuilder = true)`. ZERO framework imports — only Lombok + `java.*` | Unit: builder construction, toBuilder copy |
| **SOL-8** | Domain ports | Domain | `TransactionStream`, `TransactionRepository`, `FailedTransactionRepository`, `TransferRepository`, `MemoRepository`, `AccountRepository`, `StatsRepository`, `MetricsRecorder`. Plain interfaces, no annotations | — (interfaces only) |
| **SOL-9** | LargeTransferFilter | Domain | `amount > 1.0 SOL` threshold. Pure static method, no dependencies. Constant `LARGE_TRANSFER_THRESHOLD_SOL = 1.0` | Unit: 1.1/10/100/1000 → true, 0.0/0.5/1.0 → false (boundary: exactly 1.0 is NOT large) |
| **SOL-10** | TransactionProcessor | Domain | Takes `List<SolanaTransaction>` batch. Splits into: successful (for COPY), failed, memos, large transfers. Calls 4 repo ports **in parallel** using virtual thread executor + `join()`. Returns `BatchResult`. Uses `ReentrantLock` (not `synchronized`) for any shared state. | Unit: mock all 4 repo ports via BDDMockito. Verify correct routing: failed tx → FailedTransactionRepository, memo tx → MemoRepository (includes failed with memos), large → TransferRepository (excludes failed). Pass actual values, no `any()` |
| **SOL-11** | TransactionBatchService | Domain | Dual-trigger accumulation: 200 tx / 100ms. Uses `LinkedTransferQueue.poll(100, MILLISECONDS)`. On flush: delegates to `TransactionProcessor`. On shutdown: drains remaining buffer. Uses `ReentrantLock`. | Unit: verify flush on size threshold (200), verify flush on timeout (< 200 but timer fires), verify drain on close() |
| **SOL-12** | AccountBatchService | Domain | Dual-trigger: 200 accts / 2s. Deduplicates by pubkey in-memory (keep highest slot via `HashMap.merge()`). On flush: calls `AccountRepository.batchUpsert()`. Bounded input queue (10,000 capacity). | Unit: verify dedup (same pubkey, higher slot wins), verify flush on 200 / 2s timeout, verify drain on close() |

```
Batch 1 (parallel): SOL-7, SOL-8, SOL-9
Batch 2 (needs SOL-7, SOL-8): SOL-10, SOL-11, SOL-12
```

---

### Phase 2: Infrastructure — Persistence Adapters

All persistence adapters use **raw pgjdbc** — no JPA, no ORM. Queries are parameterized SQL strings.

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-13** | DataSourceFactory + FlywayMigrator | Infra | `DataSourceFactory`: creates two `HikariDataSource` (write: 20 max/5 min/10s acquire/60s idle, read: same). `FlywayMigrator`: runs `Flyway.configure().dataSource(writePool).load().migrate()` on startup (standalone, no Spring). | Integration: Testcontainers PostgreSQL, verify both pools connect and Flyway runs migrations |
| **SOL-14** | CopyTransactionRepository | Infra | Implements `TransactionRepository`. Constructor takes **two** DataSources (write + read). **Write** (write pool): `PgConnection.getCopyAPI().copyIn("COPY staging_transactions FROM STDIN (FORMAT TEXT)")` — builds TSV `signature\tslot\tt\n`. Then `INSERT INTO transactions SELECT * FROM staging_transactions ON CONFLICT (signature) DO NOTHING`. Then `TRUNCATE staging_transactions`. **Read** (read pool): `findBySignature` (PK lookup), `findBySlot` (idx_transactions_slot), `findAll(limit, offset, success)` (idx_transactions_created_at DESC), `countAll/countBySuccess` (COUNT). | Integration: COPY 100 rows → verify count. Duplicate signatures → ON CONFLICT skips. findBySignature returns Optional. findBySlot returns list. Pagination with limit/offset/success filter. |
| **SOL-15** | JdbcFailedTransactionRepository | Infra | Implements `FailedTransactionRepository`. Batch INSERT via `PreparedStatement.addBatch()` with `reWriteBatchedInserts=true` on the connection URL. | Integration: batch insert 50 rows, verify count and column values |
| **SOL-16** | JdbcTransferRepository | Infra | Implements `TransferRepository`. Constructor takes **two** DataSources. Write (write pool): batch INSERT. Read (read pool): `findByMinAmount(min, limit, offset)` ordered by `amount DESC` + `countByMinAmount`. | Integration: insert transfers, query with min_amount=5.0, verify ordering DESC, verify count |
| **SOL-17** | JdbcMemoRepository | Infra | Implements `MemoRepository`. Constructor takes **two** DataSources. Write (write pool): batch INSERT. Read (read pool): `findAll(limit, offset)` ordered by `created_at DESC` + `countAll`. | Integration: insert memos, query paginated, verify ordering |
| **SOL-18** | JdbcAccountRepository | Infra | Implements `AccountRepository`. Constructor takes **two** DataSources. Write (write pool): batch `INSERT INTO accounts (...) VALUES (...) ON CONFLICT (pubkey) DO UPDATE SET lamports=EXCLUDED.lamports, slot=EXCLUDED.slot, executable=EXCLUDED.executable, rent_epoch=EXCLUDED.rent_epoch`. Read (read pool): `findByPubkey` returns Optional. | Integration: insert → upsert same pubkey with higher slot → verify lamports updated. findByPubkey hit + miss. |
| **SOL-19** | JdbcStatsRepository | Infra | Implements `StatsRepository`. Query: `SELECT COALESCE(n_live_tup, 0) FROM pg_stat_user_tables WHERE relname = ?` for each of 5 tables. Uses **read pool**. | Integration: insert rows across tables, call getStats(), verify non-negative counts |

```
Batch 1: SOL-13
Batch 2 (needs SOL-13, all parallel): SOL-14, SOL-15, SOL-16, SOL-17, SOL-18, SOL-19
```

---

### Phase 3: Infrastructure — Transaction Streaming (gRPC + WebSocket)

Two adapters implement the same `TransactionStream` port — one paid (gRPC), one free (WebSocket). The domain layer is identical for both.

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-20** | Protobuf compilation | Infra | Add `protobuf-java` + Yellowstone Geyser `.proto` files. Configure Gradle `protobuf` plugin with `protoc` to generate Java stubs. No grpc-java codegen needed — Helidon has its own gRPC client. | Build: `./gradlew build` compiles protos without error |
| **SOL-21** | GrpcChannelFactory | Infra | Creates Helidon `GrpcClient` (or `Channel`): TLS with system roots, HTTP/2 adaptive window, 8MB connection/stream window, 10s TCP keepalive, 15s connect timeout, max decoding 64MB. Reads endpoint from `IndexerConfig`. | Unit: verify channel config parameters. Integration: connect to mock gRPC server (grpc-testing) |
| **SOL-22** | TransactionParser (gRPC protobuf) | Infra | Parses Geyser protobuf `SubscribeUpdateTransaction` → domain `SolanaTransaction` + `Account`. **Signature**: base58 encode from bytes. **Amount**: `max(pre_balances[i] - post_balances[i]) / 1_000_000_000.0` (saturating). **From/To**: max decrease (sender) / max increase (receiver) account, truncated `first8...last8` if > 16 chars. **Memo**: scan `message.instructions` + `meta.inner_instructions` for Memo v1 (`Memo1Uhk...`) / v2 (`MemoSq4g...`) program IDs, decode UTF-8, strip `\0`. **Fee payer**: `account_keys[0]` + `post_balances[0]`. | Unit: build protobuf test fixtures manually. Verify: bs58 encoding, amount calc, sender/receiver resolution, memo extraction (top-level + CPI inner instructions), fee payer, address truncation (> 16), missing meta returns null |
| **SOL-23** | ReconnectHandler | Infra | Exponential backoff — shared by both gRPC and WebSocket adapters. Constants: base=2s, delay=`base * 2^min(attempt,4)`, cap=30s. Produces: 4→8→16→30→30. Reset after 60s stable (`RECONNECT_RESET_SECS`). | Unit: verify delay sequence across 6 attempts. Verify reset after simulated 60s stable window. |
| **SOL-24** | YellowstoneTransactionStream (gRPC adapter) | Infra | Implements `TransactionStream` port. Subscribes to Geyser: `vote=false`, `failed=null`, `commitment=Confirmed`, slot subscription `filter_by_commitment=false`. Optional `x-token` header from config. Runs in a virtual thread. Parses each update via `TransactionParser`. Enqueues `SolanaTransaction` to tx consumer, `Account` to acct consumer (try-offer, drop if full). Logs `[SLOT]` notifications. Uses `ReconnectHandler` for auto-reconnect loop. **Requires paid gRPC endpoint ($300-500/mo).** | Unit: mock Geyser stub, verify subscription filter fields, verify routing to tx + acct consumers, verify slot logging. |
| **SOL-24b** | BlockNotificationParser (WebSocket JSON) | Infra | Parses `blockSubscribe` JSON notification → domain `SolanaTransaction` + `Account`. Same extraction logic as SOL-22 but from Jackson `JsonNode` instead of protobuf. Block notification uses `encoding: "jsonParsed"`, `transactionDetails: "full"`, `commitment: "confirmed"`, `maxSupportedTransactionVersion: 0`. Extracts: signature (string, not bytes), pre/post balances, instructions, inner instructions, fee payer. Same amount/from/to/memo logic, same address truncation. | Unit: build JSON fixture strings from real blockSubscribe responses. Verify same domain output as protobuf parser for equivalent transaction data. |
| **SOL-24c** | WebSocketTransactionStream (free adapter) | Infra | Implements `TransactionStream` port. Connects via `java.net.http.WebSocket` (JDK built-in) to `wss://<RPC_WS_ENDPOINT>`. Sends `blockSubscribe` JSON-RPC request with filter: `"all"`, `commitment: "confirmed"`, `transactionDetails: "full"`, `encoding: "jsonParsed"`. Receives `blockNotification` messages, parses via `BlockNotificationParser`. For each transaction in block: filters vote transactions (check for Vote program `Vote111...`), enqueues to tx consumer + acct consumer. Logs `[SLOT]` from block slot number. Uses `ReconnectHandler` for auto-reconnect. **Free — works with any public Solana RPC endpoint.** | Unit: mock WebSocket, verify subscription JSON, verify vote filtering, verify routing. Integration: connect to mock WS server, verify end-to-end parse. |

```
Batch 1: SOL-20
Batch 2 (needs SOL-20, parallel): SOL-21, SOL-22, SOL-23
Batch 3a (needs Batch 2): SOL-24
Batch 3b (needs SOL-23, parallel with 3a): SOL-24b, SOL-24c
```

#### Known spec deviation — HTTP/2 connection-level window (SOL-21)

The SOL-21 spec calls for an 8 MiB HTTP/2 window at **both stream and connection** level.
`GrpcChannelFactory` applies the 8 MiB **stream** window via
`Http2ClientProtocolConfig.initialWindowSize`, but Helidon 4.4.1 does not expose the
**connection-level** window on its `Http2ClientProtocolConfig` API. The full blueprint
surface is `initialWindowSize`, `maxFrameSize`, `maxHeaderListSize`, `priorKnowledge`,
`ping`, `pingTimeout`, and `flowControlBlockTimeout` — nothing for the connection window.

Per RFC 7540 §6.9.2, the HTTP/2 connection window defaults to 64 KiB. Helidon's HTTP/2
client emits `WINDOW_UPDATE` frames at the connection level as data is consumed, so in
practice throughput is gated by consumption speed rather than a static 64 KiB cap. Until
we observe sustained backpressure on the Yellowstone stream (and can tie it to the
connection window specifically), the stream-level 8 MiB cap is the dominant control.

Action if this ever bites: revisit once Helidon exposes `connectionWindowSize` (track via
the Helidon changelog), or drop to a custom `ClientConnectionProvider` implementation.
Tracked in #76.

---

### Phase 4: Infrastructure — Metrics & Console

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-25** | MicrometerMetricsRecorder | Infra | Implements `MetricsRecorder` port. Creates 8 Micrometer counters on a `PrometheusMeterRegistry`: `indexer_tx_received`, `indexer_tx_written`, `indexer_tx_failed`, `indexer_tx_memo`, `indexer_tx_transfer`, `indexer_accounts_written`, `indexer_slots`, `indexer_batches`. `recordBatch(BatchResult)` increments written/failed/memo/transfer from result + batches counter. **Note**: `tx_memo` counter counts successful memos only (non-failed), matching the reference implementation where the counter excludes failed but `batch_insert_memos` persists them. Helidon SE Micrometer integration exposes Prometheus format at `/metrics`. | Unit: create with `SimpleMeterRegistry`, call record methods, verify counter values via registry. Verify tx_memo only counts non-failed. |
| **SOL-26** | BenchmarkLogReporter | Infra | Runs on a virtual thread with `Thread.sleep(300_000)` loop. Reads `MetricsRecorder` counter values, computes TPS (delta from previous / interval), failed% (`failed*100 / (written+failed)`). Appends formatted line to configurable file path. Format matches Rust: `timestamp \| tps \| recv \| written \| failed \| failed% \| memos \| xfers \| accts \| batches \| slots`. Writes session header on startup. | Unit: verify log line format string. Verify TPS delta calculation. Verify failed% with edge cases (zero total). |
| **SOL-27** | ConsoleOutputFormatter | Infra | Color-coded terminal output via ANSI codes (or `jansi` library). **Priority chain**: (1) failed → `[TX]` red, return. (2) memo → `[MEMO]` magenta. (3) large transfer → `[TRANSFER]` yellow. (4) else → `[TX]` white. A tx with memo AND large prints BOTH. `[SLOT]` cyan always (not gated by toggle). Signature truncation > 20 chars → `first8...last8`. Address truncation > 16 chars (already in Transaction). Toggled by `IndexerConfig.consoleLog()`. | Unit: capture System.out, verify each path (failed, memo, transfer, normal, memo+transfer combo). Verify toggle. Verify truncation at 20 chars for signatures. |

```
Batch 1 (parallel): SOL-25, SOL-27
Batch 2 (needs SOL-25): SOL-26
```

---

### Phase 5: Application Layer — REST API

All routes use **Helidon 4 SE functional routing** — no annotations, no controllers.

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-28** | HealthRoutes | App | `GET /health` → `{"status":"ok","uptime_secs":N}`. No DB call. `Instant.now().getEpochSecond() - startTime`. Helidon `HttpRules.get("/health", this::health)`. | Unit: verify JSON shape. Integration: start Helidon server on random port, HTTP GET → 200 + `application/json` |
| **SOL-29** | StatsRoutes | App | `GET /api/stats` → `StatsResponse` (5 count fields). Delegates to `StatsRepository` (read pool). MapStruct mapper domain → response. | Unit: mock StatsRepository, verify JSON output. Integration (Testcontainers): insert rows, HTTP GET → verify counts |
| **SOL-30** | TransactionRoutes | App | Three endpoints: `GET /api/transactions` → `Page<TransactionResponse>` (limit default 50, max 500, clamped `Math.max(1, Math.min(limit, 500))`, offset, optional `success` filter). `GET /api/transactions/{signature}` → single or 404. `GET /api/slots/{slot}` → JSON array (not paginated, `created_at ASC`). MapStruct mapper. | Unit: mock repo, verify param clamping, 404 for missing sig. Integration: insert test data, verify pagination, success filter, ordering. |
| **SOL-31** | TransferRoutes | App | `GET /api/transfers` → `Page<TransferResponse>` (limit, offset, `min_amount` default 0.0). Ordered `amount DESC`. MapStruct mapper. | Unit: mock repo. Integration: insert, verify min_amount filter + DESC ordering |
| **SOL-32** | MemoRoutes | App | `GET /api/memos` → `Page<MemoResponse>` (limit, offset). Ordered `created_at DESC`. | Unit: mock repo. Integration: insert, verify pagination |
| **SOL-33** | AccountRoutes | App | `GET /api/accounts/{pubkey}` → `AccountResponse` or 404. | Unit: mock repo, verify 404 body. Integration: insert, verify lookup |
| **SOL-34** | ErrorHandler | App | Global Helidon error handler. Maps `NoSuchElementException` / custom `NotFoundException` → 404 JSON. `SQLException` → 500 JSON. Consistent `{"error": "...", "status": N}` body. | Unit: verify each exception → correct HTTP status + JSON body |
| **SOL-35** | CORS configuration | App | Helidon `CorsSupport.builder().allowOrigins("*").build()` added to routing. | Integration: OPTIONS request → verify `Access-Control-Allow-Origin: *` header |

```
Batch 1 (parallel): SOL-28, SOL-29, SOL-34, SOL-35
Batch 2 (needs SOL-34, parallel): SOL-30, SOL-31, SOL-32, SOL-33
```

---

### Phase 6: Application — Wiring & Lifecycle

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-36** | IndexerConfig | App | Java record parsed from `System.getenv()` — mirrors Rust's `Config::from_env()`. Fields: `streamMode` (default `websocket`, values: `grpc` or `websocket`), `grpcEndpoint` (required if mode=grpc), `rpcWsEndpoint` (required if mode=websocket, default `wss://api.mainnet-beta.solana.com`), `databaseUrl` (required), `xToken` (optional, gRPC auth), `consoleLog` (default true), `benchLog` (default "benchmark.log"), `apiPort` (default 3000). Fails fast with clear error if required vars missing for selected mode. | Unit: verify parsing for both modes. Verify defaults (websocket mode, free public endpoint). Verify fail-fast on missing required for each mode. |
| **SOL-37** | IndexerApplication (main) | App | `public static void main(String[] args)` wires the entire application: (1) Parse `IndexerConfig` from env. (2) Create `DataSourceFactory` → write pool + read pool. (3) Run `FlywayMigrator.migrate(writePool)`. (4) Create all **write-side** repository adapters injected with **write pool** (CopyTransactionRepository, JdbcFailedTransactionRepository, JdbcTransferRepository, JdbcMemoRepository, JdbcAccountRepository). (5) Create all **read-side** repository adapters injected with **read pool** (TransactionRepository read methods, TransferRepository reads, MemoRepository reads, AccountRepository reads, JdbcStatsRepository). (6) Create domain services (TransactionProcessor, TransactionBatchService, AccountBatchService). (7) Create TransactionStream adapter based on `streamMode`: if `grpc` → GrpcChannelFactory + YellowstoneTransactionStream (paid); if `websocket` → WebSocketClientFactory + WebSocketTransactionStream (free). Same `TransactionStream` port, domain layer unchanged. (8) Create metrics (PrometheusMeterRegistry) + benchmark reporter. (9) Build Helidon `WebServer` with all routes + CORS + `/metrics` Prometheus endpoint. (10) Start virtual thread executor, submit: batch worker, account worker, benchmark reporter, gRPC stream. (11) Register shutdown hook: close stream → drain batchers → shutdown executor (30s timeout) → stop Helidon server → log "Goodbye!". (12) Build Docker image via Jib (`eclipse-temurin:25-jre`) — add `jib` Gradle plugin to buildSrc convention plugin. Uses Avaje Inject `@Singleton` wiring OR explicit `new` in main — whichever is cleaner. | Integration: verify app starts without error, /health returns 200, /metrics returns Prometheus format, shutdown hook completes within timeout |

```
Batch 1: SOL-36
Batch 2 (needs SOL-36): SOL-37
```

---

### Phase 7: End-to-End & Documentation

| Story | Title | Layer | Description | Tests |
|-------|-------|-------|-------------|-------|
| **SOL-38** | End-to-end integration test | All | Full pipeline for **both adapters**: (A) gRPC: mock gRPC server (grpc-testing `InProcessServer`) sends protobuf transactions → parsed → batched → COPY/INSERT to Testcontainers PostgreSQL → query via Helidon HTTP client. (B) WebSocket: mock WS server sends `blockNotification` JSON → parsed → batched → same DB → same API queries. Verify for both: successful tx in `transactions`, failed in `failed_transactions`, memo in `memos`, large transfer in `large_transfers`, fee payer in `accounts`. Both adapters must produce identical domain objects for the same logical transaction. | Integration: two test classes (or parameterized), one per adapter, covering full data flow → DB → API. Verify domain parity between adapters. |
| **SOL-39** | Graceful shutdown test | All | Start full app with mock gRPC, push N transactions, trigger shutdown, verify all N are persisted (no data loss). Count rows in DB after shutdown. | Integration: verify drain-on-shutdown correctness |
| **SOL-40** | README.md | — | Project overview, tech stack rationale, architecture diagram (Mermaid), quick start (clone → docker-compose → env → run), API reference (8 endpoints with curl examples), configuration reference (8 env vars including STREAM_MODE and RPC_WS_ENDPOINT), streaming modes (free WebSocket vs paid gRPC), make commands |
| **SOL-41** | CLAUDE.md | — | Project identity (Helidon 4 SE, no Spring), architecture rules, coding preferences, testing standards (adapted from stablebridge-tx-recovery), ArchUnit rules, pre-commit checklist, agent workflow |

```
Batch 1 (parallel): SOL-38, SOL-39
Batch 2 (needs Batch 1): SOL-40, SOL-41
```

---

## Dependency Graph

```
Phase 0: Scaffolding
  Batch 1: SOL-1, SOL-2
  Batch 2: SOL-3, SOL-4, SOL-5, SOL-6

Phase 1: Domain (pure Java, no framework deps)
  Batch 1: SOL-7, SOL-8, SOL-9
  Batch 2: SOL-10, SOL-11, SOL-12

Phase 2: Infra — Persistence (pgjdbc + HikariCP)
  Batch 1: SOL-13
  Batch 2: SOL-14, SOL-15, SOL-16, SOL-17, SOL-18, SOL-19

Phase 3: Infra — Transaction Streaming (gRPC + WebSocket)
  Batch 1: SOL-20
  Batch 2: SOL-21, SOL-22, SOL-23
  Batch 3a: SOL-24 (gRPC adapter — paid)
  Batch 3b: SOL-24b, SOL-24c (WebSocket adapter — free, parallel with 3a)

Phase 4: Infra — Metrics & Console
  Batch 1: SOL-25, SOL-27 (parallel)
  Batch 2: SOL-26 (needs SOL-25 — reads MetricsRecorder)

Phase 5: App — REST API (Helidon SE routing)
  Batch 1: SOL-28, SOL-29, SOL-34, SOL-35
  Batch 2: SOL-30, SOL-31, SOL-32, SOL-33

Phase 6: App — Wiring & Lifecycle (main + shutdown)
  Batch 1: SOL-36
  Batch 2: SOL-37

Phase 7: E2E & Docs
  Batch 1: SOL-38, SOL-39
  Batch 2: SOL-40, SOL-41
```

**Cross-phase parallelism**: Phases 2, 3, 4 can all start once Phase 1 completes. Phase 5 needs Phase 2 (repo ports implemented). Phase 6 needs Phases 3, 4, 5 (all pieces to wire). Phase 7 needs all.

---

## Non-Negotiable Rules

### Architecture
- [ ] Domain models: Java records + `@Builder(toBuilder = true)`, ZERO framework imports (only Lombok + `java.*`)
- [ ] Domain ports: plain interfaces, no annotations, no framework types in signatures
- [ ] Domain services: constructor injection via Lombok `@RequiredArgsConstructor`, no annotations beyond Lombok
- [ ] Domain MUST NOT import from `application` or `infrastructure`
- [ ] Infrastructure: implements domain ports, raw JDBC (no JPA), adapters take `DataSource`/`HikariDataSource` in constructor
- [ ] Application: Helidon SE routes — functional handlers, mapping + delegation only
- [ ] `ReentrantLock` over `synchronized` everywhere — avoids VT carrier thread pinning
- [ ] All object mapping: MapStruct `@Mapper(componentModel = "jsr330")`, NEVER manual field copying

### Code Style (from stablebridge-tx-recovery)
- [ ] NO comments or Javadoc — code must be self-documenting
- [ ] NO `System.out`/`System.err` in production code — use `@Slf4j`
- [ ] Use `var` for local variables when type is obvious from RHS
- [ ] Functional style: streams over loops, Optional pipelines over null checks
- [ ] `@RequiredArgsConstructor` for all constructor injection
- [ ] Spotless formatting: `./gradlew spotlessApply` before every commit

### Testing (from stablebridge-tx-recovery, adapted for non-Spring)
- [ ] Single-assert pattern: build expected object → `assertThat(actual).usingRecursiveComparison().ignoringFields(...).isEqualTo(expected)`
- [ ] BDDMockito ONLY: `given()`/`then()`, NEVER `when()`/`verify()`
- [ ] NO generic matchers: no `any()`, `anyString()`, `eq()` — pass actual values
- [ ] AssertJ ONLY — no JUnit `assertEquals`/`assertTrue`
- [ ] Test method naming: `should<Action><Condition>`
- [ ] Fixture constants: `SOME_*` pattern in `src/testFixtures/`
- [ ] Fixture builders: `<concept>Builder()` pattern
- [ ] `// given`, `// when`, `// then` comments in every test method
- [ ] Unit tests: `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`
- [ ] Integration tests: Testcontainers `PostgreSQLContainer` + direct JDBC (no `@SpringBootTest`)
- [ ] Three source sets: `src/test/` (unit), `src/integration-test/` (Testcontainers), `src/testFixtures/` (shared)

### Build
- [ ] `./gradlew build` must pass (compile + Spotless + unit + integration + ArchUnit)
- [ ] Convention plugins in `buildSrc/` — service + library
- [ ] Flyway migrations: `V{N}__{description}.sql`
- [ ] Testcontainers PostgreSQL in integration tests (direct JDBC, no Spring context)
- [ ] `reWriteBatchedInserts=true` on all pgjdbc connection URLs

---

## Story Count Summary

| Phase | Stories | Layer | Key Tech |
|-------|---------|-------|----------|
| 0 — Scaffolding | 6 | — | Gradle, Flyway, ArchUnit, Testcontainers |
| 1 — Domain | 6 | Domain | Pure Java records, interfaces, ReentrantLock |
| 2 — Persistence | 7 | Infrastructure | pgjdbc CopyManager, HikariCP x2, raw JDBC |
| 3 — Streaming (gRPC + WS) | 7 | Infrastructure | Helidon gRPC (paid), JDK WebSocket (free), protobuf, Resilience4j |
| 4 — Metrics & Console | 3 | Infrastructure | Micrometer, ANSI terminal output |
| 5 — REST API | 8 | Application | Helidon 4 SE routing, MapStruct |
| 6 — Wiring & Lifecycle | 2 | Application | main(), shutdown hook, Avaje Inject |
| 7 — E2E & Docs | 4 | — | grpc-testing, Testcontainers, full pipeline |
| **Total** | **43 stories** | | |
