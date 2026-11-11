# ADR-001: Database Choice

## Status
Accepted

## Date
2026-11-07

## Context
The Wallet system requires strong consistency guarantees for financial operations. Transfers between wallets must be atomic: either both accounts are updated, or neither is. Any data loss or balance inconsistency is unacceptable in a financial system.

## Decision
Choose **PostgreSQL** as the relational database for the Wallet system.

## Options Considered

### Option A: PostgreSQL (Chosen ✅)
Mature relational database with full ACID support, distributed transactions, and granular locking.

**Pros:**
- Full ACID support with configurable transaction isolation
- Row-level locking for fine-grained concurrency control
- Distributed transactions with SAVEPOINT for complex operations
- JSONB for future extensibility (configurations, metadata)
- Open source, no licensing costs
- Active community and extensive documentation
- Testcontainers natively supports PostgreSQL

**Cons:**
- Requires connection and pool management configuration
- Limited vertical scalability (though sufficient for the MVP)
- Learning curve for advanced optimizations

### Option B: MySQL/MariaDB
Popular relational database with good read performance.

**Pros:**
- Widely adopted, easy to find professionals
- Good performance in read-heavy scenarios
- ACID support with InnoDB

**Cons:**
- Default isolation is REPEATABLE READ, not READ COMMITTED (can cause phantom reads)
- Fewer advanced features than PostgreSQL (JSON, full-text search)
- Less refined granular locking than PostgreSQL

### Option C: SQLite
Embedded database, ideal for local development and testing.

**Pros:**
- Zero configuration, single file
- Ideal for unit tests and quick integration
- Full ACID support

**Cons:**
- Not suitable for production with multiple instances
- Limited concurrency (write serialization)
- Does not scale to production scenarios

## Consequences

### Positive
- Strong ACID consistency guarantee for all financial operations
- Fine-grained concurrency control with row-level locks
- Ability to use SAVEPOINT for complex operations (transfers)
- JSONB allows extensibility without schema changes
- Testcontainers simplifies integration testing

### Negative
- Need to manage connections and connection pool
- Infrastructure overhead compared to NoSQL databases
- Requires schema migrations (Flyway/Liquibase) for evolution

## Usage Examples

### Transfer Transaction
```sql
BEGIN;
  -- Check source wallet balance
  SELECT balance FROM wallets WHERE id = :sourceId FOR UPDATE;
  
  -- Debit from source wallet
  UPDATE wallets SET balance = balance - :amount, version = version + 1 
  WHERE id = :sourceId;
  
  -- Credit to destination wallet
  UPDATE wallets SET balance = balance + :amount, version = version + 1 
  WHERE id = :destinationId;
  
  -- Record transactions
  INSERT INTO transactions (id, wallet_id, type, amount, created_at) 
  VALUES (:txId, :sourceId, 'DEBIT', :amount, NOW());
  
  INSERT INTO transactions (id, wallet_id, type, amount, created_at) 
  VALUES (:txId, :destinationId, 'CREDIT', :amount, NOW());
COMMIT;
```

## References
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [ACID Properties](https://en.wikipedia.org/wiki/ACID)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
