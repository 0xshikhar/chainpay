# ChainPay Core

**Blockchain Payout, Settlement & Stablecoin Ledger Service**  
*Java 21 / 25 · Spring Boot 3 / 4 · PostgreSQL · Redis · Web3j · Micrometer · STOMP WebSockets*

---

## 📌 Executive Summary & Key Technical Documentation

**ChainPay Core** is an enterprise-grade financial backend system that couples an immutable double-entry ledger with a state-machine-driven payout lifecycle, Web3j EVM event listening, transactional outbox webhooks, automated 3-way reconciliation, multi-chain adapter routing, and real-time STOMP WebSockets to ensure internal balances and on-chain state never silently drift apart.

### 📚 Essential Technical Documentation Guides:
* **[Master Architecture & Testing Playbook](docs/architecture-and-testing-guide.md)** — Complete component diagrams, step-by-step transaction data paths, and interactive testing instructions.
* **[Local Anvil EVM Testing Guide](docs/local-anvil-testing-guide.md)** — Zero-Docker step-by-step guide for testing against local Anvil EVM devnet.
* **[Senior / Principal Technical Interview Guide](docs/interview-deep-dive.md)** — Bridges codebase components to senior systems engineering interview questions (idempotency, zero-sum ledgers, mempool gas bumping, chain re-org handling).
* **[Implementation Status & Roadmap Tracker](docs/status.md)** — Complete 17-phase implementation tracker (Phases 0–16) and portfolio resume positioning strategy.

---

## 🚀 Key Architecture & Design Highlights

1. **Asset Domain Model (`Asset`)**:
   - Explicitly defines symbol (e.g. `USDC`), contract address, chain ID (`31337`, `11155111`, `137`, `42161`, `8453`), and decimals.
   - `JournalEntry` and `Account` entities reference `Asset` entities directly rather than using raw currency strings.
2. **Double-Entry Ledger Core**:
   - Zero-sum invariant enforced at write time (`sum(DEBITS) == sum(CREDITS)` per asset per transaction).
   - Balance materialization derived dynamically from immutable journal history (`NUMERIC(38,0)` base units).
3. **Payout State Machine with `FAILED_PERMANENTLY`**:
   - Explicit state transitions: `PENDING → PROCESSING → SUBMITTED → CONFIRMING → COMPLETED`.
   - Side branches: `FAILED → RETRYING`.
   - Terminal failure state: `FAILED_PERMANENTLY` for non-retryable errors (contract reverts, invalid recipient addresses, or max retries exceeded).
4. **Multi-Chain EVM Adapter & RPC Failover**:
   - `ChainAdapterRegistry` mapping chain IDs to EVM network adapters with primary/backup Web3j RPC failover.
5. **High-Throughput Batch Engine & Gas Cost Accounting**:
   - Bulk payout processing (`POST /api/v1/payouts/batch`) with item-level idempotency sub-keys.
   - Dynamic gas fee calculation and automated ledger fee settlement to `SYSTEM_FEE_REVENUE` accounts.
6. **Real-Time STOMP WebSockets & Enterprise Observability**:
   - STOMP broker (`/ws`) streaming live JSON events (`/topic/payouts`, `/topic/ledger`, `/topic/incidents`).
   - Custom Micrometer Prometheus metrics & exported Postman collection ([docs/chainpay-core.postman_collection.json](file:///Users/shikharsingh/Downloads/code/java/bootpay/docs/chainpay-core.postman_collection.json)).

---

## 🛠 Project Setup & Spin-Up

### Prerequisites
- Java 21+ (or OpenJDK 25)
- Docker & Docker Compose

### 1. Start Infrastructure Dependencies
Spin up PostgreSQL 16, Redis 7, and local Anvil EVM node:
```bash
docker-compose up -d
```

### 2. Run Database Migrations & Build
```bash
./gradlew build
```

### 3. Run Unit & Benchmark Tests
```bash
./gradlew test
```

### 4. Start Application
```bash
./gradlew bootRun
```

Access Swagger UI documentation at: `http://localhost:8080/swagger-ui.html`

---

## 🔐 API Reference Overview

| Module | Method | Endpoint | Description |
|---|---|---|---|
| **Auth** | `POST` | `/api/v1/auth/login` | Obtain JWT access + refresh token |
| **Auth** | `POST` | `/api/v1/auth/register` | Register new user |
| **Ledger** | `POST` | `/api/v1/accounts` | Create system or customer account |
| **Ledger** | `GET` | `/api/v1/accounts/{id}/balance` | Query derived account balance |
| **Ledger** | `POST` | `/api/v1/accounts/transactions` | Post balanced multi-line journal transaction |
| **Payouts** | `POST` | `/api/v1/payouts` | Submit payout (requires `Idempotency-Key`) |
| **Payouts** | `POST` | `/api/v1/payouts/batch` | Submit bulk payout request |
| **Payouts** | `GET` | `/api/v1/payouts/{id}` | Query payout details + status history |
| **Payouts** | `POST` | `/api/v1/payouts/{id}/retry` | Retry failed payout |
| **Audit** | `GET` | `/api/v1/audit/export?format=CSV` | Export downloadable CSV/JSON audit logs |
| **Health** | `GET` | `/api/v1/health` | System health diagnostics (Ledger & EVM node) |
