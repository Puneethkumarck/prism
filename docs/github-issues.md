# Solana Indexer — GitHub Issues

> 43 stories, agent-native format. Each issue is self-contained with context, instructions, file list, and acceptance criteria.
> Base package: `com.stablebridge.prism`
> Functional spec: `docs/functional-spec.md` | Plan: `docs/implementation-plan.md`

---

## Phase 0: Project Scaffolding

### SOL-1: Initialize multi-module Gradle project

**Labels**: `phase:0-scaffolding`
**Blocked by**: —

**Context**

This is the foundation for the entire project. We use Helidon 4 SE (no Spring Boot), Avaje Inject for compile-time DI, and Gradle 9 with Kotlin DSL convention plugins.

**Instructions**

1. Create root `build.gradle.kts` with `java` plugin, Java 25 toolchain
2. Create `settings.gradle.kts` including modules: `prism`, `prism-api`
3. Create `buildSrc/src/main/kotlin/prism.service.gradle.kts` convention plugin:
   - Applies: `java`, `java-test-fixtures`, `com.diffplug.spotless`, `com.google.cloud.tools.jib`
   - Java toolchain: 25
   - Spotless: `removeUnusedImports()`, `importOrder("java|javax", "jakarta", "org", "com", "")`, `trimTrailingWhitespace()`, `endWithNewline()`
   - Jib: `from.image = "eclipse-temurin:25-jre"`, `to.image = "stablebridge/prism"`
   - Source sets: `integrationTest` (src/integration-test/java)
   - MapStruct compiler args: `-Amapstruct.defaultComponentModel=jsr330`, `-Amapstruct.unmappedTargetPolicy=ERROR`
   - Tasks: `integrationTest` depends on `test`, `check` depends on `integrationTest`
4. Create `buildSrc/src/main/kotlin/prism.library.gradle.kts` for the API module (java-library + spotless)
5. Create `gradle/libs.versions.toml` with:
   - `helidon = "4.4.0"`, `avaje-inject = "11.x"`, `pgjdbc = "42.7.x"`, `hikari = "6.x"`, `jackson = "2.18.x"`, `mapstruct = "1.6.3"`, `flyway = "12.x"`, `resilience4j = "2.3.x"`, `micrometer = "1.14.x"`, `archunit = "1.4.1"`, `testcontainers = "1.21.4"`, `mockito = "5.x"`, `assertj = "3.x"`, `protobuf = "4.x"`, `lombok = "1.18.44"`, `logback = "1.5.x"`, `logstash-logback = "9.0"`, `jansi = "2.4.x"`
6. Create `.editorconfig` (indent_style=space, indent_size=4, charset=utf-8, end_of_line=lf)
7. Create `.gitignore` (build/, .gradle/, .idea/, *.iml, .env)
8. Create `Makefile` with targets: `build`, `test`, `integration-test`, `clean`, `format`, `run`, `infra-up`, `infra-down`, `infra-clean`, `docker-build`, `up`, `down`, `help`
9. Verify: `./gradlew build` compiles without error (no source yet, but plugins resolve)

**Files to create**

- `build.gradle.kts`
- `settings.gradle.kts`
- `buildSrc/build.gradle.kts`
- `buildSrc/settings.gradle.kts`
- `buildSrc/src/main/kotlin/prism.service.gradle.kts`
- `buildSrc/src/main/kotlin/prism.library.gradle.kts`
- `gradle/libs.versions.toml`
- `.editorconfig`
- `.gitignore`
- `Makefile`

**Acceptance criteria**

- [ ] `./gradlew build` succeeds (clean compile, spotless passes)
- [ ] `./gradlew spotlessApply` runs without error
- [ ] Two modules resolve: `prism`, `prism-api`
- [ ] Java 25 toolchain configured
- [ ] `integrationTest` source set created in service plugin
- [ ] `libs.versions.toml` has all dependencies listed above
- [ ] Makefile `help` target lists all targets

---

### SOL-2: Create API module with shared DTOs

**Labels**: `phase:0-scaffolding`
**Blocked by**: —

**Context**

Shared response DTOs used by Helidon route handlers and tests. Lives in the `prism-api` module (java-library, no Helidon dependency).

**Instructions**

1. Create `prism-api/build.gradle.kts` applying `prism.library` plugin. Dependencies: Lombok, Jackson annotations (for serialization)
2. Create all DTOs as Java records with `@Builder(toBuilder = true)` in `com.stablebridge.prism.api`:

| Record | Fields |
|--------|--------|
| `Page<T>` | `List<T> data`, `long total`, `long limit`, `long offset` |
| `TransactionResponse` | `String signature`, `long slot`, `boolean success`, `Instant createdAt` |
| `TransferResponse` | `int id`, `String signature`, `long slot`, `BigDecimal amount`, `Instant createdAt` |
| `MemoResponse` | `int id`, `String signature`, `String memo`, `Instant createdAt` |
| `AccountResponse` | `String pubkey`, `long lamports`, `long slot`, `boolean executable`, `long rentEpoch`, `Instant createdAt` |
| `StatsResponse` | `long totalTransactions`, `long totalFailed`, `long totalTransfers`, `long totalMemos`, `long totalAccounts` |
| `HealthResponse` | `String status`, `long uptimeSecs` |
| `ErrorResponse` | `String error`, `int status` |

**Files to create**

- `prism-api/build.gradle.kts`
- `prism-api/src/main/java/com/stablebridge/prism/api/Page.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/TransactionResponse.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/TransferResponse.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/MemoResponse.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/AccountResponse.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/StatsResponse.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/HealthResponse.java`
- `prism-api/src/main/java/com/stablebridge/prism/api/ErrorResponse.java`

**Acceptance criteria**

- [ ] `./gradlew :prism-api:build` compiles
- [ ] All 8 records have `@Builder(toBuilder = true)`
- [ ] `Page<T>` is generic
- [ ] No Helidon, Spring, or framework imports in this module
- [ ] Spotless passes

---

### SOL-3: Create Flyway migrations

**Labels**: `phase:0-scaffolding`
**Blocked by**: SOL-1

**Context**

Database schema from functional spec section 4. Two migrations: tables first, then staging + indexes.

**Instructions**

1. Create `V1__create_tables.sql` with 5 tables:

```sql
CREATE TABLE IF NOT EXISTS transactions (
    signature  VARCHAR(88) PRIMARY KEY,
    slot       BIGINT NOT NULL,
    success    BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS failed_transactions (
    id         SERIAL PRIMARY KEY,
    signature  VARCHAR(88) NOT NULL,
    slot       BIGINT NOT NULL,
    error      TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS large_transfers (
    id         SERIAL PRIMARY KEY,
    signature  VARCHAR(88) NOT NULL,
    slot       BIGINT NOT NULL,
    amount     NUMERIC NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS memos (
    id         SERIAL PRIMARY KEY,
    signature  VARCHAR(88) NOT NULL,
    memo       TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts (
    id         SERIAL PRIMARY KEY,
    pubkey     VARCHAR(88) NOT NULL UNIQUE,
    lamports   BIGINT NOT NULL,
    slot       BIGINT NOT NULL,
    executable BOOLEAN NOT NULL,
    rent_epoch BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

2. Create `V2__create_staging_table.sql`:

```sql
CREATE TABLE IF NOT EXISTS staging_transactions (
    signature VARCHAR(88),
    slot      BIGINT,
    success   BOOLEAN
);
```

3. Create `V3__create_indexes.sql` — **IMPORTANT**: `CREATE INDEX CONCURRENTLY` cannot run inside a transaction. This migration must include the Flyway directive to disable transactional execution:

```sql
-- flyway:executeInTransaction=false

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_slot ON transactions(slot);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_transactions_success ON transactions(success, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_large_transfers_amount ON large_transfers(amount DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_large_transfers_created_at ON large_transfers(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_memos_created_at ON memos(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_failed_tx_created_at ON failed_transactions(created_at DESC);
```

**Files to create**

- `prism/src/main/resources/db/migration/V1__create_tables.sql`
- `prism/src/main/resources/db/migration/V2__create_staging_table.sql`
- `prism/src/main/resources/db/migration/V3__create_indexes.sql`

**Acceptance criteria**

- [ ] 5 tables match functional spec section 4 exactly (column names, types, constraints)
- [ ] `staging_transactions` has no constraints (temporary staging)
- [ ] 7 indexes created with correct column ordering (DESC where specified)
- [ ] V3 migration includes `-- flyway:executeInTransaction=false` (required for `CREATE INDEX CONCURRENTLY`)
- [ ] `accounts.pubkey` has UNIQUE constraint (inline, from V1)
- [ ] Migration file naming: `V{N}__{description}.sql`

---

### SOL-4: Add ArchUnit rules

**Labels**: `phase:0-scaffolding`
**Blocked by**: SOL-1

**Context**

Enforce hexagonal architecture at build time. Domain must be framework-free (only Lombok + java.*).

**Instructions**

Create `ArchitectureTest.java` in `src/test/java` with 5 rules:

| # | Rule | Purpose |
|---|------|---------|
| 1 | `domain..` must NOT depend on `infrastructure..` | Domain is pure |
| 2 | `domain..` must NOT depend on `application..` | Domain is innermost layer |
| 3 | `domain..` must NOT import `io.helidon..`, `jakarta..`, `io.avaje..` | Only Lombok + java.* allowed |
| 4 | `domain..` must NOT import `java.sql..` | JDBC stays in infrastructure |
| 5 | `infrastructure..` must NOT depend on `application.routing..` | No reverse dependencies |

**Files to create**

- `prism/src/test/java/com/stablebridge/prism/ArchitectureTest.java`

**Acceptance criteria**

- [ ] All 5 rules defined using ArchUnit `ArchRuleDefinition`
- [ ] Tests pass with empty packages (no source yet)
- [ ] Domain allowed imports: `lombok..`, `java..` (standard library), `com.stablebridge.prism.domain..`
- [ ] `./gradlew test` passes

---

### SOL-5: Add docker-compose.yml

**Labels**: `phase:0-scaffolding`
**Blocked by**: SOL-1

**Context**

Local development infrastructure: PostgreSQL for data, Prometheus + Grafana for observability.

**Instructions**

1. Create `docker-compose.yml` with services:

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| postgres | `postgres:16-alpine` | 5432 | Transaction storage |
| prometheus | `prom/prometheus:v3.4.0` | 9091 | Metrics scraping |
| grafana | `grafana/grafana:11.6.0` | 3000 | Dashboards |
| app (profile: app) | `stablebridge/prism:latest` | 3000 / 9090 | Indexer (optional) |

2. Create `.env.example`:

```bash
STREAM_MODE=websocket
RPC_WS_ENDPOINT=wss://api.mainnet-beta.solana.com
GRPC_ENDPOINT=
X_TOKEN=
DATABASE_URL=postgresql://indexer:indexer@localhost:5432/indexer
API_PORT=3000
CONSOLE_LOG=true
BENCH_LOG=benchmark.log
```

3. Add Makefile targets: `infra-up`, `infra-down`, `infra-clean`, `infra-status`, `infra-logs`, `docker-build`, `up`, `down`

**Files to create**

- `docker-compose.yml`
- `.env.example`
- Update `Makefile` (add infra targets)

**Acceptance criteria**

- [ ] `make infra-up` starts PostgreSQL, Prometheus, Grafana
- [ ] PostgreSQL accessible on `localhost:5432` with user/pass `indexer/indexer`, database `indexer`
- [ ] `.env.example` has all 8 variables with defaults
- [ ] `STREAM_MODE` defaults to `websocket` (free)
- [ ] `make infra-clean` removes volumes

---

### SOL-6: Add test infrastructure

**Labels**: `phase:0-scaffolding`
**Blocked by**: SOL-1

**Context**

Shared test utilities following stablebridge-tx-recovery patterns. No Spring — uses Testcontainers directly with JUnit 5 extensions.

**Instructions**

1. Create `PostgresExtension` — JUnit 5 `BeforeAllCallback` that starts `PostgreSQLContainer`, runs Flyway, exposes `DataSource` via static getter
2. Create `TestDataSourceFactory` — creates HikariCP pool over the Testcontainers PostgreSQL for integration tests
3. Create `TestUtils` with:
   - `eqIgnoringTimestamps(T expected)` — Mockito `argThat` using recursive comparison ignoring `Instant`/`OffsetDateTime` types
   - `eqIgnoring(T expected, String... fields)` — same but also ignoring named fields
4. Create fixture base package `com.stablebridge.prism.fixtures` in `src/testFixtures/`

**Files to create**

- `prism/src/testFixtures/java/com/stablebridge/prism/testutil/PostgresExtension.java`
- `prism/src/testFixtures/java/com/stablebridge/prism/testutil/TestDataSourceFactory.java`
- `prism/src/testFixtures/java/com/stablebridge/prism/testutil/TestUtils.java`
- `prism/src/testFixtures/java/com/stablebridge/prism/fixtures/package-info.java`

**Acceptance criteria**

- [ ] `PostgresExtension` starts Testcontainers PostgreSQL 16 and runs Flyway migrations
- [ ] `TestDataSourceFactory` returns `HikariDataSource` connected to test container
- [ ] `TestUtils.eqIgnoring` works with recursive comparison, ignoring specified fields
- [ ] `testFixtures` source set compiles
- [ ] No Spring imports anywhere in test utilities

---

## Phase 1: Domain Layer

### SOL-7: Domain models

**Labels**: `phase:1-domain`
**Blocked by**: SOL-1

**Context**

All domain models are Java records with `@Builder(toBuilder = true)`. ZERO framework imports — only Lombok + `java.*`. See functional spec sections 3 (FR-2, FR-4) and appendix for field definitions.

**Instructions**

Create 8 records in `domain/model/`:

| Record | Fields | Notes |
|--------|--------|-------|
| `SolanaTransaction` | `Signature signature`, `long slot`, `BigDecimal amount`, `boolean failed`, `String memo` (nullable), `Pubkey from` (nullable), `Pubkey to` (nullable) | Aggregate root with toLargeTransfer(), toMemo(), toFailedTransaction() |
| `Account` | `Pubkey pubkey`, `long lamports`, `long slot`, `boolean executable`, `long rentEpoch` | Fee payer snapshot |
| `LargeTransfer` | `Signature signature`, `long slot`, `BigDecimal amount` | Projection: transfers > 1.0 SOL |
| `Memo` | `Signature signature`, `String memoText` | Projection: extracted memo payload |
| `FailedTransaction` | `Signature signature`, `long slot`, `String error` | Projection: on-chain failures |
| `Signature` | `String value` | Value object: max 88 chars, non-null, non-blank |
| `Pubkey` | `String value` | Value object: max 44 chars, non-null, non-blank |
| `Slot` | `long value` | Value object: non-negative |
| `BatchResult` | `long written`, `long failed`, `long memos`, `long transfers` | Flush outcome (all non-negative) |
| `IndexerStats` | `long totalTransactions`, `long totalFailed`, `long totalTransfers`, `long totalMemos`, `long totalAccounts` | pg_stat_user_tables result |

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/domain/model/SolanaTransaction.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/Account.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/LargeTransfer.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/Memo.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/FailedTransaction.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/Slot.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/BatchResult.java`
- `prism/src/main/java/com/stablebridge/prism/domain/model/IndexerStats.java`

**Acceptance criteria**

- [ ] All 8 records compile with `@Builder(toBuilder = true)`
- [ ] ZERO imports from `io.helidon`, `jakarta`, `java.sql`, `io.avaje`
- [ ] Only Lombok + `java.*` imports
- [ ] ArchUnit test (SOL-4) passes
- [ ] Unit test: build each record via builder, verify `toBuilder()` creates copy

---

### SOL-8: Domain ports

**Labels**: `phase:1-domain`
**Blocked by**: SOL-1

**Context**

Port interfaces define the contract between domain and infrastructure. Plain Java interfaces, no annotations, no framework types.

**Instructions**

Create 8 interfaces in `domain/port/`:

```java
public interface TransactionStream {
    void subscribe(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer);
    void close();
}

public interface TransactionRepository {
    void bulkInsert(List<SolanaTransaction> batch);
    Optional<SolanaTransaction> findBySignature(Signature signature);
    List<SolanaTransaction> findBySlot(long slot);
    List<SolanaTransaction> findAll(long limit, long offset, Boolean success);
    long countAll();
    long countBySuccess(boolean success);
}

public interface FailedTransactionRepository {
    void bulkInsert(List<FailedTransaction> batch);
}

public interface TransferRepository {
    void bulkInsert(List<LargeTransfer> transfers);
    List<LargeTransfer> findByMinAmount(BigDecimal minAmount, long limit, long offset);
    long countByMinAmount(BigDecimal minAmount);
}

public interface MemoRepository {
    void bulkInsert(List<Memo> memos);
    List<Memo> findAll(long limit, long offset);
    long countAll();
}

public interface AccountRepository {
    void batchUpsert(List<Account> accounts);
    Optional<Account> findByPubkey(Pubkey pubkey);
}

public interface StatsRepository {
    IndexerStats getStats();
}

public interface MetricsRecorder {
    void recordBatch(BatchResult result);
    void recordSlot();
    void incrementReceived();
}
```

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/domain/port/TransactionStream.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/TransactionRepository.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/FailedTransactionRepository.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/TransferRepository.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/MemoRepository.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/AccountRepository.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/StatsRepository.java`
- `prism/src/main/java/com/stablebridge/prism/domain/port/MetricsRecorder.java`

**Acceptance criteria**

- [ ] All 8 interfaces compile
- [ ] No annotations on any interface
- [ ] Only domain model types in method signatures (no `DataSource`, `Connection`, `JsonNode`, etc.)
- [ ] `TransactionStream.subscribe` takes two `Consumer` callbacks (tx + account)
- [ ] ArchUnit test passes

---

### SOL-9: LargeTransferFilter

**Labels**: `phase:1-domain`
**Blocked by**: SOL-1

**Context**

Pure function from functional spec FR-5. The filter itself only checks amount. The `NOT failed` exclusion is applied separately by the caller (TransactionProcessor).

**Instructions**

Create `LargeTransferFilter` in `domain/service/` with:
- Constant: `public static final BigDecimal LARGE_TRANSFER_THRESHOLD_SOL = new BigDecimal("1.0")`
- Static method: `public static boolean isLargeTransfer(BigDecimal amount)` returns `amount.compareTo(LARGE_TRANSFER_THRESHOLD_SOL) > 0`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/domain/service/LargeTransferFilter.java`
- `prism/src/test/java/com/stablebridge/prism/domain/service/LargeTransferFilterTest.java`

**Test cases** (unit, from functional spec section 8):

- [ ] `shouldDetectLargeTransfer` — 1.1, 10.0, 100.0, 1000.0 → all return `true`
- [ ] `shouldIgnoreSmallTransfer` — 0.0, 0.000005, 0.5 → all return `false`
- [ ] `shouldNotDetectExactThreshold` — 1.0 → returns `false` (strictly greater than)

**Acceptance criteria**

- [ ] Pure static method, no dependencies, no constructor
- [ ] Threshold is a public constant
- [ ] Exactly 1.0 SOL is NOT a large transfer
- [ ] `@Slf4j` annotation on class (Lombok convention)
- [ ] `./gradlew test` passes with all 3 test cases

---

### SOL-10: TransactionProcessor

**Labels**: `phase:1-domain`
**Blocked by**: SOL-7, SOL-8, SOL-9

**Context**

Core batch processing logic from functional spec FR-6. Takes a batch of transactions, classifies them, and writes to 4 repository ports in parallel. This is the domain service called by `TransactionBatchService` on each flush.

**Instructions**

Create `TransactionProcessor` in `domain/service/`:
- Constructor injection: `TransactionRepository`, `FailedTransactionRepository`, `TransferRepository`, `MemoRepository`, `MetricsRecorder`
- Method: `public BatchResult process(List<SolanaTransaction> batch)`
- Classification logic:
  - **Successful transactions** (`!failed`): → `TransactionRepository.bulkInsert()`
  - **Failed transactions** (`failed`): → `FailedTransactionRepository.bulkInsert()` via `tx.toFailedTransaction("Transaction failed")`
  - **Memos** (`memo != null`, includes failed): → `MemoRepository.bulkInsert()` via `tx.toMemo()`
  - **Large transfers** (`!failed && LargeTransferFilter.isLargeTransfer(amount)`): → `TransferRepository.bulkInsert()` via `tx.toLargeTransfer()`
- All 4 repo calls execute **in parallel** using virtual threads (`Executors.newVirtualThreadPerTaskExecutor()` + `Future.get()`)
- Use `ReentrantLock` if any shared state needed (not `synchronized`)
- After writes: call `metricsRecorder.recordBatch(result)` with counts

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/domain/service/TransactionProcessor.java`
- `prism/src/test/java/com/stablebridge/prism/domain/service/TransactionProcessorTest.java`
- `prism/src/testFixtures/java/com/stablebridge/prism/fixtures/TransactionFixtures.java`

**Test cases** (unit, BDDMockito, actual values — no `any()`):

- [ ] `shouldRouteSuccessfulTransactionsToTransactionRepository` — batch of 3 successful txs → `then(transactionRepository).should().bulkInsert(batch)`
- [ ] `shouldRouteFailedTransactionsToFailedTransactionRepository` — batch with 2 failed txs → verify `FailedTransaction` objects with error "Transaction failed"
- [ ] `shouldRouteMemoTransactionsToMemoRepository` — batch with 1 memo tx → verify `Memo` objects
- [ ] `shouldIncludeFailedMemoTransactionsInMemoRepository` — failed tx WITH memo → persisted to memos (NOT filtered)
- [ ] `shouldRouteLargeTransfersToTransferRepository` — tx with amount 5.0 SOL → verify `LargeTransfer`
- [ ] `shouldExcludeFailedFromLargeTransfers` — failed tx with amount 5.0 SOL → NOT routed to TransferRepository
- [ ] `shouldRecordBatchMetrics` — verify `metricsRecorder.recordBatch()` called with correct counts
- [ ] `shouldReturnBatchResultWithCorrectCounts` — verify returned `BatchResult` fields

**Acceptance criteria**

- [ ] 4 parallel writes via virtual thread executor
- [ ] Memo insert includes failed transactions with memos
- [ ] Large transfer insert excludes failed transactions
- [ ] `ReentrantLock` used (not `synchronized`) if shared state exists
- [ ] BDDMockito: `given()`/`then()`, no generic matchers
- [ ] Single-assert pattern with recursive comparison where applicable
- [ ] `// given`, `// when`, `// then` comments in every test
- [ ] Fixture builders in `TransactionFixtures` using `SOME_*` constants

---

### SOL-11: TransactionBatchService

**Labels**: `phase:1-domain`
**Blocked by**: SOL-7, SOL-8, SOL-10

**Context**

Dual-trigger batch accumulation from functional spec FR-6. Accumulates transactions from the stream and flushes when 200 items OR 100ms timeout. Delegates actual processing to `TransactionProcessor`.

**Instructions**

Create `TransactionBatchService` in `domain/service/`:
- Constructor: `TransactionProcessor processor`
- Internal: `LinkedTransferQueue<SolanaTransaction>` (unbounded — prevents gRPC backpressure)
- Method: `public void enqueue(SolanaTransaction tx)` — non-blocking add to queue
- Method: `public void run()` — main loop (intended to run on a virtual thread):
  ```
  while running:
    tx = queue.poll(100, MILLISECONDS)
    if tx != null: buffer.add(tx)
    if buffer.size >= 200 OR (tx == null AND buffer not empty):
      processor.process(buffer)
      buffer.clear()
  ```
- Method: `public void close()` — sets running=false, drains remaining buffer, calls `processor.process(remaining)`
- Use `ReentrantLock` for any shared state between `enqueue()` and `run()`
- Constants: `BATCH_SIZE = 200`, `FLUSH_INTERVAL_MS = 100`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/domain/service/TransactionBatchService.java`
- `prism/src/test/java/com/stablebridge/prism/domain/service/TransactionBatchServiceTest.java`

**Test cases** (unit):

- [ ] `shouldFlushWhenBatchSizeReached` — enqueue 200 txs → verify `processor.process()` called with 200 items
- [ ] `shouldFlushOnTimeoutWhenBelowBatchSize` — enqueue 50 txs, wait > 100ms → verify flush with 50
- [ ] `shouldDrainRemainingOnClose` — enqueue 10 txs, call close() → verify flush with 10
- [ ] `shouldNotFlushEmptyBatch` — no enqueues, timeout fires → no `processor.process()` call
- [ ] `shouldUseUnboundedQueue` — enqueue 10,000 txs without blocking (no backpressure)

**Acceptance criteria**

- [ ] `LinkedTransferQueue` (unbounded) used — not `ArrayBlockingQueue`
- [ ] Dual trigger: size (200) OR time (100ms)
- [ ] `close()` drains and flushes remaining items
- [ ] `ReentrantLock` not `synchronized`
- [ ] All tests pass with BDDMockito

---

### SOL-12: AccountBatchService

**Labels**: `phase:1-domain`
**Blocked by**: SOL-7, SOL-8

**Context**

From functional spec FR-7. Slower cadence (2s), deduplicates by pubkey (highest slot wins), bounded input queue (10K capacity).

**Instructions**

Create `AccountBatchService` in `domain/service/`:
- Constructor: `AccountRepository accountRepository`, `MetricsRecorder metricsRecorder`
- Internal: `ArrayBlockingQueue<Account>(10_000)` (bounded — drops if full)
- Method: `public boolean offer(Account account)` — non-blocking `queue.offer(account)`, returns false if full (dropped)
- Method: `public void run()` — main loop on virtual thread:
  ```
  while running:
    account = queue.poll(2000, MILLISECONDS)
    if account != null: buffer.add(account)
    if buffer.size >= 200 OR (account == null AND buffer not empty):
      dedup buffer by pubkey (keep highest slot via HashMap.merge)
      accountRepository.batchUpsert(deduped)
      metricsRecorder.recordAccountsWritten(deduped.size())
      buffer.clear()
  ```
- Method: `public void close()` — sets running=false, drains, flushes
- Constants: `BATCH_SIZE = 200`, `FLUSH_INTERVAL_MS = 2000`, `QUEUE_CAPACITY = 10_000`
- Dedup: `HashMap<String, Account>`, `map.merge(a.pubkey(), a, (prev, curr) -> curr.slot() > prev.slot() ? curr : prev)`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/domain/service/AccountBatchService.java`
- `prism/src/test/java/com/stablebridge/prism/domain/service/AccountBatchServiceTest.java`
- `prism/src/testFixtures/java/com/stablebridge/prism/fixtures/AccountFixtures.java`

**Test cases** (unit):

- [ ] `shouldFlushOnBatchSize` — offer 200 accounts → verify `batchUpsert` called
- [ ] `shouldFlushOnTimeout` — offer 50 accounts, wait > 2s → verify flush
- [ ] `shouldDeduplicateByPubkeyKeepingHighestSlot` — offer 3 accounts with same pubkey but slots 100, 300, 200 → upsert called with slot=300
- [ ] `shouldDropWhenQueueFull` — fill 10,000 entries → next `offer()` returns false
- [ ] `shouldDrainOnClose` — offer 10, close() → verify flush

**Acceptance criteria**

- [ ] `ArrayBlockingQueue` with capacity 10,000
- [ ] `offer()` returns boolean (non-blocking, drops if full)
- [ ] Dedup by pubkey keeps highest slot
- [ ] 200/2s dual trigger
- [ ] `close()` drains and flushes

---

## Phase 2: Infrastructure — Persistence Adapters

### SOL-13: DataSourceFactory + FlywayMigrator

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-3

**Context**

Two separate HikariCP pools (write + read) from functional spec section 6. Flyway runs standalone (no Spring).

**Instructions**

1. Create `DataSourceFactory` in `infrastructure/persistence/`:
   - Method: `public static HikariDataSource createWritePool(String jdbcUrl)` — max=20, min=5, acquireTimeout=10s, idleTimeout=60s, poolName="write-pool", `reWriteBatchedInserts=true` in JDBC URL
   - Method: `public static HikariDataSource createReadPool(String jdbcUrl)` — same settings, poolName="read-pool", `readOnly=true`

2. Create `FlywayMigrator` in `infrastructure/persistence/`:
   - Method: `public static void migrate(DataSource dataSource)` — runs `Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/DataSourceFactory.java`
- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/FlywayMigrator.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/DataSourceFactoryIntegrationTest.java`

**Test cases** (integration with Testcontainers):

- [ ] `shouldCreateWritePoolAndConnect` — create pool, execute `SELECT 1`, verify no error
- [ ] `shouldCreateReadPoolAndConnect` — same for read pool
- [ ] `shouldRunFlywayMigrations` — migrate, verify tables exist via `information_schema.tables`
- [ ] `shouldHaveReWriteBatchedInsertsEnabled` — verify JDBC URL property

**Acceptance criteria**

- [ ] Write pool: 20 max, 5 min, 10s acquire, 60s idle, `reWriteBatchedInserts=true`
- [ ] Read pool: same settings + `readOnly=true`
- [ ] Flyway migrates both V1 and V2 successfully
- [ ] No Spring imports

---

### SOL-14: CopyTransactionRepository

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-13, SOL-7, SOL-8

**Context**

The critical performance path from functional spec FR-6. Uses PostgreSQL COPY protocol via pgjdbc `CopyManager` for 5-10x faster writes than INSERT. Takes two DataSources (write for COPY, read for queries).

**Instructions**

Create `CopyTransactionRepository` in `infrastructure/persistence/` implementing `TransactionRepository`:

**Write methods** (use write pool):
- `bulkInsert(List<SolanaTransaction> batch)`:
  1. Filter to `!failed` only
  2. If empty, return
  3. Get connection from write pool, unwrap to `PgConnection`
  4. Build TSV string: for each tx → `signature\tslot\tt\n` (PostgreSQL TEXT format: `t` = true)
  5. `pgConn.getCopyAPI().copyIn("COPY staging_transactions (signature, slot, success) FROM STDIN (FORMAT TEXT)", new ByteArrayInputStream(tsv.getBytes(UTF_8)))`
  6. Execute: `INSERT INTO transactions (signature, slot, success) SELECT signature, slot, success FROM staging_transactions ON CONFLICT (signature) DO NOTHING`
  7. Execute: `TRUNCATE staging_transactions`

**Read methods** (use read pool):
- `findBySignature(Signature sig)` → `SELECT signature, slot, success, created_at FROM transactions WHERE signature = ?` (use `sig.value()` for PreparedStatement)
- `findBySlot(long slot)` → `SELECT ... WHERE slot = ? ORDER BY created_at ASC`
- `findAll(long limit, long offset, Boolean success)` → if success != null: `WHERE success = ?`, `ORDER BY created_at DESC LIMIT ? OFFSET ?`
- `countAll()` → `SELECT COUNT(*) FROM transactions`
- `countBySuccess(boolean success)` → `SELECT COUNT(*) FROM transactions WHERE success = ?`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/CopyTransactionRepository.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/CopyTransactionRepositoryIntegrationTest.java`

**Test cases** (integration with Testcontainers):

- [ ] `shouldCopyTransactionsToDatabase` — COPY 100 txs → `countAll()` returns 100
- [ ] `shouldSkipDuplicateSignatures` — COPY same signature twice → count still 1 (ON CONFLICT DO NOTHING)
- [ ] `shouldFilterFailedTransactionsFromCopy` — batch with 3 successful + 2 failed → only 3 in `transactions` table
- [ ] `shouldFindBySignature` — insert, find → verify all fields
- [ ] `shouldReturnEmptyForMissingSignature` — find non-existent → `Optional.empty()`
- [ ] `shouldFindBySlot` — insert 3 txs in same slot → find returns 3, ordered ASC
- [ ] `shouldPaginateWithLimitAndOffset` — insert 10 txs → limit=3, offset=0 returns 3; offset=3 returns next 3
- [ ] `shouldFilterBySuccess` — insert mixed → filter success=true returns only successful

**Acceptance criteria**

- [ ] Uses pgjdbc `CopyManager.copyIn()` with `COPY ... FROM STDIN (FORMAT TEXT)`
- [ ] TSV format: `signature\tslot\tt\n`
- [ ] Staging table merge + truncate after each COPY
- [ ] Read methods use read pool DataSource
- [ ] Write methods use write pool DataSource
- [ ] No JPA, no Spring Data — raw JDBC only

---

### SOL-15: JdbcFailedTransactionRepository

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-13, SOL-7, SOL-8

**Context**

Batch INSERT for failed transactions (functional spec FR-6). Write-only — no read methods needed.

**Instructions**

Create `JdbcFailedTransactionRepository` implementing `FailedTransactionRepository`:
- Constructor: write pool `DataSource`
- `bulkInsert(List<FailedTransaction> batch)`: `PreparedStatement.addBatch()` with `INSERT INTO failed_transactions (signature, slot, error) VALUES (?, ?, ?)`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/JdbcFailedTransactionRepository.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/JdbcFailedTransactionRepositoryIntegrationTest.java`

**Test cases** (integration):

- [ ] `shouldBatchInsertFailedTransactions` — insert 50 → verify count and column values
- [ ] `shouldHandleEmptyBatch` — insert empty list → no error

**Acceptance criteria**

- [ ] Batch INSERT via `PreparedStatement.addBatch()`
- [ ] `reWriteBatchedInserts=true` on connection URL (from DataSourceFactory)
- [ ] No read methods

---

### SOL-16: JdbcTransferRepository

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-13, SOL-7, SOL-8

**Instructions**

Create `JdbcTransferRepository` implementing `TransferRepository`:
- Constructor: write pool + read pool DataSources
- Write: `bulkInsert(List<LargeTransfer>)` → batch INSERT into `large_transfers`
- Read: `findByMinAmount(min, limit, offset)` → `WHERE amount >= ? ORDER BY amount DESC LIMIT ? OFFSET ?`
- Read: `countByMinAmount(min)` → `SELECT COUNT(*) ... WHERE amount >= ?`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/JdbcTransferRepository.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/JdbcTransferRepositoryIntegrationTest.java`

**Test cases** (integration):

- [ ] `shouldBatchInsertTransfers` — insert 10 → verify count
- [ ] `shouldFindByMinAmount` — insert amounts [0.5, 2.0, 5.0, 10.0] → min_amount=2.0 returns 3
- [ ] `shouldOrderByAmountDesc` — verify first result has highest amount
- [ ] `shouldCountByMinAmount` — verify count matches filtered results

**Acceptance criteria**

- [ ] Reads use read pool, writes use write pool
- [ ] `ORDER BY amount DESC`
- [ ] Pagination with LIMIT/OFFSET

---

### SOL-17: JdbcMemoRepository

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-13, SOL-7, SOL-8

**Instructions**

Create `JdbcMemoRepository` implementing `MemoRepository`:
- Constructor: write pool + read pool
- Write: `bulkInsert(List<Memo>)` → batch INSERT into `memos`
- Read: `findAll(limit, offset)` → `ORDER BY created_at DESC LIMIT ? OFFSET ?`
- Read: `countAll()` → `SELECT COUNT(*) FROM memos`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/JdbcMemoRepository.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/JdbcMemoRepositoryIntegrationTest.java`

**Test cases** (integration):

- [ ] `shouldBatchInsertMemos` — insert 5 → verify count
- [ ] `shouldPaginateMemos` — insert 10, limit=3, offset=0 → returns 3
- [ ] `shouldOrderByCreatedAtDesc` — verify newest first

**Acceptance criteria**

- [ ] `ORDER BY created_at DESC`
- [ ] Reads use read pool

---

### SOL-18: JdbcAccountRepository

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-13, SOL-7, SOL-8

**Instructions**

Create `JdbcAccountRepository` implementing `AccountRepository`:
- Constructor: write pool + read pool
- Write: `batchUpsert(List<Account>)`:
  ```sql
  INSERT INTO accounts (pubkey, lamports, slot, executable, rent_epoch)
  VALUES (?, ?, ?, ?, ?)
  ON CONFLICT (pubkey) DO UPDATE SET
    lamports = EXCLUDED.lamports,
    slot = EXCLUDED.slot,
    executable = EXCLUDED.executable,
    rent_epoch = EXCLUDED.rent_epoch
  ```
- Read: `findByPubkey(Pubkey)` → `SELECT ... WHERE pubkey = ?` (use `pubkey.value()` for PreparedStatement)

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/JdbcAccountRepository.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/JdbcAccountRepositoryIntegrationTest.java`

**Test cases** (integration):

- [ ] `shouldInsertNewAccount` — upsert → findByPubkey returns it
- [ ] `shouldUpdateExistingAccountOnConflict` — insert with slot=100, upsert same pubkey with slot=200 → lamports updated
- [ ] `shouldReturnEmptyForMissingPubkey` — find non-existent → `Optional.empty()`

**Acceptance criteria**

- [ ] `ON CONFLICT (pubkey) DO UPDATE` for upsert
- [ ] Reads use read pool

---

### SOL-19: JdbcStatsRepository

**Labels**: `phase:2-persistence`
**Blocked by**: SOL-13, SOL-7, SOL-8

**Instructions**

Create `JdbcStatsRepository` implementing `StatsRepository`:
- Constructor: **read pool** DataSource only
- `getStats()`: query `pg_stat_user_tables.n_live_tup` for each of 5 tables:
  ```sql
  SELECT COALESCE(n_live_tup, 0) FROM pg_stat_user_tables WHERE relname = ?
  ```
  Tables: `transactions`, `failed_transactions`, `large_transfers`, `memos`, `accounts`

**Files to create**

- `prism/src/main/java/com/stablebridge/prism/infrastructure/persistence/JdbcStatsRepository.java`
- `prism/src/integration-test/java/com/stablebridge/prism/infrastructure/persistence/JdbcStatsRepositoryIntegrationTest.java`

**Test cases** (integration):

- [ ] `shouldReturnNonNegativeCounts` — call getStats() → all 5 fields >= 0
- [ ] `shouldReflectInsertedRows` — insert rows, call getStats() → counts > 0

**Acceptance criteria**

- [ ] Uses `pg_stat_user_tables` (NOT `COUNT(*)`)
- [ ] Uses read pool only
- [ ] Returns `IndexerStats` domain model

---

## Phase 3: Infrastructure — Transaction Streaming

### SOL-20: Protobuf compilation

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-1

**Instructions**

1. Add Gradle `protobuf` plugin to `buildSrc` convention plugin
2. Download Yellowstone Geyser `.proto` files from [rpcpool/yellowstone-grpc](https://github.com/rpcpool/yellowstone-grpc/tree/master/yellowstone-grpc-proto/proto)
3. Place in `prism/src/main/proto/`
4. Configure `protoc` to generate Java stubs

**Acceptance criteria**

- [ ] `./gradlew build` compiles protos without error
- [ ] Generated Java classes for `SubscribeRequest`, `SubscribeUpdate`, `SubscribeUpdateTransaction`, etc.
- [ ] Generated sources in `build/generated/source/proto/`

---

### SOL-21: GrpcChannelFactory

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-20

**Instructions**

Create `GrpcChannelFactory` in `infrastructure/grpc/`:
- Static method: `public static Channel create(String endpoint)` using Helidon gRPC client API
- Configuration: TLS (system roots), HTTP/2 adaptive window, 8MB connection+stream window, 10s TCP keepalive, 15s connect timeout, 64MB max decoding

**Acceptance criteria**

- [ ] TLS enabled, 8MB windows, 10s keepalive, 15s timeout, 64MB decode
- [ ] Returns a usable gRPC Channel/Client

---

### SOL-22: TransactionParser (gRPC protobuf)

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-20, SOL-7

**Context**

From functional spec FR-2, FR-3, FR-4. Parses protobuf → domain models.

**Instructions**

Create `TransactionParser` in `infrastructure/grpc/`:
- Method: `public Optional<SolanaTransaction> parseTransaction(SubscribeUpdateTransaction update)`
- Method: `public Optional<Account> extractFeePayer(SubscribeUpdateTransaction update)`
- Parsing logic per functional spec:
  - **Signature**: `bs58.encode(tx_info.signature)` — use bitcoinj Base58 or custom encoder
  - **Amount**: `max(pre_balances[i].saturatingSub(post_balances[i])) / 1_000_000_000.0`
  - **Failed**: `meta.err != null`
  - **From/To**: max lamport decrease/increase account index → base58 encode → truncate if > 16 chars (`first8...last8`)
  - **Memo**: find Memo v1 (`Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo`) or v2 (`MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr`) in account_keys → scan instructions + inner_instructions → decode UTF-8 → strip `\0`
  - **Fee payer**: `account_keys[0]` → base58, `post_balances[0]` → lamports, executable=false, rent_epoch=0
- Static: `truncateAddress(String addr)` — if length > 16 → `first8...last8`

**Test cases** (unit — build protobuf objects manually):

- [ ] `shouldEncodeSignatureAsBase58`
- [ ] `shouldComputeAmountFromBalanceDifferentials`
- [ ] `shouldIdentifySenderAsMaxDecrease`
- [ ] `shouldIdentifyReceiverAsMaxIncrease`
- [ ] `shouldTruncateAddressLongerThan16Chars`
- [ ] `shouldNotTruncateShortAddress`
- [ ] `shouldExtractMemoFromTopLevelInstructions`
- [ ] `shouldExtractMemoFromInnerInstructions`
- [ ] `shouldStripNullBytesFromMemo`
- [ ] `shouldReturnNullMemoWhenNone`
- [ ] `shouldMarkFailedWhenMetaErrPresent`
- [ ] `shouldExtractFeePayer`
- [ ] `shouldReturnEmptyWhenMetaMissing`

**Acceptance criteria**

- [ ] All 13 test cases pass
- [ ] Memo v1 AND v2 program IDs supported
- [ ] Address truncation at > 16 chars
- [ ] Saturating subtraction for balance diffs (no negative amounts)

---

### SOL-23: ReconnectHandler

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-1

**Instructions**

Create `ReconnectHandler` in `infrastructure/grpc/` (shared by gRPC + WebSocket):
- Constants: `RECONNECT_BASE_SECS = 2`, `RECONNECT_MAX_SECS = 30`, `RECONNECT_RESET_SECS = 60`
- Method: `public long nextDelay()` — returns delay in seconds, increments attempt
- Method: `public void resetIfStable(long connectedDurationSecs)` — resets attempt to 0 if `>= 60`
- Formula: `min(BASE * 2^min(attempt, 4), MAX)`

**Test cases** (unit):

- [ ] `shouldProduceCorrectDelaySequence` — 4, 8, 16, 30, 30
- [ ] `shouldCapAtMaxDelay` — attempt 10 → still 30
- [ ] `shouldResetAfter60SecondsStable` — reset, next delay starts at 4 again

**Acceptance criteria**

- [ ] Delay sequence: 4→8→16→30→30
- [ ] Reset after 60s stable connection

---

### SOL-24: YellowstoneTransactionStream (gRPC adapter)

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-21, SOL-22, SOL-23

**Instructions**

Create `YellowstoneTransactionStream` implementing `TransactionStream`:
- Constructor: gRPC Channel, `TransactionParser`, `ReconnectHandler`, optional x-token
- `subscribe()`: sends `SubscribeRequest` with `vote=false`, `failed=null`, `commitment=Confirmed`, slot filter
- Runs in reconnect loop using `ReconnectHandler`
- For each `SubscribeUpdateTransaction`: parse → enqueue tx + account to consumers
- For `UpdateOneof::Slot`: log `[SLOT]` notification

**Acceptance criteria**

- [ ] Subscription filter: vote=false, commitment=Confirmed
- [ ] x-token header injected when configured
- [ ] Auto-reconnect with exponential backoff
- [ ] Transactions enqueued to tx consumer, accounts to acct consumer

---

### SOL-24b: BlockNotificationParser (WebSocket JSON)

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-23, SOL-7

**Context**

Free alternative parser. Same domain output as SOL-22 but from JSON instead of protobuf. See Solana `blockSubscribe` documentation.

**Instructions**

Create `BlockNotificationParser` in `infrastructure/websocket/`:
- Method: `public List<SolanaTransaction> parseBlock(JsonNode blockNotification)`
- Method: `public List<Account> extractFeePayers(JsonNode blockNotification)`
- Same extraction logic as `TransactionParser` but from Jackson `JsonNode`:
  - `signature`: `tx.transaction.signatures[0]` (string, not bytes)
  - `pre_balances`/`post_balances`: `tx.meta.preBalances[]` / `tx.meta.postBalances[]`
  - `failed`: `tx.meta.err != null`
  - `instructions`: `tx.transaction.message.instructions[]`
  - `inner_instructions`: `tx.meta.innerInstructions[]`
  - Same amount/from/to/memo/fee payer logic as SOL-22
  - Same address truncation (> 16 chars)

**Test cases** (unit — JSON fixtures):

- [ ] `shouldParseTransactionFromBlockNotification` — full JSON → verify signature, slot, amount, from, to
- [ ] `shouldExtractMemoFromJsonInstructions`
- [ ] `shouldExtractFeePayer`
- [ ] `shouldMarkFailedTransactions`
- [ ] `shouldProduceSameDomainObjectAsProtobufParser` — same logical tx in both formats → identical `SolanaTransaction`

**Acceptance criteria**

- [ ] Same domain output as `TransactionParser` (SOL-22) for equivalent data
- [ ] Parses `jsonParsed` encoding format
- [ ] Handles missing/null meta gracefully

---

### SOL-24c: WebSocketTransactionStream (free adapter)

**Labels**: `phase:3-streaming`
**Blocked by**: SOL-23, SOL-24b

**Instructions**

Create `WebSocketTransactionStream` implementing `TransactionStream`:
- Constructor: WS endpoint URL, `BlockNotificationParser`, `ReconnectHandler`
- Uses `java.net.http.WebSocket` (JDK built-in, no external dependency)
- On connect: sends JSON-RPC `blockSubscribe` request:
  ```json
  {"jsonrpc":"2.0","id":1,"method":"blockSubscribe",
   "params":["all",{"commitment":"confirmed","encoding":"jsonParsed",
   "transactionDetails":"full","maxSupportedTransactionVersion":0}]}
  ```
- On message: parse JSON, extract block notification, for each tx:
  - Filter vote transactions: skip if any instruction targets `Vote111111111111111111111111111111111111111`
  - Parse via `BlockNotificationParser`
  - Enqueue to tx + acct consumers
- Log `[SLOT]` from block slot number
- Auto-reconnect via `ReconnectHandler`

**Test cases** (unit):

- [ ] `shouldSendBlockSubscribeOnConnect` — verify JSON-RPC request format
- [ ] `shouldFilterVoteTransactions` — tx with Vote program → skipped
- [ ] `shouldRouteNonVoteTransactions` — normal tx → enqueued to consumers
- [ ] `shouldReconnectOnDisconnect` — mock WS closes → reconnect handler called

**Acceptance criteria**

- [ ] Uses JDK `java.net.http.WebSocket` (no external WS library)
- [ ] Vote filtering client-side (Vote111... program ID)
- [ ] Same `TransactionStream` port as gRPC adapter
- [ ] Free — works with public Solana RPC endpoint

---

## Phase 4: Infrastructure — Metrics & Console

### SOL-25: MicrometerMetricsRecorder

**Labels**: `phase:4-metrics`
**Blocked by**: SOL-8

**Instructions**

Create `MicrometerMetricsRecorder` implementing `MetricsRecorder`:
- Constructor: `MeterRegistry registry`
- 8 counters: `indexer_tx_received`, `indexer_tx_written`, `indexer_tx_failed`, `indexer_tx_memo`, `indexer_tx_transfer`, `indexer_accounts_written`, `indexer_slots`, `indexer_batches`
- `recordBatch(BatchResult)`: increments written, failed, transfer, batches. For memo: count only non-failed memos (`result.memos()` already represents this from `TransactionProcessor`)
- `recordSlot()`: increments slots counter
- `incrementReceived()`: increments tx_received counter

**Test cases** (unit with `SimpleMeterRegistry`):

- [ ] `shouldIncrementAllCountersOnRecordBatch`
- [ ] `shouldIncrementSlotCounter`
- [ ] `shouldIncrementReceivedCounter`

**Acceptance criteria**

- [ ] 8 named Prometheus counters
- [ ] `tx_memo` counts non-failed memos only
- [ ] Uses Micrometer API (not raw AtomicLong)

---

### SOL-26: BenchmarkLogReporter

**Labels**: `phase:4-metrics`
**Blocked by**: SOL-25

**Instructions**

Create `BenchmarkLogReporter` in `infrastructure/metrics/`:
- Constructor: `MetricsRecorder` (reads counters), `String logPath`, `long intervalSecs`
- `run()`: virtual thread loop — sleep `intervalSecs * 1000` ms, read counters, compute TPS delta, format line, append to file
- Log format: `timestamp | tps | recv | written | failed | failed% | memos | xfers | accts | batches | slots`
- Session header on startup

**Test cases** (unit):

- [ ] `shouldFormatLogLineCorrectly` — verify format string with known values
- [ ] `shouldComputeTpsDelta` — prev=100, curr=200, interval=300 → TPS = 0.33
- [ ] `shouldHandleZeroTotalForFailedPercent` — 0 written + 0 failed → 0%

**Acceptance criteria**

- [ ] Matches Rust log format exactly
- [ ] TPS computed as delta from previous interval
- [ ] Session header written on first call

---

### SOL-27: ConsoleOutputFormatter

**Labels**: `phase:4-metrics`
**Blocked by**: SOL-7

**Instructions**

Create `ConsoleOutputFormatter` in `infrastructure/console/`:
- Constructor: `boolean consoleLog` (from config)
- Method: `public void printTransaction(SolanaTransaction tx)`
- Method: `public void printSlot(long slot, long parentSlot)` (always shown, not gated)
- Priority chain (from functional spec FR-11):
  1. If `consoleLog == false` → return (but `printSlot` is NOT gated)
  2. If `failed` → print `[TX]` red, return
  3. If `memo != null` → print `[MEMO]` magenta (does NOT return)
  4. If `isLargeTransfer(amount)` → print `[TRANSFER]` yellow
  5. If no memo AND not large → print `[TX]` white
- Signature truncation: > 20 chars → `first8...last8`
- Use ANSI escape codes or `jansi` library for colors

**Test cases** (unit — capture System.out):

- [ ] `shouldPrintSlotAlwaysRegardlessOfToggle` — consoleLog=false, printSlot → output contains `[SLOT]`
- [ ] `shouldSuppressTransactionOutputWhenToggleOff` — consoleLog=false, printTransaction → no output
- [ ] `shouldPrintFailedInRed` — failed tx → contains `[TX]`
- [ ] `shouldPrintMemoInMagenta` — tx with memo → contains `[MEMO]`
- [ ] `shouldPrintTransferInYellow` — large transfer → contains `[TRANSFER]`
- [ ] `shouldPrintNormalInWhite` — normal tx → contains `[TX]`
- [ ] `shouldPrintBothMemoAndTransfer` — tx with memo AND amount > 1.0 → both `[MEMO]` and `[TRANSFER]`
- [ ] `shouldTruncateSignatureLongerThan20Chars`

**Acceptance criteria**

- [ ] Correct priority chain matching functional spec
- [ ] `[SLOT]` always shown, not gated by toggle
- [ ] Signature truncation at > 20 chars (not 16 — that's addresses)

---

## Phase 5: Application Layer — REST API

### SOL-28: HealthRoutes

**Labels**: `phase:5-api`
**Blocked by**: SOL-2

**Instructions**

Create `HealthRoutes` implementing Helidon `HttpService`:
- `GET /health` → `{"status":"ok","uptime_secs":N}`
- No database call. Uptime from `Instant.now().getEpochSecond() - startEpochSecs`
- Return `HealthResponse` serialized via Jackson

**Test cases**:
- [ ] Unit: verify JSON shape
- [ ] Integration: start Helidon WebServer, HTTP GET → 200 + `application/json`

---

### SOL-29: StatsRoutes

**Labels**: `phase:5-api`
**Blocked by**: SOL-2, SOL-8

**Instructions**

- `GET /api/stats` → delegates to `StatsRepository.getStats()`, maps to `StatsResponse` via MapStruct

**Test cases**:
- [ ] Unit: mock repo, verify response fields
- [ ] Integration: Testcontainers, insert rows, HTTP GET → verify counts

---

### SOL-30: TransactionRoutes

**Labels**: `phase:5-api`
**Blocked by**: SOL-2, SOL-8, SOL-34

**Instructions**

Three endpoints:
- `GET /api/transactions` → `Page<TransactionResponse>` (limit default 50, max 500, clamped, offset, optional `success` query param)
- `GET /api/transactions/{signature}` → single `TransactionResponse` or 404
- `GET /api/slots/{slot}` → `List<TransactionResponse>` (JSON array, not paginated, `created_at ASC`)

Limit clamping: `Math.max(1, Math.min(limit, 500))`

**Test cases**:
- [ ] Unit: verify limit clamping (0→1, 999→500, 50→50)
- [ ] Unit: missing signature → 404
- [ ] Integration: insert test data, verify pagination, success filter, slot query ordering

---

### SOL-31: TransferRoutes

**Labels**: `phase:5-api`
**Blocked by**: SOL-2, SOL-8, SOL-34

**Instructions**

- `GET /api/transfers` → `Page<TransferResponse>` (limit, offset, `min_amount` default 0.0). Ordered `amount DESC`.

**Test cases**:
- [ ] Unit: mock repo
- [ ] Integration: insert, verify min_amount filter + DESC ordering

---

### SOL-32: MemoRoutes

**Labels**: `phase:5-api`
**Blocked by**: SOL-2, SOL-8, SOL-34

**Instructions**

- `GET /api/memos` → `Page<MemoResponse>` (limit, offset). Ordered `created_at DESC`.

**Test cases**:
- [ ] Unit: mock repo
- [ ] Integration: insert, verify pagination + ordering

---

### SOL-33: AccountRoutes

**Labels**: `phase:5-api`
**Blocked by**: SOL-2, SOL-8, SOL-34

**Instructions**

- `GET /api/accounts/{pubkey}` → `AccountResponse` or 404

**Test cases**:
- [ ] Unit: mock repo, verify 404
- [ ] Integration: insert, verify lookup

---

### SOL-34: ErrorHandler

**Labels**: `phase:5-api`
**Blocked by**: SOL-2

**Instructions**

Global Helidon error handler:
- `NotFoundException` / `NoSuchElementException` → 404 `{"error": "...", "status": 404}`
- `SQLException` → 500 `{"error": "Internal server error", "status": 500}`
- `IllegalArgumentException` → 400

**Test cases**:
- [ ] Unit: each exception type → correct HTTP status + JSON body

---

### SOL-35: CORS configuration

**Labels**: `phase:5-api`
**Blocked by**: SOL-1

**Instructions**

Helidon `CorsSupport.builder().addCrossOrigin(CrossOriginConfig.create()).build()` — permissive, all origins.

**Test cases**:
- [ ] Integration: OPTIONS request → `Access-Control-Allow-Origin: *` header present

---

## Phase 6: Application — Wiring & Lifecycle

### SOL-36: IndexerConfig

**Labels**: `phase:6-lifecycle`
**Blocked by**: SOL-1

**Instructions**

Java record parsed from `System.getenv()`:

| Field | Env Var | Required | Default |
|-------|---------|----------|---------|
| `streamMode` | `STREAM_MODE` | No | `websocket` |
| `grpcEndpoint` | `GRPC_ENDPOINT` | If mode=grpc | — |
| `rpcWsEndpoint` | `RPC_WS_ENDPOINT` | If mode=websocket | `wss://api.mainnet-beta.solana.com` |
| `databaseUrl` | `DATABASE_URL` | Yes | — |
| `xToken` | `X_TOKEN` | No | — |
| `consoleLog` | `CONSOLE_LOG` | No | `true` |
| `benchLog` | `BENCH_LOG` | No | `benchmark.log` |
| `apiPort` | `API_PORT` | No | `3000` |

Static factory: `public static IndexerConfig fromEnv()` — fails fast with `IllegalStateException` if required vars missing.

**Test cases** (unit):
- [ ] `shouldParseAllFieldsFromEnv`
- [ ] `shouldUseDefaultsWhenOptionalMissing`
- [ ] `shouldFailFastOnMissingDatabaseUrl`
- [ ] `shouldFailFastOnMissingGrpcEndpointWhenModeIsGrpc`
- [ ] `shouldDefaultToWebsocketMode`

---

### SOL-37: IndexerApplication (main)

**Labels**: `phase:6-lifecycle`
**Blocked by**: SOL-36, all previous phases

**Instructions**

`public static void main(String[] args)` — wires and starts the entire application. See implementation plan SOL-37 for the 12-step sequence. Key: selects `TransactionStream` adapter based on `streamMode` config.

**Test cases** (integration):
- [ ] `shouldStartAndRespondToHealth` — app starts, /health returns 200
- [ ] `shouldExposePrometheusMetrics` — /metrics returns Prometheus format
- [ ] `shouldShutdownGracefully` — start, trigger shutdown, verify clean exit

---

## Phase 7: End-to-End & Documentation

### SOL-38: End-to-end integration test

**Labels**: `phase:7-e2e`
**Blocked by**: all previous phases

**Instructions**

Full pipeline test for both adapters:
- (A) gRPC: mock server → protobuf → parse → batch → COPY → query API
- (B) WebSocket: mock WS → JSON → parse → batch → INSERT → query API

Verify for both: successful tx in `transactions`, failed in `failed_transactions`, memo in `memos`, large transfer in `large_transfers`, fee payer in `accounts`.

**Acceptance criteria**

- [ ] Both adapters produce identical domain objects for same logical transaction
- [ ] Full data flow verified: stream → DB → API response

---

### SOL-39: Graceful shutdown test

**Labels**: `phase:7-e2e`
**Blocked by**: SOL-37

**Instructions**

Start app, push N transactions via mock stream, trigger shutdown, count rows in DB. Verify N rows persisted (no data loss).

**Acceptance criteria**

- [ ] All buffered transactions flushed on shutdown
- [ ] No data loss verified by row count

---

### SOL-40: README.md

**Labels**: `phase:7-docs`
**Blocked by**: SOL-38

**Instructions**

Project overview, tech stack, architecture diagram (Mermaid), quick start, API reference (8 endpoints + curl), config reference (8 env vars), make commands, streaming modes (free WS vs paid gRPC).

---

### SOL-41: CLAUDE.md

**Labels**: `phase:7-docs`
**Blocked by**: SOL-38

**Instructions**

Project identity (Helidon 4 SE, no Spring), hexagonal rules, coding preferences, testing standards, ArchUnit rules, pre-commit checklist, agent workflow with dependency graph.
