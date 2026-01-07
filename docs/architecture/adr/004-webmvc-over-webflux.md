# ADR-004: WebMVC over WebFlux

## Status
**Accepted** - Migrated in Phase 0

## Context

Spring Boot applications can use either:

1. **Spring WebMVC**: Traditional servlet-based, blocking I/O
2. **Spring WebFlux**: Reactive, non-blocking I/O with Project Reactor

The MCP (Model Context Protocol) Server and Client modules were initially implemented with WebFlux but encountered significant compatibility issues.

### Problem Statement

The initial WebFlux implementation faced these challenges:

1. **MCP Security Library Incompatibility**:
   - `spring-ai-community/mcp-security` library explicitly states:
   - > "Requires Spring WebMVC (not WebFlux); SSE transport unsupported"

2. **SSE Transport Deprecation**:
   - MCP specification moved from SSE to HTTP Streamable
   - SSE was deprecated in favor of Streamable HTTP
   - Security with SSE in WebFlux proved complex

3. **OAuth2 Integration Complexity**:
   - Adding `spring-boot-starter-oauth2-client` to WebFlux brings Spring Security
   - Default security protects ALL endpoints with 302 redirects
   - Securing SSE endpoints requires custom WebFlux security filters
   - Limited examples and documentation for WebFlux + OAuth2 + SSE

4. **Artifact Confusion**:
   - Incorrect artifact names used initially:
     - ❌ `spring-ai-mcp-server-webflux-spring-boot-starter` (doesn't exist)
     - ❌ `spring-ai-mcp-client-spring-boot-starter` (ambiguous)
   - Correct names discovered through Maven Central search:
     - ✅ `spring-ai-starter-mcp-server-webmvc`
     - ✅ `spring-ai-starter-mcp-client` (servlet-based, not WebFlux)

### Constraints

- Must use Spring AI 2.0.0-M1
- Must support OAuth2 security at all layers
- Must use HTTP Streamable (not SSE) for MCP
- Must work with Spring Boot 4.0.1 and Spring Security 7.x
- Backend must remain compatible (OAuth2 Authorization Server)

## Decision

**Migrate MCP Server and MCP Client modules from WebFlux to WebMVC.**

### Implementation Details

1. **Artifact Changes**:

   **MCP Server**:
   ```xml
   <!-- BEFORE -->
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
   </dependency>

   <!-- AFTER -->
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
   </dependency>
   ```

   **MCP Client**:
   ```xml
   <!-- BEFORE -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-webflux</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-mcp-client-webflux</artifactId>
   </dependency>

   <!-- AFTER -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-web</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.ai</groupId>
       <artifactId>spring-ai-starter-mcp-client</artifactId>
   </dependency>
   ```

2. **Code Patterns Migration**:

   | WebFlux (Reactive) | WebMVC (Servlet) |
   |--------------------|------------------|
   | `@EnableWebFluxSecurity` | `@EnableWebSecurity` |
   | `SecurityWebFilterChain` | `SecurityFilterChain` |
   | `ServerHttpSecurity` | `HttpSecurity` |
   | `.authorizeExchange()` | `.authorizeHttpRequests()` |
   | `.pathMatchers()` | `.requestMatchers()` |
   | `Mono<T>` | `T` (direct return) |
   | `Flux<String>` | `Stream<String>` or `SseEmitter` |
   | `WebClient` | `RestClient` |
   | `ReactiveOAuth2AuthorizedClientManager` | `OAuth2AuthorizedClientManager` |
   | `ServerWebExchange` | `HttpServletRequest` / `HttpServletResponse` |

3. **Streaming Pattern Change**:

   **Before (WebFlux)**:
   ```java
   @PostMapping("/chat")
   public Flux<String> chat(@RequestBody Request req) {
       return chatClient.stream()
           .content();
   }
   ```

   **After (WebMVC with SseEmitter)**:
   ```java
   @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
   public SseEmitter chat(@RequestBody Request req) {
       SseEmitter emitter = new SseEmitter(300_000L);

       chatService.stream(req.question(),
           token -> emitter.send(SseEmitter.event().data(token)),
           emitter::complete,
           emitter::completeWithError
       );

       return emitter;
   }
   ```

4. **MCP Client Manager Simplification**:

   **Before (Custom Async Manager)**:
   ```java
   @Component
   public class McpAsyncClientManager {
       private final WebClient webClient;  // Reactive
       private final McpSseClientProperties properties;

       public McpAsyncClient createClient(String name) {
           // Complex manual construction
           WebFluxSseClientTransport transport = new WebFluxSseClientTransport(...);
           return McpAsyncClient.builder()
               .transport(transport)
               .build();
       }
   }
   ```

   **After (Autoconfigured Sync Manager)**:
   ```java
   @Component
   public class McpSyncClientManager {
       private final ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider;

       public List<McpSyncClient> newMcpSyncClients() {
           return mcpSyncClientsProvider.getIfAvailable(List::of);
       }
   }
   ```

## Consequences

### Positive

1. **OAuth2 Security Compatibility**:
   - Standard Spring Security patterns work out of the box
   - Well-documented `SecurityFilterChain` configuration
   - No custom WebFlux security filters needed

2. **HTTP Streamable Support**:
   - MCP spec alignment (Streamable HTTP is standard)
   - Better OAuth2 integration
   - Simpler transport configuration

3. **Simpler Code**:
   - No reactive types (`Mono`, `Flux`) in controllers
   - Familiar servlet API (`HttpServletRequest`, `HttpServletResponse`)
   - Easier to debug (blocking call stack)

4. **Better Spring AI Integration**:
   - `spring-ai-starter-mcp-client` is servlet-based
   - Autoconfiguration works properly
   - Less manual configuration needed

5. **Reduced Complexity**:
   - No need to understand reactive programming
   - Fewer dependencies (no reactor-core, reactor-netty)
   - Simpler error handling (no `.onErrorResume()`)

6. **Community Support**:
   - More Stack Overflow answers for WebMVC
   - Baeldung examples use WebMVC
   - Spring AI examples predominantly use WebMVC

### Negative

1. **Blocking I/O**:
   - Thread-per-request model
   - Potentially less efficient under high concurrency
   - Cannot benefit from reactive backpressure

2. **Migration Effort**:
   - ~7 hours total effort (research + code + testing + docs)
   - Had to rewrite security configurations
   - Changed controller signatures and response types

3. **Lost WebFlux Benefits**:
   - No reactive composition
   - Cannot chain reactive operators
   - Thread pooling more important (vs event loop)

4. **Memory Usage**:
   - More threads = more memory (1MB stack per thread)
   - WebFlux event loop more memory-efficient
   - Tomcat thread pool vs Netty event loop

### Neutral

1. **Performance**:
   - For this use case (AI chat), throughput similar
   - WebFlux shines at 10K+ concurrent connections (not our scale)
   - Blocking I/O fine for low-to-medium concurrency

2. **Streaming**:
   - `SseEmitter` works well for streaming responses
   - Not as elegant as `Flux<T>` but equally functional
   - Browser SSE consumption unchanged

## Alternatives Considered

### Alternative 1: Stay with WebFlux + Custom Security

**Approach**: Keep WebFlux, write custom security filters for SSE.

**Pros**:
- No code migration needed
- Keep reactive benefits
- Learn advanced WebFlux patterns

**Cons**:
- Complex custom security configuration
- SSE transport deprecated by MCP spec
- Against `mcp-security` library requirements
- Limited documentation and examples
- Potential security vulnerabilities in custom code

**Rejected Because**: Fighting against the ecosystem, SSE deprecated, security complexity too high.

### Alternative 2: Use STDIO Transport (No HTTP)

**Approach**: Use STDIO transport for MCP (no HTTP, no security needed).

**Pros**:
- No HTTP server needed
- No security complexity
- Works with Claude Desktop directly

**Cons**:
- Cannot run as web service
- No browser-based UI possible
- Cannot deploy to cloud
- Limited to local desktop usage

**Rejected Because**: Need web-based chatbot UI and cloud deployment.

### Alternative 3: Dual Stack (WebFlux for some, WebMVC for others)

**Approach**: Use WebFlux for mcp-client, WebMVC for mcp-server.

**Pros**:
- Keep reactive benefits where possible
- Targeted migration

**Cons**:
- Mixed stack complexity
- Two security models
- Confusing for developers
- More dependencies

**Rejected Because**: Consistency more important than theoretical reactive benefits.

## Implementation Notes

### Migration Checklist (Completed)

**MCP Server**:
- [x] Update `pom.xml` - swap WebFlux for WebMVC starter
- [x] Update `ResOsConfig.java` - `WebClient` → `RestClient`
- [x] Update `SecurityConfig.java` - `@EnableWebFluxSecurity` → `@EnableWebSecurity`
- [x] Update `application.yml` - remove WebFlux config, HTTP Streamable is default

**MCP Client**:
- [x] Update `pom.xml` - swap WebFlux for Web starter
- [x] Delete `McpAsyncClientManager.java` - replaced with `McpSyncClientManager`
- [x] Update `ChatService.java` - callback-based streaming
- [x] Update `ChatController.java` - `SseEmitter` instead of `Flux<String>`
- [x] Update `AuthController.java` - `HttpServletRequest` instead of `ServerWebExchange`
- [x] Update `SecurityConfig.java` - `SecurityFilterChain` instead of `SecurityWebFilterChain`
- [x] Update `application.yml` - `type: SYNC`, `http:` instead of `sse:`

### Key Learnings

1. **Artifact Names Matter**: Always verify artifact names in Maven Central
2. **Start with WebMVC**: For OAuth2 + MCP, WebMVC is the standard path
3. **Trust the Spec**: MCP moved to HTTP Streamable for good reasons
4. **Autoconfiguration is Your Friend**: Use Spring Boot conventions when possible
5. **Documentation Exists**: Baeldung and Spring AI docs cover WebMVC patterns well

### Testing Verification

- [x] Backend starts successfully (port 8080)
- [x] MCP Server starts successfully (port 8082)
- [x] MCP Client starts successfully (port 8081)
- [x] OAuth2 login works (PKCE flow)
- [x] Chat streaming works (SSE to browser)
- [x] MCP tool invocation works (client → server → backend)
- [x] JWT validation works at all layers

## Related Decisions

- [ADR-002: Spring Data JDBC](002-spring-data-jdbc.md) - Blocking I/O fits JDBC model
- [ADR-005: HTTP Streamable Transport](005-http-streamable-transport.md) - Transport choice for MCP

## References

- [Spring WebMVC Documentation](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)
- [MCP Authorization Specification](https://modelcontextprotocol.info/specification/draft/basic/authorization/)
- [Baeldung: Securing Spring AI MCP Servers With OAuth2](https://www.baeldung.com/spring-ai-mcp-servers-oauth2)
- [docs/PHASE_0_LESSONS_LEARNED.md](../../../docs/PHASE_0_LESSONS_LEARNED.md)

## Decision Date

January 2026 (Phase 0 Migration)

## Reviewers

- Chris Phillipson (Architect)

## Changelog

| Date | Change |
|------|--------|
| Jan 2026 | Migration completed, lessons documented |
