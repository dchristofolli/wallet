# Wallet - Digital Wallet System

## Overview

**Wallet** is a digital wallet system demonstrating advanced software architecture, data consistency, concurrency control, and DevOps practices. The project simulates a real financial core with wallet creation, balance inquiries, transfers between wallets, and transaction history.

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL (ACID) |
| Messaging | Apache Kafka (event-driven) |
| DevOps | Docker, GitHub Actions (CI/CD) |
| Testing | JUnit 5, Testcontainers, JMeter/Gatling |

## 8-Week Roadmap

### Weeks 1-2: Foundations & Design
- System architecture and ADRs (Architecture Decision Records)
- API contracts (OpenAPI/Swagger - Contract-First)
- Data modeling and concurrency strategies

### Week 3: Spring Boot Project Setup
- Entities, repositories, and basic endpoints (create and query wallets)

### Week 4: Transfers & Concurrency
- ACID-compliant transfers with optimistic/pessimistic locking
- Concurrency handling and conflict resolution

### Week 5: Testing & Transaction History
- Unit and integration tests with Testcontainers
- Transaction statement endpoint

### Week 6: Buffer & Refinement
- Buffer week to absorb implementation surprises from weeks 3-5
- Complementary study (Effective Java)

### Week 7: Async Messaging & Docker
- Event-driven messaging with Apache Kafka
- Docker Compose setup (app + DB + Kafka)

### Week 8: CI/CD, Load Testing & Polish
- CI/CD pipeline with GitHub Actions
- Load testing with JMeter
- Final polish and technical documentation

## Key Technical Focus Areas

- **Architecture & Integration**: System design, ADRs, technical trade-offs
- **APIs**: REST, OpenAPI, versioning
- **Language & Fundamentals**: Effective Java, Spring Boot, JPA/Hibernate
- **Design Patterns**: Strategy, Repository, Unit of Work, Specification
- **DevOps & Cloud**: Docker, CI/CD, monitoring

## Documentation

- [ADR-001: Database Choice](docs/ADR-001-database-choice.md)
- [ADR-002: Architectural Style](docs/ADR-002-architectural-style.md)
- [ADR-003: Async Messaging](docs/ADR-003-message-broker.md)
- [ADR-004: Contract-First APIs](docs/ADR-004-api-contract-first.md)
- [ADR-005: Testing Strategy](docs/ADR-005-testing-strategy.md)
- [ADR-006: Build Tool Choice](docs/ADR-006-build-tool.md)
- [ADR-007: Data Access Library](docs/ADR-007-data-access-library.md)
- [System Architecture](docs/architecture.md)
- [MVP Scope](docs/mvp.md)
- [OpenAPI Specification](docs/api/openapi.yaml)

## MVP Scope

The MVP focuses on the **essential financial core**:

1. **Create wallet** - Register a new digital wallet
2. **Query wallet + balance** - View wallet data and current balance
3. **Transfer between wallets** - Transfer value with ACID consistency
4. **Transaction history** - List wallet transactions

## License

Educational project for technical demonstration.
