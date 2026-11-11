# Wallet System Architecture

## Overview

The Wallet system adopts a **modular monolithic** architecture with a planned evolution to microservices. The application is divided into modules with clear responsibilities, communicating through well-defined interfaces and sharing a relational PostgreSQL database.

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway / Spring Boot                │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Wallet     │  │   Payment    │  │   Notification       │  │
│  │   Module     │  │   Module     │  │   Module (future)    │  │
│  │              │  │              │  │                      │  │
│  │ - Create     │  │ - Transfer   │  │ - Email/SMS          │  │
│  │ - Get        │  │ - Validate   │  │ - Push notifications │  │
│  │ - Balance    │  │ - Retry      │  │                      │  │
│  │ - Statement  │  │ - Idempotency│  │                      │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘  │
│         │                 │                      │              │
│  ┌──────┴─────────────────┴──────────────────────┴──────────┐  │
│  │              Message Broker (Apache Kafka)               │  │
│  │              (Weeks 6-7, future)                         │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Database (PostgreSQL - ACID)                   │
│                                                                 │
│  ┌──────────────┐          ┌──────────────┐                    │
│  │   wallets    │◄────────►│ transactions │                    │
│  │              │          │              │                    │
│  │ - id (UUID)  │          │ - id (UUID)  │                    │
│  │ - owner_name │          │ - wallet_id  │                    │
│  │ - owner_email│          │ - type       │                    │
│  │ - balance    │          │ - amount     │                    │
│  │ - version    │          │ - created_at │                    │
│  │ - created_at │          │              │                    │
│  │ - updated_at │          │              │                    │
│  └──────────────┘          └──────────────┘                    │
└─────────────────────────────────────────────────────────────────┘
```

## Application Layers

```
┌─────────────────────────────────────────────────────────┐
│                   Controller Layer                      │
│  (REST endpoints, DTO validation, exception handling)    │
├─────────────────────────────────────────────────────────┤
│                    Service Layer                        │
│  (Business logic, transaction management, concurrency)   │
├─────────────────────────────────────────────────────────┤
│                   Repository Layer                      │
│  (Data access, JPA entities, queries)                   │
├─────────────────────────────────────────────────────────┤
│                    Entity Layer                         │
│  (Domain models, value objects, enums)                  │
└─────────────────────────────────────────────────────────┘
```

## Transfer Flow

```
Client                          Server                          Database
  │                               │                               │
  │  POST /api/v1/transfer        │                               │
  │──────────────────────────────►│                               │
  │                               │  BEGIN TRANSACTION            │
  │                               │──────────────────────────────►│
  │                               │  SELECT ... FOR UPDATE        │
  │                               │──────────────────────────────►│
  │                               │  (lock source wallet)         │
  │                               │◄──────────────────────────────│
  │                               │                               │
  │                               │  Validate balance             │
  │                               │                               │
  │                               │  UPDATE wallets (debit)       │
  │                               │──────────────────────────────►│
  │                               │◄──────────────────────────────│
  │                               │                               │
  │                               │  UPDATE wallets (credit)      │
  │                               │──────────────────────────────►│
  │                               │◄──────────────────────────────│
  │                               │                               │
  │                               │  INSERT transactions (x2)     │
  │                               │──────────────────────────────►│
  │                               │◄──────────────────────────────│
  │                               │                               │
  │                               │  COMMIT                       │
  │                               │──────────────────────────────►│
  │                               │◄──────────────────────────────│
  │                               │                               │
  │  200 OK {result}              │                               │
  │◄──────────────────────────────│                               │
```

## Concurrency Strategy

### Optimistic Locking with @Version
- Each `Wallet` entity has a `version` column
- On update, Hibernate checks if the version has changed
- If it has, throws `ObjectOptimisticLockingFailureException`
- Service layer implements retry with exponential backoff

### Lock Ordering for Transfers
To avoid deadlocks in concurrent transfers:
- Always lock the wallet with the lower ID first
- E.g., transfer A→B: if A.id > B.id, lock B first, then A

## Design Patterns Applied

### 1. Repository Pattern
Isolates data access from business logic.
```java
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByIdForUpdate(UUID id);
}
```

### 2. Service Layer Pattern
Centralizes business logic and transaction management.
```java
@Service
@Transactional
public class PaymentService {
    public TransferResult transfer(UUID sourceId, UUID destId, BigDecimal amount) {
        // business logic here
    }
}
```

### 3. DTO Pattern
Separates transfer data from domain models.
```java
public record CreateWalletRequest(String ownerName, String ownerEmail) {}
public record WalletResponse(UUID id, String ownerName, BigDecimal balance) {}
```

### 4. Global Exception Handling
Centralizes exception handling with `@ControllerAdvice`.
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(...) {
        // return 409 Conflict
    }
}
```

## Configuration

### application.yml (structure)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/wallet
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        order_updates: true
        order_inserts: true

server:
  port: 8080
```

## Future Evolution

### Weeks 6-7: Messaging
- Add Apache Kafka for async notifications
- Notification module consumes transfer events
- Consumer offset management for reliable delivery

### Weeks 6-7: DevOps
- Dockerfile for application containerization
- docker-compose.yml for full stack (app + DB + Kafka)
- GitHub Actions for CI/CD

### Week 8: Quality
- Load testing with JMeter/Gatling
- Performance and latency metrics
- Technical defense with evidence

## References
- [ADR-001: Database](ADR-001-database-choice.md)
- [ADR-002: Architectural Style](ADR-002-architectural-style.md)
- [ADR-003: Message Broker](ADR-003-message-broker.md)
- [ADR-004: Contract-First](ADR-004-api-contract-first.md)
- [ADR-005: Testing](ADR-005-testing-strategy.md)
