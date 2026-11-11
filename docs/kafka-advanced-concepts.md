# Kafka Advanced Concepts — Wallet Project Guide

This document covers advanced Kafka concepts and configurations that are most relevant for the Wallet project. Since you already have Kafka experience, this skips the basics and focuses on what separates "I used Kafka once" from "I know Kafka well."

---

## Table of Contents

1. [Exactly-Once Semantics (EOS)](#1-exactly-once-semantics-eos)
2. [Producer Configurations](#2-producer-configurations)
3. [Consumer Group Management](#3-consumer-group-management)
4. [Partitioning Strategies & Ordering Guarantees](#4-partitioning-strategies--ordering-guarantees)
5. [Schema Registry & Schema Evolution](#5-schema-registry--schema-evolution)
6. [Dead Letter Patterns & Error Handling](#6-dead-letter-patterns--error-handling)
7. [Monitoring & Observability](#7-monitoring--observability)
8. [Spring Kafka Integration Patterns](#8-spring-kafka-integration-patterns)
9. [Kafka 4.0 New Features (2025)](#9-kafka-40-new-features-2025)

---

## 1. Exactly-Once Semantics (EOS)

Kafka provides three levels of delivery guarantees, from weakest to strongest:

| Guarantee | Producer | Consumer | Use Case |
|-----------|----------|----------|----------|
| **At-most-once** | `acks=0` or `acks=1` + auto commit | Auto commit | Fire-and-forget, duplicates acceptable |
| **At-least-once** | `acks=all` + idempotence | Manual commit after processing | **Default for Wallet** — notifications, cashback |
| **Exactly-once** | `transactional.id` + `isolation.level=read_committed` | Read committed | When duplicates are unacceptable |

### Why At-Least-Once is Usually Enough for Wallet

For the Wallet project, the async operations are:
1. **Send notification after transfer** — if the notification is sent twice, it's a minor UX issue, not a data corruption
2. **Calculate cashback after transfer** — if cashback is calculated twice, it's a business logic bug that should be handled at the application layer with idempotency keys

**Recommendation:** Use **at-least-once with idempotent consumers** rather than full EOS. EOS adds ~20% overhead and complexity. For the Wallet MVP, application-level idempotency is simpler and more defensible.

### Idempotent Consumer Pattern

Instead of relying on Kafka transactions, make your consumer logic idempotent:

```java
// Store processed event IDs in PostgreSQL
@Transactional
public void handleTransferCompleted(TransferCompletedEvent event) {
    // Check if already processed
    if (eventRepository.existsByEventId(event.getEventId())) {
        log.info("Event {} already processed, skipping", event.getEventId());
        return;
    }
    
    // Process the event
    notificationService.sendTransferNotification(event);
    
    // Mark as processed
    eventRepository.save(ProcessedEvent.from(event));
}
```

### When to Use Full EOS

Use Kafka transactions (`transactional.id`) only when:
- You need atomic read-process-write within Kafka (e.g., consuming from topic A, producing to topic B atomically)
- You're building a stream processing pipeline with Kafka Streams
- The cost of a duplicate is higher than the ~20% performance overhead

### EOS Configuration (if needed)

```properties
# Broker
transaction.state.log.replication.factor=3
transaction.state.log.min.isr=2
transaction.max.timeout.ms=900000

# Producer
transactional.id=wallet-notification-producer-${instance.id}
enable.idempotence=true
acks=all
retries=Integer.MAX_VALUE
max.in.flight.requests.per.connection=5

# Consumer
isolation.level=read_committed
```

---

## 2. Producer Configurations

### Production-Ready Producer Config

```properties
# Essential for all production workloads
spring.kafka.producer.acks=all
spring.kafka.producer.enable-idempotence=true
spring.kafka.producer.retries=2147483647  # Integer.MAX_VALUE
spring.kafka.producer.max-in-flight-requests-per-connection=5

# Batch and buffer tuning
spring.kafka.producer.batch-size=16384           # 16KB default
spring.kafka.producer.buffer-memory=33554432     # 32MB default
spring.kafka.producer.compression-type=snappy    # Balance between CPU and bandwidth

# Timeout tuning
spring.kafka.producer.request-timeout-ms=30000
spring.kafka.producer.delivery-timeout-ms=120000
```

### Key Config Explanations

| Config | Default | Recommended | Why |
|--------|---------|-------------|-----|
| `acks` | `1` | `all` | Wait for all ISR replicas — no data loss on leader failover |
| `enable.idempotence` | `true` | `true` | Deduplicates retries automatically at broker level |
| `retries` | `2147483647` | `2147483647` | With idempotence, infinite retries are safe |
| `max.in.flight.requests` | `5` | `5` | Max 5 for idempotence to guarantee ordering |
| `compression.type` | `none` | `snappy` or `lz4` | Reduces network I/O; snappy is fast, lz4 is faster |
| `batch.size` | `16384` | `16384`–`32768` | Larger batches = higher throughput, more latency |
| `linger.ms` | `0` | `5`–`10` | Wait for more records to fill batches — increases throughput |

### Producer Interceptor for Correlation IDs

A common production pattern is to add a correlation ID to every message for tracing:

```java
public class CorrelationIdInterceptor implements ProducerInterceptor<String, TransferCompletedEvent> {
    
    @Override
    public ProducerRecord<String, TransferCompletedEvent> onSend(ProducerRecord<String, TransferCompletedEvent> record) {
        MDC.put("correlationId", UUID.randomUUID().toString());
        return record;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        MDC.clear();
    }
}
```

---

## 3. Consumer Group Management

### Consumer Group Fundamentals

A consumer group is the core abstraction for scaling consumption. Key rules:
- **Partitions ≥ Consumers**: If you have 6 partitions and 4 consumers, 2 consumers will handle 2 partitions each
- **Partitions < Consumers**: Extra consumers are idle — no benefit
- **One partition → One consumer**: A partition is never consumed by multiple consumers in the same group

### Consumer Configuration for Wallet

```properties
# Consumer group
spring.kafka.consumer.group-id=wallet-notification-group

# Offset management
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false  # Manual commit for at-least-once

# Session and heartbeat
spring.kafka.consumer.session-timeout-ms=30000
spring.kafka.consumer.heartbeat-interval-ms=10000
spring.kafka.consumer.max-poll-interval-ms=300000  # 5 minutes

# Fetch tuning
spring.kafka.consumer.max-poll-records=500
spring.kafka.consumer.fetch-max-wait-ms=500
spring.kafka.consumer.fetch-min-bytes=1
```

### Understanding Consumer Timeouts

Three critical timeouts that trigger rebalances:

| Timeout | Default | Purpose |
|---------|---------|---------|
| `session.timeout.ms` | 45s | Consumer heartbeat interval — if no heartbeat, consumer is removed from group |
| `max.poll.interval.ms` | 5min | Max time between `poll()` calls — if processing takes too long, consumer is removed |
| `request.timeout.ms` | 30s | Max time to wait for broker response |

**Common mistake:** Setting `max.poll.interval.ms` too low. If your consumer processes 500 records and each record takes 2 seconds, you need at least 1000 seconds between polls.

### Rebalancing Strategies

Kafka has evolved through several rebalancing strategies:

| Strategy | Behavior | When to Use |
|----------|----------|-------------|
| **Range** (deprecated) | Divides partitions by range per topic | Legacy, avoid |
| **RoundRobin** | Distributes partitions evenly | Uniform load, no ordering needs |
| **Sticky** | Minimizes partition movement during rebalance | **Default, good for most cases** |
| **CooperativeSticky** (KIP-84) | Incremental rebalance — only affected consumers stop | **Recommended for large groups** |

#### CooperativeSticky for Wallet

Enable incremental rebalancing to avoid stopping all consumers during a rebalance:

```properties
spring.kafka.consumer.partition.assignment-strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

With CooperativeSticky:
- Only the joining/leaving consumer stops processing
- Other consumers continue processing their partitions
- Rebalance time is reduced by up to 20x in Kafka 4.0 (KIP-848)

### Static Membership

Reduce unnecessary rebalances during rolling deployments:

```properties
spring.kafka.consumer.group.instance.id=wallet-notification-${instance.id}
```

With static membership, a consumer that restarts within `group.instance.id.timeout.ms` (default 10 min) will retain its partition assignments without triggering a full rebalance.

---

## 4. Partitioning Strategies & Ordering Guarantees

### The Key Insight: Ordering is Per-Partition

Kafka guarantees ordering **within a partition**, not across partitions. This is the most important concept for the Wallet project.

### Using `wallet_id` as the Partition Key

For the Wallet project, every event should use `wallet_id` as the partition key:

```java
kafkaTemplate.send(
    "wallet-events",
    walletId.toString(),  // ← KEY: ensures all events for this wallet go to the same partition
    transferCompletedEvent
);
```

**Why this matters:**
- All events for wallet A land on partition 3
- All events for wallet B land on partition 7
- Within partition 3, events for wallet A are strictly ordered
- Consumer processing partition 3 sees events in the exact order they were produced

### Default Partitioner Behavior

The default partitioner uses Murmur2 hash on the serialized key:
- Same key → same partition (guaranteed)
- Different keys → potentially different partitions (load distribution)

### Custom Partitioner (Advanced)

If you need custom logic (e.g., route VIP wallets to dedicated partitions):

```java
public class WalletPartitioner implements Partitioner {
    
    @Override
    public int partition(String topic, Object key, byte[] keyBytes, 
                         Object value, byte[] valueBytes, Cluster cluster) {
        
        String walletId = (String) key;
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();
        
        // VIP wallets go to dedicated partitions (last 2 partitions)
        if (walletId.startsWith("vip-")) {
            int hash = Math.abs(walletId.hashCode());
            return numPartitions - 2 + (hash % 2);
        }
        
        // Regular wallets use default hashing
        int hash = Math.abs(walletId.hashCode());
        return hash % (numPartitions - 2);
    }
}
```

### Partition Count Planning

| Scenario | Partitions | Rationale |
|----------|-----------|-----------|
| Local development | 1–3 | Simplicity |
| MVP with 1 consumer per group | 3 | Balance between parallelism and management |
| Production with scaling | 6–12 | Allow scaling to multiple consumers |

**Rule of thumb:** Start with 3 partitions per topic. You can increase partitions later, but you cannot decrease them.

---

## 5. Schema Registry & Schema Evolution

### Why Schema Registry

Without Schema Registry, every producer and consumer must agree on the message format. With Schema Registry:
- Centralized schema storage with versioning
- Automatic compatibility checks on schema evolution
- Binary serialization (smaller messages, faster processing)
- Schema ID in message header — no schema in the payload

### Format Comparison

| Format | Serialization | Schema Evolution | Code Generation | Best For |
|--------|--------------|------------------|-----------------|----------|
| **Avro** | Binary | Excellent (backward/forward/full) | Optional | **Kafka events, data lakes** |
| **Protobuf** | Binary | Good | Required | High-performance RPC, streaming |
| **JSON Schema** | Text | Weak | Optional | REST APIs, human-readable debugging |

### Recommendation for Wallet: Avro

Avro is the best choice for the Wallet project because:
1. **Schema evolution is first-class**: Adding fields with defaults is trivial
2. **No code generation required**: You can use `GenericRecord` or generate POJOs
3. **Compact binary format**: Smaller messages than JSON, faster than Protobuf for Kafka
4. **Industry standard for Kafka**: Most Kafka tutorials and tools assume Avro

### Avro Schema Example

```json
{
  "type": "record",
  "name": "TransferCompletedEvent",
  "namespace": "com.wallet.events",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "walletId", "type": "string"},
    {"name": "amount", "type": "double"},
    {"name": "sourceWalletId", "type": "string"},
    {"name": "destinationWalletId", "type": "string"},
    {"name": "timestamp", "type": {"type": "long", "logicalType": "timestamp-millis"}}
  ]
}
```

### Schema Evolution Rules

| Compatibility Mode | Meaning | Upgrade Strategy |
|-------------------|---------|------------------|
| **BACKWARD** | New schema reads old data | Consumers upgrade first |
| **FORWARD** | Old schema reads new data | Producers upgrade first |
| **FULL** | Both backward and forward | Coordinated upgrade |
| **BACKWARD_TRANSITIVE** | Backward + all versions compatible | **Recommended for Wallet** |

### Schema Evolution Example

**V1:**
```json
{"name": "amount", "type": "double"}
```

**V2 (add field with default — backward compatible):**
```json
{"name": "amount", "type": "double"},
{"name": "currency", "type": "string", "default": "BRL"}
```

**V3 (remove field — NOT backward compatible, requires FORWARD mode):**
```json
// Remove 'currency' field — old consumers will ignore it, new consumers won't see it
```

### Spring Kafka + Schema Registry Configuration

```properties
# Schema Registry URL
spring.kafka.producer.value.serializer=io.confluent.kafka.serializers.KafkaAvroSerializer
spring.kafka.consumer.value.deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer

# Schema Registry config
spring.kafka.producer.properties.schema.registry.url=http://localhost:8081
spring.kafka.consumer.properties.schema.registry.url=http://localhost:8081

# Specific avro reading (for generated POJOs)
spring.kafka.consumer.properties.specific.avro.reader=true
```

### Docker Compose with Schema Registry

```yaml
services:
  schema-registry:
    image: confluentinc/cp-schema-registry:latest
    environment:
      SCHEMA_REGISTRY_HOST_NAME: schema-registry
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka:9092
      SCHEMA_REGISTRY_COMPATIBILITY_LEVEL: backward
    ports:
      - "8081:8081"
```

---

## 6. Dead Letter Patterns & Error Handling

### The Problem

In Kafka, if a consumer throws an exception, the message will be redelivered indefinitely (with at-least-once semantics). This blocks the partition and starves the consumer group.

### Solution: Dead Letter Topic Pattern

Route poison pills (messages that consistently fail) to a dedicated topic for later inspection:

```java
@Configuration
public class KafkaErrorConfig {
    
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        
        // After 3 retries, send to dead letter topic
        DeadLetterPublishingRecoverer recoverer = 
            new DeadLetterPublishingRecoverer(kafkaTemplate, 
                (record, exception) -> new TopicPartition("wallet-events-dlt", record.partition()));
        
        // Retry 3 times with 1-second backoff
        FixedBackOff fixedBackOff = new FixedBackOff(1000L, 3L);
        
        return new DefaultErrorHandler(recoverer, fixedBackOff);
    }
}
```

### Consumer with Error Handler

```java
@KafkaListener(
    topics = "wallet-events",
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(TransferCompletedEvent event, Acknowledgment ack) {
    try {
        notificationService.sendTransferNotification(event);
        ack.acknowledge();  // Manual commit after successful processing
    } catch (Exception e) {
        // Error handler will retry 3 times, then send to DLT
        throw e;
    }
}
```

### Monitoring the Dead Letter Topic

Set up a separate consumer or use Kafka CLI tools to monitor the DLT:

```bash
# Check dead letter messages
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic wallet-events-dlt \
  --from-beginning
```

---

## 7. Monitoring & Observability

### Key Metrics to Monitor

| Metric | JMX Name | Alert Threshold |
|--------|----------|-----------------|
| **Consumer lag** | `kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*:records-lag-max` | > 10,000 |
| **Under-replicated partitions** | `kafka.server:type=replica-manager,name=UnderReplicatedPartitions` | > 0 |
| **Failed produce requests** | `kafka.server:type=broker-topic-metrics,topic=*:FailedProduceRequestsPerSec` | > 0 |
| **Request queue size** | `kafka.network:type=request-metrics,name=RequestQueueSize` | > 100 |
| **Leader elections** | `kafka.controller:type=kafka-controller,name=LeaderElectionsPerSec` | Spike |
| **Bytes in/out** | `kafka.server:type=broker-topic-metrics,topic=*:BytesInPerSec` | Baseline deviation |

### Prometheus + Grafana Stack

Standard monitoring stack for Kafka:

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"
  
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
  
  jmx-exporter:
    # Attach to Kafka broker JVM via JMX
    image: jmx-exporter:latest
```

### Pre-built Grafana Dashboards

Use these community dashboards for Kafka monitoring:
- **Kafka 0.11.0+ Metrics** (Dashboard ID: 759)
- **Kafka Consumer Lag** (Dashboard ID: 12059)

### Consumer Lag Calculation

Consumer lag = `log_end_offset - log_begin_offset` for each partition. High lag means consumers are falling behind producers.

```bash
# Check consumer lag
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group wallet-notification-group
```

---

## 8. Spring Kafka Integration Patterns

### Event Publishing Pattern (Transfer → Kafka)

Publish events after successful DB transaction using `@TransactionalEventListener`:

```java
@Service
public class TransferService {
    
    private final WalletRepository walletRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public TransferResult transfer(UUID sourceId, UUID destId, BigDecimal amount) {
        // 1. Lock wallets (optimistic/pessimistic)
        Wallet source = walletRepository.findByIdForUpdate(sourceId);
        Wallet dest = walletRepository.findByIdForUpdate(destId);
        
        // 2. Validate and update balances
        source.debit(amount);
        dest.credit(amount);
        
        // 3. Record transactions
        transactionRepository.saveAll(createTransactions(source, dest, amount));
        
        // 4. Publish domain event (after commit)
        eventPublisher.publishEvent(new TransferCompletedEvent(
            sourceId, destId, amount, Instant.now()
        ));
        
        return new TransferResult(source.getBalance(), dest.getBalance());
    }
}

// Listener that publishes to Kafka AFTER transaction commits
@Component
public class TransferEventPublisher {
    
    private final KafkaTemplate<String, TransferCompletedEvent> kafkaTemplate;
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishToKafka(TransferCompletedEvent event) {
        kafkaTemplate.send(
            "wallet-events",
            event.getSourceWalletId().toString(),  // key = wallet_id for ordering
            event
        );
    }
}
```

**Why `AFTER_COMMIT`?** If the DB transaction rolls back, the Kafka message is never sent. This prevents orphaned events.

### Consumer with Manual Acknowledgment

```java
@Configuration
public class KafkaConsumerConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransferCompletedEvent> 
            kafkaListenerContainerFactory(ConsumerFactory<String, TransferCompletedEvent> consumerFactory) {
        
        var factory = new ConcurrentKafkaListenerContainerFactory<String, TransferCompletedEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);  // Match partition count
        factory.setCommonErrorHandler(errorHandler());
        
        return factory;
    }
}

@Service
public class NotificationConsumer {
    
    @KafkaListener(topics = "wallet-events", groupId = "wallet-notification-group")
    public void handle(TransferCompletedEvent event, Acknowledgment ack) {
        try {
            notificationService.sendTransferNotification(event);
            ack.acknowledge();  // Commit offset only after successful processing
        } catch (Exception e) {
            // Let error handler retry, then send to DLT
            throw e;
        }
    }
}
```

### Testing with Testcontainers

```java
@Testcontainers
class KafkaIntegrationTest {
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest")
        .withTag("7.5.0"));
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    
    @Test
    void shouldPublishAndConsumeTransferEvent() {
        // Given
        TransferCompletedEvent event = new TransferCompletedEvent(...);
        
        // When
        kafkaTemplate.send("wallet-events", walletId.toString(), event);
        
        // Then — verify consumer received the event
        assertThat(testListener.receivedEvents()).hasSize(1);
    }
}
```

---

## 9. Kafka 4.0 New Features (2025)

Kafka 4.0 was released in March 2025 with significant improvements:

### KIP-848: Next-Gen Consumer Rebalance Protocol (NGCRP)

The new rebalance protocol is **up to 20x faster** than the classic protocol:
- More stable than CooperativeSticky
- Better performance with slow/unresponsive consumers
- New server-side assignors: `UniformAssignor` (default) and `RangeAssignor`

```properties
# New configuration in Kafka 4.0
group.consumer.assignors=uniform  # or range
```

### KRaft Improvements

KRaft mode (no Zookeeper) is now the **only** mode in Kafka 4.0:
- Zookeeper support completely removed
- Simplified configuration and operations
- Better crash recovery

### Other Notable Changes

- **Improved transaction handling**: Faster transaction commits
- **Better disk space management**: Automatic log cleanup
- **Enhanced security**: Improved SASL/SSL configurations

---

## Summary: What to Implement for Wallet MVP

Based on the above, here's the **minimum viable Kafka setup** for the Wallet project that still demonstrates advanced knowledge:

| Concept | Implementation | Complexity |
|---------|---------------|------------|
| **Idempotent producer** | `enable.idempotence=true`, `acks=all` | Low |
| **Manual offset commit** | `enable-auto-commit=false` + `ack.acknowledge()` | Low |
| **Partition key = wallet_id** | `kafkaTemplate.send(topic, walletId, event)` | Low |
| **Error handler with DLT** | `DefaultErrorHandler` + dead letter topic | Medium |
| **Transactional event publishing** | `@TransactionalEventListener(AFTER_COMMIT)` | Medium |
| **Consumer group with CooperativeSticky** | `CooperativeStickyAssignor` | Low |
| **Schema Registry with Avro** | KafkaAvroSerializer + backward compatibility | Medium |
| **Testcontainers for Kafka** | `KafkaContainer` in integration tests | Medium |
| **Monitoring with consumer lag** | CLI check or Prometheus/Grafana | Medium |

### What to Skip for MVP

- Full EOS with `transactional.id` — overkill for notifications
- Custom partitioners — default hashing is sufficient
- Schema Registry — use JSON serialization for MVP, add Schema Registry as a "future evolution" ADR
- Prometheus/Grafana — mention in documentation as production requirement

---

## References

- [Kafka Exactly-Once Semantics Guide](https://activewizards.com/blog/kafka-exactly-once-semantics-guide)
- [Kafka Consumer Group Rebalancing](https://www.lydtechconsulting.com/blog/kafka-consumer-group-rebalance---part-1-of-2)
- [Kafka Partition Strategy](https://www.confluent.io/learn/kafka-partition-strategy)
- [Schema Registry: Avro, Protobuf, JSON Schema](https://activewizards.com/blog/the-importance-of-schema-registry-in-kafka)
- [Kafka Monitoring Guide](https://activewizards.com/blog/kafka-monitoring-key-metrics-guide)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/)
- [Kafka 4.0 Release Notes](https://kafka.apache.org/protocol.html)
- [KIP-848: Next-Gen Consumer Rebalance Protocol](https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+Next-generation+consumer+rebalance+protocol)
