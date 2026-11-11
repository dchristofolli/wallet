# ADR-006: Build Tool Choice

## Status
Accepted

## Date
2026-11-11

## Context
O projeto Wallet precisa de uma ferramenta de build para gerenciar dependências, compilação, testes e empacotamento. A escolha impacta a curva de aprendizado, velocidade de build, flexibilidade de configuração e alinhamento com o ecossistema Spring Boot.

## Decision
Escolher **Gradle com Groovy DSL** como ferramenta de build para o projeto Wallet.

## Options Considered

### Option A: Gradle com Groovy DSL (Escolhido ✅)
Build tool moderno com Domain Specific Language baseada em Groovy.

**Prós:**
- Padrão consolidado do ecossistema Spring Boot
- Builds mais rápidos com incremental build e cache
- DSL concisa e expressiva com autocomplete no IDE (IntelliJ)
- Configuração mais legível e manutenível que XML verboso
- Comunidade open-source Java migrou massivamente para Gradle
- Documentação oficial do Spring usa Gradle como exemplo primário
- Groovy DSL é amplamente documentado e tem mais exemplos na web que Kotlin DSL

**Contras:**
- Curva de aprendizado maior para quem vem de Maven
- Build scripts são código, podem esconder complexidade se mal escritos
- Groovy é dynamically typed, menos segurança estática que Kotlin DSL

### Option B: Maven
Build tool tradicional baseado em XML com convenção sobre configuração.

**Prós:**
- XML explícito, fácil de ler a estrutura de dependências
- Mais comum em empresas tradicionais brasileiras
- Curva de aprendizado baixa
- Convenções rígidas reduzem decisões arbitrárias

**Contras:**
- Boilerplate XML verboso
- Builds mais lentos sem incremental build nativo
- Menos flexível em customizações complexas
- Configurações condicionais são difíceis em XML

## Consequences

### Positive
- Alinhamento com o padrão moderno do Spring Boot
- Builds incrementais aceleram o ciclo de desenvolvimento
- Autocomplete do Groovy DSL no IntelliJ reduz erros de digitação
- Configuração mais concisa facilita a leitura do build
- Mais exemplos e documentação na web comparado a Kotlin DSL

### Negative
- Requer aprendizado da sintaxe Groovy DSL
- Build scripts podem esconder complexidade para iniciantes
- Groovy é dynamically typed, menos segurança estática que Kotlin DSL

## Usage Examples

### build.gradle (exemplo de estrutura)
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.dchristofolli.wallet'
version = '0.0.1-SNAPSHOT'

java {
    sourceCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:testcontainers'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

## References
- [Gradle Documentation](https://docs.gradle.org/)
- [Groovy DSL](https://docs.gradle.org/current/userguide/groovy_builds.html)
- [Spring Boot with Gradle](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#build-tool)
- [Spring Initializr](https://start.spring.io/)
