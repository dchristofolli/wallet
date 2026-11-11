# ADR-004: Contract-First for APIs

## Status
Accepted

## Date
2026-11-07

## Context
REST API development requires a clear contract between producer and consumer. In the context of the Wallet project, it is essential to define endpoints, data schemas, validations, and response codes before implementation to ensure consistency and facilitate parallel development.

## Decision
Adopt a **Contract-First** approach using **OpenAPI 3.0** as the source of truth for API development.

## Options Considered

### Option A: OpenAPI 3.0 - Contract-First (Chosen ✅)
Defines the API contract first (YAML/JSON), then implements the code based on the contract.

**Pros:**
- Contract as source of truth facilitates parallel development
- Automatic and always-updated documentation (Swagger UI)
- Client code generation from the contract
- Automatic request/response validation
- Easy to review and discuss with stakeholders before coding
- Mature tools: Swagger Editor, OpenAPI Generator
- Facilitates contract testing (Pact, Dredd)

**Cons:**
- Dual maintenance: YAML contract + Java code
- Risk of contract diverging from implementation without discipline
- Learning curve for OpenAPI 3.0

### Option B: Annotation-First (Spring MVC + SpringDoc)
Defines the API directly in Java code with annotations, generates documentation automatically.

**Pros:**
- Code as source of truth, no dual maintenance
- @Operation, @Schema annotations directly in code
- SpringDoc OpenAPI generates documentation automatically
- Less boilerplate

**Cons:**
- Difficult to discuss the contract with stakeholders before coding
- Documentation only exists after implementation
- Code changes silently break the contract
- Less control over documentation structure

### Option C: gRPC/Protobuf
Defines services and messages in .proto files, generates code in multiple languages.

**Pros:**
- Superior performance with Protocol Buffers
- Multi-language code generation
- Strong contract with compile-time validation

**Cons:**
- Less suitable for public REST APIs
- Smaller ecosystem than REST/OpenAPI
- Additional complexity for the MVP
- Does not demonstrate REST skills, the project's focus

## Consequences

### Positive
- Clear contract before implementation reduces rework
- Swagger UI available for manual testing and demonstration
- Ability to generate clients in multiple languages
- Facilitates automated contract testing
- Documentation always available for API consumers

### Negative
- Need to keep contract and implementation in sync
- Initial overhead of learning OpenAPI 3.0
- Additional toolchain for code generation

## Proposed Workflow

1. **Define contract** in `docs/api/openapi.yaml`
2. **Review contract** with stakeholders (if applicable)
3. **Generate code** or implement manually based on the contract
4. **Validate implementation** against the contract with testing tools
5. **Publish documentation** via Swagger UI at `/swagger-ui.html`

## References
- [OpenAPI Specification 3.0](https://spec.openapis.org/oas/v3.0.3)
- [SpringDoc OpenAPI](https://springdoc.org/)
- [OpenAPI Generator](https://openapi-generator.tech/)
