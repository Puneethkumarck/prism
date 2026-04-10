# Coding Standards — Prism (Solana Real-Time Indexer)

> Instructions for coding agents developing Prism.
> Stack: Java 25, Helidon 4 SE, Avaje Inject, pgjdbc, PostgreSQL 16 — **NO Spring Boot**.

## Table of Contents

- [1. Architecture: Hexagonal (Ports and Adapters)](#1-architecture-hexagonal-ports-and-adapters)
- [2. Multi-Module Gradle Structure](#2-multi-module-gradle-structure)
- [3. Domain Layer](#3-domain-layer)
- [4. Application Layer](#4-application-layer)
- [5. Infrastructure Layer](#5-infrastructure-layer)
- [6. Object Mapping](#6-object-mapping)
- [7. Java Conventions](#7-java-conventions)
- [8. Concurrency and Virtual Threads](#8-concurrency-and-virtual-threads)
- [9. API Design](#9-api-design)
- [10. Quick Reference Checklist](#10-quick-reference-checklist)

---

## 1. Architecture: Hexagonal (Ports and Adapters)

Every module follows a strict three-layer package structure under `com.stablebridge.prism`:

```
com.stablebridge.prism
  ├── domain/           # Core business logic, models, ports, services
  ├── application/      # Input adapters: Helidon SE routes, lifecycle, config
  └── infrastructure/   # Output adapters: JDBC, gRPC, WebSocket, metrics
```

**Rules:**
- `domain` MUST NOT import from `application` or `infrastructure`
- `domain` models (records, value objects, enums) MUST NOT import any framework. Only Lombok + `java.*` allowed
- `domain` services use Lombok for DI (`@RequiredArgsConstructor`) — no framework annotations for DI registration
- `application` depends on `domain`. Maps API DTOs to domain models and delegates
- `infrastructure` depends on `domain`. Implements domain port interfaces
- Dependencies always point inward: `application` → `domain` ← `infrastructure`

**Enforced by ArchUnit** (5 rules, build-time):

| # | Rule |
|---|------|
| 1 | `domain..` must NOT depend on `infrastructure..` |
| 2 | `domain..` must NOT depend on `application..` |
| 3 | `domain..` must NOT import `io.helidon..`, `jakarta..`, `io.avaje..` |
| 4 | `domain..` must NOT import `java.sql..` |
| 5 | `infrastructure..` must NOT depend on `application.routing..` |

---

## 2. Multi-Module Gradle Structure

| Module | Purpose | Plugin |
|--------|---------|--------|
| `prism-api` | Shared DTOs: response records, Page\<T\>, ErrorResponse | `prism.library` (java-library) |
| `prism` | Main application: domain, application, infrastructure | `prism.service` (Helidon 4 SE) |

**Dependency rules:**
- `prism-api` has no dependency on the core module
- `prism` depends on `prism-api` via `implementation`
- Convention plugins live in `buildSrc/`
- Dependencies managed via `gradle/libs.versions.toml`

---

## 3. Domain Layer

### 3.0 DDD Classification Guide

When introducing a new type, ask these three questions in order:

| # | Question | If YES | Example |
|---|----------|--------|---------|
| 1 | Can I swap two instances with the same fields and nothing breaks? | **Value Object** | `Signature`, `Pubkey`, `Slot` |
| 2 | Does it have its own table, own queries, own lifecycle? | **Aggregate Root** | `SolanaTransaction`, `Account` |
| 3 | Is it always derived from or owned by another entity? | **Projection / Child** | `LargeTransfer`, `Memo`, `FailedTransaction` |

#### Value Objects

A value object is defined entirely by its fields. Two instances with the same fields are interchangeable.

**When to wrap a primitive in a value object:**
- Two types share the same underlying primitive but mean different things (e.g., `Signature` and `Pubkey` are both `String` — wrapping prevents mixing them at compile time)
- The value has invariants that should be enforced once (e.g., max length, non-blank)

**When NOT to wrap:**
- There is no confusion risk (e.g., `BigDecimal` for amounts — no other `BigDecimal` in the domain to confuse it with)
- The type is only used in one place and wrapping adds friction without safety

**Implementation rules:**
- Single-field records, no `@Builder`
- Compact constructor validates invariants (non-null, non-blank, max length)
- Java records provide `equals`/`hashCode` by value automatically
- Use as map keys, set elements, and method parameters where type safety matters

#### Entities (Aggregate Roots)

An entity has identity and lifecycle. Two entities with identical fields are different objects if they have different identities.

**How to identify:**
- Has a unique identifier (signature, pubkey) assigned externally (by the blockchain, not by the application)
- Stored in its own database table
- Queried directly via its own repository port
- Other aggregates reference it by ID, not by direct object reference

**Implementation rules:**
- Java records with `@Builder(toBuilder = true)`
- Compact constructor validates required fields and invariants
- Contains factory methods for derived projections (`toLargeTransfer()`, `toMemo()`)
- One repository port per aggregate root

#### Projections (Child Entities)

A projection is derived from an aggregate root and does not exist independently.

**How to identify:**
- Created from a parent entity's fields (never from scratch)
- Does not have its own independent lifecycle — if the parent didn't exist, neither would the projection
- May have its own table for query performance, but the source of truth is the parent

**Implementation rules:**
- Java records with `@Builder(toBuilder = true)`
- Created via factory methods on the aggregate root (`tx.toLargeTransfer()`)
- Share the parent's identity field (same `Signature`)
- Never created directly in services — always derived from the aggregate root

#### Decision Tree

```
New type arrives
    │
    ├── "Is it identified by value alone?"
    │       │
    │      YES → VALUE OBJECT
    │              No @Builder, compact constructor validation
    │              Examples: Signature, Pubkey, Slot
    │
    └── "Does it have independent lifecycle?"
            │
           YES → AGGREGATE ROOT
           │      @Builder, own repository port, factory methods for projections
           │      Examples: SolanaTransaction, Account
           │
            NO → PROJECTION
                   @Builder, created via aggregate root factory method
                   Examples: LargeTransfer, Memo, FailedTransaction
```

### 3.1 Domain Models

Use **Java records** with `@Builder(toBuilder = true)` for all domain models. Positional constructors are NOT acceptable for records with 3+ fields — callers must use named builder fields.

```java
@Builder(toBuilder = true)
public record SolanaTransaction(
        Signature signature,
        long slot,
        BigDecimal amount,
        boolean failed,
        String memo,
        Pubkey from,
        Pubkey to
) {
    public SolanaTransaction {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
    }
}

@Builder(toBuilder = true)
public record Account(
        Pubkey pubkey,
        long lamports,
        long slot,
        boolean executable,
        long rentEpoch
) {
    public Account {
        Objects.requireNonNull(pubkey, "pubkey must not be null");
        if (lamports < 0) {
            throw new IllegalArgumentException("lamports must not be negative");
        }
    }
}

@Builder(toBuilder = true)
public record BatchResult(long written, long failed, long memos, long transfers) {
    public BatchResult {
        if (written < 0) {
            throw new IllegalArgumentException("written must not be negative");
        }
    }
}
```

**Rules:**
- Domain models are immutable. State transitions return new instances (via `toBuilder()`)
- Records MUST have `@Builder(toBuilder = true)` — exception: value objects with 1 field (`Signature`, `Pubkey`, `Slot`)
- All records MUST have compact constructor validation:
  - `Objects.requireNonNull()` for required reference-type fields
  - Non-negative checks (`if (field < 0)`) for `long` fields representing counts, slots, or amounts
  - Nullable fields (e.g., `memo`, `from`, `to`) are NOT validated — they remain nullable by design
- Use `BigDecimal` for all monetary/SOL amounts — never `double` or `float`
- Use `Signature` and `Pubkey` value objects for identifiers — never raw `String`
- No JDBC annotations (`java.sql.*`) in domain models — stays in infrastructure
- No framework annotations. Only Lombok (`@Builder`, `@Slf4j`, `@RequiredArgsConstructor`)
- Use `@Slf4j` on all classes with logging (domain services, not records)

### 3.1.1 Value Objects

Single-field value objects wrap primitive identifiers with validation. They do NOT use `@Builder` — callers use `new Signature("...")`.

```java
public record Signature(String value) {
    public Signature {
        Objects.requireNonNull(value, "signature must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
        if (value.length() > 88) {
            throw new IllegalArgumentException("signature must not exceed 88 characters");
        }
    }
}

public record Pubkey(String value) {
    public Pubkey {
        Objects.requireNonNull(value, "pubkey must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("pubkey must not be blank");
        }
        if (value.length() > 44) {
            throw new IllegalArgumentException("pubkey must not exceed 44 characters");
        }
    }
}
```

**Rules:**
- Value objects validate in compact constructors: non-null, non-blank, max length
- No `@Builder` — single-field records use the canonical constructor directly
- Only `java.*` imports — no Lombok needed
- Java records provide `equals`/`hashCode` by value automatically
- Use in port interfaces: `findBySignature(Signature)` not `findBySignature(String)`

### 3.1.2 Aggregate Root

`SolanaTransaction` is the aggregate root. Derived projections are created via factory methods:

```java
public record SolanaTransaction(...) {
    public LargeTransfer toLargeTransfer() {
        return LargeTransfer.builder().signature(signature).slot(slot).amount(amount).build();
    }

    public Memo toMemo() {
        Objects.requireNonNull(memo, "cannot create Memo from transaction without memo");
        return Memo.builder().signature(signature).memoText(memo).build();
    }

    public FailedTransaction toFailedTransaction(String error) {
        return FailedTransaction.builder().signature(signature).slot(slot).error(error).build();
    }
}
```

**Rules:**
- `LargeTransfer`, `Memo`, `FailedTransaction` are projections derived from `SolanaTransaction`
- Projection methods live on the aggregate root, not in services
- `toMemo()` validates that memo is non-null before creating the projection

### 3.2 Domain Ports

Define interfaces in the domain layer. Infrastructure provides implementations.

```java
// domain/port/TransactionRepository.java — plain interface, no annotations
public interface TransactionRepository {
    void bulkInsert(List<SolanaTransaction> batch);
    Optional<SolanaTransaction> findBySignature(Signature signature);
    List<SolanaTransaction> findBySlot(long slot);
    List<SolanaTransaction> findAll(long limit, long offset, Boolean success);
    long countAll();
    long countBySuccess(boolean success);
}

// domain/port/TransactionStream.java
public interface TransactionStream {
    void subscribe(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer);
    void close();
}
```

**Rules:**
- Plain interfaces, no annotations
- Only domain model types in method signatures — no `DataSource`, `Connection`, `JsonNode`, `Channel`
- Use value objects in parameters: `findBySignature(Signature)`, `findByPubkey(Pubkey)` — not raw `String`
- Return `Optional` for lookups that may not find a result
- Use `List` for collection returns, `void` for writes

### 3.3 Domain Services

Services orchestrate domain logic. They use Lombok for DI — no `@Service`, `@Component`, or `@Singleton` on domain classes.

```java
@Slf4j
@RequiredArgsConstructor
public class TransactionProcessor {
    private final TransactionRepository transactionRepository;
    private final FailedTransactionRepository failedTransactionRepository;
    private final TransferRepository transferRepository;
    private final MemoRepository memoRepository;
    private final MetricsRecorder metricsRecorder;

    public BatchResult process(List<SolanaTransaction> batch) {
        // Classify and route to repos in parallel via virtual threads
    }
}
```

**DI registration** happens in the application layer (via Avaje `@Singleton` factories or manual wiring in `main()`), NOT in the domain layer.

### 3.4 Error Handling

- Define domain-specific exceptions extending `RuntimeException`
- Use descriptive messages
- Each exception type maps to a specific HTTP status in the application layer's `ErrorHandler`

```java
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String signature) {
        super("Transaction not found: " + signature);
    }
}
```

---

## 4. Application Layer

### 4.1 Helidon SE Routes

Routes use Helidon 4 SE functional `HttpService` pattern — no annotations, no controllers.

```java
@Slf4j
@RequiredArgsConstructor
public class TransactionRoutes implements HttpService {
    private final TransactionRepository transactionRepository;
    private final TransactionResponseMapper mapper;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::listTransactions)
             .get("/{signature}", this::getBySignature);
    }

    private void listTransactions(ServerRequest req, ServerResponse res) {
        var limit = Math.max(1, Math.min(req.query().first("limit").map(Long::parseLong).orElse(50L), 500));
        var offset = req.query().first("offset").map(Long::parseLong).orElse(0L);
        var success = req.query().first("success").map(Boolean::parseBoolean).orElse(null);

        var data = transactionRepository.findAll(limit, offset, success);
        var total = success != null ? transactionRepository.countBySuccess(success) : transactionRepository.countAll();
        var page = Page.<TransactionResponse>builder()
                .data(data.stream().map(mapper::toResponse).toList())
                .total(total).limit(limit).offset(offset).build();
        res.send(page);
    }
}
```

**Rules:**
- Routes are thin — mapping + delegation only. No business logic
- Limit clamping: `Math.max(1, Math.min(limit, 500))`
- Use `Optional` pipelines for query parameter extraction
- Return domain exceptions — `ErrorHandler` maps them to HTTP status

### 4.2 Configuration

Use a plain Java record parsed from environment variables — no `@ConfigurationProperties`:

```java
@Builder(toBuilder = true)
public record IndexerConfig(
        String streamMode,
        String grpcEndpoint,
        String rpcWsEndpoint,
        String databaseUrl,
        String xToken,
        boolean consoleLog,
        String benchLog,
        int apiPort
) {
    public static IndexerConfig fromEnv() {
        var databaseUrl = requireEnv("DATABASE_URL");
        var streamMode = env("STREAM_MODE", "websocket");
        // Fail fast if required vars missing for selected mode
        if ("grpc".equals(streamMode)) requireEnv("GRPC_ENDPOINT");
        return IndexerConfig.builder()
                .streamMode(streamMode)
                .grpcEndpoint(env("GRPC_ENDPOINT", ""))
                .rpcWsEndpoint(env("RPC_WS_ENDPOINT", "wss://api.mainnet-beta.solana.com"))
                .databaseUrl(databaseUrl)
                .xToken(env("X_TOKEN", null))
                .consoleLog(!"false".equals(env("CONSOLE_LOG", "true")))
                .benchLog(env("BENCH_LOG", "benchmark.log"))
                .apiPort(Integer.parseInt(env("API_PORT", "3000")))
                .build();
    }

    private static String env(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key)).orElse(defaultValue);
    }

    private static String requireEnv(String key) {
        return Optional.ofNullable(System.getenv(key))
                .orElseThrow(() -> new IllegalStateException(key + " environment variable is required"));
    }
}
```

### 4.3 Application Lifecycle

The `main()` method wires everything explicitly — no framework auto-configuration:

```java
public class IndexerApplication {
    public static void main(String[] args) {
        var config = IndexerConfig.fromEnv();
        var writePool = DataSourceFactory.createWritePool(config.databaseUrl());
        var readPool = DataSourceFactory.createReadPool(config.databaseUrl());
        FlywayMigrator.migrate(writePool);
        // Create repos, services, routes, start Helidon WebServer
        // Register shutdown hook
    }
}
```

---

## 5. Infrastructure Layer

### 5.1 Database (Raw JDBC)

**No JPA. No ORM.** All database access uses raw pgjdbc with parameterized SQL.

```java
@Slf4j
@RequiredArgsConstructor
public class CopyTransactionRepository implements TransactionRepository {
    private final DataSource writePool;
    private final DataSource readPool;

    @Override
    public void bulkInsert(List<SolanaTransaction> batch) {
        var successful = batch.stream().filter(tx -> !tx.failed()).toList();
        if (successful.isEmpty()) return;
        try (var conn = writePool.getConnection()) {
            var pgConn = conn.unwrap(PgConnection.class);
            var tsv = buildTsv(successful);  // signature\tslot\tt\n
            pgConn.getCopyAPI().copyIn(
                "COPY staging_transactions (signature, slot, success) FROM STDIN (FORMAT TEXT)",
                new ByteArrayInputStream(tsv.getBytes(UTF_8)));
            try (var stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO transactions SELECT * FROM staging_transactions ON CONFLICT (signature) DO NOTHING");
                stmt.execute("TRUNCATE staging_transactions");
            }
        }
    }

    @Override
    public Optional<SolanaTransaction> findBySignature(Signature signature) {
        try (var conn = readPool.getConnection();
             var ps = conn.prepareStatement("SELECT * FROM transactions WHERE signature = ?")) {
            ps.setString(1, signature.value());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }
}
```

**Rules:**
- Use `CopyManager` for the `transactions` table (5-10x faster than INSERT)
- Use `PreparedStatement.addBatch()` with `reWriteBatchedInserts=true` for other tables
- Use `INSERT ... ON CONFLICT (pubkey) DO UPDATE` for accounts upsert
- Read methods use **read pool**, write methods use **write pool**
- All SQL is parameterized — no string concatenation

### 5.2 Connection Pools

Two separate HikariCP pools:

| Pool | Max | Min | Acquire | Idle | Special |
|------|-----|-----|---------|------|---------|
| Write | 20 | 5 | 10s | 60s | `reWriteBatchedInserts=true` |
| Read | 20 | 5 | 10s | 60s | `readOnly=true` |

### 5.3 gRPC / WebSocket Adapters

Two adapters implement the same `TransactionStream` port:

| Adapter | Protocol | Cost | Class |
|---------|----------|------|-------|
| `YellowstoneTransactionStream` | Helidon gRPC client | Paid ($300-500/mo) | `infrastructure/grpc/` |
| `WebSocketTransactionStream` | JDK `java.net.http.WebSocket` | Free (public RPC) | `infrastructure/websocket/` |

Selected at startup based on `STREAM_MODE` config. Domain layer is identical for both.

### 5.4 Metrics

Micrometer counters exposed via Helidon's built-in `/metrics` endpoint. No Spring Actuator.

---

## 6. Object Mapping

Use **MapStruct** with `componentModel = "jsr330"` for Avaje Inject compatibility.

**Naming conventions:**

| Direction | Method name |
|-----------|-------------|
| Domain → API response | `toResponse(domainModel)` |
| API request → Domain | `toDomain(request)` |
| ResultSet → Domain | `mapRow(ResultSet)` (manual in JDBC adapters) |

**Rules:**
- One mapper interface per concern
- `@Mapper(componentModel = "jsr330")` on all mappers
- In unit tests: `Mappers.getMapper(XxxMapper.class)` or `@Spy`
- ResultSet → Domain mapping is manual (inside JDBC adapters) since MapStruct doesn't support ResultSet

---

## 7. Java Conventions

### 7.1 Language Level

Java 25 with modern features: `var`, records, pattern matching `switch`, text blocks, unnamed variables (`_`), sealed interfaces where appropriate.

### 7.2 Lombok Usage

| Annotation | Where | Purpose |
|------------|-------|---------|
| `@RequiredArgsConstructor` | Services, adapters, routes | Constructor injection via `private final` fields |
| `@Slf4j` | All classes with logging | Logging via `log.info(...)` |
| `@Builder(toBuilder = true)` | All records | Object construction + copy-and-modify |
| `@Getter` | Enums with fields | Field access |

**Never use:**
- `@Autowired`, `@Service`, `@Component` — use Avaje `@Singleton` in infrastructure, `@RequiredArgsConstructor` in domain
- `@Data` in production code
- `@AllArgsConstructor` in domain models
- Positional record constructors for 3+ fields

### 7.3 Dependency Injection

**Domain layer**: `@RequiredArgsConstructor` with `private final` fields. No DI annotations.

**Infrastructure + Application layer**: Avaje Inject `@Singleton` for adapter classes, `@Factory` + `@Bean` for infrastructure objects (DataSource, Channel):

```java
// Infra adapter — registered with Avaje Inject
@Singleton
@RequiredArgsConstructor
public class JdbcStatsRepository implements StatsRepository {
    private final DataSource readPool;
}

// Factory for infrastructure objects
@Factory
public class DataSourceFactory {
    @Bean @Named("write")
    public HikariDataSource writePool(IndexerConfig config) { ... }

    @Bean @Named("read")
    public HikariDataSource readPool(IndexerConfig config) { ... }
}
```

### 7.4 Null Handling

- Use `Optional` for return types that may not have a value
- Never return `null` from repository lookups — return `Optional`
- Use `Optional.ofNullable()` when bridging nullable external data

### 7.5 Naming

| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase, singular | `domain.model`, `infrastructure.persistence` |
| Class | PascalCase | `TransactionProcessor`, `CopyTransactionRepository` |
| Interface | PascalCase, no `I` prefix | `TransactionRepository` |
| Method | camelCase, verb-first | `findBySignature`, `bulkInsert` |
| Constant | SCREAMING_SNAKE_CASE | `LARGE_TRANSFER_THRESHOLD_SOL`, `BATCH_SIZE` |
| Test method | `should<Action><Condition>` | `shouldFlushWhenBatchSizeReached` |
| Fixture constant | `SOME_*` | `SOME_TRANSACTION`, `SOME_ACCOUNT` |
| Fixture builder | `<concept>Builder()` | `transactionBuilder()`, `accountBuilder()` |

### 7.6 Import Order

```java
import static ...;         // Static imports first

import com.stablebridge...; // Internal imports
import com.other...;        // Third-party imports
import java...;             // Java standard library
import jakarta...;          // Jakarta imports
import org...;              // Framework imports
import io...;               // Helidon, Avaje, etc.
```

### 7.7 Functional Over Imperative

```java
// GOOD — functional
var result = items.stream()
        .filter(Item::isActive)
        .map(Item::name)
        .toList();

// GOOD — Optional pipeline
return repository.findBySignature(sig)
        .map(mapper::toResponse)
        .orElseThrow(() -> new TransactionNotFoundException(sig));

// GOOD — Map.merge for dedup
map.merge(account.pubkey(), account,
    (prev, curr) -> curr.slot() > prev.slot() ? curr : prev);
```

**Rules:**
- Streams over loops for transformations/filtering
- Optional pipelines over null checks
- `Map.merge`, `computeIfAbsent`, `getOrDefault` over get-check-put
- Method references over lambdas when clear
- Prefer `toList()` (returns unmodifiable list)
- **Exception:** Imperative style OK for complex stateful iteration or side-effectful loops

---

## 8. Concurrency and Virtual Threads

### 8.1 Virtual Threads

All blocking I/O (JDBC, gRPC, WebSocket) runs on virtual threads. Java 25 makes this transparent.

```java
var executor = Executors.newVirtualThreadPerTaskExecutor();
executor.submit(() -> transactionBatchService.run());
executor.submit(() -> accountBatchService.run());
```

### 8.2 ReentrantLock Over synchronized

**MANDATORY**: Use `ReentrantLock` everywhere. Never `synchronized`.

`synchronized` blocks **pin the virtual thread's carrier thread**, destroying throughput. `ReentrantLock` does not pin.

```java
// BAD — pins carrier thread
private synchronized void flush() { ... }

// GOOD — virtual-thread safe
private final ReentrantLock lock = new ReentrantLock();
private void flush() {
    lock.lock();
    try { ... }
    finally { lock.unlock(); }
}
```

### 8.3 Queue Choices

| Queue | Use Case | Why |
|-------|----------|-----|
| `LinkedTransferQueue` (unbounded) | Transaction stream → batch writer | Prevents gRPC backpressure disconnection |
| `ArrayBlockingQueue` (bounded 10K) | Fee payer → account writer | Drop if full (`offer()` returns false) |

---

## 9. API Design

### 9.1 Pagination

All list endpoints return `Page<T>`:

```json
{"data": [...], "total": 1000, "limit": 50, "offset": 0}
```

- `limit` default 50, max 500, clamped: `Math.max(1, Math.min(limit, 500))`
- `offset` default 0

### 9.2 Error Responses

Consistent JSON error body:

```json
{"error": "Transaction not found: 5Kx7a...", "status": 404}
```

### 9.3 CORS

Permissive (all origins) via Helidon `CorsSupport`:

```java
CorsSupport.builder().addCrossOrigin(CrossOriginConfig.create()).build()
```

---

## 10. Quick Reference Checklist

Before committing code, verify:

- [ ] Domain models have ZERO framework imports (only Lombok + `java.*`)
- [ ] Domain models have compact constructor validation (`Objects.requireNonNull`, non-negative checks)
- [ ] Identifiers use value objects: `Signature`, `Pubkey` — not raw `String`
- [ ] Monetary amounts use `BigDecimal` — not `double` or `float`
- [ ] Domain ports are plain interfaces with no annotations, using value object types
- [ ] Domain layer does NOT import from `application` or `infrastructure`
- [ ] All mapping uses MapStruct (`componentModel = "jsr330"`), not manual field copying
- [ ] Repository interfaces in `domain/port/`, implementations in `infrastructure/persistence/`
- [ ] JDBC adapters use parameterized SQL — no string concatenation
- [ ] Write methods use write pool, read methods use read pool
- [ ] `ReentrantLock` used everywhere — no `synchronized`
- [ ] Constructor injection via `@RequiredArgsConstructor`, no `@Autowired`
- [ ] Functional style: streams over loops, Optional pipelines over null checks
- [ ] `var` used for local variables when type is obvious from RHS
- [ ] No comments or Javadoc — code is self-documenting
- [ ] No `System.out`/`System.err` — use `@Slf4j`
- [ ] Tests follow [TESTING_STANDARDS.md](TESTING_STANDARDS.md)
- [ ] `./gradlew build` passes (compile + Spotless + unit + integration + ArchUnit)
