# ADR-006: OAuth2 Authorization Server Integration and Debugging

## Status

**Accepted** - Implemented across backend and mcp-client modules

## Context

The Spring AI ResOS application uses a three-tier OAuth2 architecture:

1. **Backend (port 8080)**: Spring Authorization Server acting as the OAuth2/OIDC provider
2. **MCP-Client (port 8081)**: Frontend application using OAuth2 Login with Authorization Code + PKCE flow
3. **MCP-Server (port 8082)**: Resource server protected by OAuth2 tokens

### Problem Statement

After implementing the OAuth2 infrastructure, the login flow was broken. Users could not authenticate through the mcp-client application. The symptoms manifested as:

1. Initial redirect to login working, but authentication failing silently
2. Infinite redirect loops after successful login
3. `authorization_request_not_found` errors during callback processing

### Architecture Overview

```text
User Browser
     │
     ▼
┌─────────────────┐      ┌─────────────────┐
│   MCP-Client    │◄────►│     Backend     │
│   (port 8081)   │      │   (port 8080)   │
│                 │      │                 │
│ OAuth2 Client   │      │ Authorization   │
│ (PKCE flow)     │      │ Server + Login  │
└─────────────────┘      └─────────────────┘
         │                        │
         ▼                        ▼
┌─────────────────┐      ┌─────────────────┐
│   MCP-Server    │      │    Database     │
│   (port 8082)   │      │   (H2/Postgres) │
│                 │      │                 │
│ Resource Server │      │ OAuth2 tables   │
└─────────────────┘      └─────────────────┘
```

## Decision

**Implement a fully working OAuth2 Authorization Code flow with PKCE, fixing multiple integration issues discovered through debugging.**

### Issues Discovered and Fixed

#### Issue 1: Missing Authentication Entry Point for Authorization Server

**Symptom**: After visiting mcp-client, user was redirected to backend's `/oauth2/authorize`, but received a 401 status instead of being redirected to the login page.

**Root Cause**: The `AuthorizationServerConfig` security filter chain (at `@Order(1)`) required authentication for all requests but didn't specify how to handle unauthenticated browser requests. Spring Security defaulted to `HttpStatusEntryPoint` which returns 401.

**Backend Logs**:

```text
No match found. Using default entry point org.springframework.security.web.authentication.HttpStatusEntryPoint@5a3e2166
```

**Fix** (`AuthorizationServerConfig.java`):

```java
http
    .securityMatcher(endpointsMatcher)
    .with(authorizationServerConfigurer, authServer -> authServer
        .oidc(Customizer.withDefaults())
    )
    .authorizeHttpRequests(authorize -> authorize
        .anyRequest().authenticated()
    )
    // ADD THIS: Redirect browser requests to login page
    .exceptionHandling(exceptions -> exceptions
        .defaultAuthenticationEntryPointFor(
            new LoginUrlAuthenticationEntryPoint("/login"),
            new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
        )
    )
    .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher));
```

**Imports Added**:

```java
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;
```

---

#### Issue 2: Login Success Redirecting to Swagger Instead of OAuth2 Flow

**Symptom**: After successful login, user was redirected to Swagger UI instead of completing the OAuth2 authorization flow.

**Root Cause**: `DefaultSecurityConfig` had `.defaultSuccessUrl("/", true)` with `true` parameter, which means "always use this URL, ignoring any saved request". This overrode the saved OAuth2 authorization request.

**Fix** (`DefaultSecurityConfig.java`):

```java
// Before: Always redirect to "/" regardless of saved request
.formLogin(form -> form
    .loginPage("/login")
    .defaultSuccessUrl("/", true)  // BAD: ignores saved request
    .permitAll()
)

// After: Only use "/" as fallback when no saved request exists
.formLogin(form -> form
    .loginPage("/login")
    .defaultSuccessUrl("/", false)  // GOOD: respects saved OAuth2 request
    .permitAll()
)
```

---

#### Issue 3: Missing `oauth2_authorization_consent` Database Table

**Symptom**: After login and redirect to `/oauth2/authorize`, a 500 Internal Server Error occurred.

**Backend Logs**:

```text
org.h2.jdbc.JdbcSQLSyntaxErrorException: Table "oauth2_authorization_consent" not found
```

**Root Cause**: The `OAuth2AuthorizationConsentEntity` exists but has a composite primary key (`registered_client_id` + `principal_name`) instead of a single `@Id` field. The `SchemaCreator` skips entities without `@Id` annotations (see ADR-003), so no Liquibase changelog was generated for this table.

**Fix**: Created a manual Liquibase patch file.

**New File** (`backend/src/main/resources/db/changelog/patches/003_create_oauth2_authorization_consent.yml`):

```yaml
databaseChangeLog:
  - changeSet:
      id: 003_create_oauth2_authorization_consent
      author: security-patch
      comment: Create oauth2_authorization_consent table for Spring Authorization Server
      preConditions:
        - onFail: MARK_RAN
        - not:
            tableExists:
              tableName: oauth2_authorization_consent
      changes:
        - createTable:
            tableName: oauth2_authorization_consent
            columns:
              - column:
                  name: registered_client_id
                  type: varchar(100)
                  constraints:
                    nullable: false
              - column:
                  name: principal_name
                  type: varchar(200)
                  constraints:
                    nullable: false
              - column:
                  name: authorities
                  type: varchar(1000)
                  constraints:
                    nullable: false
        - addPrimaryKey:
            tableName: oauth2_authorization_consent
            columnNames: registered_client_id, principal_name
            constraintName: pk_oauth2_authorization_consent
```

**Note**: The `SchemaCreator` automatically picks up patch files from `classpath:db/changelog/patches/*.yml` and includes them in the master changelog.

---

#### Issue 4: JwtDecoder Fetching from Remote URL

**Symptom**: Potential startup issues and unnecessary HTTP calls.

**Root Cause**: The `JwtDecoder` bean was configured to fetch JWK keys from a remote URL (the server itself), which is unnecessary when the `JWKSource` is available locally.

**Fix** (`AuthorizationServerConfig.java`):

```java
// Before: Fetches from remote URL (causes HTTP call to self)
@Bean
public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return NimbusJwtDecoder
        .withJwkSetUri(issuerUri + "/oauth2/jwks")
        .build();
}

// After: Uses local JWK source directly
@Bean
public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return NimbusJwtDecoder.withJwkSource(jwkSource).build();
}
```

---

#### Issue 5: Infinite Redirect Loop After Login

**Symptom**: After successful login and authorization code generation, the mcp-client kept redirecting back to the authorization server with new state parameters, creating an infinite loop.

**Backend Logs**:

```text
Redirecting to http://localhost:8081/login/oauth2/code/frontend-app?code=...&state=ABC
[immediately followed by]
Securing GET /oauth2/authorize?...&state=XYZ  (different state!)
Set SecurityContextHolder to anonymous SecurityContext
```

**Root Cause**: Multiple configuration issues in mcp-client:

1. **Wrong JWK Set URI**: mcp-client was configured to fetch JWKs from `/.well-known/jwks.json` but Spring Authorization Server serves them at `/oauth2/jwks`
2. **Missing `client-authentication-method`**: Public clients need `client-authentication-method: none`
3. **Wrong `user-name-attribute`**: Configured to use `preferred_username` which wasn't in the token

**Fix** (`mcp-client/src/main/resources/application.yml`):

```yaml
# Before
spring.security.oauth2.client:
  registration:
    frontend-app:
      client-id: ${OAUTH2_CLIENT_ID:frontend-app}
      # MISSING: client-authentication-method
      authorization-grant-type: authorization_code
      redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
      scope: openid,profile,email,chat.read,chat.write
  provider:
    frontend-app:
      authorization-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/authorize
      token-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/token
      user-info-uri: ${AUTH_SERVER_URL:http://localhost:8080}/userinfo
      jwk-set-uri: ${AUTH_SERVER_URL:http://localhost:8080}/.well-known/jwks.json  # WRONG
      user-name-attribute: preferred_username  # NOT IN TOKEN

# After
spring.security.oauth2.client:
  registration:
    frontend-app:
      client-id: ${OAUTH2_CLIENT_ID:frontend-app}
      client-authentication-method: none  # ADDED: Required for public clients
      authorization-grant-type: authorization_code
      redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
      scope: openid,profile,email,chat.read,chat.write
  provider:
    frontend-app:
      authorization-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/authorize
      token-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/token
      user-info-uri: ${AUTH_SERVER_URL:http://localhost:8080}/userinfo
      jwk-set-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/jwks  # FIXED
      user-name-attribute: sub  # FIXED: 'sub' is always present in OIDC tokens
```

---

#### Issue 6: Missing `preferred_username` Claim in ID Token

**Symptom**: Token didn't contain `preferred_username` claim that was expected.

**Root Cause**: The `JwtTokenCustomizer` only added `roles` to ID tokens, not OIDC standard claims.

**Fix** (`backend/src/main/java/.../security/JwtTokenCustomizer.java`):

```java
// For ID tokens, add additional user info
if (context.getTokenType().getValue().equals("id_token")) {
    Authentication principal = context.getPrincipal();

    // ADD: preferred_username claim (standard OIDC claim)
    context.getClaims().claim("preferred_username", principal.getName());

    // Existing: Add roles to ID token
    Set<String> roles = principal.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(auth -> auth.startsWith("ROLE_"))
        .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
        context.getClaims().claim("roles", roles);
    }
}
```

---

#### Issue 7: `authorization_request_not_found` Error

**Symptom**: Even after all previous fixes, the OAuth2 callback failed with:

```text
OAuth2AuthenticationException: [authorization_request_not_found]
```

**Root Cause**: The `HttpSessionOAuth2AuthorizationRequestRepository` (default) stores the OAuth2 authorization request (state, code_verifier, etc.) in the HTTP session. The session was being lost between:

1. Starting the OAuth2 flow (request to `/oauth2/authorization/frontend-app`)
2. Receiving the callback (request to `/login/oauth2/code/frontend-app`)

This happens because:

- Session ID might change between requests
- Session cookies might not be sent on cross-origin redirects
- Browser privacy features may block cookies

**Fix**: Implemented cookie-based authorization request storage.

**New File** (`mcp-client/src/main/java/.../config/HttpCookieOAuth2AuthorizationRequestRepository.java`):

```java
package me.pacphi.ai.resos.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;
import java.util.Base64;

public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180; // 3 minutes

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (cookie == null) {
            return null;
        }
        return deserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            return;
        }

        String serialized = serialize(authorizationRequest);
        Cookie cookie = new Cookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                  HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        }
        return authorizationRequest;
    }

    // Helper methods: getCookie, deleteCookie, serialize, deserialize
    // ... (see full implementation in codebase)
}
```

**SecurityConfig Update** (`mcp-client/src/main/java/.../config/SecurityConfig.java`):

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // ... other configuration ...
        .oauth2Login(oauth2 -> oauth2
            .authorizationEndpoint(authorization -> authorization
                .authorizationRequestRepository(cookieAuthorizationRequestRepository())
            )
            .successHandler(authenticationSuccessHandler())
            .failureHandler(authenticationFailureHandler())
        )
        // ... rest of configuration ...
        .build();
}

@Bean
public HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
    return new HttpCookieOAuth2AuthorizationRequestRepository();
}
```

---

#### Issue 8: Silent Authentication Failures Causing Redirect Loops

**Symptom**: Authentication failures weren't visible, causing silent redirect loops.

**Root Cause**: The custom `AuthenticationEntryPoint` redirected to `/oauth2/authorization/frontend-app` for all unauthenticated requests, including failed OAuth2 callbacks. This restarted the OAuth2 flow, creating an infinite loop.

**Fix**: Added an authentication failure handler with logging.

```java
@Bean
public AuthenticationFailureHandler authenticationFailureHandler() {
    return (request, response, exception) -> {
        logger.error("OAuth2 authentication failed: {}", exception.getMessage(), exception);
        if (exception.getCause() != null) {
            logger.error("Root cause: {}", exception.getCause().getMessage(), exception.getCause());
        }
        // Return error page instead of redirecting (breaks the loop)
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(
            "<html><body><h1>OAuth2 Authentication Failed</h1>" +
            "<p>Error: " + exception.getMessage() + "</p>" +
            "<p><a href='/oauth2/authorization/frontend-app'>Try again</a></p>" +
            "</body></html>"
        );
    };
}
```

## Consequences

### Positive

1. **Working OAuth2 Flow**: Users can authenticate through the mcp-client using the backend as authorization server
2. **PKCE Support**: Public client (no client secret) with PKCE for secure authorization code flow
3. **OIDC Compliance**: ID tokens include standard claims (`sub`, `preferred_username`)
4. **Session-Independent**: Cookie-based authorization request storage avoids session issues
5. **Debuggable**: Authentication failures are logged with full stack traces

### Negative

1. **Cookie Storage**: Authorization request stored in cookie (serialized, ~2KB) instead of session
2. **Additional Complexity**: Custom `AuthorizationRequestRepository` implementation
3. **Cookie Size**: Large OAuth2 authorization requests might exceed browser cookie limits

### Neutral

1. **Development Setup**: Requires all three services running (backend, mcp-client, mcp-server)
2. **Port Configuration**: Services must be on configured ports for redirects to work

## Files Modified

### Backend Module

| File                                                               | Change                                                 |
| ------------------------------------------------------------------ | ------------------------------------------------------ |
| `AuthorizationServerConfig.java`                                   | Added exception handling entry point, fixed JwtDecoder |
| `DefaultSecurityConfig.java`                                       | Fixed `defaultSuccessUrl` parameter                    |
| `JwtTokenCustomizer.java`                                          | Added `preferred_username` claim to ID tokens          |
| `db/changelog/patches/003_create_oauth2_authorization_consent.yml` | New file for missing table                             |

### MCP-Client Module

| File                                                  | Change                                                                                   |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `application.yml`                                     | Fixed `jwk-set-uri`, added `client-authentication-method`, changed `user-name-attribute` |
| `SecurityConfig.java`                                 | Added failure handler, configured cookie-based auth request repository                   |
| `HttpCookieOAuth2AuthorizationRequestRepository.java` | New file for cookie-based storage                                                        |

## Debugging Techniques Used

1. **Backend DEBUG Logging**: `logging.level.org.springframework.security: DEBUG`
2. **Request Tracing**: Following request flow through filter chain logs
3. **State Parameter Comparison**: Verifying state matches between authorization request and callback
4. **Session Analysis**: Checking if session IDs changed between requests
5. **Error Handler Addition**: Custom failure handler to capture and log exceptions
6. **Incremental Testing**: Testing each fix individually before proceeding

## Testing Recommendations

### Unit Tests

1. **AuthorizationServerConfig**: Test that entry point redirects browser requests to `/login`
2. **JwtTokenCustomizer**: Verify `preferred_username` claim is added to ID tokens
3. **HttpCookieOAuth2AuthorizationRequestRepository**: Test serialize/deserialize, cookie operations

### Integration Tests

1. **OAuth2 Authorization Flow**: End-to-end test from mcp-client through backend and back
2. **Token Exchange**: Test that authorization code can be exchanged for tokens
3. **Session Independence**: Test that flow works even if session changes

### Manual Testing Checklist

- [ ] Visit `http://localhost:8081` → redirected to login
- [ ] Enter credentials (`admin`/`admin123`) → login successful
- [ ] Redirected back to `/oauth2/authorize` → consent (if required) or direct redirect
- [ ] Redirected to mcp-client with authorization code
- [ ] mcp-client exchanges code for tokens
- [ ] User sees chat interface (authenticated state)

## Related Decisions

- [ADR-003: Dynamic Liquibase](003-dynamic-liquibase.md) - Explains why entities without `@Id` aren't auto-generated
- [ADR-004: WebMVC over WebFlux](004-webmvc-over-webflux.md) - Servlet-based security configuration

## References

- [Spring Authorization Server Documentation](https://docs.spring.io/spring-authorization-server/reference/)
- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [OAuth2 Authorization Code Flow with PKCE](https://oauth.net/2/pkce/)
- [Spring Security Exception Handling](https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-exceptiontranslationfilter)

## Decision Date

January 2026 (Session debugging)

## Reviewers

- Chris Phillipson (Architect)

## Changelog

| Date        | Change                                           |
| ----------- | ------------------------------------------------ |
| Jan 7, 2026 | Initial decision document from debugging session |

## Appendix: OAuth2 Flow Diagram

```text
┌──────────┐                              ┌──────────┐                              ┌──────────┐
│  Browser │                              │MCP-Client│                              │ Backend  │
│          │                              │ (8081)   │                              │ (8080)   │
└────┬─────┘                              └────┬─────┘                              └────┬─────┘
     │                                         │                                         │
     │ 1. GET /                                │                                         │
     │────────────────────────────────────────►│                                         │
     │                                         │                                         │
     │ 2. 302 → /oauth2/authorization/frontend-app                                       │
     │◄────────────────────────────────────────│                                         │
     │                                         │                                         │
     │ 3. GET /oauth2/authorization/frontend-app                                         │
     │────────────────────────────────────────►│                                         │
     │                                         │ Store auth request in cookie            │
     │                                         │ (state, code_verifier, nonce)           │
     │ 4. 302 → http://localhost:8080/oauth2/authorize?...                               │
     │◄────────────────────────────────────────│                                         │
     │                                         │                                         │
     │ 5. GET /oauth2/authorize?response_type=code&client_id=frontend-app&...            │
     │─────────────────────────────────────────────────────────────────────────────────►│
     │                                         │                                         │
     │ 6. 302 → /login (unauthenticated)       │                                         │
     │◄─────────────────────────────────────────────────────────────────────────────────│
     │                                         │                                         │
     │ 7. GET /login                           │                                         │
     │─────────────────────────────────────────────────────────────────────────────────►│
     │                                         │                                         │
     │ 8. 200 OK (login form)                  │                                         │
     │◄─────────────────────────────────────────────────────────────────────────────────│
     │                                         │                                         │
     │ 9. POST /login (username, password)     │                                         │
     │─────────────────────────────────────────────────────────────────────────────────►│
     │                                         │                                         │
     │ 10. 302 → /oauth2/authorize?... (saved request)                                   │
     │◄─────────────────────────────────────────────────────────────────────────────────│
     │                                         │                                         │
     │ 11. GET /oauth2/authorize?... (now authenticated)                                 │
     │─────────────────────────────────────────────────────────────────────────────────►│
     │                                         │                                         │
     │ 12. 302 → http://localhost:8081/login/oauth2/code/frontend-app?code=...&state=...│
     │◄─────────────────────────────────────────────────────────────────────────────────│
     │                                         │                                         │
     │ 13. GET /login/oauth2/code/frontend-app?code=...&state=...                        │
     │────────────────────────────────────────►│                                         │
     │                                         │ Load auth request from cookie           │
     │                                         │ Verify state matches                    │
     │                                         │                                         │
     │                                         │ 14. POST /oauth2/token                  │
     │                                         │ (code, code_verifier, redirect_uri)     │
     │                                         │────────────────────────────────────────►│
     │                                         │                                         │
     │                                         │ 15. 200 OK (access_token, id_token)     │
     │                                         │◄────────────────────────────────────────│
     │                                         │                                         │
     │                                         │ Validate tokens, create session         │
     │                                         │                                         │
     │ 16. 302 → / (authenticated)             │                                         │
     │◄────────────────────────────────────────│                                         │
     │                                         │                                         │
     │ 17. GET / (with session cookie)         │                                         │
     │────────────────────────────────────────►│                                         │
     │                                         │                                         │
     │ 18. 200 OK (chat interface)             │                                         │
     │◄────────────────────────────────────────│                                         │
     │                                         │                                         │
```
