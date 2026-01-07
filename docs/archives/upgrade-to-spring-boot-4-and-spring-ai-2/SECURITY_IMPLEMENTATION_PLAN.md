# Spring AI Resos Security Implementation Plan

## Overview

Implement comprehensive OAuth2 security using Spring Authorization Server embedded in the backend, with:

- **Backend**: OAuth2 Authorization Server + Resource Server (port 8080)
- **MCP-Server**: OAuth2 Resource Server + OAuth2 Client (port 8082)
- **MCP-Client**: OAuth2 Client with authorization_code + PKCE for React SPA (port 8081)
- **Transport**: HTTP Streamable (WebMVC-based) instead of SSE (WebFlux)
- **Roles**: USER, OPERATOR, ADMIN with granular permissions
- **Credentials**: BCrypt-hashed passwords in PostgreSQL/H2

---

## Archived: SSE + WebFlux Approach (Unsuccessful)

### What Was Attempted

The original implementation used WebFlux-based reactive stack with SSE transport:

- **mcp-server**: `spring-ai-starter-mcp-server-webflux`
- **mcp-client**: `spring-ai-starter-mcp-client-webflux` + `spring-boot-starter-webflux`
- **Transport**: Server-Sent Events (SSE) at `/sse` endpoint

### Why It Failed

1. **Spring Security Default Behavior**: Adding `spring-boot-starter-oauth2-client` to mcp-server brought Spring Security onto the classpath. By default, Spring Security protects ALL endpoints, causing unauthenticated requests to `/sse` to receive 302 redirects to the OAuth2 login page.

2. **MCP Security Library Incompatibility**: The official [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security) library states:

   > "Requires Spring WebMVC (not WebFlux); SSE transport unsupported"
   > "The deprecated SSE transport is not supported. Use Streamable HTTP or stateless transport instead."

3. **MCP Specification Evolution**: The [MCP Authorization Specification](https://modelcontextprotocol.info/specification/draft/basic/authorization/) recommends OAuth 2.1 for HTTP-based transports. The June 2025 spec update deprecated SSE in favor of Streamable HTTP.

### Files Created (To Be Migrated)

| File                                                      | Status                                                           |
| --------------------------------------------------------- | ---------------------------------------------------------------- |
| `mcp-server/src/main/java/.../ResOsConfig.java`           | Has reactive WebClient - needs WebMVC conversion                 |
| `mcp-client/src/main/java/.../McpAsyncClientManager.java` | Uses `WebFluxSseClientTransport` - needs replacement             |
| `mcp-client/src/main/java/.../ChatService.java`           | Returns `Flux<String>` - needs blocking conversion               |
| `mcp-client/src/main/java/.../ChatController.java`        | Returns `ResponseEntity<Flux<String>>` - needs conversion        |
| `mcp-client/src/main/java/.../AuthController.java`        | Uses `Mono<>` and `ServerWebExchange` - needs servlet conversion |
| `mcp-client/src/main/java/.../SecurityConfig.java`        | Uses `@EnableWebFluxSecurity` - needs `@EnableWebSecurity`       |

### Lessons Learned

- Always verify library compatibility before choosing transport (SSE vs Streamable HTTP)
- Spring Security autoconfiguration affects ALL endpoints by default
- The MCP ecosystem is moving toward Streamable HTTP as the standard

---

## New Architecture: WebMVC + Streamable HTTP

### Reference Materials

- [Securing Spring AI MCP Servers With OAuth2 (Baeldung)](https://www.baeldung.com/spring-ai-mcp-servers-oauth2)
- [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security)
- [MCP Authorization Specification](https://modelcontextprotocol.info/specification/draft/basic/authorization/)

### Architecture Diagram

```text
┌─────────────────────┐     OAuth2 (auth_code+PKCE)    ┌─────────────────────────────────────┐
│   React SPA         │ ◄─────────────────────────────►│           Backend (8080)            │
│   (mcp-client)      │                                │  ┌─────────────────────────────────┐│
│   port 8081         │                                │  │   Authorization Server          ││
│   WebMVC + Tomcat   │                                │  │   /oauth2/*, /login, /userinfo  ││
└─────────────────────┘                                │  └─────────────────────────────────┘│
         │                                             │  ┌─────────────────────────────────┐│
         │ MCP Protocol                                │  │   Resource Server               ││
         │ (Streamable HTTP)                           │  │   /customers, /bookings, etc.   ││
         ▼                                             │  └─────────────────────────────────┘│
┌─────────────────────┐     OAuth2 (client_credentials)│  ┌─────────────────────────────────┐│
│   MCP-Server        │ ──────────────────────────────►│  │   PostgreSQL / H2               ││
│   port 8082         │                                │  │   Users, Roles, OAuth2 tables   ││
│   WebMVC + Tomcat   │◄── JWT Bearer Token ──────────►│  └─────────────────────────────────┘│
│   OAuth2 Resource   │                                └─────────────────────────────────────┘
│   Server            │
└─────────────────────┘
```

---

## Phase 0: WebFlux to WebMVC Migration

### 0.1 MCP-Server Module Changes

#### `mcp-server/pom.xml`

**Remove:**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
</dependency>
```

**Add:**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

#### `mcp-server/src/main/resources/application.yml`

**Update transport configuration:**

```yaml
spring:
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.2-modified
        # Remove: stdio: true (was for STDIO mode)
        # Streamable HTTP is the default for WebMVC starter

  # Remove web-application-type: none (server now runs HTTP by default)
```

#### `mcp-server/src/main/java/.../ResOsConfig.java`

**Convert from reactive WebClient to RestClient:**

| Before (Reactive)                                    | After (Blocking)                                   |
| ---------------------------------------------------- | -------------------------------------------------- |
| `WebClient.Builder`                                  | `RestClient.Builder`                               |
| `ReactorClientHttpConnector`                         | `JdkClientHttpRequestFactory` or Apache HttpClient |
| `Mono.just(request)`                                 | Direct return                                      |
| `ServerOAuth2AuthorizedClientExchangeFilterFunction` | `OAuth2ClientHttpRequestInterceptor`               |

### 0.2 MCP-Client Module Changes

#### `mcp-client/pom.xml`

**Remove:**

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

**Add:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-spring-boot-starter</artifactId>
</dependency>
```

#### Java File Conversions

| File                         | Changes Required                                                                                  |
| ---------------------------- | ------------------------------------------------------------------------------------------------- |
| `ChatController.java`        | `Flux<String>` → `SseEmitter` or streaming `ResponseEntity<StreamingResponseBody>`                |
| `ChatService.java`           | `Flux<String>` → `Stream<String>` or callback-based streaming                                     |
| `McpAsyncClientManager.java` | `WebFluxSseClientTransport` → `HttpClientSseClientTransport` or Streamable HTTP transport         |
| `AuthController.java`        | `Mono<>` → direct return, `ServerWebExchange` → `HttpServletRequest`                              |
| `SecurityConfig.java`        | `@EnableWebFluxSecurity` → `@EnableWebSecurity`, `SecurityWebFilterChain` → `SecurityFilterChain` |
| `Http.java`                  | Remove `reactor.netty` imports, use JDK HttpClient or Apache                                      |

### 0.3 Client Module (Generated API Client)

The `client/` module has both WebMVC and WebFlux dependencies. Evaluate whether WebFlux is actually needed for the generated HTTP client or if it can be purely servlet-based.

---

## Phase 1: Database Schema (Liquibase)

_[Unchanged from original plan]_

### Security Tables

| Table                          | Purpose                                      |
| ------------------------------ | -------------------------------------------- |
| `app_user`                     | Application users with BCrypt passwords      |
| `authority`                    | Roles (ROLE_USER, ROLE_OPERATOR, ROLE_ADMIN) |
| `user_authority`               | Join table                                   |
| `oauth2_registered_client`     | OAuth2 clients                               |
| `oauth2_authorization`         | Active tokens                                |
| `oauth2_authorization_consent` | User consents                                |

---

## Phase 2: Backend Security (Authorization Server)

_[Unchanged from original plan]_

### Key Configuration Classes

- `AuthorizationServerConfig.java` - OAuth2 Authorization Server
- `ResourceServerConfig.java` - Protects `/api/**` endpoints
- `DefaultSecurityConfig.java` - Form login for browser auth
- `AppUserDetailsService.java` - User lookup from database
- `JwtTokenCustomizer.java` - Custom JWT claims

---

## Phase 3: MCP-Server Security (Resource Server)

Following the [Baeldung pattern](https://www.baeldung.com/spring-ai-mcp-servers-oauth2):

### Create `mcp-server/src/main/java/.../SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class McpServerSecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").authenticated()
                // Note: No /sse with Streamable HTTP
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .csrf(CsrfConfigurer::disable)
            .cors(Customizer.withDefaults())
            .build();
    }
}
```

### Update `mcp-server/src/main/resources/application.yml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_SERVER_URL:http://localhost:8080}
```

---

## Phase 4: MCP-Client Security

### Create `mcp-client/src/main/java/.../SecurityConfig.java`

**WebMVC version (not WebFlux):**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/api/auth/status", "/api/auth/login-url").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true))
            .oauth2Client(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .build();
    }
}
```

### Update `mcp-client/src/main/java/.../AuthController.java`

**Convert from reactive to servlet:**

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getFullName());
        userInfo.put("email", user.getEmail());
        userInfo.put("roles", user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList());
        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/status")
    public Map<String, Object> getAuthStatus(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);
        return Map.of("authenticated", authenticated);
    }
}
```

### MCP Client with OAuth2 Tokens

Configure the MCP client to send Bearer tokens when connecting to mcp-server:

```java
@Configuration
public class McpClientConfig {

    @Bean
    public McpSyncClient mcpClient(
            OAuth2AuthorizedClientManager authorizedClientManager) {

        // Create HTTP client with OAuth2 interceptor
        RestClient restClient = RestClient.builder()
            .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
                authorizedClientManager, "mcp-client"))
            .build();

        // Create MCP client with authenticated transport
        return McpClient.sync(new HttpClientTransport(restClient))
            .clientInfo(new Implementation("mcp-frontend", "1.0"))
            .build();
    }
}
```

---

## Phase 5: Chat Streaming Conversion

### Option A: SseEmitter (Recommended for AI chat)

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Inquiry inquiry) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        chatService.streamResponse(inquiry.question(), token -> {
            try {
                emitter.send(SseEmitter.event().data(token));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }, emitter::complete, emitter::completeWithError);

        return emitter;
    }
}
```

### Option B: StreamingResponseBody

```java
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<StreamingResponseBody> streamChat(@RequestBody Inquiry inquiry) {
    StreamingResponseBody body = outputStream -> {
        chatService.streamResponse(inquiry.question(), token -> {
            try {
                outputStream.write(("data: " + token + "\n\n").getBytes());
                outputStream.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    };
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(body);
}
```

---

## Phase 6: React SPA Updates

### 6.1 Authentication Flow Changes

The React SPA currently uses WebFlux-based endpoints. With WebMVC:

- **Auth endpoints remain similar**: `/api/auth/user`, `/api/auth/status`, `/api/auth/login-url`
- **Response format unchanged**: JSON responses work the same
- **OAuth2 redirect flow unchanged**: Browser-based OAuth2 still works

### 6.2 Chat Streaming Changes

The main impact is how streaming responses are consumed:

#### Current (WebFlux Flux)

```javascript
// App.jsx - current implementation
const eventSource = new EventSource('/api/chat/stream?...');
// OR
fetch('/api/chat', { method: 'POST', ... })
  .then(response => response.body.getReader())
```

#### Updated (WebMVC SseEmitter)

The frontend code **should not need changes** if using standard SSE:

- `SseEmitter` produces standard `text/event-stream` format
- Browser's `EventSource` API works identically
- `fetch()` with streaming also works the same

### 6.3 Files to Review

| File                                                | Potential Changes                            |
| --------------------------------------------------- | -------------------------------------------- |
| `mcp-client/src/main/frontend/src/App.jsx`          | Verify SSE consumption works with SseEmitter |
| `mcp-client/src/main/frontend/src/AuthContext.jsx`  | Should work unchanged (JSON endpoints)       |
| `mcp-client/src/main/frontend/src/components/*.jsx` | Review any direct API calls                  |

### 6.4 CORS Configuration

With WebMVC, CORS is configured differently:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173", "http://localhost:8081")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
```

### 6.5 Proxy Configuration

If using Vite dev server proxy:

```javascript
// vite.config.js - should remain unchanged
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
```

### 6.6 Testing React Changes

- [ ] Verify OAuth2 login redirect works
- [ ] Verify auth status endpoint returns correct format
- [ ] Verify user info endpoint returns correct format
- [ ] Verify chat streaming works with new SseEmitter backend
- [ ] Verify logout flow works
- [ ] Test with both dev server proxy and production build

---

## Implementation Checklist

### Phase 0: WebFlux → WebMVC Migration

- [ ] Update `mcp-server/pom.xml` - swap webflux for webmvc starter
- [ ] Update `mcp-client/pom.xml` - swap webflux for web starter
- [ ] Convert `ResOsConfig.java` to use RestClient
- [ ] Convert `ChatController.java` to SseEmitter
- [ ] Convert `ChatService.java` to callback-based streaming
- [ ] Convert `McpAsyncClientManager.java` to sync MCP client
- [ ] Convert `AuthController.java` to servlet API
- [ ] Convert `SecurityConfig.java` to WebMVC security
- [ ] Update `application.yml` files to remove reactive config

### Phase 1: Database Schema

- [ ] Create security tables changelog
- [ ] Create seed data changelog
- [ ] Update master changelog

### Phase 2: Backend Authorization Server

- [ ] Add dependencies to backend pom.xml
- [ ] Create AuthorizationServerConfig.java
- [ ] Create ResourceServerConfig.java
- [ ] Create DefaultSecurityConfig.java
- [ ] Create AppUserDetailsService.java
- [ ] Create login.html template

### Phase 3: MCP-Server OAuth2 Resource Server

- [ ] Add oauth2-resource-server dependency
- [ ] Create SecurityConfig.java with JWT validation
- [ ] Update application.yml with JWT issuer config
- [ ] Test token validation

### Phase 4: MCP-Client OAuth2 Client

- [ ] Create WebMVC SecurityConfig.java
- [ ] Configure OAuth2 client for mcp-server calls
- [ ] Update React SPA auth integration
- [ ] Test end-to-end auth flow

### Phase 5: Integration Testing

- [ ] Test backend auth endpoints
- [ ] Test mcp-server with JWT tokens
- [ ] Test mcp-client OAuth2 login flow
- [ ] Test React SPA authentication
- [ ] Test full chat flow with security

### Phase 6: React SPA Updates

- [ ] Review `App.jsx` chat streaming implementation
- [ ] Review `AuthContext.jsx` for API compatibility
- [ ] Add CORS configuration (WebMvcConfigurer)
- [ ] Verify Vite proxy configuration
- [ ] Test OAuth2 login/logout flow in browser
- [ ] Test chat streaming with SseEmitter backend

---

## Key Dependencies Summary

| Module     | Old (WebFlux)                          | New (WebMVC)                                      |
| ---------- | -------------------------------------- | ------------------------------------------------- |
| mcp-server | `spring-ai-starter-mcp-server-webflux` | `spring-ai-mcp-server-webmvc-spring-boot-starter` |
| mcp-server | -                                      | `spring-boot-starter-oauth2-resource-server`      |
| mcp-client | `spring-boot-starter-webflux`          | `spring-boot-starter-web`                         |
| mcp-client | `spring-ai-starter-mcp-client-webflux` | `spring-ai-mcp-client-spring-boot-starter`        |

---

## Security Best Practices

- BCrypt password hashing (cost factor 12)
- JWT tokens signed with RSA-256
- PKCE for public clients (React SPA)
- HTTP-only cookies for session
- Short-lived access tokens (1 hour)
- Refresh tokens with rotation
- CORS configured for specific origins
- CSRF protection for browser requests
- Scope-based and role-based access control
- Database-backed token storage (revocation support)
- MCP endpoints protected with JWT validation
