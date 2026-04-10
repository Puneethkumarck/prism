# Solana Real-Time Transaction Indexer — Functional Specification

> Reference implementation: [gamandeepsingh/Solana-Indexer-rs](https://github.com/gamandeepsingh/Solana-Indexer-rs)
> Target stack: Java 25, Virtual Threads, gRPC, PostgreSQL, Hexagonal Architecture

---

## 1. Overview

A high-performance, real-time Solana blockchain indexer that subscribes to the Yellowstone gRPC stream (Geyser plugin), filters and parses confirmed transactions, and persists them to PostgreSQL. Exposes a paginated REST API for querying indexed data.

### Goals

- Stream **all confirmed non-vote transactions** from Solana mainnet in real time
- Persist transactions, failed transactions, large transfers, memos, and fee payer accounts to PostgreSQL
- Achieve **~99.5% indexing efficiency** at mainnet throughput (~150 slots/minute, variable TPS)
- Expose a **paginated REST API** for querying indexed data with dedicated read connection pool
- Auto-reconnect on gRPC stream failure with exponential backoff
- Graceful shutdown with queue drain (no data loss)

### Non-Goals (v1)

- Historical backfill (only real-time streaming)
- SPL token transfer tracking (only native SOL amount via balance differentials)
- Multi-chain support (Solana only)
- Authentication on the REST API
- Kafka/event streaming (direct DB persistence only)

---

## 2. System Architecture

### Data Flow

```
Yellowstone gRPC (Solana validator plugin)
  → gRPC Stream Client (subscribe to confirmed transactions)
    → Transaction Parser (decode protobuf, extract fields)
      → Unbounded Channel (decouple reader from writer)
        → Batch Writer (200 tx / 100ms dual trigger)
          → PostgreSQL (COPY + staging merge for transactions,
                        batch INSERT for failed/memos/transfers)

Fee Payer Extraction (from same gRPC stream)
  → Bounded Account Channel (10,000 capacity)
    → Account Worker (200 accounts / 2s dual trigger)
      → PostgreSQL (batch UPSERT on pubkey)

REST API (separate read pool)
  → PostgreSQL read pool (20 connections)
    → Paginated JSON responses
```

### Concurrency Model

Four concurrent workers on a shared async runtime:

| Worker | Input | Trigger | Output |
|--------|-------|---------|--------|
| **gRPC Stream** | Yellowstone subscription | Continuous push | Enqueues to tx channel + account channel |
| **Transaction Batch Writer** | Unbounded tx channel | 200 items OR 100ms timeout | Parallel writes to 4 tables |
| **Account Batch Writer** | Bounded account channel (10K) | 200 items OR 2,000ms timeout | UPSERT to accounts table |
| **Metrics Reporter** | Atomic counters | Every 300 seconds (5 min) | Append to benchmark log file |

The **REST API server** runs on the same runtime but uses a **separate read-only connection pool** to prevent write bursts from starving queries.

---

## 3. Functional Requirements

### FR-1: gRPC Stream Subscription

**Description**: Connect to a Yellowstone gRPC endpoint and subscribe to real-time Solana transaction and slot updates.

**Subscription filter**:

| Filter | Value | Purpose |
|--------|-------|---------|
| `vote` | `false` | Exclude validator vote transactions (high volume, no business value) |
| `failed` | `null` (no filter) | Include both successful and failed transactions |
| `commitment` | `Confirmed` | Process confirmed transactions (not finalized — lower latency) |
| Slot subscription | `filter_by_commitment: false`, `interslot_updates: false` | Receive slot notifications for logging |

**gRPC channel configuration**:

| Setting | Value | Purpose |
|---------|-------|---------|
| TLS | Enabled with native roots | Secure connection to validator |
| HTTP/2 adaptive window | `true` | Dynamic flow control |
| Initial connection window | 8 MB (`1 << 23`) | Handle burst of transaction data |
| Initial stream window | 8 MB (`1 << 23`) | Per-stream flow control |
| TCP keepalive | 10 seconds | Detect dead connections |
| Connect timeout | 15 seconds | Fail fast on unreachable endpoint |
| Max decoding message size | 64 MB | Handle large transaction batches |

**Authentication**: Optional `x-token` header injected via gRPC interceptor when `X_TOKEN` env var is set.

**Acceptance criteria**:
- [ ] Connects to Yellowstone gRPC endpoint with TLS
- [ ] Subscribes with vote=false, commitment=Confirmed
- [ ] Injects x-token header when configured
- [ ] Receives transaction updates and slot notifications
- [ ] Feeds parsed transactions to the unbounded channel without blocking

---

### FR-2: Transaction Parsing

**Description**: Parse each gRPC `SubscribeUpdateTransaction` into a domain `Transaction` object.

**Fields extracted**:

| Field | Source | Transformation |
|-------|--------|---------------|
| `signature` | `tx_info.signature` (bytes) | Base58 encode via `bs58` |
| `slot` | `tx_update.slot` | Cast to `long` |
| `failed` | `tx_info.meta.err` | `true` if error is present, `false` otherwise |
| `amount` | `meta.pre_balances` vs `meta.post_balances` | Max lamport decrease across all accounts, converted to SOL (`/ 1_000_000_000.0`) |
| `memo` | Transaction instructions + inner instructions | Decode UTF-8 data from instructions targeting Memo Program v1 or v2 (see FR-3) |
| `from` | Balance differentials | Account with largest lamport **decrease** (sender) |
| `to` | Balance differentials | Account with largest lamport **increase** (receiver) |

**Amount calculation logic**:
```
For each account index i:
  decrease[i] = pre_balances[i] - post_balances[i]  (saturating subtraction)
amount = max(decrease) / 1_000_000_000.0
```

**Sender/receiver resolution**:
```
sender  = account with max(pre_balance - post_balance)  → largest decrease
receiver = account with max(post_balance - pre_balance)  → largest increase
```
Sender/receiver addresses are **truncated at parse time** (stored truncated in the Transaction struct, not just for display): `first8...last8` if length > 16 characters. This means downstream consumers (DB, console) receive pre-truncated addresses.

**Acceptance criteria**:
- [ ] Correctly base58-encodes transaction signatures
- [ ] Computes SOL amount from balance differentials
- [ ] Identifies sender (max decrease) and receiver (max increase)
- [ ] Marks failed transactions when `meta.err` is present
- [ ] Handles missing meta/message gracefully (returns None)

---

### FR-3: Memo Extraction

**Description**: Detect and extract memo payloads from transactions using the Solana Memo Program.

**Memo Program IDs**:

| Program | Address |
|---------|---------|
| Memo v1 | `Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo` |
| Memo v2 | `MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr` |

**Extraction logic**:
1. Find the account index of the Memo Program in `message.account_keys`
2. Scan top-level `message.instructions` — if `instruction.program_id_index` matches the memo account index, decode `instruction.data` as UTF-8
3. If not found in top-level instructions, scan `meta.inner_instructions` (CPI calls)
4. Strip null bytes (`\0`) from decoded string
5. Return `None` if empty after cleaning

**Acceptance criteria**:
- [ ] Detects Memo v1 and Memo v2 program IDs
- [ ] Extracts memo from top-level instructions
- [ ] Extracts memo from inner instructions (CPI)
- [ ] Strips null bytes from UTF-8 payload
- [ ] Returns None for transactions without memos

---

### FR-4: Fee Payer Extraction

**Description**: Extract the fee payer account from each transaction for balance tracking.

**Logic**:
- Fee payer is always the **first account key** in `message.account_keys` (`account_keys[0]`)
- Post-balance of fee payer is `meta.post_balances[0]`

**Account fields**:

| Field | Source | Value |
|-------|--------|-------|
| `pubkey` | `msg.account_keys[0]` | Base58 encoded |
| `lamports` | `meta.post_balances[0]` | Post-transaction balance |
| `slot` | `tx_update.slot` | Slot of the transaction |
| `executable` | — | Always `false` (fee payers are wallets) |
| `rent_epoch` | — | Always `0` |

**Acceptance criteria**:
- [ ] Extracts first account key as fee payer
- [ ] Records post-transaction lamport balance
- [ ] Sends to bounded account channel (try_send, drop if full)

---

### FR-5: Large Transfer Detection

**Description**: Flag transactions with SOL movement above a configurable threshold.

**Threshold**: `1.0 SOL` (constant: `LARGE_TRANSFER_THRESHOLD_SOL`)

**Filter function** (`processor/filters.rs`):
```
is_large_transfer(amount) = amount > 1.0
```

The function itself only checks the amount. The `NOT failed` check is applied separately:
- In `batch_insert_transfers`: `!t.failed && t.amount > threshold`
- In console output (`print_tx`): only checked for non-failed transactions (failed returns early)
- In metrics counter: `t.memo.is_some() && !t.failed` (combined with memo check pattern)

Transfers are persisted to the `large_transfers` table during batch flush.

**Acceptance criteria**:
- [ ] `is_large_transfer()` detects amounts strictly > 1.0 SOL
- [ ] Amount of exactly 1.0 SOL is NOT a large transfer (strictly greater than)
- [ ] Failed transactions excluded from `large_transfers` table (in batch writer, not in filter function)
- [ ] Failed transactions excluded from `[TRANSFER]` console output (early return in `print_tx`)

---

### FR-6: Transaction Batch Writer

**Description**: Accumulate parsed transactions and write them to PostgreSQL in batches using a dual-trigger strategy.

**Dual-trigger flush**:

| Trigger | Threshold | Purpose |
|---------|-----------|---------|
| **Size** | 200 transactions | Prevent unbounded memory growth |
| **Time** | 100 milliseconds | Bound maximum write latency |

Whichever trigger fires first causes a flush. On flush, **four parallel database writes** execute concurrently:

| Write Operation | Target Table | Method | Filter |
|----------------|-------------|--------|--------|
| Successful transactions | `transactions` | `COPY FROM STDIN` + staging table merge | `NOT failed` |
| Failed transactions | `failed_transactions` | Batch `INSERT` | `failed = true` |
| Memos | `memos` | Batch `INSERT` | `memo IS NOT NULL` (includes failed transactions with memos) |
| Large transfers | `large_transfers` | Batch `INSERT` | `NOT failed AND amount > 1.0` |

**COPY protocol for transactions** (critical performance path):
1. Build TSV (tab-separated) string: `signature\tslot\tt\n` for each successful transaction (PostgreSQL TEXT format uses `t` for boolean true)
2. Execute `COPY staging_transactions (signature, slot, success) FROM STDIN (FORMAT TEXT)` with the TSV payload
3. Merge into main table: `INSERT INTO transactions SELECT * FROM staging_transactions ON CONFLICT (signature) DO NOTHING`
4. Truncate staging table: `TRUNCATE staging_transactions`

This achieves **5-10x faster writes** than `INSERT VALUES` for the high-volume transactions table.

**Graceful shutdown**: When the channel closes (sender dropped), flush any remaining buffered transactions before stopping.

**Acceptance criteria**:
- [ ] Flushes on 200 transactions accumulated
- [ ] Flushes on 100ms timeout (even if < 200 transactions)
- [ ] COPY protocol used for successful transactions (staging + merge)
- [ ] Batch INSERT for failed transactions, memos, and large transfers
- [ ] All 4 writes execute in parallel per flush
- [ ] Remaining buffer flushed on shutdown
- [ ] Metrics updated after each flush (written, failed, memo, transfer, batch counts)

---

### FR-7: Account Batch Writer

**Description**: Accumulate fee payer accounts and upsert them to PostgreSQL on a slower cadence.

**Dual-trigger flush**:

| Trigger | Threshold | Purpose |
|---------|-----------|---------|
| **Size** | 200 accounts | Prevent memory growth |
| **Time** | 2,000 milliseconds (2s) | Slower flush — accounts are less latency-sensitive |

**Deduplication**: Before upserting, deduplicate by `pubkey` — keep the entry with the **highest slot** (most recent state).

**Upsert logic**:
```sql
INSERT INTO accounts (pubkey, lamports, slot, executable, rent_epoch)
VALUES ($1, $2, $3, $4, $5), ...
ON CONFLICT (pubkey) DO UPDATE SET
  lamports   = EXCLUDED.lamports,
  slot       = EXCLUDED.slot,
  executable = EXCLUDED.executable,
  rent_epoch = EXCLUDED.rent_epoch
```

**Channel**: Bounded with capacity 10,000. If full, new accounts are dropped (`try_send` — non-blocking). This prevents backpressure from propagating to the gRPC stream.

**Acceptance criteria**:
- [ ] Flushes on 200 accounts accumulated
- [ ] Flushes on 2-second timeout
- [ ] Deduplicates by pubkey before upsert (highest slot wins)
- [ ] ON CONFLICT updates existing accounts with latest state
- [ ] Remaining buffer flushed on shutdown

---

### FR-8: Auto-Reconnection

**Description**: Automatically reconnect to the gRPC stream on disconnection with exponential backoff.

**Backoff schedule** (constants: `RECONNECT_BASE_SECS=2`, `RECONNECT_MAX_SECS=30`, exponent capped at `min(attempt, 4)`):

| Attempt | Formula | Computed | Actual Delay |
|---------|---------|----------|-------------|
| 1 | `2 * 2^1` | 4 | 4 seconds |
| 2 | `2 * 2^2` | 8 | 8 seconds |
| 3 | `2 * 2^3` | 16 | 16 seconds |
| 4 | `2 * 2^4` | 32 | 30 seconds (capped) |
| 5+ | `2 * 2^min(n,4)` | 32 | 30 seconds (capped) |

**Reset condition**: If the stream stays connected for **60 seconds** (`RECONNECT_RESET_SECS`), the attempt counter resets to 0. The next disconnection starts back at 4 seconds.

**Acceptance criteria**:
- [ ] Reconnects automatically on stream end or error
- [ ] Exponential backoff: 4s → 8s → 16s → 30s (capped)
- [ ] Resets attempt counter after 60 seconds of stable connection
- [ ] Logs reconnection attempts with delay and attempt number

---

### FR-9: REST API

**Description**: Serve paginated read-only queries over HTTP using a dedicated read connection pool.

**Server configuration**:
- Default port: `3000` (configurable via `API_PORT`)
- CORS: Permissive (allow all origins) via tower-http
- Connection pool: Read-only, 20 connections, separate from write pool

#### FR-9.1: Health Check

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/health` |
| **Parameters** | None |
| **Response** | `{"status": "ok", "uptime_secs": 3600}` |
| **Notes** | No database call. Uptime computed from process start time. |

#### FR-9.2: Statistics

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/stats` |
| **Parameters** | None |
| **Response** | `{"total_transactions": N, "total_failed": N, "total_transfers": N, "total_memos": N, "total_accounts": N}` |
| **Notes** | Uses `pg_stat_user_tables.n_live_tup` for O(1) performance instead of `COUNT(*)`. Returns approximate row counts. |

#### FR-9.3: List Transactions

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/transactions` |
| **Parameters** | `limit` (default 50, max 500), `offset` (default 0), `success` (optional boolean filter) |
| **Response** | `{"data": [TxRow...], "total": N, "limit": N, "offset": N}` |
| **Ordering** | `created_at DESC` (newest first) |
| **Index used** | `idx_transactions_success` (when filtered), `idx_transactions_created_at` (unfiltered) |

**TxRow fields**: `signature` (string), `slot` (long), `success` (boolean), `created_at` (ISO 8601 timestamp)

**Note**: The `total` field uses `COUNT(*)` (exact count), not `pg_stat_user_tables`. Only `/api/stats` uses the approximate O(1) approach. Paginated endpoints pay the COUNT cost for accurate totals.

#### FR-9.4: Get Transaction by Signature

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/transactions/:signature` |
| **Parameters** | Path parameter: base58 signature |
| **Response** | `TxRow` object or `404` |
| **Index used** | Primary key lookup |

#### FR-9.5: Get Transactions by Slot

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/slots/:slot` |
| **Parameters** | Path parameter: slot number |
| **Response** | `[TxRow...]` (array, not paginated) |
| **Ordering** | `created_at ASC` |
| **Index used** | `idx_transactions_slot` |

#### FR-9.6: List Large Transfers

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/transfers` |
| **Parameters** | `limit` (default 50, max 500), `offset` (default 0), `min_amount` (default 0.0, in SOL) |
| **Response** | `{"data": [TransferRow...], "total": N, "limit": N, "offset": N}` |
| **Ordering** | `amount DESC` (largest first) |
| **Index used** | `idx_large_transfers_amount` |

**TransferRow fields**: `id` (int), `signature` (string), `slot` (long), `amount` (double, in SOL), `created_at` (ISO 8601)

#### FR-9.7: List Memos

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/memos` |
| **Parameters** | `limit` (default 50, max 500), `offset` (default 0) |
| **Response** | `{"data": [MemoRow...], "total": N, "limit": N, "offset": N}` |
| **Ordering** | `created_at DESC` (newest first) |
| **Index used** | `idx_memos_created_at` |

**MemoRow fields**: `id` (int), `signature` (string), `memo` (string), `created_at` (ISO 8601)

#### FR-9.8: Get Account by Pubkey

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/api/accounts/:pubkey` |
| **Parameters** | Path parameter: base58 pubkey |
| **Response** | `AccountRow` object or `404` |
| **Index used** | Unique index on `pubkey` |

**AccountRow fields**: `pubkey` (string), `lamports` (long), `slot` (long), `executable` (boolean), `rent_epoch` (long), `created_at` (ISO 8601)

**Acceptance criteria**:
- [ ] All 8 endpoints return correct response format
- [ ] Pagination with `limit` (clamped 1-500) and `offset`
- [ ] 404 for missing transaction/account lookups
- [ ] Stats endpoint uses `pg_stat_user_tables` (not COUNT)
- [ ] All queries use the read-only connection pool
- [ ] CORS enabled (permissive)

---

### FR-10: Metrics and Benchmark Logging

**Description**: Track operational counters and write a benchmark summary to a log file at regular intervals.

**Counters** (atomic, lock-free):

| Counter | Tracks |
|---------|--------|
| `tx_received` | Transactions received from gRPC stream |
| `tx_written` | Successful transactions written to DB |
| `tx_failed` | Failed transactions written to DB |
| `tx_memo` | Successful transactions with memos (note: counter excludes failed, but `batch_insert_memos` persists failed memos too — this is a discrepancy in the reference implementation) |
| `tx_transfer` | Large transfers persisted |
| `accounts_written` | Account rows upserted |
| `slots` | Slot notifications received |
| `batches` | Total batch flush cycles |

**Log format** (appended every 300 seconds):
```
timestamp            | tps   | recv      | written   | failed    | failed% | memos | xfers | accts     | batches | slots
2026-04-06T19:54:15Z |   319 |      1635 |       968 |       631 |     39% |     7 |   121 |         0 |      30 |     8
```

**TPS calculation**: `(written + failed - previous_processed) / interval_seconds`

**Failed percentage**: `(failed * 100) / (written + failed)` — typical Solana mainnet: 20-40%

**Acceptance criteria**:
- [ ] All 8 counters increment correctly
- [ ] Benchmark log file created/appended at configured path
- [ ] Log line written every 300 seconds
- [ ] TPS computed as delta from previous interval
- [ ] Session header written on startup

---

### FR-11: Console Output

**Description**: Formatted, color-coded terminal output for real-time monitoring.

**Output format by event type**:

| Prefix | Color | Content | When |
|--------|-------|---------|------|
| `[SLOT]` | Cyan (bold) | `Slot: N (Parent: N-1)` | Every slot notification (always shown, not gated by `CONSOLE_LOG`) |
| `[TX]` | Red (prefix), dimmed (rest) | `signature amount SOL` | Failed transactions only |
| `[MEMO]` | Magenta (bold) | Memo text payload | Successful transactions with a memo (printed before `[TRANSFER]` check) |
| `[TRANSFER]` | Yellow (bold) | `From/To/Amount` multiline | Successful transactions where `amount > 1.0 SOL` |
| `[TX]` | White | `signature amount SOL` | Successful transactions with NO memo AND NOT a large transfer (fallthrough) |

**Display priority** (from `processor/transaction.rs`):
1. If `failed` → print `[TX]` red, return (skip all other checks)
2. If memo present → print `[MEMO]` (does NOT return — continues to next check)
3. If large transfer → print `[TRANSFER]`
4. If no memo AND not large transfer → print `[TX]` white

A single transaction can produce both `[MEMO]` and `[TRANSFER]` output if it has a memo AND is a large transfer.

**Toggle**: Controlled by `CONSOLE_LOG` env var (default: `true`). When `false`, `[TX]`/`[MEMO]`/`[TRANSFER]` output is suppressed. `[SLOT]` output is always shown (emitted from `grpc/stream.rs`, not gated by the toggle).

**Truncation**:
- Signatures truncated to `first8...last8` if length > 20 characters (in `processor/transaction.rs`)
- Addresses truncated to `first8...last8` if length > 16 characters (in `grpc/stream.rs` — stored truncated in Transaction struct)

**Acceptance criteria**:
- [ ] Color-coded output per event type with correct priority
- [ ] Failed transactions always print `[TX]` red regardless of memo/amount
- [ ] Memo + large transfer prints BOTH `[MEMO]` and `[TRANSFER]`
- [ ] Normal successful transactions print `[TX]` white only when no memo and not large
- [ ] Configurable via CONSOLE_LOG (true/false/0)
- [ ] Slot notifications always shown regardless of toggle
- [ ] Signatures truncated at > 20 chars, addresses at > 16 chars

---

### FR-12: Graceful Shutdown

**Description**: Clean shutdown on `SIGTERM`/`Ctrl+C` with queue drain.

**Shutdown sequence**:
1. `Ctrl+C` / `SIGTERM` received
2. gRPC stream closes (sender drops)
3. Metrics reporter aborted
4. Transaction worker drains remaining buffer and flushes to DB
5. Account worker drains remaining buffer and flushes to DB
6. Process exits

**Acceptance criteria**:
- [ ] No data loss — all buffered transactions flushed before exit
- [ ] Workers log "Flushing N remaining transactions..." if buffer non-empty
- [ ] Clean exit with "Goodbye!" message

---

## 4. Database Schema

### Tables

#### `transactions`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `signature` | `VARCHAR(88)` | `PRIMARY KEY` | Base58-encoded transaction signature |
| `slot` | `BIGINT` | `NOT NULL` | Solana slot number |
| `success` | `BOOLEAN` | `NOT NULL` | `true` for successful, `false` for failed (always `true` — failed go to separate table) |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `DEFAULT NOW()` | Indexer insertion time |

#### `failed_transactions`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `SERIAL` | `PRIMARY KEY` | Auto-increment |
| `signature` | `VARCHAR(88)` | `NOT NULL` | Base58-encoded transaction signature |
| `slot` | `BIGINT` | `NOT NULL` | Solana slot number |
| `error` | `TEXT` | `NOT NULL` | Error description (currently hardcoded: "Transaction failed") |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `DEFAULT NOW()` | Indexer insertion time |

#### `large_transfers`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `SERIAL` | `PRIMARY KEY` | Auto-increment |
| `signature` | `VARCHAR(88)` | `NOT NULL` | Transaction signature |
| `slot` | `BIGINT` | `NOT NULL` | Solana slot number |
| `amount` | `NUMERIC` | `NOT NULL` | Transfer amount in SOL (BigDecimal in Java) |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `DEFAULT NOW()` | Indexer insertion time |

#### `memos`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `SERIAL` | `PRIMARY KEY` | Auto-increment |
| `signature` | `VARCHAR(88)` | `NOT NULL` | Transaction signature |
| `memo` | `TEXT` | `NOT NULL` | Decoded memo payload (UTF-8, null bytes stripped) |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `DEFAULT NOW()` | Indexer insertion time |

#### `accounts`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | `SERIAL` | `PRIMARY KEY` | Auto-increment |
| `pubkey` | `VARCHAR(88)` | `NOT NULL UNIQUE` | Base58-encoded public key |
| `lamports` | `BIGINT` | `NOT NULL` | Post-transaction balance in lamports |
| `slot` | `BIGINT` | `NOT NULL` | Slot of last update |
| `executable` | `BOOLEAN` | `NOT NULL` | Always `false` for fee payer accounts |
| `rent_epoch` | `BIGINT` | `NOT NULL` | Always `0` for fee payer accounts |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | `DEFAULT NOW()` | First seen timestamp |

#### `staging_transactions` (temporary, for COPY protocol)

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `signature` | `VARCHAR(88)` | — | No constraints (staging) |
| `slot` | `BIGINT` | — | |
| `success` | `BOOLEAN` | — | |

### Indexes

| Index Name | Table | Columns | Type | Purpose |
|-----------|-------|---------|------|---------|
| (PK) | `transactions` | `signature` | B-tree unique | Signature lookup |
| `idx_transactions_slot` | `transactions` | `slot` | B-tree | Slot queries (FR-9.5) |
| `idx_transactions_created_at` | `transactions` | `created_at DESC` | B-tree | Pagination ordering (FR-9.3) |
| `idx_transactions_success` | `transactions` | `success, created_at DESC` | Composite B-tree | Filtered pagination (FR-9.3 with `success` param) |
| `idx_large_transfers_amount` | `large_transfers` | `amount DESC` | B-tree | Transfer amount queries (FR-9.6) |
| `idx_large_transfers_created_at` | `large_transfers` | `created_at DESC` | B-tree | Transfer time ordering |
| `idx_memos_created_at` | `memos` | `created_at DESC` | B-tree | Memo pagination (FR-9.7) |
| `idx_failed_tx_created_at` | `failed_transactions` | `created_at DESC` | B-tree | Failed tx ordering |
| (UNIQUE) | `accounts` | `pubkey` | B-tree unique | Account lookup + upsert target (FR-9.8) |

---

## 5. Configuration

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GRPC_ENDPOINT` | Yes | — | Yellowstone gRPC endpoint URL (e.g., `https://grpc.mainnet.solana.com`) |
| `DATABASE_URL` | Yes | — | PostgreSQL connection string (e.g., `postgresql://user:pass@localhost:5432/indexer`) |
| `X_TOKEN` | No | — | Authentication token for gRPC endpoint (injected as `x-token` metadata header) |
| `CONSOLE_LOG` | No | `true` | Enable/disable colored terminal output (`true`/`false`/`0`) |
| `BENCH_LOG` | No | `benchmark.log` | File path for benchmark metrics log |
| `API_PORT` | No | `3000` | HTTP API server port |

---

## 6. Connection Pool Strategy

Two physically separate PostgreSQL connection pools prevent write/read contention:

| Pool | Max Connections | Min Connections | Acquire Timeout | Idle Timeout | Purpose |
|------|----------------|-----------------|-----------------|--------------|---------|
| **Write** | 20 | 5 | 10 seconds | 60 seconds | Transaction COPY, batch INSERT, account UPSERT |
| **Read** | 20 | 5 | 10 seconds | 60 seconds | All REST API queries exclusively |

**Why separate pools**: During heavy ingest (mainnet burst), all 20 write connections may be active. Without separation, API queries would wait in the write pool's queue. With dedicated pools, API latency is independent of ingest load.

---

## 7. Performance Design Decisions

| # | Decision | Problem It Solves | Impact |
|---|----------|-------------------|--------|
| 1 | **Unbounded tx channel** | Bounded channels block the gRPC read loop, causing Yellowstone to disconnect with "lagged" errors | Zero dropped transactions from backpressure |
| 2 | **COPY FROM STDIN + staging merge** | Individual INSERT VALUES is 5-10x slower for high-volume transaction writes | 5-10x write throughput for the hottest table |
| 3 | **200 tx / 100ms dual-trigger batch** | Per-transaction writes create ~200x more DB round-trips | ~200x fewer round-trips, <200ms max latency |
| 4 | **Separate account worker (2s flush)** | Original design spawned 4000+ tokio tasks/sec for per-tx account updates | Eliminates task creation overhead, reduces DB pressure |
| 5 | **Exponential backoff reconnect (4s→30s cap)** | Rapid reconnection to a failing endpoint creates thundering herd | Progressive delay prevents overload, 60s reset resumes fast reconnect |
| 6 | **Dual read/write connection pools (20 each)** | Write-heavy ingestion starves API read queries | API latency independent of ingest load |
| 7 | **`pg_stat_user_tables` for stats** | `COUNT(*)` on million-row tables is O(N) — ~100x slower | O(1) approximate counts, sufficient for dashboard stats |
| 8 | **Parallel table writes per batch** | Sequential writes to 4 tables multiply flush latency | All 4 writes (COPY + 3 INSERTs) execute concurrently per flush |
| 9 | **Account dedup before upsert** | Same pubkey appears multiple times in a 2s window | Deduplicate in-memory (keep highest slot), reduce upsert row count |

---

## 8. Testing Requirements

**32 test cases** across 4 modules (`processor/filters`: 2, `grpc/stream`: 2, `processor/transaction`: 3, `api/handlers`: 25):

### Filter Tests
- [ ] Detects large transfer (1.1, 10.0, 100.0, 1000.0 SOL)
- [ ] Ignores small transfer (0.0, 0.000005, 0.5 SOL)
- [ ] Exactly 1.0 SOL is NOT a large transfer (boundary)

### Address Truncation Tests (`grpc/stream.rs`)
- [ ] Long address (> 16 chars) truncated to `first8...last8`
- [ ] Short address (<= 16 chars) returned unchanged

### Signature Truncation Tests (`processor/transaction.rs`)
- [ ] Long signature (> 20 chars) truncated to `first8...last8`
- [ ] Short signature (<= 20 chars) returned unchanged
- [ ] Large transfer detection flag consistent with filter module

### API Handler Tests
- [ ] `GET /health` returns 200 with `status: "ok"` and `uptime_secs`
- [ ] `GET /api/stats` returns 200 with all 5 count fields
- [ ] `GET /api/transactions` returns paginated response with `data`, `total`, `limit`, `offset`
- [ ] `GET /api/transactions?success=false` filters correctly
- [ ] `GET /api/transactions/:signature` returns 200 for existing, 404 for missing
- [ ] `GET /api/slots/:slot` returns array of transactions in slot
- [ ] `GET /api/transfers` returns paginated, ordered by amount DESC
- [ ] `GET /api/transfers?min_amount=10` filters by minimum amount
- [ ] `GET /api/memos` returns paginated, ordered by created_at DESC
- [ ] `GET /api/accounts/:pubkey` returns 200 for existing, 404 for missing
- [ ] Limit clamped to range [1, 500]

**Database-optional**: Tests requiring `DATABASE_URL` skip gracefully when the variable is not set, allowing `cargo test` (or `./gradlew test`) to pass without a live database.

---

## 9. Deployment

### Docker Compose Services

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| PostgreSQL | `postgres:16` | 5432 | Transaction storage |
| Indexer | Custom (built from source) | 3000 | gRPC consumer + REST API |

### Startup Sequence

1. PostgreSQL starts and becomes healthy
2. Indexer connects to PostgreSQL (write pool + read pool)
3. Indexer connects to Yellowstone gRPC endpoint
4. Subscription established — streaming begins
5. API server starts on configured port
6. Metrics reporter begins 5-minute interval logging

---

## 10. Appendix: Solana Concepts

| Concept | Description | Relevance to Indexer |
|---------|-------------|---------------------|
| **Slot** | ~400ms time window where a validator can produce a block | Unit of blockchain progress — logged for monitoring |
| **Transaction** | Atomic set of instructions signed by one or more accounts | Core indexed entity — parsed, classified, and persisted |
| **Signature** | Base58-encoded unique identifier for a transaction (88 chars max) | Primary key in `transactions` table |
| **Lamport** | Smallest unit of SOL (1 SOL = 1,000,000,000 lamports) | Balance differentials computed in lamports, converted to SOL |
| **Account** | State container on Solana identified by a public key | Fee payer accounts tracked for balance snapshots |
| **Memo Program** | System program that embeds arbitrary text in transactions | Memo payloads extracted and persisted to `memos` table |
| **Yellowstone gRPC** | Validator plugin (Geyser) that streams real-time data via gRPC | Primary data source — replaces RPC polling |
| **Confirmed** | Transaction included in a block voted on by 2/3+ of stake | Commitment level used for subscription (lower latency than finalized) |
| **Vote Transaction** | Validator consensus vote (~50% of all Solana transactions) | Excluded by subscription filter (`vote: false`) |
