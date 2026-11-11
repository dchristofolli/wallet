# ADR-002: Architectural Style

## Status
Accepted

## Date
2026-11-07

## Context
The Wallet project needs an architecture that allows demonstrating advanced technical skills, but is simple enough to be implemented in 60 days by a single person. The architecture must support future evolution, including async messaging and possible split into microservices.

## Decision
Adopt **Modular Monolith** as the initial architectural style, with well-defined modules that can evolve into microservices later.

## Options Considered

### Option A: Modular Monolith (Chosen ✅)
Single application divided into modules with clear responsibilities and minimal coupling.

**Pros:**
- Operational simplicity: one application to deploy and debug
- Shared database simplifies ACID transactions
- Lower infrastructure complexity for the MVP
- Easy to test locally without Docker Compose
- Allows gradual evolution to microservices
- Focus on what matters: data consistency and concurrency

**Cons:**
- Risk of coupling between modules without discipline
- Monolithic deploy: changes in one module require full rebuild
- Less flexibility to scale components individually

### Option B: Microservices from the start
Each domain as an independent service with separate deployment.

**Pros:**
- Granular scalability per service
- Fault isolation between services
- Independent teams can work on different services
- Demonstration of microservices patterns

**Cons:**
- High operational complexity for MVP (service discovery, API gateway, circuit breakers)
- Complex distributed transactions (Saga, 2PC)
- Communication overhead between services
- Difficulty with debugging and local testing
- Can distract from the main focus: ACID consistency

### Option C: Serverless
Functions as a service (AWS Lambda, Azure Functions) for each operation.

**Pros:**
- Zero infrastructure to manage
- Automatic scalability
- Pay-per-use cost model

**Cons:**
- Cold starts affect latency of financial operations
- Difficulty maintaining transactional state between functions
- Vendor lock-in with cloud provider
- Complexity of local testing
- Not suitable for demonstrating traditional architecture skills

## Consequences

### Positive
- Full focus on critical non-functional requirements (ACID, concurrency)
- Clear documentation of trade-offs via ADRs
- Well-defined modules facilitate future evolution
- Demonstration of architectural maturity by justifying the choice
- Ability to extract modules into microservices when needed

### Negative
- Need for discipline to keep modules decoupled
- Constant documentation of dependencies between modules
- Risk of "distributed monolith" if evolving to microservices without planning

## Proposed Module Structure

```
com.wallet
├── wallet          # Wallet domain: creation, query, balance
├── payment         # Payment domain: transfers between wallets
├── notification    # Notification domain (async, future)
└── cashback        # Cashback domain (async, future)
```

## Microservice Extraction Criteria
A module should be extracted into a microservice when:
1. Independent scaling is needed (e.g., notifications overload the system)
2. The module has an independent deploy cycle
3. The team grows and needs development isolation
4. The module has different compliance or security requirements

## References
- [Building Modular Monoliths](https://www.milanjovanovic.tech/blog/what-is-a-modular-monolith)
- [Sam Newman - Monoliths](https://samnewman.io/blog/2014/02/17/monoliths-are-perfect/)
- [Martin Fowler - Monolithic Architecture](https://martinfowler.com/bliki/MonolithicApplicationArchitecture.html)
