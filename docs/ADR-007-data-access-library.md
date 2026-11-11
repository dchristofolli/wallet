# ADR-007: Data Access Library

## Status
Accepted

## Date
2026-11-11

## Context
O Spring Initializr oferece duas opções de acesso a dados no ecossistema Spring: `spring-boot-starter-data-jpa` (JPA/Hibernate) e `spring-boot-starter-data-jdbc` (Spring Data JDBC). Ambas fornecem repositories, mas com filosofias e recursos diferentes.

## Decision
Escolher **Spring Data JPA com Hibernate** para o projeto Wallet.

## Options Considered

### Spring Data JPA (Hibernate) — Escolhido ✅
ORM completo com cache, lazy loading, dirty tracking e lock otimista nativo (`@Version`).

**Prós:** Lock otimista nativo essencial para o contrato de API (409 Conflict), dirty tracking reduz boilerplate em transações, domina o mercado de vagas Java no Brasil, ecossistema maduro de testes e documentação.

**Contras:** Curva de aprendizado maior, comportamentos de ORM (cache, N+1) que exigem entendimento, overhead vs JDBC puro.

### Spring Data JDBC
Lightweight, sem ORM overhead, mapeamento direto e previsível.

**Prós:** Simplicidade, sem surpresas de cache ou lazy loading, performance superior em operações bulk.

**Contras:** Sem lock otimista nativo (implementação manual necessária), sem dirty tracking (UPDATE explícito), adoção mínima no mercado, poucos recursos de aprendizado.

## Consequences
- O lock otimista via `@Version` do Hibernate implementa diretamente o controle de concorrência previsto no OpenAPI (409 Conflict).
- O dirty tracking simplifica o código do service de transferência, onde `wallet.setBalance()` gera UPDATE automático dentro de `@Transactional`.
- A escolha alinha com o que o mercado de vagas Java/Spring Boot no Brasil exige, reforçando o projeto como portfólio técnico.

## References
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/docs/current/reference/)
- [Spring Data JDBC Reference](https://docs.spring.io/spring-data/jdbc/docs/current/reference/)
