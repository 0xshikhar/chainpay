# ChainPay — Local Anvil EVM Testnet Execution Guide

This guide provides step-by-step instructions for testing **ChainPay Core** against a local **Anvil** EVM node without requiring Docker or external cloud infrastructure.

---

## 📌 Prerequisites

- **Java 21+ or Java 25** (`java -version`)
- **Foundry (Anvil)** (`anvil --version`)

---

## 🛠 Step 1: Start the Local Anvil EVM Node

Run the following command in Terminal 1 to start your local devnet:

```bash
anvil
```

**Anvil Startup Output**:
- **JSON-RPC Endpoint**: `http://127.0.0.1:8545`
- **Chain ID**: `31337`
- **Default Account #0 Private Key**: `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`
- **Default Account #0 Address**: `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266`

---

## 🚀 Step 2: Start ChainPay Core in Local Test Profile

In Terminal 2, run Spring Boot using the `test` profile (which uses in-memory H2 PostgreSQL and connects to Anvil):

```bash
./gradlew bootRun --args='--spring.profiles.active=test'
```

---

## 🧪 Step 3: Execute End-to-End Payout Transaction

### 1. Authenticate & Obtain JWT Token

Send a `POST` request to `/api/v1/auth/login`:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

### 2. Submit a Live Blockchain Payout

Using the `accessToken` returned from login, submit a payout request:

```bash
curl -X POST http://localhost:8080/api/v1/payouts \
  -H "Authorization: Bearer <YOUR_ACCESS_TOKEN>" \
  -H "Idempotency-Key: idemp-anvil-1001" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "<CUSTOMER_ACCOUNT_UUID>",
    "assetId": "<USDC_ASSET_UUID>",
    "destinationAddress": "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
    "amount": 1000000
  }'
```

---

### 3. Observe Real-Time Execution in Anvil Logs

In your Anvil terminal, you will see real-time JSON-RPC transaction logs:

```text
eth_sendRawTransaction
  Transaction: 0xa1b2c3d4...
  Contract: null
  From: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
  To: 0x70997970C51812dc3A010C7d01b50e0d17dc79C8
  Value: 1000000 wei
  Gas Price: 20000000000
  Gas Used: 21000
  Block Hash: 0x...
  Block Number: 1
```

---

## 📊 Step 4: Verify Payout State Machine Progress

Query payout status via `GET /api/v1/payouts/{id}`:

```bash
curl -H "Authorization: Bearer <YOUR_ACCESS_TOKEN>" \
  http://localhost:8080/api/v1/payouts/<PAYOUT_ID>
```

**Lifecycle Transitions**:
$$\text{PENDING} \longrightarrow \text{PROCESSING} \longrightarrow \text{SUBMITTED} \longrightarrow \text{CONFIRMING} \longrightarrow \text{COMPLETED}$$
