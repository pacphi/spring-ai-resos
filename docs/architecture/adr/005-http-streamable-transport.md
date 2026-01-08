# ADR-005: HTTP Streamable Transport for MCP

## Status

**Accepted** - Implemented in MCP Server and Client

## Context

The Model Context Protocol (MCP) specification supports multiple transport mechanisms:

1. **STDIO**: Standard input/output, for local desktop applications
2. **SSE** (Server-Sent Events): HTTP-based, one-way streaming
3. **HTTP Streamable**: HTTP-based, bidirectional, spec-compliant

### Problem Statement

When implementing the MCP Server and Client, we had to choose a transport layer that:

- Supports OAuth2 security (Bearer tokens in HTTP headers)
- Works with Spring AI 2.0 MCP starters
- Aligns with MCP specification evolution
- Integrates well with WebMVC (servlet stack)
- Supports bidirectional communication (tool calls + responses)

### Historical Context

The MCP specification evolved significantly:

1. **Early 2024**: SSE was the primary HTTP transport
2. **Mid 2024**: HTTP Streamable introduced
3. **June 2025**: MCP spec updated, SSE marked as deprecated
4. **Current (Jan 2026)**: HTTP Streamable is the recommended standard

### Initial Implementation Issues

The first implementation used SSE transport with WebFlux:

**Problems Encountered**:

- `spring-ai-community/mcp-security` library doesn't support SSE
- OAuth2 Bearer token authentication difficult with SSE
- WebFlux + OAuth2 + SSE combination poorly documented
- SSE is unidirectional (server→client only), MCP needs bidirectional

## Decision

**Use HTTP Streamable transport for all MCP communication between client and server.**

### Implementation Details

1. **MCP Server Configuration**:
   - Spring AI MCP Server WebMVC starter uses HTTP Streamable by default
   - No explicit transport configuration needed
   - Endpoints exposed under `/mcp/**`

   ```yaml
   spring:
     ai:
       mcp:
         server:
           name: ${spring.application.name}
           version: 1.2-modified
           # HTTP Streamable is the default transport
   ```

2. **MCP Client Configuration**:
   - Type: `SYNC` (servlet-based, not reactive)
   - Transport: HTTP Streamable (property key: `streamable-http`)
   - Connection URL: `http://localhost:8082`
   - Lazy initialization required for OAuth2 timing

   ```yaml
   spring:
     ai:
       mcp:
         client:
           type: SYNC
           # Do NOT initialize at startup - OAuth2 tokens not ready yet
           # Clients are initialized on first use in McpSyncClientManager
           initialized: false
           streamable-http: # HTTP Streamable transport (not 'http' or 'sse')
             connections:
               butler:
                 url: http://localhost:8082
   ```

   **Important**: The `initialized: false` setting is required because:
   - OAuth2 client credentials tokens aren't available at startup
   - The `mcp-security` library requires lazy initialization
   - `McpSyncClientManager` handles initialization on first use

3. **Security Integration**:
   - OAuth2 Bearer tokens in `Authorization` header
   - Standard HTTP request/response cycle
   - JWT validation by resource server

   ```java
   // OAuth2 automatically adds Bearer token
   RestClient restClient = RestClient.builder()
       .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
           authorizedClientManager,
           "mcp-client-to-server"
       ))
       .build();
   ```

4. **Tool Invocation Flow**:

   ```text
   MCP Client → HTTP POST /mcp/tools/{toolName}
       Headers: Authorization: Bearer {jwt}
       Body: { "arguments": {...} }

   MCP Server → Validate JWT → Execute Tool → Return Response
       Response: { "result": {...} }
   ```

## Consequences

### Positive

1. **Spec Compliance**:
   - HTTP Streamable is the current MCP standard
   - Future-proof as SSE support may be removed
   - Aligns with MCP community direction

2. **OAuth2 Security**:
   - Bearer tokens in HTTP headers (standard practice)
   - No workarounds needed for authentication
   - Works with `spring-boot-starter-oauth2-resource-server`
   - Compatible with `mcp-security` library

3. **Bidirectional Communication**:
   - Client can call server tools
   - Server can stream responses back
   - Full request/response cycle support

4. **WebMVC Compatibility**:
   - Standard servlet request/response
   - No reactive types needed
   - Familiar Spring MVC patterns

5. **Debugging & Monitoring**:
   - Standard HTTP requests visible in logs
   - Can use HTTP debugging tools (Postman, curl)
   - Spring Actuator metrics work normally

6. **Spring AI Integration**:
   - Spring AI MCP Server WebMVC starter designed for HTTP Streamable
   - Autoconfiguration works correctly
   - Tool registration automatic with `@Tool` annotations

### Negative

1. **Not True Streaming**:
   - HTTP Streamable uses request/response, not persistent connection
   - Each tool call is separate HTTP request
   - More overhead than persistent SSE connection

2. **No Server Push**:
   - Server cannot initiate communication
   - Client must poll or wait for response
   - Unlike WebSockets or SSE (but SSE is unidirectional anyway)

3. **Different from Early Examples**:
   - Some early MCP examples used SSE
   - May confuse developers following old tutorials
   - Need to verify examples use current spec

### Neutral

1. **Connection Overhead**:
   - HTTP Streamable: new connection per tool call
   - Acceptable for MCP use case (infrequent tool calls)
   - Not suitable for high-frequency streaming (but that's not MCP's purpose)

2. **Latency**:
   - HTTP Streamable: ~5-10ms connection overhead
   - Negligible compared to AI model latency (100ms-5s)
   - Not a performance bottleneck

## Alternatives Considered

### Alternative 1: SSE (Server-Sent Events)

**Approach**: Use SSE transport with custom OAuth2 handling.

**Pros**:

- True server-to-client streaming
- Lower latency for streams
- Simpler client (EventSource API)

**Cons**:

- Unidirectional (server→client only)
- Deprecated by MCP specification
- `mcp-security` library doesn't support it
- OAuth2 Bearer tokens difficult to pass
- Requires custom security filters

**Rejected Because**: Deprecated by spec, poor OAuth2 support, unidirectional.

### Alternative 2: STDIO

**Approach**: Use standard input/output for MCP communication.

**Pros**:

- No HTTP server needed
- No security concerns (local only)
- Works with Claude Desktop directly
- Lower latency

**Cons**:

- Local desktop only (no cloud deployment)
- Cannot run web-based chatbot
- No browser access
- Limited scalability

**Rejected Because**: Need web-based UI and cloud deployment capability.

### Alternative 3: WebSockets

**Approach**: Use WebSockets for bidirectional communication.

**Pros**:

- True bidirectional streaming
- Persistent connection
- Lower latency than HTTP

**Cons**:

- Not part of MCP specification
- Custom protocol implementation needed
- More complex than HTTP
- No Spring AI starter support

**Rejected Because**: Not MCP-compliant, too much custom implementation.

### Alternative 4: gRPC

**Approach**: Use gRPC for efficient RPC.

**Pros**:

- Efficient binary protocol
- Bidirectional streaming
- Strong typing

**Cons**:

- Not part of MCP specification
- Incompatible with MCP protocol
- No Spring AI MCP starter support
- Different tooling ecosystem

**Rejected Because**: Not MCP-compliant, completely different protocol.

## Implementation Notes

### MCP Server Endpoint Structure

**Auto-exposed by Spring AI**:

- `GET /mcp/info` - Server information
- `GET /mcp/tools` - List available tools
- `POST /mcp/tools/{toolName}` - Invoke specific tool
- `GET /mcp/resources` - List available resources (if any)

**Security Applied**:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").authenticated()  // Requires JWT
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

### MCP Client Request Example

**HTTP Request**:

```http
POST /mcp/tools/getCustomers HTTP/1.1
Host: localhost:8082
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "limit": 100,
  "skip": 0,
  "sort": null,
  "customQuery": null
}
```

**HTTP Response**:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "result": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "name": "John Doe",
      "email": "john@example.com"
    }
  ]
}
```

### Tool Registration

Tools automatically discovered from `@Tool` annotations:

```java
@Component
public class ResOsService {

    @Tool(description = "Fetch customer records with optional filtering")
    public List<Customer> getCustomers(
            @ToolParam(description = "Max records") Integer limit,
            @ToolParam(description = "Offset") Integer skip) {
        return resOsApi.customersGet(limit, skip, null, null);
    }
}
```

Spring AI automatically:

1. Scans for `@Tool` methods
2. Generates JSON Schema from signatures
3. Exposes HTTP endpoints under `/mcp/tools/{methodName}`
4. Handles parameter binding and result serialization

### Error Handling

**Client-side**:

```java
try {
    var result = mcpClient.invoke("getCustomers", arguments);
    return result;
} catch (HttpClientErrorException e) {
    logger.error("MCP tool invocation failed: {}", e.getMessage());
    throw new ToolExecutionException("Failed to invoke tool", e);
}
```

**Server-side**:

```java
@Tool
public List<Customer> getCustomers(...) {
    try {
        return backendApi.customersGet(...);
    } catch (Exception e) {
        logger.error("Backend API call failed", e);
        throw new McpToolException("Failed to fetch customers", e);
    }
}
```

## Performance Characteristics

| Metric                 | HTTP Streamable | SSE             | WebSocket        |
| ---------------------- | --------------- | --------------- | ---------------- |
| Connection per request | Yes             | No (persistent) | No (persistent)  |
| Bidirectional          | Yes             | No              | Yes              |
| OAuth2 support         | Excellent       | Poor            | Good             |
| MCP spec compliance    | ✅ Standard     | ⚠️ Deprecated   | ❌ Not supported |
| Spring AI support      | ✅ Full         | ⚠️ Limited      | ❌ None          |
| Latency overhead       | ~5-10ms         | ~1ms            | ~1ms             |

**For MCP use case (infrequent tool calls)**: HTTP Streamable's connection overhead is negligible.

## Lessons Learned

1. **Follow the Spec**: MCP specification guidance is correct
2. **Verify Deprecation Status**: Check spec for current transport recommendations
3. **Security First**: OAuth2 Bearer tokens simpler with standard HTTP
4. **Trust Autoconfiguration**: Spring AI starters know the best transport
5. **Don't Optimize Prematurely**: Connection overhead not a bottleneck for this use case

## Related Decisions

- [ADR-004: WebMVC over WebFlux](004-webmvc-over-webflux.md) - WebMVC enables HTTP Streamable
- [ADR-001: OpenAPI-First](001-openapi-first.md) - API contract for backend calls

## References

- [MCP Specification - Transports](https://modelcontextprotocol.info/specification/draft/basic/transports/)
- [MCP Authorization Specification](https://modelcontextprotocol.info/specification/draft/basic/authorization/)
- [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [Spring AI MCP Client Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
- [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)

## Decision Date

January 2026 (Phase 0 Migration)

## Reviewers

- Chris Phillipson (Architect)

## Changelog

| Date     | Change                                          |
| -------- | ----------------------------------------------- |
| Jan 2026 | Initial decision, SSE→HTTP Streamable migration |
