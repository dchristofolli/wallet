# MVP Scope

## Overview

The MVP (Minimum Viable Product) of the Wallet system focuses on the **essential financial core**, implementing the minimum features to demonstrate ACID consistency, concurrency control, and code quality. The scope was deliberately limited to allow technical depth rather than functional breadth.

## Features

### 1. Create Wallet
**Description:** Registers a new digital wallet with a unique identifier.

**Business Rules:**
- `owner_name` is required and must be between 3 and 100 characters
- `owner_email` is required, must be unique and valid
- Initial balance is zero
- Wallet is created with `version = 0` for optimistic locking

**Validations:**
- Email already registered → 409 Conflict
- Invalid fields → 400 Bad Request

### 2. Query Wallet + Balance
**Description:** Returns wallet data including the current balance.

**Business Rules:**
- Balance is calculated from registered transactions
- Returns 404 if the wallet does not exist

**Returned Data:**
- Wallet ID
- Owner name
- Owner email
- Current balance
- Creation and update timestamps

### 3. Transfer Between Wallets
**Description:** Transfers value from one wallet to another with ACID guarantee.

**Business Rules:**
- Both wallets must exist
- Source wallet must have sufficient balance
- Amount must be positive
- A wallet cannot transfer to itself
- Operation is atomic: either both wallets are updated or neither

**Validations:**
- Wallet not found → 404 Not Found
- Insufficient balance → 400 Bad Request
- Invalid amount → 400 Bad Request
- Concurrency conflict → 409 Conflict

**Transaction:**
```
BEGIN;
  SELECT * FROM wallets WHERE id = :sourceId FOR UPDATE;
  SELECT * FROM wallets WHERE id = :destId FOR UPDATE;
  
  -- Validate balance
  IF source.balance < amount THEN
    ROLLBACK;
    RAISE 'Insufficient balance';
  END IF;
  
  -- Update balances
  UPDATE wallets SET balance = balance - :amount, version = version + 1 
  WHERE id = :sourceId;
  
  UPDATE wallets SET balance = balance + :amount, version = version + 1 
  WHERE id = :destId;
  
  -- Record transactions
  INSERT INTO transactions (id, wallet_id, type, amount, description, created_at) 
  VALUES (gen_random_uuid(), :sourceId, 'DEBIT', :amount, 'Transfer to ' || :destId, NOW());
  
  INSERT INTO transactions (id, wallet_id, type, amount, description, created_at) 
  VALUES (gen_random_uuid(), :destId, 'CREDIT', :amount, 'Transfer from ' || :sourceId, NOW());
COMMIT;
```

### 4. Transaction Statement
**Description:** Lists wallet transactions in descending chronological order.

**Business Rules:**
- Returns transactions ordered by creation date (newest first)
- Simple pagination with offset and limit
- Returns 404 if the wallet does not exist

## Data Modeling

### Table: wallets
| Column | Type | Constraints | Description |
|--------|------|-------------|-----------|
| id | UUID | PRIMARY KEY | Unique wallet identifier |
| owner_name | VARCHAR(100) | NOT NULL | Owner name |
| owner_email | VARCHAR(255) | NOT NULL, UNIQUE | Owner email |
| balance | DECIMAL(19, 2) | NOT NULL, DEFAULT 0.00 | Current wallet balance |
| version | INTEGER | NOT NULL, DEFAULT 0 | Version for optimistic locking |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Creation date |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last update date |

**Indexes:**
- `pk_wallets` (id) - Primary key
- `uk_wallets_owner_email` (owner_email) - Unique constraint

### Table: transactions
| Column | Type | Constraints | Description |
|--------|------|-------------|-----------|
| id | UUID | PRIMARY KEY | Unique transaction identifier |
| wallet_id | UUID | NOT NULL, FK → wallets.id | Associated wallet |
| type | VARCHAR(10) | NOT NULL, CHECK (type IN ('CREDIT', 'DEBIT')) | Transaction type |
| amount | DECIMAL(19, 2) | NOT NULL, CHECK (amount > 0) | Transaction amount |
| description | VARCHAR(255) | NULL | Optional description |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Transaction date |

**Indexes:**
- `pk_transactions` (id) - Primary key
- `idx_transactions_wallet_id` (wallet_id) - Foreign key lookup
- `idx_transactions_created_at` (created_at) - Temporal ordering

### ER Diagram
```
┌─────────────────┐       ┌──────────────────────┐
│    wallets      │       │    transactions      │
├─────────────────┤       ├──────────────────────┤
│ * id (UUID)     │◄──┐   │ * id (UUID)          │
│   owner_name    │   │   │   wallet_id (UUID) ──┼──┘ FK
│   owner_email   │   │   │   type (ENUM)        │
│   balance       │   │   │   amount (DECIMAL)   │
│   version       │   │   │   description        │
│   created_at    │   │   │   created_at         │
│   updated_at    │   │   └──────────────────────┘
└─────────────────┘   │
                      │ 1:N
                      │
└─────────────────────┘
```

## Non-Functional Requirements

### ACID Consistency
- All financial operations are transactional
- Transfers are atomic: debit and credit occur together or not at all
- Wallet balance always reflects the sum of transactions

### Concurrency Control
- Optimistic locking with `@Version` to detect conflicts
- On conflict, returns 409 Conflict to the client
- The client can retry the operation

### Performance
- Balance query: O(1) via materialized column
- Transaction statement: indexed by wallet_id and created_at
- Transfer: two row locks + two inserts

### Testing
- Minimum 80% coverage on critical services
- Integration tests with Testcontainers (real PostgreSQL)
- Concurrency tests to validate behavior under contention

## API Endpoints

| Method | Path | Description | Status Codes |
|--------|------|-----------|--------------|
| POST | `/api/v1/wallets` | Create wallet | 201, 400, 409 |
| GET | `/api/v1/wallets/{id}` | Query wallet | 200, 404 |
| GET | `/api/v1/wallets/{id}/transactions` | Transaction statement | 200, 404 |
| POST | `/api/v1/transactions/transfer` | Transfer between wallets | 200, 400, 404, 409 |

## Out of MVP Scope

The following features are **not** part of the MVP and will be implemented in later phases:

- **Value deposit** → Can be added in the future
- **Value withdrawal** → Can be added in the future
- **Multiple currencies** → Significant complexity, out of scope
- **Cashback** → Requires async messaging (weeks 6-7)
- **Notifications** → Requires async messaging (weeks 6-7)
- **Messaging (RabbitMQ/Kafka)** → Weeks 6-7
- **Docker and CI/CD** → Weeks 6-7
- **Load testing (JMeter/Gatling)** → Week 8
- **Advanced business validations** → Daily limits, minimum balance, etc.

## MVP Acceptance Criteria

The MVP is complete when:

1. [ ] It is possible to create a wallet via API
2. [ ] It is possible to query a wallet and view the balance
3. [ ] It is possible to transfer value between two wallets
4. [ ] It is possible to query the transaction statement
5. [ ] Transfers maintain ACID consistency
6. [ ] Concurrency is handled correctly (no balance corruption)
7. [ ] Integration tests pass with Testcontainers
8. [ ] API documentation is available (Swagger UI)

## References
- [ADR-001: Database Choice](ADR-001-database-choice.md)
- [ADR-002: Architectural Style](ADR-002-architectural-style.md)
- [ADR-005: Testing Strategy](ADR-005-testing-strategy.md)
- [System Architecture](architecture.md)
