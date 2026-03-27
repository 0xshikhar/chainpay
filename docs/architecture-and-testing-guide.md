# ChainPay — Master Architecture & Testing Playbook

This master guide provides a comprehensive technical breakdown of **ChainPay Core**, detailing end-to-end data paths, component interactions, and step-by-step testing instructions for code reviews and portfolio demonstrations.

---

## 🏛 1. High-Level Architecture Overview

```
                      +---------------------------------------+
                      |   Client / API / WebSockets (STOMP)   |
                      +-------------------+-------------------+
                                          | (REST / JWT / WS)
                                          v
                      +-------------------+-------------------+
                      |     Security & Auth Filter (JJWT)     |
                      +-------------------+-------------------+
                                          |
        +---------------------------------+---------------------------------+
        |                                 |                                 |
        v                                 v                                 v
+-------+-------+                 +-------+-------+                 +-------+-------+
| Account API   |                 | Payout API    |                 | Ops & Copilot |
| Controller    |                 | Controller    |                 | Controller    |
+-------+-------+                 +-------+-------+                 +-------+-------+
        |                                 |                                 |
        v                                 v                                 v
+-------+-------+                 +-------+-------+                 +-------+-------+
| Ledger        |                 | Payout State  |                 | Anomaly Detect|
| Service       |                 | Machine (FSM) |                 | & AI Tools    |
+-------+-------+                 +-------+-------+                 +-------+-------+
        |                                 |                                 |
        |  sum(DEBIT) == sum(CREDIT)      v                                 v
        |  +----------------------+--------------------+                    |
        |  | Outbox Publisher     | Blockchain Worker  |                    |
        |  | (Transactional)      | (Web3j EVM Signer) |                    |
        |  +----------+-----------+---------+----------+                    |
        |             |                     |                               |
        v             v                     v                               v
+-------+-------------+---------------------+-------------------------------+-------+
|                          PostgreSQL 16 Database                                   |
|   (Accounts, JournalEntries, LedgerTransactions, Payouts, OutboxEvents, Incidents) |
+-----------------------------------------------------------------------------------+
```

---

## 🔄 2. End-to-End Data Paths & Transaction Tracing

### Data Path 1: Multi-Asset Double-Entry Ledger Transaction

When a client posts a financial transaction via `POST /api/v1/accounts/transactions`:

1. **Authentication & Authorization**:
   - `JwtAuthenticationFilter.java` validates the `Authorization: Bearer <jwt>` header.
   - `@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")` on `AccountController.java` checks user credentials.

2. **Controller Request Ingestion**:
   - `AccountController.java` receives `PostTransactionRequest` containing entries list:
     - Debit line: `ACCOUNT_A`, `ASSET_USDC`, `DEBIT`, `100.00 USDC` (`100000000 base units`)
     - Credit line: `ACCOUNT_B`, `ASSET_USDC`, `CREDIT`, `100.00 USDC` (`100000000 base units`)

3. **Double-Entry Invariant Validation**:
   - `LedgerService.java` (`postTransaction` method) groups entries by `Asset` ID.
   - Evaluates the zero-sum invariant:
     $$\sum \text{DEBITS} - \sum \text{CREDITS} = 0$$
   - If debits != credits, throws `UnbalancedTransactionException` and aborts transaction.

4. **Database Persistence**:
   - Creates a single `LedgerTransaction` record.
   - Persists balanced `JournalEntry` records referencing `Asset` directly.
   - Balances are derived dynamically using `calculateRunningBalance` (`sum(CREDITS) - sum(DEBITS)` for customer available accounts).

---

### Data Path 2: EVM Payout Lifecycle & Blockchain Settlement

When a client submits a blockchain payout request via `POST /api/v1/payouts`:

```
[ POST /payouts ] ──> [ PayoutService ] ──> [ PayoutStateMachine: PENDING ]
                                                    │
                                                    ▼
                                           [ OutboxPublisher ]
                                                    │
                                                    ▼
                                        [ BlockchainWorker (Web3j) ]
                                                    │
                                                    ▼ (EVM RPC Broadcast)
                                         [ Status: SUBMITTED ]
                                                    │
                                                    ▼ (N=12 Confirmations)
                                     [ BlockchainEventListener ]
                                                    │
                                                    ▼
                                         [ Status: COMPLETED ]
                                                    │
                                                    ▼
                                        [ OutboxRelayJob Webhook ]
```

1. **Idempotency Check**:
   - `PayoutService.java` checks `Idempotency-Key` header against database unique index (`PayoutRepository.findByIdempotencyKey`).

2. **State Machine Initialization (`PENDING`)**:
   - `PayoutStateMachine.java` validates transition: `null → PENDING`.
   - Creates `Payout` entity with `status = PENDING`.

3. **Transactional Outbox Event Creation**:
   - `OutboxPublisherService.java` appends `OutboxEvent` with `eventType = PAYOUT_CREATED` in the **same database transaction**.

4. **Blockchain Signing & RPC Submission (`SUBMITTED`)**:
   - Scheduled `BlockchainWorker.java` polls `PENDING` payouts.
   - Queries `HotWalletPoolService.java` for hot wallet allocation and sequential nonces.
   - Generates and signs Web3j ERC-20 `transfer(to, amount)` transaction.
   - Broadcasts signed raw transaction via `MultiRpcFailoverProvider.java` to EVM RPC node.
   - `PayoutStateMachine.java` advances status: `PENDING → PROCESSING → SUBMITTED`.
   - Creates `BlockchainTransaction` entity with tx hash and gas parameters.

5. **Inbound Confirmation Listener (`COMPLETED`)**:
   - Scheduled `BlockchainEventListener.java` queries receipt block numbers.
   - Tracks confirmation count:
     $$\text{Confirmations} = \text{CurrentBlock} - \text{ReceiptBlock} + 1$$
   - When confirmations $\ge 12$, `PayoutStateMachine.java` advances status: `CONFIRMING → COMPLETED`.
   - `ChainPayMetrics.java` increments `chainpay.payouts.total` Prometheus counter.

6. **Terminal Failure Handling (`FAILED_PERMANENTLY`)**:
   - If contract reverts, address is invalid, or max retries are exceeded, `PayoutStateMachine.java` routes payout directly to `FAILED_PERMANENTLY`.
   - `AnomalyDetectionService.java` creates an `OperationalIncident` log.

7. **Webhook Relay**:
   - Scheduled `OutboxRelayJob.java` polls pending `OutboxEvent` records and dispatches HTTP POST webhooks to subscribers.
   - `WebSocketEventBroadcaster.java` broadcasts live JSON updates over `/topic/payouts` STOMP WebSocket.

---

## 🧪 3. Complete System Testing & Demo Playbook

### Step 1: Execute Automated Unit & Benchmark Test Suite
Run the full test suite from the project root directory:

```bash
./gradlew test
```

**Verified Test Classes**:
* `LedgerServiceTest`: Zero-sum invariant validation.
* `PayoutStateMachineTest`: Full FSM legal and illegal transition matrix testing.
* `ChainPayLoadBenchmarkTest`: 20-thread concurrent stress testing under load.
* `ArchitectureGovernanceTest`: Automated package boundary checks.
* `SystemHealthServiceTest`: Multi-component diagnostic health check.

---

### Step 2: Spin Up Local Docker Infrastructure
Launch PostgreSQL 16, Redis 7, and Anvil EVM local node:

```bash
docker-compose up -d
```

Verify running containers:
```bash
docker ps
```

---

### Step 3: Start Application & Access API Documentation
Start the Spring Boot backend application:

```bash
./gradlew bootRun
```

* **Swagger UI Documentation**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **System Health Endpoint**: `GET http://localhost:8080/api/v1/health`

---

### Step 4: Execute Postman API Collection
Import the pre-configured Postman Collection:
* File Path: [docs/chainpay-core.postman_collection.json](file:///Users/shikharsingh/Downloads/code/java/bootpay/docs/chainpay-core.postman_collection.json)

**Available Endpoints**:
1. `POST /api/v1/auth/login` — Authenticate and obtain JWT token.
2. `POST /api/v1/accounts` — Create Customer and System accounts.
3. `POST /api/v1/payouts` — Submit individual payout with `Idempotency-Key`.
4. `POST /api/v1/payouts/batch` — Submit bulk batch payout request.
5. `GET /api/v1/audit/export?format=CSV` — Download compliance audit log CSV.

---

### Step 5: Connect to Live STOMP WebSockets
Connect any STOMP/SockJS client to:
* **WebSocket Endpoint**: `ws://localhost:8080/ws`
* **Topic Channels**:
  * `/topic/payouts` (Live payout status transitions)
  * `/topic/ledger` (Live double-entry journal postings)
  * `/topic/incidents` (Live operational alerts)
