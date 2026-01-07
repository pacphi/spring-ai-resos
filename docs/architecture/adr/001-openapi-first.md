# ADR-001: OpenAPI-First Development

## Status
**Accepted** - Implemented in initial architecture

## Context

When building a multi-module system with client-server communication, there's a fundamental challenge of keeping the API contract synchronized between the client code and the server implementation. Traditional approaches include:

1. **Code-First**: Write server code, generate client from server annotations
2. **Manual Client**: Write server code, manually write client code
3. **Contract-First (OpenAPI)**: Write API specification, generate both client and server code

### Problem Statement

This project needed to:
- Generate a type-safe HTTP client for the MCP Server to call the Backend API
- Transform generated POJOs into Spring Data JDBC entities
- Ensure zero drift between API contract and implementation
- Support both local backend (dev) and external ResOs API (production)
- Enable automated testing with contract validation

### Constraints

- Using Spring Boot 4.0.1 and Spring AI 2.0.0-M1
- Need Spring HTTP Interface (not OpenFeign) for better integration
- Must support code generation at build time (Maven)
- OpenAPI spec must be comprehensive enough for entity generation

## Decision

**Use OpenAPI-First development with the OpenAPI specification as the single source of truth for API contracts.**

### Implementation Details

1. **OpenAPI Specification**:
   - File: `client/src/main/resources/openapi/resos-openapi-modified.yml`
   - Modified from original ResOs API specification
   - Comprehensive schema definitions for all entities
   - API endpoints with request/response schemas

2. **Client Generation**:
   - Tool: OpenAPI Generator Maven Plugin
   - Library: `spring-http-interface` (Spring Boot 4 compatible)
   - Output: Type-safe `DefaultApi` interface with model POJOs

3. **Code Generation Pipeline**:
   ```
   OpenAPI Spec → OpenAPI Generator → HTTP Client (POJOs)
   → Maven Unpack → EntityGenerator (JavaParser)
   → JDBC Entities → SchemaCreator → Database Schema
   ```

4. **Configuration** (`client/pom.xml`):
   ```xml
   <plugin>
       <groupId>org.openapitools</groupId>
       <artifactId>openapi-generator-maven-plugin</artifactId>
       <configuration>
           <inputSpec>src/main/resources/openapi/resos-openapi-modified.yml</inputSpec>
           <generatorName>java</generatorName>
           <library>spring-http-interface</library>
           <generateApiTests>false</generateApiTests>
           <configOptions>
               <useJakartaEe>true</useJakartaEe>
           </configOptions>
       </configuration>
   </plugin>
   ```

## Consequences

### Positive

1. **Zero Drift**: API contract and client code always in sync
2. **Type Safety**: Compile-time errors for API mismatches
3. **Documentation**: OpenAPI spec serves as living documentation
4. **Testing**: Contract testing possible with spec validation
5. **Code Reuse**: Generated POJOs serve as input for entity transformation
6. **Swagger UI**: Automatic API explorer from spec
7. **Multi-Backend Support**: Same client works with backend or external ResOs API
8. **Versioning**: Spec changes tracked in git, clear API evolution

### Negative

1. **Build Complexity**: Additional build step (code generation)
2. **Build Time**: Initial builds slower due to code generation
3. **Spec Maintenance**: Must keep OpenAPI spec comprehensive and accurate
4. **Learning Curve**: Developers must understand OpenAPI specification format
5. **Circular Dependency**: Cannot use generated code to define OpenAPI spec
6. **Generated Code Noise**: Large number of generated files in target/

### Neutral

1. **Tool Lock-in**: Dependent on OpenAPI Generator tool and plugins
2. **Spring HTTP Interface**: Newer than OpenFeign, less community examples (but officially supported)

## Alternatives Considered

### Alternative 1: Code-First with Spring Annotations

**Approach**: Write Spring REST controllers with `@RestController`, generate OpenAPI spec from annotations.

**Pros**:
- Simpler build process
- No separate spec file to maintain
- Familiar to Spring developers

**Cons**:
- Server-side logic mixed with API contract
- Generated spec may not be comprehensive
- Hard to generate JDBC entities from controllers
- Cannot easily swap backend implementations

**Rejected Because**: Need to support both internal backend and external ResOs API, which requires client-first approach.

### Alternative 2: Manual Client Implementation

**Approach**: Write server code, manually implement HTTP client.

**Pros**:
- Full control over client implementation
- No code generation tools needed
- Simpler build process

**Cons**:
- High maintenance burden
- Easy to introduce API contract drift
- No compile-time contract verification
- Duplicate effort (server + client code)

**Rejected Because**: Too error-prone and time-consuming for a multi-entity system.

### Alternative 3: GraphQL

**Approach**: Use GraphQL instead of REST, with schema-first development.

**Pros**:
- Type-safe by design
- Flexible queries
- Single endpoint

**Cons**:
- Complete architectural change
- ResOs API is REST-based (no GraphQL)
- More complex tooling
- Not suitable for MCP server integration

**Rejected Because**: Must be compatible with existing ResOs REST API.

## Implementation Notes

### Key Files

| File | Purpose |
|------|---------|
| `client/src/main/resources/openapi/resos-openapi-modified.yml` | API specification |
| `client/pom.xml` | OpenAPI Generator configuration |
| `client/target/generated-sources/` | Generated client code |

### Entity Transformation

Generated POJOs are transformed into JDBC entities:

**OpenAPI Model** → **Generated POJO** → **JDBC Entity**

```java
// Generated POJO (from OpenAPI)
public class Customer {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    @NotNull
    private String name;
}

// JDBC Entity (transformed)
@Table("customer")
public class CustomerEntity {
    @Id
    private UUID id;

    @Column("name")
    private String name;
}
```

### Build Integration

```bash
# Generate client code
mvn generate-sources

# Full build with entity transformation
mvn clean install
```

## Lessons Learned

1. **Spec Completeness Matters**: Incomplete OpenAPI specs lead to incomplete generated code
2. **Type Mapping**: OpenAPI type formats must map correctly to Java types (e.g., `format: uuid` → `UUID`)
3. **Validation**: OpenAPI validation annotations (`@NotNull`, `@Size`) carry through to generated code
4. **Spring HTTP Interface**: Works well but requires understanding of RestClient configuration
5. **Generated Code Review**: Occasionally review generated code to ensure quality

## Related Decisions

- [ADR-002: Spring Data JDBC over JPA](002-spring-data-jdbc.md) - Entities use JDBC, not JPA
- [ADR-003: Dynamic Liquibase Generation](003-dynamic-liquibase.md) - Database schema from entities
- [ADR-004: WebMVC over WebFlux](004-webmvc-over-webflux.md) - Client uses RestClient (WebMVC)

## References

- [OpenAPI Specification 3.0](https://spec.openapis.org/oas/v3.0.0)
- [OpenAPI Generator](https://openapi-generator.tech/)
- [Spring HTTP Interface](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface)
- [ResOs API Documentation](https://documenter.getpostman.com/view/3308304/SzzehLGp?version=latest)

## Decision Date

January 2026 (Initial Architecture)

## Reviewers

- Chris Phillipson (Architect)

## Changelog

| Date | Change |
|------|--------|
| Jan 2026 | Initial decision document |
