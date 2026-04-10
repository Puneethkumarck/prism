# Testing Standards — Prism (Solana Real-Time Indexer)

> Mandatory testing rules. Coding agents must follow these exactly.
> Adapted from stablebridge-tx-recovery conventions for a non-Spring, Helidon 4 SE stack.

## Table of Contents

- [GOLDEN RULE: Build Expected Object + Single Recursive Comparison](#golden-rule-build-expected-object--single-recursive-comparison)
- [1. Test Strategy Overview](#1-test-strategy-overview)
- [2. Test Naming Conventions](#2-test-naming-conventions)
- [3. Test Structure: Given / When / Then](#3-test-structure-given--when--then)
- [4. Mocking Approach](#4-mocking-approach)
- [5. Test Fixtures and Builders](#5-test-fixtures-and-builders)
- [6. Assertions](#6-assertions)
- [7. Integration Test Setup](#7-integration-test-setup)
- [8. Architecture Test](#8-architecture-test)
- [9. Test Utilities](#9-test-utilities)
- [10. Anti-Patterns Summary](#10-anti-patterns-summary)

---

## GOLDEN RULE: Build Expected Object + Single Recursive Comparison

**Every test that verifies an object result MUST construct an expected object and compare with a single `assertThat(...).usingRecursiveComparison()`.** Multiple `assertThat` calls on individual fields are FORBIDDEN.

### The Pattern

```java
// 1. Build expected object using toBuilder(), factory, or constructor
var expected = input.toBuilder()
        .status(NEW_STATUS)
        .build();

// 2. Single assertion with recursive comparison
assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("generatedId", "createdAt")   // only non-deterministic fields
        .isEqualTo(expected);
```

### Why

- Multiple scattered asserts create **incomplete verification** — if a field changes that you didn't assert on, the test passes silently
- A single recursive comparison catches **every field change**, making tests fail-fast on regressions
- Building the expected object makes the test **self-documenting**

### FORBIDDEN vs REQUIRED

```java
// FORBIDDEN: multiple asserts on individual fields
var result = processor.process(batch);
assertThat(result.written()).isEqualTo(3);     // NEVER DO THIS
assertThat(result.failed()).isEqualTo(2);      // NEVER DO THIS
assertThat(result.memos()).isEqualTo(1);       // NEVER DO THIS

// REQUIRED: build expected + single recursive comparison
var expected = BatchResult.builder().written(3).failed(2).memos(1).transfers(0).build();
assertThat(result)
        .usingRecursiveComparison()
        .isEqualTo(expected);
```

### Narrow Exceptions (ONLY cases where individual asserts are allowed)

| Case | Why allowed | Example |
|------|------------|---------|
| Exception assertions | No object to compare | `assertThatThrownBy(...).isInstanceOf(...).hasMessage(...)` |
| Single boolean/primitive | Trivial comparison | `assertThat(LargeTransferFilter.isLargeTransfer(new BigDecimal("1.1"))).isTrue()` |
| Collection size + containment | AssertJ collection API | `assertThat(list).hasSize(3).containsOnly(a, b, c)` |
| Single enum/string mapping | One-to-one mapping | `assertThat(result).isEqualTo(EXPECTED_ENUM)` |
| Optional presence checks | Wrapper, not domain object | `assertThat(result).isPresent().hasValue(expected)` |

---

## 1. Test Strategy Overview

Three physically separated test source sets:

| Source Set | Directory | Scope | Speed | Framework |
|-----------|-----------|-------|-------|-----------|
| **Unit** | `src/test/java/` | Single class, mocked deps | Fast | JUnit 5 + Mockito |
| **Test Fixtures** | `src/testFixtures/java/` | Shared data, utilities | N/A | AssertJ + Mockito |
| **Integration** | `src/integration-test/java/` | Real DB (Testcontainers) | Medium | JUnit 5 + Testcontainers + direct JDBC |

**No Spring test context.** Integration tests use Testcontainers directly with a JUnit 5 extension — no `@SpringBootTest`, no `@WebMvcTest`, no `@MockBean`.

### Test Pyramid

```
         /\
        /  \       Integration Tests
       /----\      Testcontainers + Helidon WebServer + real JDBC
      /      \
     /        \
    /          \
   / Unit Tests \ 
  /--------------\ Pure Mockito, no DB, no server
```

---

## 2. Test Naming Conventions

Use `should*` in camelCase:

```java
void shouldFlushWhenBatchSizeReached()
void shouldDeduplicateByPubkeyKeepingHighestSlot()
void shouldReturnEmptyForMissingSignature()
void shouldCopyTransactionsToDatabase()
void shouldFilterVoteTransactions()
```

---

## 3. Test Structure: Given / When / Then

**Every test** follows the Given/When/Then pattern with explicit comment markers:

```java
@Test
void shouldRouteSuccessfulTransactionsToTransactionRepository() {
    // given
    var tx1 = transactionBuilder().signature("sig1").failed(false).build();
    var tx2 = transactionBuilder().signature("sig2").failed(false).build();
    var batch = List.of(tx1, tx2);

    // when
    var result = processor.process(batch);

    // then
    then(transactionRepository).should().bulkInsert(batch);
    var expected = BatchResult.builder().written(2).failed(0).memos(0).transfers(0).build();
    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
}
```

**For exception tests**, `// when` and `// then` are combined:

```java
@Test
void shouldThrowWhenDatabaseUrlMissing() {
    // given — DATABASE_URL not set

    // when/then
    assertThatThrownBy(IndexerConfig::fromEnv)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DATABASE_URL");
}
```

**Rules:**
- `// given` sets up inputs and mock stubs
- `// when` contains exactly one action
- `// then` contains only assertions or mock verifications
- Use `var` for all local variables

---

## 4. Mocking Approach

### 4.1 BDDMockito Only

| BDD Style (REQUIRED) | Standard Style (FORBIDDEN) |
|---|---|
| `given(...).willReturn(...)` | `when(...).thenReturn(...)` |
| `then(...).should()` | `verify(...)` |
| `then(...).should(never())` | `verify(..., never())` |
| `then(...).shouldHaveNoInteractions()` | `verifyNoInteractions(...)` |

### 4.2 Unit Test Setup

```java
@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {
    @Mock private TransactionRepository transactionRepository;
    @Mock private FailedTransactionRepository failedTransactionRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private MetricsRecorder metricsRecorder;
    @InjectMocks private TransactionProcessor processor;
}
```

### 4.3 No Generic Argument Matchers (MANDATORY)

**NEVER use `any()`, `anyString()`, `anyLong()`, `eq()`, or similar generic Mockito matchers.** Always pass actual values.

```java
// FORBIDDEN
given(repo.findBySignature(any())).willReturn(Optional.of(tx));     // NEVER
then(repo).should().bulkInsert(any());                               // NEVER

// REQUIRED — actual values
given(repo.findBySignature("sig123")).willReturn(Optional.of(tx));   // CORRECT
then(repo).should().bulkInsert(List.of(tx1, tx2));                   // CORRECT
```

**Allowed custom matchers** (these verify actual content):
- `eqIgnoringTimestamps(expected)` — recursive comparison ignoring timestamp types
- `eqIgnoring(expected, "field1", "field2")` — recursive comparison ignoring specific fields

### 4.4 @Spy for Real Mappers

```java
@Spy private final TransactionResponseMapper mapper = Mappers.getMapper(TransactionResponseMapper.class);
```

MapStruct mappers are spied rather than mocked, so their real logic executes.

---

## 5. Test Fixtures and Builders

### Location

All fixtures live in `src/testFixtures/java/com/stablebridge/prism/fixtures/`. NEVER define shared fixtures in `src/test/`.

### Fixture Design Patterns

**Pattern 1: `SOME_*` prefix for constants**

```java
public final class TransactionFixtures {
    private TransactionFixtures() {}

    public static final SolanaTransaction SOME_TRANSACTION = transactionBuilder().build();
    public static final SolanaTransaction SOME_FAILED_TRANSACTION = transactionBuilder()
            .signature(new Signature("5Kx7aEwMbFailedSig00001")).failed(true).build();
    public static final SolanaTransaction SOME_MEMO_TRANSACTION = transactionBuilder()
            .signature(new Signature("5Kx7aEwMbMemoSignature01")).memo("hello solana").build();
    public static final SolanaTransaction SOME_LARGE_TRANSFER = transactionBuilder()
            .signature(new Signature("5Kx7aEwMbLargeTransfer01")).amount(new BigDecimal("5.0")).build();
}
```

**Pattern 2: Builder factory methods**

Builder factories use value objects and UUID-based unique identifiers:

```java
public static SolanaTransaction.SolanaTransactionBuilder transactionBuilder() {
    return SolanaTransaction.builder()
            .signature(new Signature("5Kx7aEwMb" + UUID.randomUUID().toString().substring(0, 8)))
            .slot(280_000_000L)
            .amount(new BigDecimal("0.5"))
            .failed(false)
            .from(new Pubkey("SenderPubkey1234abcd5678"))
            .to(new Pubkey("ReceiverPubkey12efgh5678"));
}

public static Account.AccountBuilder accountBuilder() {
    return Account.builder()
            .pubkey(new Pubkey("7xKXtg2C" + UUID.randomUUID().toString().substring(0, 8)))
            .lamports(1_000_000_000L)
            .slot(280_000_000L)
            .executable(false)
            .rentEpoch(0);
}
```

**Pattern 3: Static imports for clean test code**

```java
import static com.stablebridge.prism.fixtures.TransactionFixtures.SOME_TRANSACTION;
import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
```

**Rules:**
- One fixture class per aggregate: `TransactionFixtures`, `AccountFixtures`, etc.
- Fixture classes are `public final` with a `private` constructor
- All factory methods are `public static`
- Both unit tests (`src/test/`) and integration tests (`src/integration-test/`) use testFixtures
- Factory methods that need a DB connection stay in the test class — only pure factories go to testFixtures

---

## 6. Assertions

### Library: AssertJ (exclusive)

No JUnit `assertEquals`/`assertTrue` anywhere. Only `assertThat` from AssertJ.

### Domain Model Assertions (MANDATORY — see Golden Rule)

```java
var result = processor.process(batch);
var expected = BatchResult.builder().written(3).failed(2).memos(1).transfers(1).build();
assertThat(result).usingRecursiveComparison().isEqualTo(expected);
```

### Service Interaction Verification (MANDATORY for handlers/services with side effects)

```java
// Build expected from same logic the service will apply
var expectedTransfers = batch.stream()
        .filter(tx -> !tx.failed() && LargeTransferFilter.isLargeTransfer(tx.amount()))
        .map(tx -> LargeTransfer.builder().signature(tx.signature()).slot(tx.slot()).amount(tx.amount()).build())
        .toList();

then(transferRepository).should().bulkInsert(expectedTransfers);
```

### Exception Assertions

```java
assertThatThrownBy(() -> config.fromEnv())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DATABASE_URL");
```

### Collection Assertions

```java
assertThat(transactions).hasSize(3);
assertThat(transactions).extracting(SolanaTransaction::signature).containsExactly(sig1, sig2, sig3);
```

### Validation Assertions (MANDATORY for all compact constructors)

Every compact constructor constraint MUST have a corresponding test:

```java
@Test
void shouldRejectNullSignature() {
    // when/then
    assertThatThrownBy(() -> transactionBuilder().signature(null).build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("signature");
}

@Test
void shouldRejectNegativeSlot() {
    // when/then
    assertThatThrownBy(() -> transactionBuilder().slot(-1).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("slot");
}
```

**Rules:**
- One test per constraint (null check, range check, blank check, max length)
- Use `assertThatThrownBy` with exact exception type and message substring
- Test uses the fixture builder with only the invalid field overridden
- Value objects (`Signature`, `Pubkey`) get dedicated test classes

### Aggregate Projection Assertions

Test factory methods on the aggregate root:

```java
@Test
void shouldCreateLargeTransfer() {
    // given
    var tx = SOME_LARGE_TRANSFER;

    // when
    var result = tx.toLargeTransfer();

    // then
    var expected = LargeTransfer.builder()
            .signature(tx.signature())
            .slot(tx.slot())
            .amount(tx.amount())
            .build();
    assertThat(result).usingRecursiveComparison().isEqualTo(expected);
}
```

---

## 7. Integration Test Setup

### No Spring — Direct Testcontainers + JDBC

Integration tests use a JUnit 5 extension that starts PostgreSQL via Testcontainers and provides a `DataSource`:

```java
class CopyTransactionRepositoryIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource writePool;
    static DataSource readPool;
    static CopyTransactionRepository repository;

    @BeforeAll
    static void setUp() {
        postgres.start();
        writePool = TestDataSourceFactory.create(postgres.getJdbcUrl(), false);
        readPool = TestDataSourceFactory.create(postgres.getJdbcUrl(), true);
        FlywayMigrator.migrate(writePool);
        repository = new CopyTransactionRepository(writePool, readPool);
    }

    @AfterAll
    static void tearDown() {
        postgres.stop();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (var conn = writePool.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE transactions, staging_transactions, failed_transactions, large_transfers, memos, accounts");
        }
    }

    @Test
    void shouldCopyTransactionsToDatabase() {
        // given
        var batch = IntStream.range(0, 100)
                .mapToObj(i -> transactionBuilder().signature("sig" + i).build())
                .toList();

        // when
        repository.bulkInsert(batch);

        // then
        assertThat(repository.countAll()).isEqualTo(100);
    }
}
```

### Helidon WebServer Integration Tests

For API route testing, start a real Helidon WebServer on a random port:

```java
@Test
void shouldReturnHealthOk() throws Exception {
    var server = WebServer.builder()
            .port(0)  // random port
            .routing(r -> r.register("/health", healthRoutes))
            .build()
            .start();
    try {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + "/health"))
                .GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var health = objectMapper.readValue(response.body(), HealthResponse.class);
        assertThat(health.status()).isEqualTo("ok");
    } finally {
        server.stop();
    }
}
```

### Data Isolation

- `@BeforeEach` truncates all tables via JDBC (no `@Transactional` rollback — not using Spring)
- Each test class manages its own Testcontainers lifecycle
- Use `PostgresExtension` from testFixtures for shared container setup

---

## 8. Architecture Test

**MANDATORY — must be the first test written** (SOL-4).

```java
class ArchitectureTest {
    private static final String BASE = "com.stablebridge.prism";

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure =
        noClasses().that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure..");

    @ArchTest
    static final ArchRule domainMustNotDependOnApplication =
        noClasses().that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".application..");

    @ArchTest
    static final ArchRule domainMustNotImportFrameworks =
        noClasses().that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.helidon..", "jakarta..", "io.avaje..");

    @ArchTest
    static final ArchRule domainMustNotImportJdbc =
        noClasses().that().resideInAPackage(BASE + ".domain..")
            .should().dependOnClassesThat().resideInAPackage("java.sql..");

    @ArchTest
    static final ArchRule infrastructureMustNotDependOnRouting =
        noClasses().that().resideInAPackage(BASE + ".infrastructure..")
            .should().dependOnClassesThat().resideInAPackage(BASE + ".application.routing..");
}
```

---

## 9. Test Utilities

### `TestUtils` (MUST be in testFixtures)

```java
public final class TestUtils {
    private TestUtils() {}

    public static <T> T eqIgnoringTimestamps(T expected) {
        return eqIgnoring(expected);
    }

    public static <T> T eqIgnoring(T expected, String... fieldsToIgnore) {
        return argThat(it -> isEqualIgnoringTimestampsAnd(it, expected, fieldsToIgnore));
    }

    private static <T> boolean isEqualIgnoringTimestampsAnd(T original, T expected, String... fieldsToIgnore) {
        try {
            assertThat(original)
                    .usingRecursiveComparison()
                    .ignoringFieldsOfTypes(Instant.class, ZonedDateTime.class, LocalDateTime.class)
                    .ignoringFields(fieldsToIgnore)
                    .isEqualTo(expected);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
```

### `TestDataSourceFactory`

```java
public final class TestDataSourceFactory {
    private TestDataSourceFactory() {}

    public static HikariDataSource create(String jdbcUrl, boolean readOnly) {
        var config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(5);
        config.setReadOnly(readOnly);
        return new HikariDataSource(config);
    }
}
```

---

## 10. Anti-Patterns Summary

| FORBIDDEN | REQUIRED |
|-----------|----------|
| Multiple `assertThat` on individual fields | Build expected object + single `usingRecursiveComparison()` |
| `when().thenReturn()` | `given().willReturn()` |
| `verify()` | `then().should()` |
| `any()`, `anyString()`, `eq()` | Actual values or `eqIgnoringTimestamps`/`eqIgnoring` |
| JUnit `assertEquals`/`assertTrue` | AssertJ `assertThat()` |
| Fixtures as private methods in test classes | Fixture classes in `src/testFixtures/` |
| `@SpringBootTest` | Testcontainers + direct JDBC (no Spring context) |
| `@MockBean` / `@SpyBean` | `@Mock` / `@Spy` with `@ExtendWith(MockitoExtension.class)` |
| `synchronized` in production code under test | `ReentrantLock` (VT-safe) |
| Tests without `// given`, `// when`, `// then` comments | Always include section markers |
