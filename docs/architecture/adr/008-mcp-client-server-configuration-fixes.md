# ADR-008: MCP Client-Server Configuration Fixes

## Status

**Accepted** - Implemented January 2026

## Context

Following the OAuth2 and session fixes documented in ADR-007, the chat functionality still wasn't working. The MCP client was either not creating clients at startup or failing to connect to the MCP server. This ADR documents the systematic debugging and fixes required to get the MCP client-server communication working with Spring AI 2.0 and OAuth2 authentication.

### Problem Statement

After resolving OAuth2 login and session issues, chat requests showed:

```text
MCP Clients available: 0
NO MCP clients available! Tools will not work.
Tool callbacks registered: 0
```

The AI would respond but had no access to MCP tools, making it unable to fetch restaurant data.

## Decision

Multiple configuration and code changes were required across both mcp-client and mcp-server.

### Issue 1: Wrong Transport Property Name

**Symptom**: `MCP Clients available: 0` at runtime

**Root Cause**: The configuration used `http` as the transport key, but Spring AI 2.0 expects `streamable-http`.

**What We Tried (Failed)**:

- Setting `initialized: true` - caused startup failures
- Various permutations of `http` configuration - never recognized

**Solution**:

```yaml
# WRONG - not recognized by Spring AI
spring.ai.mcp.client.http.connections.butler.url: http://localhost:8082

# CORRECT - proper transport key
spring.ai.mcp.client.streamable-http.connections.butler.url: http://localhost:8082
```

**File Changed**: `mcp-client/src/main/resources/application.yml`

**Key Learning**: Spring AI MCP client starter recognizes these transport types:

- `streamable-http` - HTTP Streamable transport (current standard)
- `sse` - Server-Sent Events (deprecated)
- `stdio` - Standard I/O (for local processes)

The `http` key is NOT valid and will be silently ignored, resulting in zero MCP clients.

### Issue 2: OAuth2 Token Timing with Startup Initialization

**Symptom**: With `initialized: true`, startup failed with:

```text
Client failed to initialize by explicit API call
McpTransportException: Server Not Found. Status code:404, path:"/mcp"
```

**Root Cause**: The `mcp-security` library requires lazy initialization because OAuth2 client credentials tokens aren't available at application startup.

**What We Tried (Failed)**:

- `initialized: true` - OAuth2 context not ready at startup, initialization fails
- `initialized: false` without manual init - clients exist but never initialized, tools don't work

**Solution**:

1. Set `initialized: false` in configuration:

```yaml
spring:
  ai:
    mcp:
      client:
        initialized: false # Required for OAuth2 timing
```

1. Implement lazy initialization in `McpSyncClientManager`:

```java
@Component
public class McpSyncClientManager {
    private final ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider;
    private final ConcurrentMap<McpSyncClient, Boolean> initializedClients = new ConcurrentHashMap<>();

    public List<McpSyncClient> newMcpSyncClients() {
        List<McpSyncClient> clients = mcpSyncClientsProvider.getIfAvailable(List::of);
        for (McpSyncClient client : clients) {
            initializeIfNeeded(client);
        }
        return clients;
    }

    private void initializeIfNeeded(McpSyncClient client) {
        initializedClients.computeIfAbsent(client, c -> {
            c.initialize();  // Now OAuth2 tokens are available
            return true;
        });
    }
}
```

**Files Changed**:

- `mcp-client/src/main/resources/application.yml`
- `mcp-client/src/main/java/me/pacphi/ai/resos/service/McpSyncClientManager.java`

**Key Learning**: From `mcp-security` documentation: "Ensure that you do not initialize the clients on startup" - OAuth2 client credentials flow must complete before MCP initialization.

### Issue 3: MCP Server Not Exposing /mcp Endpoint

**Symptom**: Even with OAuth2 tokens working, server returned 404:

```text
NoResourceFoundException: No static resource mcp for request '/mcp'
Mapped to ResourceHttpRequestHandler [classpath [META-INF/resources/]...]
```

**Root Cause**: The MCP server autoconfiguration wasn't creating the MCP controller. The `/mcp` path was falling through to static resource handling.

**Diagnosis via Server Logs**:

```text
o.s.w.s.handler.SimpleUrlHandlerMapping  : Mapped to ResourceHttpRequestHandler
o.s.w.s.r.ResourceHttpRequestHandler     : Resource not found
```

**What We Tried (Failed)**:

- Assuming defaults would work - they didn't
- Comment in config claimed "protocol: STREAMABLE is the default" - incorrect

**Solution**: Explicit configuration in mcp-server:

```yaml
spring:
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.2-modified
        type: SYNC # Explicit sync type
        protocol: STREAMABLE # Explicit protocol
        streamable-http:
          mcp-endpoint: /mcp # Explicit endpoint
```

**File Changed**: `mcp-server/src/main/resources/application.yml`

**Key Learning**: Don't trust comments about defaults. Explicitly configure:

- `type: SYNC` or `ASYNC`
- `protocol: STREAMABLE`, `SSE`, or `STATELESS`
- `streamable-http.mcp-endpoint: /mcp`

### Issue 4: Spring Security 7 Logout Configuration

**Symptom**: Navigating to `/logout` showed "No static resource logout" error

**Root Cause**:

1. Spring Security default logout requires POST (CSRF protection)
2. Browser navigation is GET
3. `AntPathRequestMatcher` was removed in Spring Security 7

**What We Tried (Failed)**:

```java
// COMPILATION ERROR - class removed in Spring Security 7
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
.logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
```

**Solution**: Lambda-based RequestMatcher:

```java
.logout(logout -> logout
    .logoutUrl("/logout")
    .logoutRequestMatcher(request ->
        request.getServletPath().equals("/logout"))
    .logoutSuccessHandler(logoutSuccessHandler())
    .invalidateHttpSession(true)
    .clearAuthentication(true)
    .deleteCookies("JSESSIONID")
)
```

**File Changed**: `mcp-client/src/main/java/me/pacphi/ai/resos/config/SecurityConfig.java`

**Key Learning**: Spring Security 7 removed `AntPathRequestMatcher`. Use:

- Lambda-based `RequestMatcher` for simple patterns
- `PathPatternRequestMatcher` for complex patterns

## Consequences

### Positive

1. **MCP Communication Works**: Client successfully connects to server with OAuth2 authentication
2. **Tools Registered**: AI can access restaurant data via MCP tools
3. **Lazy Initialization**: OAuth2 timing issues resolved
4. **Logout Works**: Users can logout via browser navigation

### Negative

1. **Configuration Complexity**: Multiple explicit settings required across client and server
2. **Documentation Gaps**: Spring AI MCP defaults are unclear; explicit config is safer
3. **Debugging Difficulty**: Silent failures (wrong property names ignored) hard to diagnose

### Lessons Learned

1. **Always Check Transport Property Names**: `streamable-http` not `http`
2. **OAuth2 + MCP = Lazy Init**: Set `initialized: false` and manually initialize
3. **Explicit > Implicit**: Don't trust default comments; configure explicitly
4. **Check Server Logs**: The 404 vs 401 distinction revealed the real issue
5. **Spring Security 7 Breaking Changes**: `AntPathRequestMatcher` removed; use lambdas

## Files Changed Summary

| File                                                     | Changes                                                     |
| -------------------------------------------------------- | ----------------------------------------------------------- |
| `mcp-client/src/main/resources/application.yml`          | `http` → `streamable-http`, `initialized: false`            |
| `mcp-client/src/main/java/.../McpSyncClientManager.java` | Added lazy initialization logic                             |
| `mcp-client/src/main/java/.../SecurityConfig.java`       | Lambda-based logout RequestMatcher                          |
| `mcp-server/src/main/resources/application.yml`          | Explicit `type`, `protocol`, `streamable-http.mcp-endpoint` |
| `docs/architecture/adr/005-http-streamable-transport.md` | Updated to reflect correct config                           |

## Testing Recommendations

### Unit Tests Needed

1. **McpSyncClientManager**:
   - Test lazy initialization only happens once per client
   - Test thread safety of concurrent initialization
   - Test behavior when initialization fails

2. **SecurityConfig**:
   - Test logout works with GET request
   - Test session invalidation on logout
   - Test cookie deletion on logout

### Integration Tests Needed

1. **MCP Client-Server**:
   - Test tool discovery after lazy initialization
   - Test OAuth2 token included in MCP requests
   - Test error handling when server unavailable

2. **End-to-End**:
   - Test chat with tool calling
   - Test login → chat → logout flow

### Test Configuration Note (January 2026)

MCP client autoconfiguration is **disabled in tests** via `spring.ai.mcp.client.enabled: false`
in `mcp-client/src/test/resources/application-test.yml`.

**Rationale**: The mcp-client tests use TestContainers to start a backend OAuth2 server, but
adding an mcp-server container introduces significant complexity:
- Multi-container Docker networking
- OAuth2 token validation between containers
- Container startup timing issues

The current tests verify MCP client configuration beans exist without requiring actual MCP
server connectivity. `McpSyncClientManager.newMcpSyncClients()` returns an empty list in tests.

Full MCP end-to-end testing is deferred as a future enhancement. See [TESTS.md](../../../TESTS.md)
troubleshooting section for details.

## Related Decisions

- [ADR-005: HTTP Streamable Transport](005-http-streamable-transport.md) - Updated with correct property names
- [ADR-006: OAuth2 Authorization Server](006-oauth2-authorization-server-integration.md) - OAuth2 foundation
- [ADR-007: Frontend API Session Fixes](007-frontend-api-session-and-sse-fixes.md) - Session/SSE fixes

## References

- [Spring AI MCP Client Boot Starter Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html)
- [Spring AI MCP Server WebMVC Docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html)
- [spring-ai-community/mcp-security README](https://github.com/spring-ai-community/mcp-security)
- Spring Security 7 Migration Guide (AntPathRequestMatcher removal)

## Decision Date

January 2026

## Changelog

| Date     | Change                                          |
| -------- | ----------------------------------------------- |
| Jan 2026 | Initial ADR documenting MCP client-server fixes |
