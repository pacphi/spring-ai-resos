# Spring Boot 4 Migration Patterns

This document provides a comprehensive guide for migrating to Spring Boot 4, Spring AI 2.0, and related ecosystem updates based on real migration experience.

## Overview

This project successfully migrated to:
- **Spring Boot**: 3.x → 4.0.1
- **Spring AI**: 1.x → 2.0.0-M1
- **Spring Security**: 6.x → 7.0.2
- **Jackson**: 2.x → 3.0.3
- **JUnit**: 5.x → 6.0.0
- **Java**: 21 → 25

**Total Migration Effort**: ~7 hours (Phase 0: WebFlux → WebMVC)

**Reference**: [docs/PHASE_0_LESSONS_LEARNED.md](../../PHASE_0_LESSONS_LEARNED.md)

---

## WebFlux to WebMVC Migration

### Context

**Why Migrate?**:
- MCP security library (`spring-ai-community/mcp-security`) requires WebMVC
- SSE transport deprecated in favor of HTTP Streamable
- OAuth2 integration simpler with WebMVC
- Better Spring Security autoconfiguration support

See [ADR-004: WebMVC over WebFlux](adr/004-webmvc-over-webflux.md) for detailed rationale.

### Artifact Changes

#### MCP Server

**Before**:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
</dependency>
```

**After**:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**Common Mistake**: Using `spring-ai-mcp-server-webmvc-spring-boot-starter` (doesn't exist)
**Correct Name**: `spring-ai-starter-mcp-server-webmvc`

#### MCP Client

**Before**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client-webflux</artifactId>
</dependency>
```

**After**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>  <!-- Note: no -webmvc suffix -->
</dependency>
```

**Note**: The base `spring-ai-starter-mcp-client` is servlet-based (WebMVC compatible)

### Code Pattern Changes

#### Security Configuration

| WebFlux (Reactive) | WebMVC (Servlet) |
|--------------------|------------------|
| `@EnableWebFluxSecurity` | `@EnableWebSecurity` |
| `SecurityWebFilterChain` | `SecurityFilterChain` |
| `ServerHttpSecurity` | `HttpSecurity` |
| `.authorizeExchange()` | `.authorizeHttpRequests()` |
| `.pathMatchers("/api/**")` | `.requestMatchers("/api/**")` |

**Before**:
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/mcp/**").authenticated()
                .anyExchange().permitAll())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

**After**:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/mcp/**").authenticated()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

#### Controller Methods

| WebFlux | WebMVC |
|---------|--------|
| `Mono<ResponseEntity<T>>` | `ResponseEntity<T>` |
| `Flux<String>` | `SseEmitter` (for streaming) |
| `ServerWebExchange` | `HttpServletRequest` / `HttpServletResponse` |
| Return `Mono.just(value)` | Return `value` directly |
| `@AuthenticationPrincipal Principal principal` | Same (unchanged) |

**Before**:
```java
@GetMapping("/user")
public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(
        @AuthenticationPrincipal OidcUser user) {

    if (user == null) {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    Map<String, Object> userInfo = Map.of(
        "name", user.getFullName(),
        "email", user.getEmail()
    );

    return Mono.just(ResponseEntity.ok(userInfo));
}
```

**After**:
```java
@GetMapping("/user")
public ResponseEntity<Map<String, Object>> getCurrentUser(
        @AuthenticationPrincipal OidcUser user) {

    if (user == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Map<String, Object> userInfo = Map.of(
        "name", user.getFullName(),
        "email", user.getEmail()
    );

    return ResponseEntity.ok(userInfo);
}
```

**Change**: Remove `Mono.just()` wrapper, return directly

#### HTTP Clients

| WebFlux | WebMVC |
|---------|--------|
| `WebClient` | `RestClient` |
| `WebClient.Builder` | `RestClient.Builder` |
| `ReactorClientHttpConnector` | `JdkClientHttpRequestFactory` |
| `.bodyValue(obj)` | `.body(obj)` |
| `.retrieve().bodyToMono(T.class)` | `.retrieve().body(T.class)` |

**Before**:
```java
@Bean
public WebClient webClient(WebClient.Builder builder) {
    return builder
        .baseUrl("http://localhost:8080")
        .filter(new OAuth2ClientExchangeFilterFunction(authorizedClientManager))
        .build();
}

// Usage
Mono<Customer> customer = webClient.get()
    .uri("/customers/{id}", customerId)
    .retrieve()
    .bodyToMono(Customer.class);
```

**After**:
```java
@Bean
public RestClient restClient(RestClient.Builder builder,
        OAuth2AuthorizedClientManager authorizedClientManager) {
    return builder
        .baseUrl("http://localhost:8080")
        .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
            authorizedClientManager,
            "mcp-server"
        ))
        .build();
}

// Usage
Customer customer = restClient.get()
    .uri("/customers/{id}", customerId)
    .retrieve()
    .body(Customer.class);
```

#### OAuth2 Managers

| WebFlux | WebMVC |
|---------|--------|
| `ReactiveOAuth2AuthorizedClientManager` | `OAuth2AuthorizedClientManager` |
| `ReactiveClientRegistrationRepository` | `ClientRegistrationRepository` |
| `ServerOAuth2AuthorizedClientExchangeFilterFunction` | `OAuth2ClientHttpRequestInterceptor` |

**Before**:
```java
@Bean
public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
        ReactiveClientRegistrationRepository clientRegistrationRepository,
        ServerOAuth2AuthorizedClientRepository authorizedClientRepository) {

    ReactiveOAuth2AuthorizedClientProvider provider =
        ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build();

    DefaultReactiveOAuth2AuthorizedClientManager manager =
        new DefaultReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientRepository
        );
    manager.setAuthorizedClientProvider(provider);

    return manager;
}
```

**After**:
```java
@Bean
public OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService) {

    OAuth2AuthorizedClientProvider provider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientService
        );
    manager.setAuthorizedClientProvider(provider);

    return manager;
}
```

**Key Changes**:
- Remove "Reactive" prefix from class names
- Use `OAuth2AuthorizedClientService` instead of `ServerOAuth2AuthorizedClientRepository`

#### Streaming Responses

| WebFlux | WebMVC |
|---------|--------|
| `Flux<String>` | `SseEmitter` or `StreamingResponseBody` |
| Return Flux directly | Create SseEmitter, send via callbacks |
| `.subscribe()` for side effects | `.subscribe()` bridges to callbacks |

**Before**:
```java
@PostMapping("/chat")
public Flux<String> chat(@RequestBody Request req) {
    return chatClient.stream()
        .content();  // Return reactive stream
}
```

**After**:
```java
@PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@RequestBody Request req) {
    SseEmitter emitter = new SseEmitter(300_000L);

    chatService.streamResponse(req.question(),
        token -> {
            try {
                emitter.send(SseEmitter.event().data(token));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        },
        emitter::complete,
        emitter::completeWithError
    );

    return emitter;
}
```

**Service Layer**:
```java
// ChatService bridges Flux to callbacks
public void streamResponse(String question,
        Consumer<String> onToken,
        Runnable onComplete,
        Consumer<Throwable> onError) {

    Flux<String> stream = chatClient.stream().content();

    stream.subscribe(
        onToken::accept,
        onError::accept,
        onComplete::run
    );
}
```

---

## Jackson 2.x to 3.x Migration

### Package Namespace Change

**Key Change**: `com.fasterxml.jackson` → `tools.jackson`

### Import Updates

**Before**:
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
```

**After**:
```java
import tools.jackson.databind.ObjectMapper;
import tools.jackson.annotation.JsonProperty;
import tools.jackson.core.JsonProcessingException;
```

**Automated Fix**:
```bash
# Find and replace across codebase
find . -name "*.java" -type f -exec sed -i '' 's/com\.fasterxml\.jackson/tools.jackson/g' {} +
```

### Dependency Management

**Parent POM**:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>tools.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>3.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Module Dependencies** (change groupId):
```xml
<dependency>
    <groupId>tools.jackson.core</groupId>  <!-- Changed -->
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### OpenAPI Generator Configuration

**Must use Jakarta EE**:
```xml
<configOptions>
    <useJakartaEe>true</useJakartaEe>  <!-- Essential for Jackson 3.x -->
</configOptions>
```

**Why**: Jackson 3.x only works with `jakarta.*` annotations, not `javax.*`

### Dependency Exclusions

**Problem**: Some transitive dependencies bring Jackson 2.x

**Solution**: Exclude explicitly
```xml
<dependency>
    <groupId>some-library</groupId>
    <artifactId>some-artifact</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Check for Conflicts**:
```bash
mvn dependency:tree | grep jackson-databind
# Should only see tools.jackson, not com.fasterxml
```

---

## Spring Security 7.x Patterns

### SecurityFilterChain Ordering

**Spring Security 7 Requirement**: Use `@Order` annotation for multiple filter chains

```java
@Bean
@Order(1)  // Highest priority
public SecurityFilterChain authServerChain(HttpSecurity http) { ... }

@Bean
@Order(2)  // Second priority
public SecurityFilterChain resourceServerChain(HttpSecurity http) { ... }

@Bean
@Order(3)  // Lowest priority (catch-all)
public SecurityFilterChain defaultChain(HttpSecurity http) { ... }
```

**Why Ordering Matters**:
- First matching chain handles request
- Auth server chain matches `/oauth2/**`
- Resource server chain matches `/api/**`
- Default chain matches everything else

**Without `@Order`**: Random order, unpredictable behavior

### Deprecated Methods Removed

| Spring Security 6.x | Spring Security 7.x |
|---------------------|---------------------|
| `.and()` chaining | Lambda DSL only |
| `.authorizeRequests()` | `.authorizeHttpRequests()` |
| `.antMatchers()` | `.requestMatchers()` |
| `.mvcMatchers()` | `.requestMatchers()` |
| `.regexMatchers()` | `.requestMatchers()` with regex |

**Before** (deprecated):
```java
http
    .authorizeRequests()
        .antMatchers("/public/**").permitAll()
        .anyRequest().authenticated()
        .and()
    .formLogin()
        .loginPage("/login")
        .and()
    .logout();
```

**After** (lambda DSL):
```java
http
    .authorizeHttpRequests(requests -> requests
        .requestMatchers("/public/**").permitAll()
        .anyRequest().authenticated())
    .formLogin(form -> form
        .loginPage("/login"))
    .logout(Customizer.withDefaults());
```

### OAuth2 Authorization Server

**Integration Package**: `org.springframework.security.oauth2.server.authorization`

**Key Changes**:
- JDBC repositories now required for production (in-memory for dev only)
- `RegisteredClientRepository` replaces `ClientDetailsService`
- `OAuth2AuthorizationService` for token storage
- `AuthorizationServerSettings` for issuer configuration

**Configuration**:
```java
@Bean
@Order(1)
public SecurityFilterChain authServerChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfigurer authServerConfigurer =
        new OAuth2AuthorizationServerConfigurer();

    http
        .securityMatcher(authServerConfigurer.getEndpointsMatcher())
        .with(authServerConfigurer, authServer ->
            authServer.oidc(Customizer.withDefaults())
        )
        .authorizeHttpRequests(authorize ->
            authorize.anyRequest().authenticated()
        );

    return http.build();
}
```

---

## Spring HTTP Interface Adoption

### Context

**Migration**: OpenFeign → Spring HTTP Interface

**Why**:
- Spring HTTP Interface is official Spring 6+ feature
- Better integration with RestClient
- No runtime proxying (compile-time generation)
- Type-safe with minimal boilerplate

### OpenAPI Generator Configuration

**Before** (OpenFeign):
```xml
<configOptions>
    <library>feign</library>
</configOptions>
```

**After** (Spring HTTP Interface):
```xml
<configOptions>
    <library>spring-http-interface</library>
</configOptions>
```

### Generated Code Differences

**OpenFeign**:
```java
@FeignClient(name = "resos-api", url = "${resos.api.url}")
public interface DefaultApi {

    @RequestLine("GET /customers")
    List<Customer> customersGet(
        @Param("limit") Integer limit,
        @Param("skip") Integer skip
    );
}
```

**Spring HTTP Interface**:
```java
public interface DefaultApi {

    @GetExchange("/customers")
    ResponseEntity<List<Customer>> customersGet(
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "skip", required = false) Integer skip
    );
}
```

### Client Configuration

**OpenFeign** (automatic):
```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
```

**Spring HTTP Interface** (manual RestClient):
```java
@Bean
public RestClient restClient() {
    return RestClient.builder()
        .baseUrl(apiEndpoint)
        .requestFactory(new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
        ))
        .build();
}

@Bean
public DefaultApi defaultApi(RestClient restClient) {
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build();
    return factory.createClient(DefaultApi.class);
}
```

**Benefits of Spring HTTP Interface**:
- Native Spring support (no third-party library)
- Works with RestClient (Spring 6+)
- Better error handling
- Simpler configuration

---

## MCP Client Manager Simplification

### Context

**Initial Approach**: Custom `McpAsyncClientManager` with manual client construction

**Problems**:
- Tightly coupled to WebFlux `WebClient`
- Used deprecated `WebFluxSseClientTransport`
- Complex property injection
- Manual lifecycle management

### Migration

**Before** (Complex Custom Manager):
```java
@Component
public class McpAsyncClientManager {

    private final WebClient webClient;
    private final McpSseClientProperties properties;
    private final ObjectMapper objectMapper;

    public McpAsyncClient createClient(String name) {
        McpSseClientProperties.Connection conn = properties.getConnections().get(name);

        // Manually construct transport
        WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder()
            .webClient(webClient)
            .baseUrl(conn.getUrl())
            .objectMapper(objectMapper)
            .build();

        // Manually build client
        return McpAsyncClient.builder()
            .transport(transport)
            .clientInfo(new Implementation("mcp-frontend", "1.0"))
            .build();
    }
}
```

**After** (Simple Autoconfigured Manager):
```java
@Component
public class McpSyncClientManager {

    private final ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider;

    public McpSyncClientManager(ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider) {
        this.mcpSyncClientsProvider = mcpSyncClientsProvider;
    }

    public List<McpSyncClient> newMcpSyncClients() {
        return mcpSyncClientsProvider.getIfAvailable(List::of);
    }
}
```

**Configuration** (application.yml):
```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC
        http:
          connections:
            butler:
              url: http://localhost:8082
```

**Benefits**:
- 90% less code (100 lines → 10 lines)
- Spring Boot autoconfiguration handles everything
- No manual transport creation
- Easier to test

**Lesson**: Trust Spring Boot autoconfiguration when possible

---

## Application Configuration Changes

### MCP Server

**Before** (WebFlux with SSE):
```yaml
spring:
  main:
    web-application-type: none  # STDIO mode

  ai:
    mcp:
      server:
        stdio: true
```

**After** (WebMVC with HTTP Streamable):
```yaml
spring:
  # Removed: web-application-type (HTTP enabled by default)

  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.2-modified
        # HTTP Streamable is default (no explicit config needed)

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080
```

### MCP Client

**Before** (WebFlux with SSE):
```yaml
spring:
  ai:
    mcp:
      client:
        type: ASYNC
        sse:
          connections:
            butler:
              url: http://localhost:8082
```

**After** (WebMVC with HTTP):
```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC
        initialized: false
        http:  # Changed from 'sse' to 'http'
          connections:
            butler:
              url: http://localhost:8082
```

---

## Common Pitfalls & Solutions

### Pitfall 1: Spring Security Autoconfiguration

**Problem**: Adding `spring-boot-starter-oauth2-client` brings Spring Security, which protects ALL endpoints by default with 302 redirects.

**Symptom**:
```
Expected: 200 OK from /mcp/tools
Actual: 302 Found → /login
```

**Solution**: Create explicit `SecurityFilterChain`:
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/mcp/**").authenticated()
            .requestMatchers("/actuator/**").permitAll()
            .anyRequest().permitAll())  // Explicitly permit others
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(Customizer.withDefaults()))
        .build();
}
```

### Pitfall 2: Missing Dependency Versions

**Problem**: Spring AI BOM doesn't include all MCP artifacts in milestone versions.

**Symptom**:
```
[ERROR] Failed to execute goal ... could not resolve dependencies for me.pacphi:spring-ai-resos-mcp-server
```

**Solution**: Add explicit version
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    <version>${spring-ai.version}</version>  <!-- Explicit version -->
</dependency>
```

### Pitfall 3: Reactor Dependencies Lingering

**Problem**: Compilation errors for `reactor.core.publisher.Mono` even after removing webflux.

**Symptom**:
```
[ERROR] cannot find symbol
  symbol:   class Mono
  location: package reactor.core.publisher
```

**Solution**:
1. Search for reactive imports:
   ```bash
   grep -r "import reactor" src/main/java/
   ```
2. Replace with blocking alternatives
3. Delete old files entirely (don't comment out)
4. Verify no WebFlux dependencies:
   ```bash
   mvn dependency:tree | grep webflux
   ```

### Pitfall 4: Incorrect Artifact Names

**Problem**: Guessing artifact names based on conventions.

**Symptoms**:
- ❌ `spring-ai-mcp-server-webmvc-spring-boot-starter` (doesn't exist)
- ❌ `spring-ai-mcp-client-spring-boot-starter` (ambiguous)

**Solution**: Always verify in Maven Central:
1. Search: https://central.sonatype.com/
2. Check Spring AI GitHub: https://github.com/spring-projects/spring-ai
3. Read documentation: https://docs.spring.io/spring-ai/reference/

**Correct Names**:
- ✅ `spring-ai-starter-mcp-server-webmvc`
- ✅ `spring-ai-starter-mcp-client` (servlet-based)
- ✅ `spring-ai-starter-mcp-client-webflux` (if needed)

---

## JUnit 5 to JUnit 6 Migration

### BOM Update

**Before**:
```xml
<junit-jupiter.version>5.11.3</junit-jupiter.version>
```

**After**:
```xml
<junit-jupiter.version>6.0.0</junit-jupiter.version>
```

### Import Changes

**Mostly unchanged**, but verify:
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;
```

### TestContainers Integration

**Spring Boot 4 Support**:
```java
@SpringBootTest
@Testcontainers
class BackendIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void testDatabaseConnection() {
        assertThat(postgres.isRunning()).isTrue();
    }
}
```

---

## Migration Checklist

### Phase 1: Dependency Updates

- [ ] Update Spring Boot version in parent POM
- [ ] Update Spring AI version
- [ ] Add Jackson 3.x BOM
- [ ] Update JUnit to 6.x
- [ ] Verify all BOM versions compatible
- [ ] Run `mvn dependency:tree` - check for conflicts

### Phase 2: WebFlux → WebMVC (if applicable)

- [ ] Replace `spring-boot-starter-webflux` with `spring-boot-starter-web`
- [ ] Update MCP starter artifacts (webflux → webmvc)
- [ ] Update security config annotations
- [ ] Replace `Mono<T>` with `T`
- [ ] Replace `Flux<T>` with `SseEmitter` or callbacks
- [ ] Update `WebClient` to `RestClient`
- [ ] Fix OAuth2 manager types (remove "Reactive" prefix)

### Phase 3: Jackson Migration

- [ ] Update Jackson imports (`com.fasterxml` → `tools.jackson`)
- [ ] Add Jackson 3.x BOM to parent POM
- [ ] Update OpenAPI Generator to use Jakarta EE
- [ ] Exclude Jackson 2.x from transitive dependencies
- [ ] Verify serialization still works

### Phase 4: Security Updates

- [ ] Add `@Order` to all SecurityFilterChain beans
- [ ] Replace `.authorizeExchange()` with `.authorizeHttpRequests()`
- [ ] Replace `.pathMatchers()` with `.requestMatchers()`
- [ ] Update lambda DSL (remove `.and()` chaining)
- [ ] Test OAuth2 flows (authorization code, client credentials)

### Phase 5: Testing

- [ ] Update test dependencies (JUnit 6, AssertJ)
- [ ] Run all tests: `mvn test`
- [ ] Fix compilation errors
- [ ] Verify integration tests pass
- [ ] Test with TestContainers (if used)

### Phase 6: Configuration

- [ ] Update application.yml for MCP (SSE → HTTP)
- [ ] Remove WebFlux-specific configurations
- [ ] Verify profile configurations
- [ ] Test with all profiles (dev, postgres, etc.)

### Phase 7: Documentation

- [ ] Document breaking changes
- [ ] Update README with new versions
- [ ] Create migration guide (this document!)
- [ ] Update architecture diagrams if needed

---

## Lessons Learned

### 1. Always Verify Artifact Names

**Don't Assume**: Naming conventions vary

**Do**:
- Search Maven Central
- Check official documentation
- Look at Spring AI GitHub repository
- Test with simple project first

### 2. Start with WebMVC for OAuth2 + MCP

**Recommendation**: If building new MCP project with OAuth2 security, start with WebMVC.

**Rationale**:
- Better library support
- Simpler security configuration
- More examples and documentation
- HTTP Streamable is the standard

### 3. Use Autoconfiguration

**Don't**: Manually construct complex beans

**Do**: Let Spring Boot autoconfigure, customize only when needed

**Example**: McpSyncClientManager uses autoconfigured clients (10 lines vs 100 lines)

### 4. Trust the Spec

**MCP Specification**: HTTP Streamable is the recommended transport

**Don't Fight It**: SSE was deprecated for good reasons (OAuth2 incompatibility, unidirectional)

### 5. Document Migration Paths

**This Document Exists Because**: Future projects/teams can learn from this migration

**Recommendation**: Document your migrations with:
- Before/after code examples
- Rationale for changes
- Gotchas and solutions
- Time estimates

### 6. Test Incrementally

**Don't**: Change everything at once

**Do**: Migrate module-by-module:
1. Update dependencies
2. Compile (fix errors)
3. Run tests (fix failures)
4. Manual testing
5. Move to next module

### 7. Use Version Control

**Git Strategy**:
```bash
git checkout -b feature/spring-boot-4-migration
git commit -m "Phase 0: Update dependencies"
git commit -m "Phase 1: Migrate backend security"
git commit -m "Phase 2: Migrate MCP server"
# ... incremental commits
```

**Benefits**:
- Easy rollback if issues
- Clear migration history
- Review changes in isolation

---

## Estimated Migration Effort

### By Component

| Component | Effort | Notes |
|-----------|--------|-------|
| **Research** | 2 hours | Understanding new APIs, finding correct artifacts |
| **Dependency Updates** | 1 hour | Update POMs, resolve conflicts |
| **Security Migration** | 2 hours | SecurityFilterChain, OAuth2 config |
| **Controller Migration** | 1 hour | Mono/Flux → blocking types |
| **HTTP Client Migration** | 1 hour | WebClient → RestClient |
| **Configuration Updates** | 30 min | application.yml changes |
| **Testing** | 2 hours | Fix test failures, integration testing |
| **Documentation** | 1 hour | Update docs, create migration guide |
| **Total** | **~10.5 hours** | For 3-module project |

**Factors Affecting Time**:
- Team familiarity with Spring Boot 4
- Number of custom security filters
- Amount of WebFlux-specific code
- Test coverage (more tests = more to fix)

---

## Migration Timeline

### Week 1: Planning & Research

- [ ] Read Spring Boot 4 release notes
- [ ] Review Spring AI 2.0 changes
- [ ] Check dependency compatibility
- [ ] Create migration plan
- [ ] Set up test environment

### Week 2: Implementation

- [ ] Update dependencies (Day 1)
- [ ] Migrate security config (Day 2)
- [ ] Migrate controllers and services (Day 3)
- [ ] Update configuration files (Day 4)
- [ ] Fix tests and integration issues (Day 5)

### Week 3: Testing & Documentation

- [ ] Comprehensive testing (Days 1-2)
- [ ] Performance testing (Day 3)
- [ ] Documentation updates (Day 4)
- [ ] Code review and cleanup (Day 5)

---

## Resources

### Official Documentation

- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring AI 2.0 Documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring Security 7.0 Migration](https://docs.spring.io/spring-security/reference/migration/index.html)
- [Jackson 3.0 Migration](https://github.com/FasterXML/jackson/wiki/Jackson-3.0)

### Community Resources

- [Baeldung: Spring Boot 4](https://www.baeldung.com/spring-boot-4)
- [Baeldung: Securing Spring AI MCP Servers](https://www.baeldung.com/spring-ai-mcp-servers-oauth2)
- [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)

### Internal Documentation

- [docs/PHASE_0_LESSONS_LEARNED.md](../../PHASE_0_LESSONS_LEARNED.md) - Real migration lessons
- [docs/SECURITY_IMPLEMENTATION_PLAN.md](../../SECURITY_IMPLEMENTATION_PLAN.md) - Security architecture

---

## Critical Files

| File | Purpose |
|------|---------|
| `pom.xml` | Parent POM with version updates |
| `backend/pom.xml` | Backend dependencies |
| `mcp-server/pom.xml` | MCP server artifacts |
| `mcp-client/pom.xml` | MCP client artifacts |
| `.github/workflows/ci.yml` | CI/CD with JDK 25 |

## Related Documentation

- [Technology Stack](02-technology-stack.md) - Current versions
- [ADR-004: WebMVC over WebFlux](adr/004-webmvc-over-webflux.md) - Migration rationale
- [Security Architecture](06-security-architecture.md) - Spring Security 7.x patterns
- [Build Workflow](10-build-workflow.md) - Build commands
