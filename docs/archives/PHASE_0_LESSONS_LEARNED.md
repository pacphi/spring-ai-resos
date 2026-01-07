# Phase 0: WebFlux to WebMVC Migration - Lessons Learned

## Overview

Successfully migrated the Spring AI Resos project from WebFlux (reactive) to WebMVC (servlet) stack to support OAuth2 security with MCP HTTP Streamable transport.

**Date**: January 2026
**Spring AI Version**: 2.0.0-M1
**Spring Boot Version**: 4.0.1

---

## Critical Discovery: MCP Transport Compatibility

### What We Learned

The official [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security) library states:

> "Requires Spring WebMVC (not WebFlux); SSE transport unsupported"
> "The deprecated SSE transport is not supported. Use Streamable HTTP or stateless transport instead."

**Key Insight**: The MCP specification has evolved away from SSE (Server-Sent Events) toward HTTP Streamable as the standard transport, especially when implementing OAuth2 security.

### Why This Matters

1. **Spring Security Integration**: Adding `spring-boot-starter-oauth2-client` to a WebFlux application brings Spring Security autoconfiguration, which by default protects ALL endpoints with 302 redirects
2. **SSE + Security = Complex**: Securing SSE endpoints requires custom WebFlux security configuration that isn't well-supported by the mcp-security library
3. **WebMVC = Standard Path**: The Baeldung articles and Spring AI documentation focus on WebMVC implementations with OAuth2

---

## Correct Maven Artifact Names

### MCP Server

❌ **Wrong**: `spring-ai-mcp-server-webmvc-spring-boot-starter`
✅ **Correct**: `spring-ai-starter-mcp-server-webmvc`

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    <!-- Version inherited from spring-ai-bom -->
</dependency>
```

### MCP Client

❌ **Wrong**: `spring-ai-mcp-client-spring-boot-starter`
❌ **Wrong**: `spring-ai-starter-mcp-client-webmvc` (doesn't exist)
✅ **Correct**: `spring-ai-starter-mcp-client` (servlet-based, uses JDK HttpClient)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

**Note**: The WebFlux version is `spring-ai-starter-mcp-client-webflux`. The base `spring-ai-starter-mcp-client` is servlet/WebMVC compatible.

---

## Java Code Conversion Patterns

### 1. Security Configuration

| WebFlux (Reactive) | WebMVC (Servlet) |
|--------------------|------------------|
| `@EnableWebFluxSecurity` | `@EnableWebSecurity` |
| `SecurityWebFilterChain` | `SecurityFilterChain` |
| `ServerHttpSecurity` | `HttpSecurity` |
| `.authorizeExchange()` | `.authorizeHttpRequests()` |
| `.pathMatchers()` | `.requestMatchers()` |

### 2. Controllers

| WebFlux | WebMVC |
|---------|--------|
| `Mono<ResponseEntity<T>>` | `ResponseEntity<T>` |
| `Flux<String>` | `SseEmitter` (for streaming) |
| `ServerWebExchange` | `HttpServletRequest` / `HttpServletResponse` |
| Return `Mono.just(value)` | Return `value` directly |

### 3. HTTP Clients

| WebFlux | WebMVC |
|---------|--------|
| `WebClient` | `RestClient` |
| `ReactorClientHttpConnector` | `JdkClientHttpRequestFactory` |
| `WebClient.Builder` | `RestClient.Builder` |
| Reactive filters | `ClientHttpRequestInterceptor` |

### 4. OAuth2

| WebFlux | WebMVC |
|---------|--------|
| `ReactiveOAuth2AuthorizedClientManager` | `OAuth2AuthorizedClientManager` |
| `ServerOAuth2AuthorizedClientExchangeFilterFunction` | Custom interceptor (servlet) |
| `ReactiveClientRegistrationRepository` | `ClientRegistrationRepository` |

### 5. Streaming Patterns

**WebFlux Reactive Streaming:**
```java
public Flux<String> stream() {
    return chatClient.stream().content();
}
```

**WebMVC Callback-Based Streaming:**
```java
public void stream(Consumer<String> onToken, Runnable onComplete, Consumer<Throwable> onError) {
    var stream = chatClient.stream().content();
    stream.subscribe(onToken::accept, onError::accept, onComplete::run);
}
```

**WebMVC SseEmitter:**
```java
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestBody Request req) {
    SseEmitter emitter = new SseEmitter(300_000L);
    chatService.stream(req.question(),
        token -> emitter.send(SseEmitter.event().data(token)),
        emitter::complete,
        emitter::completeWithError
    );
    return emitter;
}
```

---

## MCP Client Manager Simplification

### Original Approach (Complex)

Created custom `McpAsyncClientManager` that manually constructed MCP clients with custom transports, JSON mappers, and configuration.

**Issues**:
- Tightly coupled to WebFlux `WebClient`
- Used `WebFluxSseClientTransport` (deprecated)
- Manually managed client lifecycle
- Complex property injection (`McpSseClientProperties`, etc.)

### Final Approach (Simple)

Use Spring AI autoconfiguration:

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

**Benefits**:
- Leverages Spring AI autoconfiguration
- No manual transport creation
- Works with application.yml properties
- Simpler to test and maintain

---

## Application Configuration Changes

### MCP Server

**Before (WebFlux):**
```yaml
spring:
  main:
    web-application-type: none  # STDIO mode
  ai:
    mcp:
      server:
        stdio: true
```

**After (WebMVC):**
```yaml
spring:
  main:
    # Removed web-application-type: none (HTTP enabled)
  ai:
    mcp:
      server:
        # WebMVC starter uses HTTP Streamable by default
        # protocol: STREAMABLE is the default
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080
```

### MCP Client

**Before (WebFlux):**
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

**After (WebMVC):**
```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC
        initialized: false  # Let manager handle per-request init
        http:  # Changed from 'sse' to 'http'
          connections:
            butler:
              url: http://localhost:8082
```

---

## Common Pitfalls & Solutions

### 1. Spring Security Autoconfiguration

**Problem**: Adding OAuth2 dependencies brings Spring Security, which protects ALL endpoints by default.

**Solution**:
- For mcp-server: Create `SecurityFilterChain` that explicitly permits/protects endpoints
- For mcp-client: Configure OAuth2 login and client for the frontend SPA

### 2. Missing Dependency Versions

**Problem**: `spring-ai-bom` doesn't include all MCP artifacts in milestone versions.

**Solution**: Either add explicit `<version>${spring-ai.version}</version>` or ensure BOM is imported correctly.

### 3. Reactor Dependencies Lingering

**Problem**: Compilation errors for `reactor.core.publisher.Mono` or `Flux` even after removing webflux.

**Solution**:
- Check all Java files for reactive imports
- Some Spring AI APIs still return `Flux` even in servlet mode (use `.subscribe()` to convert)
- Delete old files entirely rather than commenting out

### 4. Property Class Names

**Problem**: Guessing property class names like `McpHttpClientProperties` that don't exist.

**Solution**:
- Check Spring AI source code or documentation
- Use `ObjectProvider` to safely inject optional beans
- Let autoconfiguration handle complex setups

---

## Testing Strategy

### What We Tested

1. ✅ **Compilation**: Both modules compile without errors
2. ⏳ **Runtime startup**: Not yet tested (next phase)
3. ⏳ **MCP connectivity**: Need to verify client→server communication
4. ⏳ **OAuth2 flow**: Need to test token issuance and validation

### Recommended Test Order

1. Start mcp-server standalone - verify it starts on port 8082
2. Start backend (when available) - verify OAuth2 auth server
3. Start mcp-client - verify OAuth2 login works
4. Test chat streaming - verify MCP client connects to server
5. Verify OAuth2 tokens are passed correctly

---

## References

- [Spring AI MCP Server WebMVC Artifact](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-starter-mcp-server-webmvc)
- [Spring AI MCP Client Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
- [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)
- [Securing Spring AI MCP Servers With OAuth2 (Baeldung)](https://www.baeldung.com/spring-ai-mcp-servers-oauth2)
- [MCP Authorization Specification](https://modelcontextprotocol.info/specification/draft/basic/authorization/)

---

## Team Recommendations

1. **Always check artifact names** in Maven Central before assuming naming conventions
2. **Start with WebMVC** for new MCP projects when OAuth2 security is required
3. **Use autoconfiguration** rather than custom managers when possible
4. **Trust the spec**: MCP moved to HTTP Streamable for good reasons
5. **Document migration paths**: This will help future projects avoid the same issues

---

## Estimated Effort

- **Planning & Research**: 2 hours (understanding why SSE+WebFlux failed)
- **Code Migration**: 3 hours (systematic conversion of all files)
- **Debugging & Fixes**: 1 hour (artifact names, property classes)
- **Documentation**: 1 hour

**Total**: ~7 hours for Phase 0

**Remaining Work**: Phases 1-5 for full OAuth2 security implementation
