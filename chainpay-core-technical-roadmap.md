# ChainPay Core — Technical Roadmap & Architecture Spec

**Blockchain Payout, Settlement & Stablecoin Ledger Service**
Java 21 · Spring Boot 3 · PostgreSQL · Redis · Web3j

This merges the two projects into one system: the double-entry ledger is the source of truth, the payout state machine is how money moves, and the blockchain worker/reconciliation layer is how the two stay honest with each other.

---

## 1. Positioning & Scope

**One-line pitch:** A financial infrastructure backend that lets a business hold internal balances, move stablecoins (USDC/USDT) on-chain, and guarantee that internal records and blockchain state never silently drift apart.

**In scope for the build:**
- Double-entry internal ledger (accounts, journal entries)
- Payout lifecycle with an explicit state machine
- On-chain settlement via Web3j (ERC-20 transfers, event listening)
- Idempotent APIs, transactional outbox for webhooks
- Reconciliation between internal ledger and on-chain truth
- Auth, RBAC, audit trail

**Explicitly out of scope (mention as "future work," don't build):**
- Multi-chain support (start with one EVM testnet — Sepolia or a local Anvil node)
- Real HSM/KMS integration (simulate the pattern with an encrypted software keystore)
- Actual banking rails / fiat on-off ramp
- The AI Ops Copilot (that's Project 2, plugs into this one's read APIs later)

---

## 2. Feature List

### 2.1 Ledger (the core differentiator — this is what makes it "senior")
- Chart of accounts: system accounts (e.g. `HOT_WALLET`, `FEE_REVENUE`, `PENDING_SETTLEMENT`) and per-customer accounts
- Journal entries: every movement of value is a set of balanced debit/credit lines, never a single balance mutation
- Invariant enforcement: sum of debits = sum of credits within a transaction, enforced at the service layer and checked by a scheduled integrity job
- Running balance materialization (denormalized balance per account, rebuildable from journal history)
- Multi-currency-aware ledger (internal unit + on-chain token, even if only USDC is supported initially)

### 2.2 Payout Lifecycle
- Explicit state machine: `PENDING → PROCESSING → SUBMITTED → CONFIRMING → COMPLETED`, with `FAILED → RETRYING` as a side branch
- Illegal-transition rejection (a payout can't jump from `PENDING` to `COMPLETED`)
- Retry policy with a capped attempt count and backoff
- Crash-safety: if the API dies after broadcasting a transaction but before recording it, recovery must detect and reconcile this on restart (this is your single best interview story)

### 2.3 Blockchain Integration
- Web3j client wrapping an EVM RPC provider (Infura/Alchemy for a testnet, or a local Anvil node for fully offline dev)
- Outbound: submit ERC-20 `transfer` calls for approved payouts, manage nonce sequencing
- Inbound: event listener subscribed to `Transfer` events on the stablecoin contract, filtered to addresses you control
- Confirmation tracking: don't consider a transaction final until N block confirmations (12 is a reasonable EVM default for the demo)
- Reorg handling: detect when a previously-seen block is replaced, roll back any state that depended on now-orphaned blocks

### 2.4 Idempotency & Reliability
- Idempotency-Key header on all mutating endpoints, deduplicated at the DB layer
- Transactional outbox for anything that needs to leave the service (webhooks, notifications) — no dual-write problem
- Outbox relay/poller with retry and dead-letter handling

### 2.5 Reconciliation
- Scheduled job comparing internal ledger state against on-chain state for every settled payout
- Detects: missing on-chain transactions, status mismatches, unexpected incoming transfers, duplicate records, balance discrepancies
- Produces a reconciliation report entity, queryable via API, with a discrepancy severity classification

### 2.6 Auth, RBAC & Audit
- JWT access + refresh token flow
- Roles: `ADMIN`, `OPERATOR`, `VIEWER` (minimum viable set — enough to demonstrate RBAC without overengineering)
- Method-level authorization on sensitive endpoints
- Immutable audit log: every state-changing action recorded with actor, timestamp, before/after state

### 2.7 API & Docs
- REST API, versioned (`/api/v1/...`)
- OpenAPI/Swagger auto-generated docs
- Postman collection exported alongside

### 2.8 Testing & Quality
- Unit tests for ledger invariants and state machine transitions (these are the tests that matter most — prioritize them)
- Integration tests with Testcontainers: real Postgres, real Redis, and either a local Anvil node or a mocked Web3j client
- A specific test for the "crash after broadcast, before persist" scenario — simulate it deliberately

---

## 3. High-Level Architecture

```
                    ┌────────────────────┐
                    │   Admin/API Client  │  (Postman / simple UI / curl)
                    └──────────┬──────────┘
                               │ REST (JWT auth)
                    ┌──────────▼──────────┐
                    │   Spring Boot API    │
                    │  Auth · RBAC · REST  │
                    └──┬────────────────┬──┘
                       │                │
             ┌─────────▼───────┐   ┌────▼──────────┐
             │   Ledger Service │   │ Payout Service │
             │ (double-entry)   │   │ (state machine)│
             └─────────┬────────┘   └────┬───────────┘
                       │                 │
                ┌──────▼─────────────────▼──────┐
                │         PostgreSQL             │
                │  accounts / journal_entries /  │
                │  payouts / outbox / audit_log  │
                └──────┬─────────────────┬───────┘
                       │                 │
              ┌────────▼───────┐  ┌──────▼─────────┐
              │  Outbox Relay   │  │ Blockchain      │
              │ (webhook queue) │  │ Worker          │
              └────────┬────────┘  └──────┬──────────┘
                       │                  │
                ┌──────▼──────┐    ┌──────▼───────────┐
                │  Webhook     │    │  Web3j / EVM RPC  │
                │  Consumers   │    │  (Sepolia/Anvil)  │
                └─────────────┘    └──────┬────────────┘
                                          │
                                   ┌──────▼───────────┐
                                   │ Reconciliation Job │
                                   │ (scheduled, diffs   │
                                   │  ledger vs chain)   │
                                   └────────────────────┘
```

Redis sits alongside Postgres for: idempotency key caching, rate limiting counters, and (optionally) a lightweight job queue if you don't want to bring in Kafka/RabbitMQ for the demo.

---

## 4. Tech Stack & Rationale

| Layer | Choice | Why |
|---|---|---|
| Language/runtime | Java 21 | Virtual threads (Project Loom) are a legitimate talking point for the blockchain worker's I/O-bound polling |
| Framework | Spring Boot 3 | Matches the JD directly |
| Persistence | PostgreSQL + Spring Data JPA/Hibernate | Relational integrity matters for a ledger — this is not a NoSQL problem |
| Migrations | Flyway | Versioned schema, matches real team workflows |
| Caching / dedup | Redis | Idempotency key store, rate limiting |
| Blockchain client | Web3j | Standard Java library for EVM interaction |
| Resilience | Resilience4j | Circuit breaker + retry + rate limiter around RPC calls — this is what turns "calls a blockchain" into "calls a blockchain safely" |
| Security | Spring Security + JJWT | JWT issuing/validation, RBAC |
| API docs | springdoc-openapi | Swagger UI out of the box |
| Testing | JUnit 5, Mockito, Testcontainers | Real infra in integration tests, not mocks-all-the-way-down |
| Containerization | Docker + docker-compose | Local dev parity, one-command spin-up |
| CI | GitHub Actions | Build + test on push, optional deploy step |
| Observability | Micrometer + a Prometheus/Grafana stack (or just structured JSON logs if time-constrained) | Shows you think about running this in production, not just demoing it locally |

---

## 5. Domain Model (entities — names and purpose only, no code)

- **Account** — internal ledger account (system or customer-owned), holds account type and currency, not a raw balance column
- **JournalEntry** — a single debit or credit line, always part of a balanced group; immutable once written
- **LedgerTransaction** — groups journal entries that must sum to zero; the atomic unit of the ledger
- **Payout** — the customer-facing request; owns the state machine status, references the LedgerTransaction(s) it caused
- **PayoutStatusHistory** — append-only log of every state transition, with reason/actor
- **BlockchainTransaction** — tracks a submitted on-chain tx: hash, nonce, gas params, confirmation count, block number
- **IdempotencyRecord** — request fingerprint → stored response, unique-constrained
- **OutboxEvent** — event payload + delivery status, written in the same DB transaction as the state change it represents
- **WebhookSubscription** / **WebhookDeliveryAttempt** — subscriber config and per-attempt delivery log with retry count
- **ReconciliationReport** / **ReconciliationDiscrepancy** — output of the scheduled reconciliation job
- **User** / **Role** — auth principals and RBAC roles
- **AuditLogEntry** — immutable record of state-changing actions



---

## 6. Folder / Package Structure

Package-by-feature (not package-by-layer) — scales better and reads more like a real production codebase than the generic `controller/service/repository` tutorial layout.

```
chainpay-core/
├── src/main/java/com/chainpay/core/
│   ├── ChainPayApplication.java
│   │
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── OpenApiConfig.java
│   │   ├── RedisConfig.java
│   │   ├── Web3jConfig.java
│   │   └── ResilienceConfig.java
│   │
│   ├── common/
│   │   ├── exception/           (domain exceptions + global handler)
│   │   ├── idempotency/         (idempotency key filter/interceptor)
│   │   └── audit/               (audit logging aspect)
│   │
│   ├── security/
│   │   ├── jwt/
│   │   ├── rbac/
│   │   └── user/
│   │
│   ├── ledger/
│   │   ├── domain/               (Account, JournalEntry, LedgerTransaction)
│   │   ├── repository/
│   │   ├── service/              (LedgerService — enforces the debit=credit invariant)
│   │   └── api/                  (controllers + DTOs)
│   │
│   ├── payout/
│   │   ├── domain/                (Payout, PayoutStatusHistory, state machine definition)
│   │   ├── repository/
│   │   ├── service/               (PayoutService, PayoutStateMachine)
│   │   └── api/
│   │
│   ├── blockchain/
│   │   ├── client/                (Web3j wrapper, contract bindings)
│   │   ├── listener/              (event subscription, confirmation tracking, reorg detection)
│   │   ├── worker/                (submission worker, nonce manager)
│   │   └── api/                   (read-only endpoints for tx status)
│   │
│   ├── webhook/
│   │   ├── domain/                (OutboxEvent, WebhookSubscription)
│   │   ├── relay/                 (outbox poller/publisher)
│   │   └── api/
│   │
│   ├── reconciliation/
│   │   ├── domain/
│   │   ├── job/                   (scheduled reconciliation task)
│   │   └── api/
│   │
│   └── audit/
│       ├── domain/
│       └── api/
│
├── src/main/resources/
│   ├── db/migration/              (Flyway V1__..., V2__..., etc.)
│   ├── application.yml
│   └── application-local.yml
│
├── src/test/java/com/chainpay/core/
│   ├── ledger/                    (unit tests for invariant enforcement)
│   ├── payout/                    (state machine transition tests)
│   ├── blockchain/                (mocked Web3j + reorg simulation tests)
│   └── integration/                (Testcontainers-based full-stack tests)
│
├── docker-compose.yml             (Postgres + Redis + optional local Anvil node)
├── Dockerfile
└── .github/workflows/ci.yml
```

---

## 7. Key Algorithms & Patterns to Explicitly Implement

This is the section to actually rehearse for interviews — naming these correctly and explaining *why* each one is there is what separates "I used AI to scaffold a CRUD app" from "I understand financial backend engineering."

- **Double-entry bookkeeping** — every value movement is a balanced set of debits/credits; balance is never mutated directly, only derived from journal history. Enforce the zero-sum invariant in the service layer, verify it independently with a scheduled integrity check.
- **Idempotency key pattern** — hash the request (or use a client-supplied key), store the first response, return the cached response on retry instead of reprocessing. Prevents duplicate payouts from network retries.
- **Transactional Outbox pattern** — write the "event to publish" row in the same DB transaction as the state change, relay it asynchronously. Solves the dual-write problem (DB commit succeeds, message publish fails, or vice versa).
- **Finite State Machine** — explicit, validated transitions for payout status; illegal transitions rejected at the service layer, not just at the UI. Worth mentioning whether you hand-rolled it or used Spring StateMachine, and why.
- **Optimistic locking (`@Version`) vs. pessimistic locking (`SELECT ... FOR UPDATE`)** — decide and justify which one guards concurrent balance/journal writes. Optimistic is usually the right call for a ledger with low write contention per account; know the trade-off either way.
- **N-block confirmation threshold** — a blockchain transaction isn't "final" until N subsequent blocks have been mined on top of it; track confirmation count per `BlockchainTransaction` and only mark a payout `COMPLETED` once the threshold is met.
- **Chain reorg detection & rollback** — store block hash + parent hash per observed block; if a new block's parent doesn't match what you have, you've detected a fork — roll back state derived from the orphaned branch.
- **Exponential backoff with jitter** — for RPC call retries against the blockchain node provider (Resilience4j `Retry`), avoids thundering-herd retries during provider outages.
- **Circuit breaker** — around the Web3j RPC client (Resilience4j `CircuitBreaker`) so a struggling node provider degrades the blockchain worker gracefully instead of cascading failures into the API.
- **Three-way reconciliation diff** — compare internal ledger state, expected on-chain state, and actual on-chain state; classify discrepancies (missing, mismatched status, unexpected transfer, duplicate, balance drift) rather than just flagging "different."
- **Nonce management** — sequential nonce assignment per hot wallet address to prevent transaction collisions when multiple payouts submit concurrently; a simple DB-backed counter with locking is sufficient for the demo.
- **Rate limiting** — Resilience4j `RateLimiter` or Bucket4j on public-facing endpoints, separate from the RPC-facing circuit breaker.
- **Audit logging via AOP** — an aspect around state-changing service methods rather than manual logging calls scattered through the codebase, so nothing is forgotten.

**Worth naming as "considered but deliberately scoped out"** (good interview material even unbuilt): Saga pattern for cross-service consistency if this ever splits into microservices; event sourcing for the ledger instead of snapshot + journal; CQRS if read and write load diverge significantly.

---

## 8. API Surface (endpoint list — no implementation)

**Ledger**
- `POST /api/v1/accounts` — create an account
- `GET /api/v1/accounts/{id}/balance` — current balance (derived, not stored raw)
- `GET /api/v1/accounts/{id}/journal` — paginated journal entry history

**Payouts**
- `POST /api/v1/payouts` — create a payout (requires `Idempotency-Key`)
- `GET /api/v1/payouts/{id}` — payout detail incl. status history
- `GET /api/v1/payouts?status=FAILED&minAmount=10000` — filtered search
- `POST /api/v1/payouts/{id}/retry` — manual retry trigger (RBAC: `OPERATOR`+)

**Blockchain**
- `GET /api/v1/blockchain/transactions/{hash}` — tx detail + confirmation count
- `GET /api/v1/blockchain/wallets/{address}/balance` — on-chain balance check

**Reconciliation**
- `GET /api/v1/reconciliation/reports` — list reports
- `GET /api/v1/reconciliation/reports/{id}/discrepancies`

**Webhooks**
- `POST /api/v1/webhooks/subscriptions` — register a subscriber
- `GET /api/v1/webhooks/deliveries/{id}` — delivery attempt log

**Auth**
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`

---

## 9. Testing Strategy

- **Unit tests** (highest priority — these prove you understand the domain, not just Spring wiring): ledger invariant enforcement, state machine legal/illegal transitions, reconciliation diff logic
- **Integration tests with Testcontainers**: real Postgres + Redis; either a local Anvil/Hardhat container for genuine on-chain interaction or a stubbed Web3j client for CI speed — ideally both, gated behind a test profile
- **The one test to make sure you build**: simulate "transaction broadcast succeeds, service crashes before persisting the result" and prove recovery-on-restart reconciles correctly. This single test is your best interview story.
- **Postman collection**: exported and versioned alongside the OpenAPI spec, used for manual smoke testing

---

## 10. Detailed Build Roadmap

Structured as phases, not fixed calendar weeks — pace it to your actual availability, but build in this order. Each phase should end in something demoable, not half-finished.

### Phase 0 — Foundations
- Repo scaffolding, package structure above
- Docker Compose: Postgres + Redis + local Anvil node
- Flyway baseline migration
- CI pipeline: build + unit test on push
- Spring Security skeleton with JWT login (no RBAC yet, just auth)

**Demoable at end of phase:** app boots, `/api/v1/auth/login` works, empty DB migrated.

### Phase 1 — Ledger Core
- Account and JournalEntry/LedgerTransaction entities + migrations
- LedgerService with the debit=credit invariant enforced at write time
- Scheduled integrity-check job (independent verification of the invariant across all transactions)
- Unit tests for invariant enforcement, including deliberately-broken cases that should be rejected

**Demoable at end of phase:** create accounts, post balanced journal entries via API, retrieve derived balances, watch an unbalanced entry get rejected.

### Phase 2 — Payout State Machine
- Payout + PayoutStatusHistory entities
- Explicit FSM implementation with legal-transition validation
- Idempotency key handling on `POST /payouts` (Redis-backed dedup)
- Unit tests covering every legal and illegal transition

**Demoable at end of phase:** create a payout, watch it move through states via manual triggers, prove duplicate idempotent requests don't double-process.

### Phase 3 — Blockchain Integration
- Web3j config against local Anvil node (or Sepolia testnet)
- Blockchain worker: submit ERC-20 transfer for an approved payout, nonce management
- Event listener: subscribe to `Transfer` events, persist as `BlockchainTransaction`
- Confirmation tracking (N-block threshold) driving payout status forward
- Resilience4j circuit breaker + retry around all RPC calls

**Demoable at end of phase:** a payout created via API actually results in an on-chain transfer on the test network, and its status advances automatically as confirmations accrue.

### Phase 4 — Reliability & Outbox
- OutboxEvent entity + relay/poller process
- Webhook subscription + delivery with retry/backoff and a dead-letter path after max attempts
- Simulate the "crash after broadcast, before persist" scenario deliberately and build recovery logic for it
- Integration test proving recovery correctness

**Demoable at end of phase:** kill the app mid-flow, restart it, watch it reconcile the in-flight payout correctly instead of double-submitting or losing it.

### Phase 5 — Reconciliation
- Scheduled reconciliation job comparing ledger vs. on-chain state
- Discrepancy classification and ReconciliationReport generation
- API to query reports and drill into discrepancies
- Deliberately inject a mismatch in a test environment and confirm the job catches it

**Demoable at end of phase:** manually create a ledger/chain mismatch, run reconciliation, see it flagged with the correct classification.

### Phase 6 — RBAC, Audit, Docs & Polish
- Role-based access control across sensitive endpoints
- AOP-based audit logging on state-changing actions
- Full OpenAPI/Swagger pass, exported Postman collection
- README with architecture diagram, setup instructions, and a "known limitations / future work" section (Saga, multi-chain, real HSM — listed deliberately, not apologetically)

**Demoable at end of phase:** the whole thing, end to end, with docs good enough that someone unfamiliar could run it from the README alone.

### Phase 7 (Optional) — Bridge to Project 2
- Expose read-only service methods cleanly enough that the AI Ops Copilot (Spring AI, `@Tool`-annotated methods) can be layered on top without touching ledger/payout internals
- This is the natural stopping point if you want to demonstrate the AI layer as a second, connected artifact

---

## 11. Interview Talking Points (rehearse these explicitly)

- Why double-entry instead of a single balance column, and what invariant you enforce and how
- The exact failure mode you handled in Phase 4 (crash between broadcast and persist) and how recovery works
- Why idempotency keys live where they live (Redis vs. DB) and what happens on a cache miss after a crash
- How you decided N=12 confirmations (or whatever you chose) and what the trade-off is (finality vs. latency)
- What a chain reorg actually is and how your listener detects and rolls back from one
- Why outbox instead of a direct publish-then-commit (or commit-then-publish) approach
- One thing you scoped out deliberately (Saga, multi-chain, real HSM) and why, with what you'd do differently at 10x scale


## 12. Production Hardening & EVM Mechanics

This section addresses the distributed state and EVM-specific edge cases required to run this architecture in production safely.

- **The Base-Unit Ledger:** All internal balances are stored in the asset's absolute smallest unit using `NUMERIC(38,0)` mapped to Java's `BigInteger`, mirroring the raw `transfer()` requirements. Token decimals (e.g., 6 for USDC, 18 for BNB) are applied strictly at the API boundary. Over-precise requests are explicitly rejected, never silently truncated.
- **Reorg Reversals:** `JournalEntry` records remain strictly immutable. Chain reorganizations trigger automated compensating entries (reversals) that explicitly reference the orphaned transaction, preserving a perfect financial audit trail of what the system believed and when.
- **Gas Bumping & Stuck Transactions:** A dedicated background monitor identifies transactions pending in the mempool beyond a set time threshold. It resubmits them using the exact same nonce with elevated gas fees, accommodating network volatility.
- **Block-Range Polling:** The inbound event listener relies on `eth_getLogs` queried over specific block ranges with a persisted PostgreSQL watermark (last processed block). This guarantees zero dropped events during RPC WebSocket disconnects or node downtime.
- **Nonce Sharding (High Throughput):** To bypass the strict serial bottleneck of EVM nonces during high-frequency concurrent payouts, transactions are sharded across a pool of authorized hot wallet addresses rather than bottlenecking on a single sender lock.
- **Terminal FSM States & Gas Protection:** Permanent on-chain failures (e.g., contract reverts, invalid addresses) route to a `FAILED_PERMANENTLY` state. This prevents endless retries from draining the native gas reserves. A separate monitor alerts when the hot wallet's native gas balance falls below a critical safety floor.
- **Hybrid Idempotency:** The `Idempotency-Key` is strictly enforced via a unique database constraint on the `Payout` table to guarantee ACID compliance. Redis acts exclusively as a fast-path read-through cache to short-circuit duplicate requests before they hit the database.
- **Gas Cost Accounting:** [To Be Determined — either centrally subsidized from a platform reserve account, or explicitly deducted from the user's stablecoin/native balance via a separate journal entry].