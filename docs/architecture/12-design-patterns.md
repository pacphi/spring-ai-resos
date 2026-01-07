# Design Patterns

This document catalogs the architectural and design patterns demonstrated in the Spring AI ResOs project.

## OpenAPI-First Pattern

**Category**: Architectural Pattern
**ADR**: [001-OpenAPI-First](adr/001-openapi-first.md)

### Intent

Use OpenAPI specification as the single source of truth for API contracts, driving code generation for both client and server implementations.

### Motivation

**Problems Solved**:
- API contract drift between client and server
- Manual client code synchronization
- Duplicate model definitions
- Inconsistent validation rules

**Benefits**:
- Compile-time contract verification
- Automated code generation
- Living documentation (Swagger UI)
- Version control for API changes

### Implementation

```
OpenAPI Spec (YAML)
    ↓ OpenAPI Generator
Generated HTTP Client (Java)
    ↓ Maven Unpack
POJOs (source)
    ↓ EntityGenerator
JDBC Entities
    ↓ SchemaCreator
Database Schema
```

**Key Files**:
- `client/src/main/resources/openapi/resos-openapi-modified.yml` - API specification
- `client/pom.xml` - OpenAPI Generator configuration
- `client/target/generated-sources/` - Generated client code

### Applicability

**Use When**:
- Building client-server applications
- Need strong API contracts
- Multiple consumers of same API
- Want automated client generation

**Don't Use When**:
- Simple internal APIs (overhead not justified)
- Rapidly changing APIs (spec maintenance burden)
- GraphQL or other non-REST paradigms

### Related Patterns

- **Code Generation Pipeline** (this project)
- **Entity Transformation Pattern** (below)

---

## Entity Transformation Pattern

**Category**: Code Generation Pattern
**ADR**: [001-OpenAPI-First](adr/001-openapi-first.md)

### Intent

Transform API data transfer objects (DTOs) into persistence entities automatically, avoiding duplicate class definitions.

### Motivation

**Problem**: Generated POJOs have Jackson annotations, but Spring Data JDBC needs different annotations.

**Solutions Considered**:
1. **Manual Entities**: Write entities separately (duplicate code)
2. **Dual Annotations**: Entities have both Jackson and JDBC annotations (messy)
3. **Runtime Transformation**: Use reflection (performance cost)
4. **Build-Time Transformation**: Parse and transform source code (chosen)

### Implementation

**EntityGenerator** (JavaParser):
```java
public void transform(File inputDir, File outputDir) {
    // For each Java file in inputDir
    for (File file : findJavaFiles(inputDir)) {
        // Parse source code
        CompilationUnit cu = JavaParser.parse(file);

        // Rename class: Customer → CustomerEntity
        cu.getClassByName("Customer").ifPresent(cls -> {
            cls.setName("CustomerEntity");

            // Remove Jackson annotations
            cls.getAnnotationByName("JsonPropertyOrder").ifPresent(Node::remove);

            // Add Spring Data JDBC annotations
            cls.addAnnotation(createTableAnnotation("customer"));

            // Process fields
            cls.getFields().forEach(field -> {
                // Remove @JsonProperty
                field.getAnnotationByName("JsonProperty").ifPresent(Node::remove);

                // Add @Id or @Column
                if (field.getNameAsString().equals("id")) {
                    field.addAnnotation("Id");
                }
                field.addAnnotation(createColumnAnnotation(field.getNameAsString()));
            });

            // Add conversion methods
            cls.addMethod("toPojo", Modifier.Keyword.PUBLIC);
            cls.addMethod("fromPojo", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);
        });

        // Write to outputDir
        Files.write(outputDir.toPath().resolve(file.getName()), cu.toString().getBytes());
    }
}
```

**Example**:

**Input** (OpenAPI POJO):
```java
public class Customer {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    @NotNull
    private String name;
}
```

**Output** (JDBC Entity):
```java
@Table("customer")
public class CustomerEntity {
    @Id
    @Column("id")
    private UUID id;

    @Column("name")
    private String name;

    public Customer toPojo() { /* conversion */ }
    public static CustomerEntity fromPojo(Customer pojo) { /* conversion */ }
}
```

### Applicability

**Use When**:
- Have generated DTOs (OpenAPI, Avro, Protobuf)
- Need different annotations for persistence
- Want single source of truth
- Build-time transformation acceptable

**Don't Use When**:
- DTOs and entities significantly different
- Complex transformation logic needed
- Runtime flexibility required

---

## Dynamic Schema Generation Pattern

**Category**: Persistence Pattern
**ADR**: [003-Dynamic-Liquibase](adr/003-dynamic-liquibase.md)

### Intent

Generate database schema migrations from entity annotations at runtime, eliminating manual SQL and Liquibase changelog writing.

### Motivation

**Problem**: Three sources of truth - entities, Liquibase changelogs, actual schema

**Traditional Approach**:
1. Write entity: `@Table("customer") class CustomerEntity`
2. Write Liquibase: `createTable: customer`
3. Apply to database

**Issues**: Drift between entity and schema, manual synchronization

### Implementation

**SchemaCreator Algorithm**:
```java
@PostConstruct
public void generateSchemas() {
    // 1. Scan for @Table entities
    List<Class<?>> entities = scanForTableEntities();

    // 2. Build dependency graph (foreign keys)
    Map<String, Set<String>> depGraph = buildDependencyGraph(entities);

    // 3. Topological sort (parent tables before children)
    List<String> sortedTables = topologicalSort(depGraph);

    // 4. Generate Liquibase YAML for each table
    for (String tableName : sortedTables) {
        generateChangelogYaml(tableName);
    }

    // 5. Update master changelog
    updateMasterChangelog(sortedTables);
}
```

**Type Mapping**:
```java
private String mapType(Class<?> javaType) {
    return switch (javaType.getSimpleName()) {
        case "UUID" -> "uuid";
        case "String" -> "varchar(255)";
        case "OffsetDateTime" -> isPostgreSQL() ? "timestamp with time zone" : "timestamp";
        case "BigDecimal" -> "decimal(19,2)";
        case "Integer" -> "integer";
        case "Boolean" -> "boolean";
        default -> javaType.isEnum() ? "varchar(50)" : "varchar(255)";
    };
}
```

### Applicability

**Use When**:
- Using Spring Data JDBC (or JPA)
- Want zero-boilerplate schema management
- Entities are authoritative
- Development environment (not production)

**Don't Use When**:
- Complex schema features needed (triggers, views, custom indexes)
- Production deployments (use pre-generated changelogs)
- Database schema drives entity design (database-first)

### Challenges

| Challenge | Solution |
|-----------|----------|
| JAR execution (no write to classpath) | Use temp directory with system property |
| Circular dependencies | Topological sort with cycle detection |
| Complex constraints | Manual patch changelogs |
| Performance (startup time) | Acceptable for dev (~300ms for 20 entities) |

---

## OAuth2 Client Credentials Pattern

**Category**: Security Pattern

### Intent

Authenticate service-to-service communication using OAuth2 client credentials grant, with automatic token management.

### Motivation

**Problems Solved**:
- Hard-coded API keys (security risk)
- Manual token refresh (complexity)
- Token expiration handling (errors)
- Credential rotation (deployment issues)

### Implementation

**Configuration**:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          backend-client:
            client-id: mcp-server
            client-secret: ${SECRET}
            authorization-grant-type: client_credentials
            scope:
              - backend.read
              - backend.write
        provider:
          backend-client:
            token-uri: http://localhost:8080/oauth2/token
```

**Bean Setup**:
```java
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(...) {
    OAuth2AuthorizedClientProvider provider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .refreshToken()
            .build();

    var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(...);
    manager.setAuthorizedClientProvider(provider);
    return manager;
}

@Bean
public RestClient restClient(OAuth2AuthorizedClientManager manager) {
    return RestClient.builder()
        .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
            manager,
            "backend-client"  // Registration ID
        ))
        .build();
}
```

**Automatic Behavior**:
- First request: Fetch token from auth server
- Subsequent requests: Reuse cached token
- Token expiry: Automatically refresh
- All transparent to application code

### Applicability

**Use When**:
- Microservices architecture
- Service-to-service authentication
- OAuth2 infrastructure available
- Need centralized credential management

**Don't Use When**:
- User-based authentication (use authorization_code instead)
- Simple applications (overhead not justified)
- No OAuth2 server available

---

## Streaming Response Pattern

**Category**: Communication Pattern

### Intent

Stream LLM responses token-by-token to the browser using Server-Sent Events (SSE), providing real-time feedback.

### Motivation

**Problem**: LLM responses take 2-5 seconds to generate fully

**Traditional Approach**: Wait for complete response, then send
- **User Experience**: Long wait, appears frozen

**Streaming Approach**: Send tokens as generated
- **User Experience**: Immediate feedback, feels responsive

### Implementation

**Backend** (ChatService with Flux → Callback Bridge):
```java
public void streamResponse(
        String question,
        Consumer<String> onToken,       // Called for each token
        Runnable onComplete,            // Called when done
        Consumer<Throwable> onError) {  // Called on error

    // Get reactive stream from ChatClient
    Flux<String> stream = chatClient.prompt()
        .user(question)
        .stream()
        .content();

    // Subscribe with callbacks (bridges reactive to imperative)
    stream.subscribe(
        onToken::accept,
        onError::accept,
        onComplete::run
    );
}
```

**Controller** (SseEmitter):
```java
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody Request req) {
    SseEmitter emitter = new SseEmitter(300_000L);

    chatService.streamResponse(
        req.question(),
        token -> emitter.send(SseEmitter.event().data(token)),
        emitter::complete,
        emitter::completeWithError
    );

    return emitter;
}
```

**Frontend** (Fetch API):
```javascript
const response = await fetch('/api/chat', {
  method: 'POST',
  body: JSON.stringify({ question })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  const chunk = decoder.decode(value);
  // Parse SSE format and update UI
}
```

### Applicability

**Use When**:
- Long-running operations (AI, data processing)
- Real-time feedback important
- User experience priority
- WebMVC (servlet) stack

**Alternatives**:
- WebSockets (bidirectional, more complex)
- Long polling (inefficient)
- HTTP/2 Server Push (deprecated)

---

## Repository Resolver Pattern

**Category**: Dependency Injection Pattern

### Intent

Dynamically resolve Spring Data repositories at runtime based on entity class type.

### Motivation

**Problem**: CSV seeding needs to persist many entity types, but repositories are type-specific.

**Traditional Approach**: Hard-code repository for each entity type
```java
if (entityClass == CustomerEntity.class) {
    customerRepository.save(entity);
} else if (entityClass == BookingEntity.class) {
    bookingRepository.save(entity);
}
// ... 20+ entity types
```

**Issues**: Not extensible, repetitive code

### Implementation

**RepositoryResolver**:
```java
@Component
public class RepositoryResolver {

    private final ApplicationContext applicationContext;

    public <T, ID> CrudRepository<T, ID> resolveRepository(Class<T> entityClass) {
        // Get all CrudRepository beans
        Map<String, CrudRepository> repositories =
            applicationContext.getBeansOfType(CrudRepository.class);

        // Find repository for entity class
        for (CrudRepository<?, ?> repository : repositories.values()) {
            Class<?> repositoryEntityClass = extractEntityClass(repository);

            if (repositoryEntityClass.equals(entityClass)) {
                return (CrudRepository<T, ID>) repository;
            }
        }

        throw new IllegalArgumentException(
            "No repository found for entity: " + entityClass.getName()
        );
    }

    private Class<?> extractEntityClass(CrudRepository<?, ?> repository) {
        // Use reflection to get generic type parameter
        ParameterizedType type = (ParameterizedType) repository.getClass()
            .getGenericInterfaces()[0];
        return (Class<?>) type.getActualTypeArguments()[0];
    }
}
```

**Usage** (DataSeeder):
```java
for (String filename : csvFiles) {
    EntityMapper<?> mapper = findMapper(filename);
    List<?> entities = parseAndMap(filename, mapper);

    // Resolve repository dynamically
    Class<?> entityClass = mapper.getEntityClass();
    CrudRepository repository = repositoryResolver.resolveRepository(entityClass);

    // Persist all
    repository.saveAll(entities);
}
```

### Applicability

**Use When**:
- Generic data processing (batch imports, exports)
- Plugin architectures (new entities added dynamically)
- Framework code (don't know entity types upfront)

**Don't Use When**:
- Simple CRUD (direct repository injection easier)
- Type safety critical (generics can hide errors)

---

## Annotation-Driven Discovery Pattern

**Category**: Registration Pattern

### Intent

Use custom annotations to enable automatic discovery and registration of components without manual configuration.

### Example: @CsvEntityMapper

**Annotation Definition**:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component  // Makes it a Spring bean
public @interface CsvEntityMapper {
    String value();  // CSV filename (without .csv extension)
}
```

**Usage**:
```java
@CsvEntityMapper("users")
public class AppUserMapper implements EntityMapper<AppUserEntity> {

    @Override
    public AppUserEntity mapFromCsv(String[] line) {
        // Mapping logic
    }

    @Override
    public Class<AppUserEntity> getEntityClass() {
        return AppUserEntity.class;
    }
}
```

**Discovery** (DataSeeder):
```java
Map<String, EntityMapper<?>> mappers =
    applicationContext.getBeansOfType(EntityMapper.class);

for (EntityMapper<?> mapper : mappers.values()) {
    CsvEntityMapper annotation = mapper.getClass()
        .getAnnotation(CsvEntityMapper.class);

    if (annotation != null) {
        String filename = annotation.value();
        mapperRegistry.put(filename, mapper);
    }
}
```

**Benefits**:
- No central registration file
- Add new mapper = just create class with annotation
- Type-safe (implements interface)
- Spring-managed (dependency injection)

### Applicability

**Use When**:
- Plugin architecture
- Extensible systems
- Many similar components (converters, validators, mappers)

**Examples in Spring**:
- `@RestController` (auto-discovered)
- `@Repository` (auto-discovered)
- `@Tool` (Spring AI tool discovery)

---

## Callback-Based Streaming Pattern

**Category**: Communication Pattern

### Intent

Bridge reactive streams (Flux) to imperative callback-based API for WebMVC compatibility.

### Motivation

**Problem**: Spring AI returns `Flux<String>`, but WebMVC uses blocking I/O.

**Can't Do**: Return `Flux` directly from WebMVC controller (reactive vs imperative mismatch)

**Solution**: Subscribe to `Flux` with callbacks, send via `SseEmitter`

### Implementation

**Service Layer**:
```java
public void streamResponse(
        String question,
        Consumer<String> onToken,       // For each token
        Runnable onComplete,            // On completion
        Consumer<Throwable> onError) {  // On error

    Flux<String> stream = chatClient.prompt()
        .user(question)
        .stream()
        .content();

    stream.subscribe(onToken, onError, onComplete);
}
```

**Controller Layer**:
```java
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody Request req) {
    SseEmitter emitter = new SseEmitter(300_000L);

    chatService.streamResponse(
        req.question(),
        token -> emitter.send(SseEmitter.event().data(token)),  // onToken
        emitter::complete,                                      // onComplete
        emitter::completeWithError                              // onError
    );

    return emitter;
}
```

**Benefits**:
- Reactive benefits (backpressure) with imperative API
- Type-safe callbacks
- Testable (can mock callbacks)
- Clean separation (service doesn't know about SSE)

### Applicability

**Use When**:
- Need reactive streams in WebMVC
- Streaming responses to browser
- Bridging reactive libraries with imperative code

**Alternatives**:
- `StreamingResponseBody` (lower-level, more control)
- Full WebFlux migration (if entire app can be reactive)

---

## Three-Tier OAuth2 Pattern

**Category**: Security Pattern

### Intent

Layer OAuth2 security across multiple services: Authorization Server → Resource Server → OAuth2 Client.

### Structure

```
┌────────────────────┐
│ Authorization      │  Issues JWT tokens
│ Server             │  Validates credentials
│ (Backend)          │  OIDC provider
└─────────┬──────────┘
          │ Validates JWT
          ↓
┌────────────────────┐
│ Resource Server    │  Protects API endpoints
│ (MCP Server)       │  Validates JWT signatures
└─────────┬──────────┘  OAuth2 Client (calls backend)
          │
          ↓
┌────────────────────┐
│ OAuth2 Client      │  Fetches tokens
│ (MCP Client)       │  Adds Bearer headers
└────────────────────┘  PKCE for users
```

### Participants

1. **Authorization Server**:
   - Issues JWT tokens
   - Validates user credentials
   - Manages OAuth2 clients
   - Provides JWKS endpoint for public keys

2. **Resource Server**:
   - Protects API endpoints with JWT validation
   - Extracts authorities from JWT claims
   - Enforces authorization rules (scopes, roles)

3. **OAuth2 Client**:
   - Obtains access tokens (client credentials or authorization code)
   - Adds Bearer tokens to outbound requests
   - Manages token lifecycle (refresh, expiry)

### Implementation

**Authorization Server**:
```java
@Bean
@Order(1)
public SecurityFilterChain authServerChain(HttpSecurity http) {
    OAuth2AuthorizationServerConfigurer configurer =
        new OAuth2AuthorizationServerConfigurer();

    http.securityMatcher(configurer.getEndpointsMatcher())
        .with(configurer, authServer -> authServer.oidc(Customizer.withDefaults()))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());

    return http.build();
}
```

**Resource Server**:
```java
@Bean
@Order(2)
public SecurityFilterChain resourceServerChain(HttpSecurity http) {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/**")
                .hasAnyAuthority("SCOPE_backend.read", "ROLE_USER")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
}
```

**OAuth2 Client**:
```java
@Bean
public RestClient restClient(OAuth2AuthorizedClientManager manager) {
    return RestClient.builder()
        .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
            manager,
            "client-registration-id"
        ))
        .build();
}
```

### Benefits

- **Centralized Authentication**: One auth server for all services
- **Stateless**: JWT tokens carry all auth info
- **Scalable**: No shared sessions, horizontal scaling easy
- **Revocable**: Database-backed tokens can be revoked
- **Auditable**: Token issuance logged

### Applicability

**Use When**:
- Microservices architecture
- Multiple services need authentication
- Want centralized user management
- Cloud-native applications

**Don't Use When**:
- Monolithic application (simpler auth sufficient)
- Internal-only services (mutual TLS might be better)
- Very high throughput (JWT validation overhead)

---

## Aggregate-Oriented Design Pattern

**Category**: Domain Modeling Pattern (DDD)

### Intent

Model domain entities as aggregates with clear boundaries, using Spring Data JDBC's aggregate root pattern.

### Motivation

**Spring Data JDBC Philosophy**: Aggregates, not object graphs

**Aggregate**: Cluster of domain objects treated as a single unit for data changes

### Example: Booking Aggregate

**Aggregate Root**:
```java
@Table("booking")
public class BookingEntity {  // Aggregate root
    @Id
    private UUID id;

    @Column("guest_id")
    private AggregateReference<CustomerEntity, UUID> guest;  // Reference, not object

    @MappedCollection(idColumn = "booking_id")
    private Set<BookingTableEntity> tables;  // Owned collection

    // Booking owns tables relationship
}
```

**Part of Aggregate**:
```java
@Table("booking_tables")
public class BookingTableEntity {  // Part of booking aggregate
    @Column("booking_id")
    private UUID bookingId;  // Parent FK

    @Column("table_id")
    private AggregateReference<TableEntity, UUID> table;  // Reference to other aggregate
}
```

**Not Part of Aggregate**:
```java
@Table("customer")
public class CustomerEntity {  // Separate aggregate
    @Id
    private UUID id;

    // No reference back to bookings (not in aggregate boundary)
}
```

### Rules

1. **One Repository Per Aggregate**: Only `BookingRepository`, not `BookingTableRepository`
2. **Explicit Loading**: Load `CustomerEntity` explicitly via repository (not automatic)
3. **Transactional Boundary**: Save aggregate = save root + children in one transaction
4. **No Lazy Loading**: All or nothing (load complete aggregate or none)

### Benefits

- **Clear Boundaries**: Explicit ownership
- **Consistency**: Aggregate consistency guaranteed
- **Performance**: No N+1 queries (no lazy loading)
- **Testability**: Aggregates easy to test in isolation

### Applicability

**Use When**:
- Using Spring Data JDBC
- Clear domain boundaries
- Want explicit data loading
- DDD approach

**Don't Use When**:
- Complex object graphs (use JPA instead)
- Need lazy loading (performance critical)
- Unclear aggregate boundaries

---

## Interceptor-Based Cross-Cutting Concerns

**Category**: AOP Pattern

### Examples

#### Logging Interceptor

```java
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        logger.debug("Request: {} {}", request.getMethod(), request.getURI());
        logger.debug("Headers: {}", request.getHeaders());

        long startTime = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long duration = System.currentTimeMillis() - startTime;

        logger.debug("Response: {} in {}ms",
            response.getStatusCode(), duration);

        return response;
    }
}
```

**Usage**:
```java
RestClient.builder()
    .requestInterceptor(new LoggingInterceptor())
    .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(...))
    .build();
```

**Benefits**:
- Separation of concerns
- Reusable across clients
- Composable (multiple interceptors)
- Testable (can mock)

---

## Configuration Property Pattern

**Category**: Configuration Pattern

### Intent

Externalize configuration using Spring Boot properties with type-safe binding.

### Example: CSV Properties

```java
@ConfigurationProperties(prefix = "app.seed.csv")
public class CsvProperties {

    private String basePath = "./seed-data";
    private List<String> files = new ArrayList<>();

    // Getters and setters
}
```

**Configuration** (application.yml):
```yaml
app:
  seed:
    csv:
      base-path: ./seed-data
      files:
        - authorities.csv
        - users.csv
```

**Usage**:
```java
@Configuration
@EnableConfigurationProperties(CsvProperties.class)
public class CsvConfig {

    private final CsvProperties csvProperties;

    // Inject and use
}
```

**Benefits**:
- Type-safe (not String properties)
- IDE autocomplete
- Validation support (`@Valid`, `@NotNull`)
- Documentation (via `@ConfigurationProperties`)

---

## Summary

| Pattern | Category | Key Benefit |
|---------|----------|-------------|
| **OpenAPI-First** | Architectural | Zero API drift |
| **Entity Transformation** | Code Generation | Zero duplicate entities |
| **Dynamic Schema Generation** | Persistence | Zero manual SQL |
| **OAuth2 Client Credentials** | Security | Automatic token management |
| **Streaming Response** | Communication | Real-time user feedback |
| **Repository Resolver** | Dependency Injection | Extensible data access |
| **Annotation-Driven Discovery** | Registration | Plugin-friendly architecture |
| **Callback-Based Streaming** | Integration | Reactive-imperative bridge |
| **Three-Tier OAuth2** | Security | Defense in depth |
| **Aggregate-Oriented Design** | Domain Modeling | Clear boundaries |

## Related Documentation

- All [ADRs](adr/) - Detailed rationale for architectural decisions
- [Code Generation Pipeline](04-code-generation.md) - OpenAPI and Entity transformation
- [Data Architecture](05-data-architecture.md) - Repository and aggregate patterns
- [Security Architecture](06-security-architecture.md) - OAuth2 patterns
- [MCP Architecture](07-mcp-architecture.md) - Tool callbacks
- [AI Integration](08-ai-integration.md) - Streaming pattern
