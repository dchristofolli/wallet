# ADR-003: Async Messaging

## Status
Accepted

## Date
2026-11-07

## Context
The Wallet system needs to support async operations for notifications and cashback, decoupled from synchronous financial transactions. These operations must not block or affect the consistency of main transactions, but need to be delivered reliably. Messaging will be implemented in weeks 6-7 of the roadmap.

The project follows an **event-driven architecture** approach, where domain events (e.g., `TransferCompleted`) are published to a message broker and consumed asynchronously by interested services. This project also serves as a **technical portfolio piece**, so demonstrating skills with in-demand technologies is a valid selection criterion.

## Decision
Choose **Apache Kafka** as the message broker for async operations in the Wallet system.

## Options Considered

### Option A: Apache Kafka (KRaft mode) (Chosen ✅)
Distributed streaming platform with high throughput, data retention, and event replay. Since Kafka 3.x, KRaft mode eliminates the Zookeeper dependency, simplifying local setup significantly.

**Pros:**
- High throughput for millions of messages per second
- Data retention allows event replay — consumers can reprocess historical events
- Rich ecosystem (Kafka Streams, Connect) for stream processing
- Ideal for event-driven architecture and event sourcing patterns
- Partitioned topics allow parallel processing
- KRaft mode simplifies local setup: single Docker container, no Zookeeper needed
- Multiple consumer groups can independently read the same event stream
- Testcontainers supports Kafka natively
- **High market demand**: Kafka skills are highly valued in senior-level positions
- **Portfolio value**: Demonstrates experience with event streaming, a sought-after architecture pattern
- Natural fit for event-driven design: events are first-class citizens, not just messages to be consumed

**Cons:**
- Steeper learning curve for concepts like partitions, offsets, and consumer groups
- Operational complexity higher than RabbitMQ for cluster management
- Kafka is an *event log*, not a *task queue*: messages persist and require offset management
- Consumer failure handling requires manual offset management vs RabbitMQ's built-in acks
- May be overkill for simple "process once and forget" scenarios

### Option B: RabbitMQ
Mature message broker with AMQP protocol support, queues, and flexible exchanges.

**Pros:**
- Easy to configure and operate locally with Docker
- Intuitive web interface for monitoring
- Message acknowledgment (acks) support for guaranteed delivery
- Flexible exchanges (direct, topic, fanout) for different routing patterns
- Dead Letter Exchange for failed messages
- Low learning curve
- Testcontainers natively supports it
- Simpler mental model for task queues

**Cons:**
- Less suitable for real-time data streaming and event replay
- Horizontal scalability more complex than Kafka
- Smaller data analytics tooling ecosystem
- **Lower market demand**: RabbitMQ skills are less frequently requested in job postings
- Less aligned with event-driven architecture: designed for command/task patterns, not event streaming
- Does not demonstrate event sourcing or CQRS readiness

### Option C: Spring Events (In-Process)
Internal Spring events for decoupling within the same application.

**Pros:**
- Zero additional infrastructure
- Simple to implement and test
- Integrated with Spring Framework

**Cons:**
- Does not survive application restarts
- Does not allow horizontal scalability
- Not suitable for future inter-service communication
- Does not demonstrate messaging skills

## Key Distinction: Task Queue vs Event Log

The core difference between RabbitMQ and Kafka is architectural:

| Aspect | RabbitMQ | Kafka |
|--------|----------|-------|
| **Model** | Message broker — messages flow through | Distributed log — messages persist |
| **Consumer** | Message delivered to one consumer, then removed | Multiple consumer groups read independently |
| **Failure handling** | Built-in acks, retries, dead letter queues | Manual offset management |
| **Best for** | Background tasks, job queues, notifications | Event streaming, replay, analytics |
| **Mental model** | "Process this task once" | "These events are valuable data" |

For the Wallet project, **Kafka is chosen despite the use cases being "background tasks"** because:
1. The project follows an **event-driven architecture** where events are domain facts, not just tasks
2. The project serves as a **portfolio piece** — Kafka demonstrates more marketable skills
3. Event retention allows future extensions (audit trails, analytics, replay) without architectural changes

## Consequences

### Positive
- Clear decoupling between synchronous transactions and async operations via event publishing
- Events persist in topics, allowing replay, audit, and future consumers
- Multiple consumer groups can independently process the same events (notifications, cashback, analytics)
- Demonstrates event-driven architecture skills — highly valued in the job market
- Natural preparation for evolution to microservices with event-based communication
- Kafka Streams or Connect can be added later for advanced stream processing
- Portfolio differentiation: fewer candidates demonstrate real Kafka experience vs RabbitMQ

### Negative
- Need to manage Kafka as additional infrastructure (brokers, topics, consumer groups)
- Integration testing complexity with Kafka (Testcontainers needed)
- Need to handle duplicate messages (idempotency) — Kafka delivers at-least-once
- Consumer offset management requires careful design
- Steeper learning curve: partitions, replication factors, retention policies
- For simple notifications, Kafka is architecturally heavier than needed — but the learning value justifies it

## Use Cases

### 1. Transfer Notification
After a successful transfer, publishes a `TransferCompletedEvent` to the `wallet-events` topic. The notification consumer, in the `notifications` consumer group, reads the event and sends a notification to the user without blocking the API response.

### 2. Cashback Processing
After a transfer, the same `TransferCompletedEvent` in the `wallet-events` topic is consumed by the cashback service in a separate consumer group. It calculates and applies cashback based on business rules.

### 3. Event Replay (Future)
Since events persist in Kafka, new consumers can be added later to reprocess historical events — e.g., building analytics dashboards or audit trails without modifying the existing system.

## Kafka Configuration for Local Development

```yaml
# docker-compose.yml snippet
services:
  kafka:
    image: confluentinc/cp-kafka:latest
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9094
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_TOPIC_AUTO_CREATE: 'true'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

## References
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kafka KRaft Mode (No Zookeeper)](https://kafka.apache.org/documentation/#kraft)
- [Spring Kafka](https://spring.io/projects/spring-kafka)
- [Running Kafka on Docker (KRaft) — Confluent](https://developer.confluent.io/confluent-tutorials/kafka-on-docker)
- [Kafka vs RabbitMQ: A Practical Guide](https://medium.com/@taycode/rabbitmq-vs-kafka-a-practical-guide-61b82c096cf7)
- [Event-Driven Architecture Patterns](https://docs.microsoft.com/en-us/azure/architecture/guide/architecture-styles/event-driven)
