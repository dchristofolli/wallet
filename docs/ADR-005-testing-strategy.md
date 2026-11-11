# ADR-005: Testing Strategy

## Status
Accepted

## Date
2026-11-07

## Context
The Wallet system requires a robust testing strategy to ensure the quality of financial operations. Integration tests with a real database are essential to validate ACID transactions, concurrency control, and system behavior under load. The strategy must cover everything from unit tests to integration and load tests.

## Decision
Adopt **Testcontainers** for integration tests with a real database, combined with **JUnit 5** for unit tests and **JMeter/Gatling** for load tests.

## Options Considered

### Option A: Testcontainers (Chosen ✅)
Ephemeral Docker containers for integration tests with real infrastructure.

**Pros:**
- Real database (PostgreSQL) in tests, not a mock
- Full isolation: each test runs in a clean container
- Support for multiple services (DB, RabbitMQ, Kafka)
- Integrated with JUnit 5 via @Container
- Test parallelization with independent containers
- Demonstrates DevOps and modern testing skills
- Eliminates the gap between test and production environments

**Cons:**
- Requires Docker installed on the development machine
- Slower tests than mocks (container startup)
- CI/CD must have Docker available
- Resource consumption (CPU, memory) during tests

### Option B: H2 in-memory
Embedded database for fast and isolated tests.

**Pros:**
- Zero configuration, runs in memory
- Very fast tests
- Does not require Docker

**Cons:**
- Behavior differences with PostgreSQL (SQL dialect, data types)
- Does not validate real integration with the production database
- False sense of security: tests pass with H2, fail with PostgreSQL
- Does not demonstrate DevOps skills

### Option C: Mocks and Stubs
Replaces external dependencies with mocked objects.

**Pros:**
- Extremely fast tests
- Full isolation of the system under test
- Ideal for pure unit tests

**Cons:**
- Does not validate integration with a real database
- Mocks may diverge from real behavior
- Does not test real ACID transactions
- Not suitable for validating concurrency control

## Consequences

### Positive
- Reliable integration tests with real PostgreSQL
- Real validation of ACID transactions and concurrency control
- Preparation for messaging tests with real RabbitMQ
- Demonstration of DevOps and modern testing skills
- Minimal gap between test and production environments

### Negative
- Need for Docker in all environments (dev, CI)
- Slower tests, require adequate timeout in CI
- Learning curve for Testcontainers
- Resource consumption during test execution

## Proposed Test Pyramid

```
        /    \
       / E2E  \          Few end-to-end tests
      /--------\
     / Integration \     Tests with Testcontainers (DB, MQ)
    /--------------\
   /    Unit        \    Many fast unit tests
  /------------------\
```

### Layers
1. **Unit Tests** (70%): Business logic, validations, utilities
2. **Integration Tests** (20%): Repositories, services with DB, controllers
3. **Load Tests** (10%): JMeter/Gatling to validate performance under concurrency

## Usage Example

### Integration Test with Testcontainers
```java
@Testcontainers
class WalletServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldTransferBetweenWallets() {
        // Given
        Wallet source = walletService.createWallet("Source", "source@test.com");
        Wallet destination = walletService.createWallet("Dest", "dest@test.com");

        // When
        TransferResult result = walletService.transfer(
            source.getId(), destination.getId(), BigDecimal.TEN
        );

        // Then
        assertThat(result.getSourceBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.getDestinationBalance()).isEqualTo(BigDecimal.TEN);
    }
}
```

## References
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
