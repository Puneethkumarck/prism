<div align="center">

![Build](https://github.com/Puneethkumarck/prism/actions/workflows/ci.yml/badge.svg)
![Java 25](https://img.shields.io/badge/Java-25_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Helidon](https://img.shields.io/badge/Helidon-4.4_SE-00569C?style=for-the-badge&logo=oracle&logoColor=white)
![Virtual Threads](https://img.shields.io/badge/Virtual_Threads-Project_Loom-7B1FA2?style=for-the-badge&logo=java&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Solana](https://img.shields.io/badge/Solana-Mainnet-9945FF?style=for-the-badge&logo=solana&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Hexagonal-purple?style=for-the-badge)
![No Spring Boot](https://img.shields.io/badge/No-Spring_Boot-red?style=for-the-badge)

# 🔺 Prism

### Refract the Solana firehose into a queryable data stream.

**A zero-Spring, zero-JPA, real-time Solana transaction indexer built on Java 25 Virtual Threads and Helidon 4 SE.**
Streams confirmed transactions via Yellowstone gRPC (paid) or WebSocket `blockSubscribe` (free), persists them with the PostgreSQL `COPY` protocol, and serves them over a paginated REST API — all with sub-100ms startup and a <50 MB resident footprint.

[Why Prism?](#-why-does-this-exist) · [Architecture](#-architecture) · [The Hot Path](#-the-hot-path-how-a-transaction-becomes-a-row) · [Quick Start](#-quick-start) · [API Reference](#-api-reference) · [Tech Stack](#-tech-stack)

</div>

---

## 🎬 The Problem

Solana produces a new block roughly every **400 milliseconds**. At any given moment, mainnet can push **50,000+ transactions per second** through the firehose. If you want to know what happened on chain — payments, memos, failed swaps, large transfers — you have exactly three options:

| Option | What Happens | 💀 Verdict |
|---|---|---|
| 🐢 **Poll `getBlock`** | `getBlock(slot)` → repeat, repeat, repeat | Falls behind in minutes. Burns RPC credits. Dies at 50K TPS. |
| 🏗️ **Use a hosted indexer** | Pay per query. Sit behind someone else's cache. Hope their schema fits | $$$, schema lock-in, no custom parsing |
| 🚀 **Stream + batch yourself** | Subscribe to Geyser/WebSocket, parse in-process, batch write to Postgres | Full control. Sub-second freshness. Your schema, your queries. |

Prism is option 3 — a sharp, opinionated take on **option 3** — written in Java 25 with Virtual Threads, Helidon 4 SE, and raw pgjdbc. No Spring Boot. No JPA. No reflection. Just a tight hot path from socket to row.

## 💡 The Solution

```text
 🌊 Solana                 🔺 Prism                     🗄️  PostgreSQL
 ─────────                 ──────                       ─────────────
                                                        transactions
 Yellowstone gRPC   ──►   stream adapter    ──► COPY ──► failed_tx
 (or WebSocket)           │                            ─► memos
                          ▼                            ─► large_transfers
                   LinkedTransferQueue                 ─► accounts
                   (unbounded, lock-free)                     ▲
                          │                                   │
                          ▼                            ──────┘
                  TransactionBatchService              parallel writes
                  (200 tx / 100ms dual-trigger)        on virtual threads
                          │
                          ▼
                  TransactionProcessor
                  split → [success | failed | memo | transfer]
```

## 🎯 The Result

A real-time pipeline that stays caught up with mainnet, survives RPC flaps, flushes batches in ~100ms, and exposes 8 paginated read endpoints — all inside a JVM that starts in under a second and sips heap.

<div align="center">

| Metric | Value |
|--------|-------|
| **Throughput target** | ~99.5% indexing efficiency at mainnet velocity |
| **Write strategy** | PostgreSQL `COPY FROM STDIN` + staging merge — **5-10× faster** than `INSERT VALUES` |
| **Startup time** | < 100 ms (Helidon 4 SE, no classpath scanning) |
| **Thread model** | Virtual Threads — no reactive `.flatMap().subscribeOn()` gymnastics |
| **Backpressure** | Unbounded tx queue, bounded account queue — zero disconnects from Yellowstone |
| **Streaming modes** | 🆓 WebSocket `blockSubscribe` (free) · 💰 Yellowstone gRPC (paid) |
| **Stack size** | Helidon 4 SE + pgjdbc + Avaje Inject + Micrometer — **NO Spring Boot** |

</div>

---

## 📚 Table of Contents

- [🤔 Why Does This Exist?](#-why-does-this-exist)
- [🌈 Why "Prism"?](#-why-prism)
- [🎞️ A Day in the Life of a Solana Transaction](#%EF%B8%8F-a-day-in-the-life-of-a-solana-transaction)
- [⚡ The Hot Path: How a Transaction Becomes a Row](#-the-hot-path-how-a-transaction-becomes-a-row)
- [🏛️ Architecture](#%EF%B8%8F-architecture)
- [🧬 The COPY Protocol: Why We Bypass `INSERT VALUES`](#-the-copy-protocol-why-we-bypass-insert-values)
- [🧵 Virtual Threads: Why No Reactor, No WebFlux, No Spring Boot](#-virtual-threads-why-no-reactor-no-webflux-no-spring-boot)
- [🌊 Dual Streaming Modes: Free or Fast](#-dual-streaming-modes-free-or-fast)
- [🪣 Dual-Trigger Batching: Size OR Time](#-dual-trigger-batching-size-or-time)
- [🔁 The Reconnect Dance](#-the-reconnect-dance)
- [🛠️ Tech Stack](#%EF%B8%8F-tech-stack)
- [🧱 Module Structure](#-module-structure)
- [🚀 Quick Start](#-quick-start)
- [🎛️ Make Targets](#%EF%B8%8F-make-targets)
- [🌐 API Reference](#-api-reference)
- [⚙️ Configuration Reference](#%EF%B8%8F-configuration-reference)
- [📊 Observability](#-observability)
- [🧪 Testing Strategy](#-testing-strategy)
- [🗂️ Database Schema](#%EF%B8%8F-database-schema)
- [🧠 Design Decisions, Quick Reference](#-design-decisions-quick-reference)
- [📜 License](#-license)

---

## 🤔 Why Does This Exist?

Because every on-chain product eventually asks the same questions:

- 💸 *"Did our transaction confirm?"*
- 💰 *"Which wallets just moved more than 1 SOL?"*
- 📝 *"Did the customer include a memo with that payment?"*
- 🚫 *"How many of our swaps failed in the last hour?"*
- 🏦 *"What's the current balance of every fee payer we've seen?"*

These questions need **fresh, queryable, relational** data — not JSON-RPC round-trips to a Solana RPC node, and not someone else's hosted indexer. You need *your* Postgres with *your* indexes and *your* schema, populated a few hundred milliseconds after finality.

Prism answers all five questions out of the box.

> **🎯 Design principle:** Hot path stays **synchronous and boring**. No reactive, no actors, no fancy schedulers. One virtual thread per job. One queue per concurrency boundary. The JVM does the rest.

---

## 🌈 Why "Prism"?

Because a prism takes one stream of white light and **splits it into the colors that were always there** — it doesn't create information, it reveals structure.

That's exactly what the indexer does to Solana's stream:

```text
                    ┌──────────────────────────────────────┐
                    │                                      │
 ✨ Solana stream ──┤  🔺 Prism                            │
  (undifferentiated)│                                      │
                    │   ┌─► 🟢 successful transactions    │
                    │   ├─► 🔴 failed transactions         │
                    │   ├─► 🟡 large transfers (>1 SOL)    │
                    │   ├─► 🟣 memos                       │
                    │   └─► 🔵 fee payer accounts          │
                    │                                      │
                    └──────────────────────────────────────┘
                        five queryable tables, refracted
                        out of one protobuf soup
```

Every incoming transaction is refracted into the projections you actually want to query. No more scanning JSON blobs. No more `getBlock` loops. Just `SELECT`.

---

## 🎞️ A Day in the Life of a Solana Transaction

> **🎬 Scene 1 — Somewhere in a Solana validator, 400 ms ago**

```text
 ⚙️  validator          📡  Geyser plugin           🔺  Prism
 ─────────              ──────────────              ───────

 🧱 builds slot 312_701                                 │
 📝 includes tx 5Kx7aLm...                              │
 ✉️  finalizes block                                    │
                        🚀 "new tx!"  ─────────►   📥 arrives on gRPC
                                                        │
                                                   🔍 TransactionParser
                                                        │
                                                        │   signature: 5Kx7a...
                                                        │   slot:      312_701
                                                        │   amount:    4.2 SOL
                                                        │   from:      7xKX...h9Fz
                                                        │   to:        9vBM...n3Tr
                                                        │   memo:      "invoice #7341"
                                                        │   failed:    false
                                                        ▼
                                                   LinkedTransferQueue (unbounded)
                                                        │
                                                        ▼
                                                   📦 TransactionBatchService
                                                        │   (accumulating...)
                                                        │   199 tx + this one = 200 → 🚽 FLUSH
                                                        ▼
                                                   🔀 TransactionProcessor
                                                        │
                                ┌───────────────────────┼────────────────────────┐
                                ▼                       ▼                        ▼
                         📄 transactions        📝 memos                  💰 large_transfers
                         (COPY FROM STDIN)      (batch INSERT)            (batch INSERT)
                         staging → merge        reWriteBatched=true       reWriteBatched=true
                                                                                 │
                                                                                 ▼
                                                                           done in ~8 ms
                                                                           total, parallel
```

**Timeline for one transaction:**

| Stage | Latency | What happens |
|---|---|---|
| 📡 Geyser publish | ~5 ms | Validator plugin flushes to wire |
| 🚚 Network hop | ~10-30 ms | HTTP/2 frame to Prism |
| 🔍 Parse | < 1 ms | Protobuf → domain record |
| 🪣 Queue | < 1 μs | `LinkedTransferQueue.offer()` |
| 📦 Batch wait | 0-100 ms | Dual-trigger: 200 tx OR 100 ms |
| 🖊️ COPY + merge | ~5-10 ms | 200 rows in one write |
| ✅ **End-to-end** | **< 200 ms** | From finality to queryable row |

> **Scene 2 — A developer runs `curl localhost:3000/api/transfers?min_amount=4.0` and sees the transaction from Scene 1 in the results.** That's the whole movie.

---

## ⚡ The Hot Path: How a Transaction Becomes a Row

The write side is the most interesting part of the system. It's designed to be boring, fast, and **impossible to back-pressure**.

```mermaid
flowchart LR
    subgraph Source["🌊 Solana Source"]
        direction TB
        YS["Yellowstone gRPC<br/>(paid)"]
        WS["WebSocket<br/>blockSubscribe<br/>(free)"]
    end

    subgraph Parse["🔍 Parsing"]
        P1["TransactionParser<br/><i>protobuf / json</i>"]
        P2["BlockNotificationParser<br/><i>shared logic</i>"]
    end

    subgraph Queue["🪣 Concurrency Boundary"]
        direction TB
        TQ["LinkedTransferQueue<br/><b>unbounded</b><br/>tx stream"]
        AQ["ArrayBlockingQueue(10K)<br/><b>bounded, drop-if-full</b><br/>account stream"]
    end

    subgraph Batch["📦 Batching"]
        direction TB
        TB["TransactionBatchService<br/>200 tx / 100 ms"]
        AB["AccountBatchService<br/>200 acct / 2 s<br/>+ dedup by pubkey"]
    end

    subgraph Processor["🔀 Processor"]
        TP["TransactionProcessor<br/><i>split into 4 buckets</i>"]
    end

    subgraph DB["🗄️ PostgreSQL"]
        direction TB
        T1["transactions<br/><b>COPY FROM STDIN</b>"]
        T2["failed_transactions<br/>batch INSERT"]
        T3["memos<br/>batch INSERT"]
        T4["large_transfers<br/>batch INSERT"]
        T5["accounts<br/>UPSERT ON CONFLICT"]
    end

    YS --> P1 --> TQ
    WS --> P2 --> TQ
    P1 --> AQ
    P2 --> AQ
    TQ --> TB --> TP
    AQ --> AB --> T5
    TP --> T1
    TP --> T2
    TP --> T3
    TP --> T4

    style T1 fill:#4caf50,color:#fff
    style YS fill:#9945FF,color:#fff
    style WS fill:#00D18C,color:#fff
```

**Two concurrency boundaries, two queue strategies:**

| Queue | Type | Capacity | Policy | Why |
|---|---|---|---|---|
| 🪣 **Transaction** | `LinkedTransferQueue` | **Unbounded** | Never blocks producer | If this queue blocks, Yellowstone hangs up with a `lagged` error. Losing a transaction is worse than using heap. |
| 🪣 **Account** | `ArrayBlockingQueue` | **10,000** | `try_offer` — drop if full | Accounts are less critical and dedup-friendly. Dropping the occasional fee payer snapshot is fine. |

This asymmetry is the whole trick. Transactions get backpressure protection; accounts get memory protection. Nobody wins both fights at once.

---

## 🏛️ Architecture

Prism follows strict **hexagonal architecture (ports & adapters)** with DDD tactical patterns. Dependencies always point inward.

```text
                        ┌───────────────────────────────────────────┐
                        │                                           │
                        │         🏛️  application/                  │
                        │    ┌────────────────────────────────┐     │
                        │    │ Helidon 4 SE functional routes │     │
                        │    │ IndexerApplication (main)      │     │
                        │    │ IndexerConfig (env parsing)    │     │
                        │    │ GlobalErrorHandler             │     │
                        │    │ MapStruct mappers              │     │
                        │    └────────────────────────────────┘     │
                        │                  │                        │
                        │                  ▼ delegates to            │
                        │    ┌────────────────────────────────┐     │
                        │    │         🧠 domain/             │     │
                        │    │                                │     │
                        │    │  ┌──── model ────┐             │     │
                        │    │  │ Signature     │             │     │
                        │    │  │ Pubkey        │             │     │
                        │    │  │ Slot          │             │     │
                        │    │  │ SolanaTx      │             │     │
                        │    │  │ Account       │             │     │
                        │    │  └───────────────┘             │     │
                        │    │                                │     │
                        │    │  ┌──── service ──┐             │     │
                        │    │  │ BatchService  │             │     │
                        │    │  │ Processor     │             │     │
                        │    │  │ LargeTransfer │             │     │
                        │    │  │   Filter      │             │     │
                        │    │  └───────────────┘             │     │
                        │    │                                │     │
                        │    │  ┌──── port ─────┐             │     │
                        │    │  │ TxStream      │◄── implemented by
                        │    │  │ TxRepo        │             │     │
                        │    │  │ MemoRepo      │             │     │
                        │    │  │ ...           │             │     │
                        │    │  └───────────────┘             │     │
                        │    │                                │     │
                        │    │  ZERO framework imports.       │     │
                        │    │  Only Lombok + java.*          │     │
                        │    └────────────────────────────────┘     │
                        │                  ▲                        │
                        │                  │ implements ports       │
                        │    ┌────────────────────────────────┐     │
                        │    │    🔌 infrastructure/          │     │
                        │    │                                │     │
                        │    │  grpc/        Yellowstone       │     │
                        │    │  websocket/   blockSubscribe    │     │
                        │    │  persistence/ pgjdbc + COPY     │     │
                        │    │  metrics/     Micrometer        │     │
                        │    │  console/     ANSI formatter    │     │
                        │    │  solana/      Base58, balance   │     │
                        │    └────────────────────────────────┘     │
                        │                                           │
                        └───────────────────────────────────────────┘

                        🛡️ ArchUnit enforces these rules at build time
```

**The rules (enforced by ArchUnit):**

| Rule | What It Stops |
|---|---|
| `domain` ⊥ `infrastructure` | Prevents domain from leaking JDBC/gRPC types |
| `domain` ⊥ `application` | Prevents domain from reaching up into routes |
| `domain` has **zero** Helidon/Jakarta imports | Keeps domain framework-free (Lombok + `java.*` only) |
| `domain` has **zero** `java.sql.*` imports | No DB types in business logic |
| `infrastructure` ⊥ `application.routing` | Infra adapters can't call routes directly |

Break any rule and the build fails. No social contracts, only compile errors.

---

## 🧬 The COPY Protocol: Why We Bypass `INSERT VALUES`

PostgreSQL has two fundamentally different write paths. Most ORMs use the slower one. Prism uses the faster one.

> **🎬 The 5× speedup you get by ignoring your instincts**

```text
 ❌ INSERT VALUES (what JPA / Hibernate / Spring Data give you)
 ─────────────────────────────────────────────────────────────
 INSERT INTO transactions VALUES ($1, $2, $3);
 INSERT INTO transactions VALUES ($4, $5, $6);
 INSERT INTO transactions VALUES ($7, $8, $9);
 ... × 200

 Each row:
   🔄 parse SQL
   📋 plan query
   🔒 acquire lock
   💾 write WAL
   📝 update index
   ✅ commit row

 200 rows × overhead = 💀 slow


 ✅ COPY FROM STDIN (what pgjdbc's CopyManager gives you)
 ──────────────────────────────────────────────────────
 COPY staging_transactions (signature, slot, success) FROM STDIN (FORMAT TEXT);
 5Kx7a...	312701	t
 6Lm8b...	312701	t
 7Nv9c...	312701	t
 ... × 200
 \.
 
 INSERT INTO transactions SELECT * FROM staging_transactions
   ON CONFLICT (signature) DO NOTHING;
 TRUNCATE staging_transactions;
 
 One batch:
   🔄 parse SQL once
   📋 plan query once
   🚀 stream 200 rows over STDIN
   💾 one WAL flush
   📝 index update once
   ✅ commit batch

 5-10× faster on the hottest table 🔥
```

**Why a staging table?** `COPY` doesn't support `ON CONFLICT`. So we:

1. `COPY` into `staging_transactions` (no constraints, no indexes, pure speed)
2. `INSERT ... SELECT ... ON CONFLICT (signature) DO NOTHING` from staging → main
3. `TRUNCATE staging_transactions` and repeat

The staging merge costs an extra statement, but `COPY` + merge is still ~5× faster than individual `INSERT`s because the expensive parts — parsing, planning, locking, WAL — amortize across 200 rows.

> **💡 Secondary tables** (`failed_transactions`, `memos`, `large_transfers`) are low volume, so they use plain `PreparedStatement.addBatch()` with `reWriteBatchedInserts=true` on the pgjdbc URL. The driver rewrites `INSERT ... VALUES ($1, $2)` batches into a single `INSERT ... VALUES ($1, $2), ($3, $4), ...` statement — nearly `COPY`-level throughput without the staging dance.

---

## 🧵 Virtual Threads: Why No Reactor, No WebFlux, No Spring Boot

Traditional Java servers tried to solve the C10K problem with reactive streams:

```java
// Reactive way — every I/O op is a callback in a chain
return webClient.get()
    .uri("/slot")
    .retrieve()
    .bodyToMono(Slot.class)
    .flatMap(slot -> repo.findBySlot(slot))
    .flatMap(txs -> Flux.fromIterable(txs)
        .parallel()
        .runOn(Schedulers.boundedElastic())
        .map(this::process)
        .sequential()
        .collectList())
    .onErrorResume(e -> Mono.error(new IndexerException(e)));
```

That's fine code. It's also impossible to debug, step through, or reason about at 3 AM during an incident.

**Virtual Threads (Project Loom, finalized in JDK 21) change the rules.** You can write plain blocking code and the JVM parks the virtual thread on any I/O wait — no OS thread is held, no carrier is pinned, no Schedulers, no operators:

```java
// Loom way — boring, blocking, testable
var slot = httpClient.get("/slot", Slot.class);
var txs = repo.findBySlot(slot);
for (var tx : txs) {
    process(tx);
}
```

One virtual thread per job. The JVM multiplexes millions onto a handful of carrier threads. Helidon 4 SE was built from the ground up on this model — it has no Netty event loop, no Servlet container, no CDI graph to warm up. Startup is under 100 ms and p99.999 latency is under 7 ms.

**Prism's one rule:** never call `synchronized`, always use `ReentrantLock`. `synchronized` pins a virtual thread to its carrier and kills throughput. ArchUnit enforces this at build time.

---

## 🌊 Dual Streaming Modes: Free or Fast

Prism can consume Solana's transaction stream in two ways — **same domain, same batching, same persistence** — through a pluggable `TransactionStream` port.

```text
                           ┌─────────────────────────┐
                           │  TransactionStream port │
                           │  (domain interface)     │
                           └────────────┬────────────┘
                                        │
                  ┌─────────────────────┴─────────────────────┐
                  │                                           │
         ┌────────┴─────────┐                        ┌────────┴────────┐
         │ 🆓 WebSocket      │                        │ 💰 Yellowstone   │
         │ blockSubscribe    │                        │ gRPC (Geyser)    │
         └──────────────────┘                         └─────────────────┘
```

| | 🆓 WebSocket mode | 💰 gRPC mode |
|---|---|---|
| **Endpoint** | `wss://api.mainnet-beta.solana.com` | Paid Yellowstone provider |
| **Protocol** | JSON-RPC `blockSubscribe` over WS | Protobuf over HTTP/2 |
| **Cost** | **$0** — public RPC | **$300-500/mo** typical |
| **Latency** | Higher — JSON parse + `confirmed` commitment | Lower — native protobuf + direct Geyser |
| **Throughput** | Lower — JSON overhead | Higher — 8 MB HTTP/2 window |
| **Stability** | Public RPC can be flaky | Dedicated, SLA-backed |
| **Vote filtering** | Client-side (check Vote program) | Server-side (`vote: false` filter) |
| **Tx data** | `encoding: "jsonParsed"`, full | Raw protobuf (richer) |
| **When to use** | Dev, testnet, hobby projects, low TPS | Production, mainnet, real workloads |

Switch with a single env var — `STREAM_MODE=websocket` (default) or `STREAM_MODE=grpc`. The domain layer doesn't know or care.

> **⚠️ Known HTTP/2 limitation (Helidon 4.4):** The stream-level window is a configurable 8 MiB, but Helidon doesn't yet expose the connection-level window (defaults to 64 KiB per RFC 7540). In practice, Helidon emits `WINDOW_UPDATE` frames as data is consumed, so throughput is gated by consumption speed — not a static cap. Tracked in the `docs/implementation-plan.md` for a future revisit.

---

## 🪣 Dual-Trigger Batching: Size OR Time

The worst thing you can do to a write-heavy Postgres workload is flush one row at a time. The second-worst thing is to wait forever for a batch that never fills up.

Prism uses **dual-trigger batching** — flush when *either* threshold fires.

```text
                    ┌──────────────────────────────────────┐
                    │ 📦 TransactionBatchService           │
                    │                                      │
                    │  Buffer: [ 🟦 🟦 🟦 🟦 🟦 ... ]     │
                    │                                      │
                    │  Trigger A: size ≥ 200 txs           │
                    │  Trigger B: elapsed ≥ 100 ms         │
                    │                                      │
                    │  Whichever fires first → FLUSH 🚽    │
                    └──────────────────────────────────────┘

 ⚡ High TPS (40K/s)       🪶 Low TPS (100/s)        💤 Idle (0/s)
 ──────────────────        ──────────────────         ────────────
 200 txs in 5 ms           200 txs in 2 sec           0 txs
 → size triggers           → time triggers            → no flush
 → flush every 5 ms        → flush every 100 ms       → buffer stays empty
 ~200× fewer round-trips   bounded max latency         no wasted writes
```

**The numbers:**

| Scenario | TPS in | Batches/s | DB round-trips/s | Max write latency |
|---|---|---|---|---|
| Mainnet burst | 40,000 | ~200 | ~200 | 5 ms |
| Typical mainnet | 4,000 | ~40 | ~40 | 50 ms |
| Quiet dev chain | 100 | 10 | 10 | 100 ms |

Compare that to naive per-row writes at 40K TPS: **40,000 round-trips per second**. The database would melt.

**Account batching uses the same pattern with different thresholds** (200 / 2,000 ms) because account upserts are less latency-sensitive and dedup well — the same `pubkey` often appears multiple times in a 2-second window, and we keep the one with the highest slot in memory before sending a single `UPSERT`.

---

## 🔁 The Reconnect Dance

Solana RPC endpoints — whether free public or paid Yellowstone — will drop you. Count on it. Here's what happens when they do:

```text
 T+0s     🌊 stream is flowing... transactions pouring in
 T+120s   💥 stream ends unexpectedly (TCP reset / GOAWAY / network blip)
          │
          │    🧮 attempt 1: delay = 2 × 2¹ = 4 s
          ▼
 T+124s   🔄 retry → connected! streaming resumes
 T+184s   ✅ 60 s of stable flow → attempt counter resets to 0
          │
          │    🎉 next disconnect will start at 4 s again
          │
          ▼
 ...
 T+900s   💥 another disconnect
          │    attempt 1: 4 s  → fails
          │    attempt 2: 8 s  → fails
          │    attempt 3: 16 s → fails
          │    attempt 4: 30 s (capped) → connects
          ▼
 T+958s   ✅ back online, counter resets after 60 s stable
```

**Formula:** `delay = base × 2^min(attempt, 4)` where `base = 2 s`, capped at `30 s`.

| Attempt | Computed | Actual Delay |
|---|---|---|
| 1 | 4 s | **4 s** |
| 2 | 8 s | **8 s** |
| 3 | 16 s | **16 s** |
| 4 | 32 s | **30 s** (capped) |
| 5+ | 32 s | **30 s** (capped) |

**Reset rule:** after **60 seconds** of stable connection, attempt counter resets to 0 — so transient blips don't accumulate into slow restarts.

The same `ReconnectHandler` is shared by both the gRPC and WebSocket adapters. One strategy, two transports.

---

## 🛠️ Tech Stack

| Component | Choice | Version | Why |
|-----------|--------|---------|-----|
| **Runtime** | Java + Virtual Threads | 25 LTS | Scoped Values finalized, +291% VT throughput vs JDK 21 |
| **HTTP server** | Helidon 4 SE | 4.4.0 | Built on VTs from the ground up, <7 ms p99.999, <50 MB RSS, <100 ms startup, no CDI/reflection |
| **gRPC client** | Helidon 4 SE gRPC | 4.4.0 | Built-in HTTP/2 engine, VT-native, no grpc-java |
| **DI** | Avaje Inject | latest | Compile-time codegen, JSR-330 (`@Singleton`), zero reflection |
| **DB driver** | pgjdbc | 42.7+ | `CopyManager` + `reWriteBatchedInserts=true` |
| **Connection pool** | HikariCP × 2 | 7.x | Dual pools: write (20) + read (20) |
| **JSON** | Jackson | 2.18+ | Helidon native media support |
| **Migrations** | Flyway (standalone) | 12.x | No Spring integration, runs in `main()` |
| **Resilience** | Resilience4j | 2.3+ | Reconnect backoff strategy |
| **Metrics** | Micrometer + Prometheus | 1.14+ | Native Helidon integration |
| **Mapping** | MapStruct | 1.6.3 | Compile-time, `componentModel = "jsr330"` |
| **Architecture tests** | ArchUnit | 1.4.1 | Hexagonal rules enforced at build time |
| **Logging** | SLF4J + Logback | — | Structured via `@Slf4j` |
| **Testing** | JUnit 5 + Mockito BDD + AssertJ + Testcontainers + Awaitility | — | Three source sets: unit, integration, fixtures |
| **Build** | Gradle (Kotlin DSL) + convention plugins | 9.0 | `prism.service` + `prism.library` in `buildSrc/` |

### ❌ What Prism Explicitly Does Not Use

| Avoided | Replacement | Why |
|---|---|---|
| **Spring Boot** | Helidon 4 SE + `public static void main` | No classpath scanning, no reflection, <100 ms startup |
| **Spring Data JPA** | Raw pgjdbc + `CopyManager` | `COPY FROM STDIN` is 5-10× faster than `saveAll()` |
| **`@Autowired`** | Avaje `@Singleton` + Lombok `@RequiredArgsConstructor` | Constructor injection only |
| **`@ConfigurationProperties`** | `IndexerConfig` record + `System.getenv()` | Fail-fast parsing, no magic binding |
| **`@RestController`** | Helidon SE `HttpService` functional routing | No annotations, pure function composition |
| **`synchronized`** | `java.util.concurrent.locks.ReentrantLock` | `synchronized` pins virtual threads to carrier threads |
| **`System.out`/`println`** | `@Slf4j` everywhere | Structured logs only |
| **Comments/Javadoc** | Self-documenting code | If a method needs a comment, rename it |

---

## 🧱 Module Structure

```text
prism/                                 ← root project
│
├── buildSrc/                          ← Gradle convention plugins
│   └── src/main/kotlin/
│       ├── prism.service.gradle.kts   ← applied to main service
│       └── prism.library.gradle.kts   ← applied to shared libs
│
├── prism/                             ← main service (Helidon 4 SE)
│   └── src/
│       ├── main/java/com/stablebridge/prism/
│       │   ├── application/           ← inbound adapters
│       │   │   ├── IndexerApplication.java    ← main(), wiring
│       │   │   ├── IndexerLifecycle.java      ← shutdown hook
│       │   │   ├── config/IndexerConfig.java  ← env → record
│       │   │   ├── route/                     ← Helidon SE routes
│       │   │   │   ├── HealthRoutes.java
│       │   │   │   ├── StatsRoutes.java
│       │   │   │   ├── TransactionRoutes.java
│       │   │   │   ├── TransferRoutes.java
│       │   │   │   ├── MemoRoutes.java
│       │   │   │   ├── AccountRoutes.java
│       │   │   │   ├── CorsConfiguration.java
│       │   │   │   └── PaginationLimits.java
│       │   │   ├── mapper/                    ← MapStruct
│       │   │   └── error/GlobalErrorHandler.java
│       │   │
│       │   ├── domain/                ← core — zero framework imports
│       │   │   ├── model/             ← SolanaTransaction, Account, ...
│       │   │   ├── port/              ← TransactionStream, TransactionRepository, ...
│       │   │   ├── service/           ← BatchService, Processor, filters
│       │   │   ├── solana/            ← Base58, balance math, programs
│       │   │   └── exception/
│       │   │
│       │   └── infrastructure/        ← outbound adapters
│       │       ├── grpc/              ← Yellowstone stream + parser
│       │       ├── websocket/         ← blockSubscribe stream + parser
│       │       ├── persistence/       ← pgjdbc repositories
│       │       │   ├── DataSourceFactory.java      ← HikariCP × 2
│       │       │   ├── FlywayMigrator.java
│       │       │   ├── CopyTransactionRepository.java
│       │       │   ├── JdbcFailedTransactionRepository.java
│       │       │   ├── JdbcMemoRepository.java
│       │       │   ├── JdbcTransferRepository.java
│       │       │   ├── JdbcAccountRepository.java
│       │       │   └── JdbcStatsRepository.java
│       │       ├── solana/Base58.java
│       │       ├── metrics/
│       │       │   ├── MicrometerMetricsRecorder.java
│       │       │   └── BenchmarkLogReporter.java
│       │       └── console/ConsoleOutputFormatter.java
│       │
│       └── main/resources/
│           └── db/migration/          ← Flyway V1, V2, V3 ...
│
├── prism-api/                         ← shared DTOs (java-library)
│   └── src/main/java/.../api/model/   ← Page<T>, TransactionResponse, ...
│
├── docs/
│   ├── functional-spec.md             ← single source of truth
│   ├── implementation-plan.md         ← phases 0-7
│   ├── CODING_STANDARDS.md
│   └── TESTING_STANDARDS.md
│
├── infra/
│   └── prometheus.yml                 ← scrape config
│
├── docker-compose.yml                 ← Postgres + Prometheus + Grafana + app
├── Makefile                           ← developer workflow automation
├── build.gradle.kts / settings.gradle.kts
└── CLAUDE.md                          ← agent instructions
```

---

## 🚀 Quick Start

### Prerequisites

- 🐘 **Docker & Docker Compose** (for PostgreSQL, Prometheus, Grafana)
- ☕ **Java 25** (for local builds)
- 🛠️ **Make** (optional, just thin wrappers around `./gradlew` and `docker compose`)

### 60-Second Onboarding

```bash
# 1️⃣ Clone
git clone https://github.com/Puneethkumarck/prism.git
cd prism

# 2️⃣ Boot the infrastructure (Postgres + Prometheus + Grafana)
make infra-up

# 3️⃣ Run Prism against the free public Solana WebSocket endpoint
#     Defaults: STREAM_MODE=websocket, RPC_WS_ENDPOINT=wss://api.mainnet-beta.solana.com
DATABASE_URL=postgresql://indexer:indexer@localhost:5432/indexer \
  make run

# 4️⃣ In another terminal, watch it work
curl -s http://localhost:3000/health | jq
curl -s http://localhost:3000/api/stats | jq
curl -s "http://localhost:3000/api/transactions?limit=5" | jq
curl -s "http://localhost:3000/api/transfers?min_amount=1.0&limit=10" | jq
```

Within a few seconds you'll see `[SLOT]`, `[TX]`, `[MEMO]`, and `[TRANSFER]` events streaming to stdout, and rows accumulating in Postgres.

### Switching to Paid gRPC Mode

```bash
STREAM_MODE=grpc \
GRPC_ENDPOINT=https://<your-yellowstone-provider>.com \
X_TOKEN=<your-provider-token> \
DATABASE_URL=postgresql://indexer:indexer@localhost:5432/indexer \
  make run
```

### Run Everything in Docker

```bash
make up     # builds image via Jib + starts Postgres + Prometheus + Grafana + Prism
make down   # stops it all
```

---

## 🎛️ Make Targets

| Target | Description |
|---|---|
| `make build` | Compile + Spotless + unit + integration + ArchUnit |
| `make test` | Unit tests only |
| `make integration-test` | Integration tests (requires Docker for Testcontainers) |
| `make clean` | Remove all build artifacts |
| `make format` | Auto-format with Spotless |
| `make lint` | Spotless check + ArchUnit (matches pre-commit hook) |
| `make run` | Run Prism locally via Gradle |
| `make infra-up` | Start Postgres + Prometheus + Grafana |
| `make infra-down` | Stop infrastructure |
| `make infra-clean` | Stop + delete volumes |
| `make infra-status` | Show infra container status |
| `make infra-logs` | Tail infrastructure logs |
| `make docker-build` | Build Docker image via Jib (no Dockerfile) |
| `make up` | Start infra + app container |
| `make down` | Stop everything |
| `make setup-hooks` | Point git at `.githooks/` |
| `make help` | List all targets |

---

## 🌐 API Reference

Base URL: `http://localhost:3000` — no authentication (v1).
Metrics on `http://localhost:9090/metrics` (Prometheus format).

### 🩺 Health

```http
GET /health
```

```json
{ "status": "ok", "uptime_secs": 3600 }
```

### 📊 Stats

```http
GET /api/stats
```

Uses `pg_stat_user_tables.n_live_tup` for **O(1) approximate counts** — vastly faster than `COUNT(*)` on million-row tables.

```json
{
  "total_transactions": 4_812_344,
  "total_failed": 1_203_111,
  "total_transfers": 38_201,
  "total_memos": 914_102,
  "total_accounts": 87_433
}
```

### 🧾 Transactions

| Method | Path | Query Params | Notes |
|---|---|---|---|
| `GET` | `/api/transactions` | `limit` (default 50, max 500), `offset`, `success` (optional bool) | Paginated, `created_at DESC` |
| `GET` | `/api/transactions/{signature}` | — | Returns `TxRow` or `404` |
| `GET` | `/api/slots/{slot}` | — | Array, not paginated, `created_at ASC` |

```bash
# List the latest 10 successful transactions
curl -s "http://localhost:3000/api/transactions?limit=10&success=true" | jq

# Look up one by signature
curl -s "http://localhost:3000/api/transactions/5Kx7aLm..." | jq

# All transactions in slot 312_701_542
curl -s "http://localhost:3000/api/slots/312701542" | jq
```

### 💰 Large Transfers

```http
GET /api/transfers?limit=50&offset=0&min_amount=10.0
```

Paginated, ordered by `amount DESC`. Threshold is configurable; default `1.0 SOL`.

### 📝 Memos

```http
GET /api/memos?limit=50&offset=0
```

Paginated, ordered by `created_at DESC`.

### 🧍 Accounts

```http
GET /api/accounts/{pubkey}
```

Returns the most recent balance snapshot for a fee payer, or `404`.

### 🚨 Error Response Format

```json
{
  "error": "transaction not found",
  "status": 404
}
```

| Status | Meaning |
|---|---|
| `400` | Validation error (invalid base58, out-of-range pagination) |
| `404` | Resource not found (signature / pubkey) |
| `500` | Internal error (DB unreachable, etc.) |

---

## ⚙️ Configuration Reference

Every setting is an environment variable. `IndexerConfig` is a Java record parsed via `System.getenv()` — fail-fast on missing required vars, no binding magic, no `application.yml`.

| Variable | Required | Default | Description |
|---|---|---|---|
| `DATABASE_URL` | ✅ Yes | — | `postgresql://user:pass@host:port/db` |
| `STREAM_MODE` | No | `websocket` | `websocket` (free) or `grpc` (paid) |
| `RPC_WS_ENDPOINT` | if `STREAM_MODE=websocket` | `wss://api.mainnet-beta.solana.com` | Solana WebSocket RPC URL |
| `GRPC_ENDPOINT` | if `STREAM_MODE=grpc` | — | Yellowstone gRPC endpoint (https required except localhost) |
| `X_TOKEN` | No | — | Auth token for Yellowstone, injected as `x-token` metadata |
| `API_PORT` | No | `3000` | Helidon HTTP port |
| `CONSOLE_LOG` | No | `true` | `false` or `0` suppresses `[TX]`/`[MEMO]`/`[TRANSFER]` output |
| `BENCH_LOG` | No | `benchmark.log` | Path for 5-minute benchmark summary file |

**Fail-fast validation** (in `IndexerConfig.fromEnv()`):
- `DATABASE_URL` is always required.
- `STREAM_MODE` must be `websocket` or `grpc` (case-insensitive).
- `GRPC_ENDPOINT` must be a valid URI with `https` scheme (or `http` for localhost).
- `API_PORT` must be a non-negative integer ≤ 65535.

---

## 📊 Observability

| Layer | Technology | Details |
|---|---|---|
| **Metrics** | Micrometer + Prometheus | 8 counters (`indexer_tx_received`, `..._written`, `..._failed`, `..._memo`, `..._transfer`, `..._accounts_written`, `..._slots`, `..._batches`) scraped at `:9090/metrics` |
| **Dashboards** | Grafana | Runs at `http://localhost:3001` (admin/admin), Prometheus at `http://localhost:9091` |
| **Benchmark log** | File appender | Every 5 minutes: `timestamp | tps | recv | written | failed | failed% | memos | xfers | accts | batches | slots` |
| **Console output** | ANSI color coded | `[SLOT]` cyan · `[TX]` white/red · `[MEMO]` magenta · `[TRANSFER]` yellow |
| **Health** | Helidon | `GET /health` — no DB call, uptime from process start |

Sample benchmark log line:

```text
2026-04-11T09:17:42Z |   412 |      4120 |      2581 |      1539 |     37% |    18 |   104 |     31200 |      42 |    12
```

Mainnet is a *chaotic* environment — a 37% failure rate is completely normal (bots, MEV, failed swaps). The indexer records all of it.

---

## 🧪 Testing Strategy

Three-tier pyramid, with conventions adapted from `stablebridge-tx-recovery`:

```text
                    ┌───────────────┐
                    │  Integration  │  Testcontainers PostgreSQL, real JDBC,
                    │  (Docker)     │  end-to-end against both stream adapters
                    ├───────────────┤
                    │  Architecture │  ArchUnit: hexagonal layer rules,
                    │  (no deps)    │  no @Autowired, no System.out, no synchronized
                ┌───┴───────────────┴───┐
                │      Unit Tests       │  BDD Mockito + AssertJ, no Spring context,
                │  (single assertion)   │  fixture builders, no generic matchers
                └───────────────────────┘
```

| Tier | Source Set | Frameworks | Docker? |
|---|---|---|---|
| Unit | `src/test/` | JUnit 5, BDD Mockito (`given`/`then`), AssertJ, Awaitility | No |
| Architecture | `src/test/` | ArchUnit 1.4 | No |
| Integration | `src/integration-test/` | JUnit 5, Testcontainers PostgreSQL, direct JDBC | ✅ Yes |

**Non-negotiable testing rules:**

- 🎯 **Single-assert pattern** — build expected object, then `assertThat(actual).usingRecursiveComparison().isEqualTo(expected)`.
- 🗣️ **BDD Mockito only** — `given().willReturn()` / `then().should()`, never `when()/verify()`.
- 🚫 **No generic matchers** — no `any()`, `anyString()`, `eq()`. Pass actual values.
- 💬 **`// given` / `// when` / `// then` comments in every test**.
- 🏗️ **Fixture builders** — `SOME_*` constants and `<concept>Builder()` in `src/testFixtures/`.
- ⏱️ **Awaitility over `Thread.sleep`** — polling with timeout, not arbitrary waits.

Run them:

```bash
./gradlew test              # unit + architecture (~5 s)
./gradlew integrationTest   # integration tests (requires Docker, ~30 s)
./gradlew build             # everything + Spotless + ArchUnit
```

---

## 🗂️ Database Schema

Five tables, seven indexes, one staging table. All migrations live in `prism/src/main/resources/db/migration/`.

```text
 📄 transactions            primary key: signature (varchar 88)
    signature  varchar(88)  PK
    slot       bigint       idx_transactions_slot
    success    boolean      idx_transactions_success
    created_at timestamptz  idx_transactions_created_at DESC

 ❌ failed_transactions
    id         serial       PK
    signature  varchar(88)
    slot       bigint
    error      text
    created_at timestamptz  idx_failed_tx_created_at DESC

 💰 large_transfers
    id         serial       PK
    signature  varchar(88)
    slot       bigint
    amount     numeric      idx_large_transfers_amount DESC
    created_at timestamptz  idx_large_transfers_created_at DESC

 📝 memos
    id         serial       PK
    signature  varchar(88)
    memo       text
    created_at timestamptz  idx_memos_created_at DESC

 🧍 accounts                unique: pubkey
    id         serial       PK
    pubkey     varchar(88)  UNIQUE
    lamports   bigint
    slot       bigint
    executable boolean
    rent_epoch bigint
    created_at timestamptz

 🛠️ staging_transactions    (no constraints, no indexes)
    signature  varchar(88)
    slot       bigint
    success    boolean       ← COPY target, truncated per flush
```

### Dual Connection Pools

```text
 HikariCP Write Pool          HikariCP Read Pool
 ───────────────────          ──────────────────
 max:     20                  max:     20
 min:     5                   min:     5
 usage:                       usage:
   COPY staging_tx              GET  /api/transactions
   INSERT failed_tx             GET  /api/transfers
   INSERT memos                 GET  /api/memos
   INSERT large_transfers       GET  /api/accounts/:pubkey
   UPSERT accounts              GET  /api/stats
```

**Why two pools?** During a mainnet burst the write pool can saturate all 20 connections for 50-100 ms. If the API shared that pool, every `GET` would sit in line behind the writes. With dedicated pools, API latency is independent of ingest load — the primary complaint about every "indexer plus API on one DB" setup.

---

## 🧠 Design Decisions, Quick Reference

| # | Decision | Problem It Solves | Impact |
|---|---|---|---|
| 1 | **Unbounded tx queue** (`LinkedTransferQueue`) | Bounded queues cause producer block → Yellowstone `lagged` disconnect | Zero dropped transactions from backpressure |
| 2 | **COPY FROM STDIN + staging merge** | `INSERT VALUES` is 5-10× slower for high-volume tables | 5-10× write throughput on the hottest path |
| 3 | **200 tx / 100 ms dual-trigger batch** | Per-row writes create ~200× more DB round-trips | ~200× fewer round-trips, bounded max latency |
| 4 | **200 acct / 2 s batch with dedup** | Per-tx account upserts spawn thousands of tasks/sec | Eliminates task churn, reduces DB pressure |
| 5 | **Exponential reconnect** (4s→8s→16s→30s cap, 60s reset) | Thundering herd against a flapping endpoint | Progressive delay, fast recovery after stability |
| 6 | **Dual read/write HikariCP pools** | Write bursts starve API read queries | API latency independent of ingest load |
| 7 | **`pg_stat_user_tables` for `/api/stats`** | `COUNT(*)` on million-row tables is O(N) | O(1) approximate counts for dashboard |
| 8 | **4 parallel writes per flush** | Sequential writes to 4 tables multiply flush latency | All 4 writes (COPY + 3 INSERTs) run concurrently on virtual threads |
| 9 | **Helidon 4 SE (not Boot)** | Spring classpath scan, reflection, CDI → slow startup | <100 ms startup, <50 MB RSS, <7 ms p99.999 |
| 10 | **`ReentrantLock` (not `synchronized`)** | `synchronized` pins virtual threads to carrier | No carrier pinning, full VT throughput |
| 11 | **MapStruct at layer boundaries** | Hand-written copy loops are bug-prone and ugly | Compile-time, type-safe, zero runtime cost |
| 12 | **ArchUnit at build time** | Architectural rules degrade without enforcement | Hexagonal rules fail the build if violated |

---

## 📜 License

Released under the **MIT License**. See [`LICENSE`](LICENSE) for full text.

---

<div align="center">

### 🔺 Prism — Refract the Solana firehose into a queryable data stream.

Built on **Java 25 · Virtual Threads · Helidon 4 SE · pgjdbc · PostgreSQL 16**
No Spring Boot · No JPA · No reflection · No apologies.

*Every block. Every signature. Every memo. Every time.*

</div>
