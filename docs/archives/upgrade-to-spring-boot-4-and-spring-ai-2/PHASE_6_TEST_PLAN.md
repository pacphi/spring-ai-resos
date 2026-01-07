# Phase 6: Integration Testing - Automated Test Plan

**Date**: January 2026
**Status**: Planning
**Scope**: Automated tests for OAuth2 security implementation across all modules

---

## Testing Objectives

1. ✅ Verify OAuth2 Authorization Server issues valid tokens
2. ✅ Verify JWT validation works on resource servers
3. ✅ Verify scope and role-based authorization
4. ✅ Verify end-to-end authentication flows
5. ✅ Verify error handling (invalid tokens, missing scopes)
6. ✅ Verify inter-service OAuth2 communication

---

## Test Architecture

### Test Pyramid Approach

```text
                    E2E Tests (Manual/Selenium)
                           /\
                          /  \
                         /    \
                        /      \
              Integration Tests (SpringBootTest)
                      /          \
                     /            \
                    /              \
          Unit Tests (Existing)
```

**Focus**: Integration Tests (automated, fast, reliable)

---

## Module 1: Backend Integration Tests

**Location**: `backend/src/test/java/me/pacphi/ai/resos/security/`

### Test 1: OAuth2TokenGenerationTest

**Purpose**: Verify token issuance for all grant types

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class OAuth2TokenGenerationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    // Test Cases:

    @Test
    void shouldIssueTokenForClientCredentials() {
        // Given: Valid client credentials
        // When: Request token with grant_type=client_credentials
        // Then: Returns access_token with correct scopes
    }

    @Test
    void shouldRejectInvalidClientCredentials() {
        // Given: Invalid client secret
        // When: Request token
        // Then: Returns 401 Unauthorized
    }

    @Test
    void shouldIssueTokensForPKCEFlow() {
        // Given: Valid PKCE parameters (code_verifier, code_challenge)
        // When: Complete authorization code flow
        // Then: Returns access_token and refresh_token
    }

    @Test
    void shouldIncludeCustomClaimsInJWT() {
        // Given: Valid client credentials
        // When: Decode issued JWT
        // Then: Contains expected custom claims (user roles, etc.)
    }
}
```

**Dependencies**:

- `spring-security-test`
- `rest-assured` (optional, for fluent API testing)

**Estimated**: 4-6 tests, ~2 hours implementation

---

### Test 2: ProtectedEndpointSecurityTest

**Purpose**: Verify scope/role-based access control

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtectedEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = {"SCOPE_backend.read"})
    void shouldAllowReadAccessWithReadScope() {
        // When: GET /api/customers with backend.read scope
        // Then: Returns 200 OK
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_backend.write"})
    void shouldAllowWriteAccessWithWriteScope() {
        // When: POST /api/bookings with backend.write scope
        // Then: Returns 200 or 201
    }

    @Test
    @WithAnonymousUser
    void shouldDenyAccessWithoutAuthentication() {
        // When: GET /api/customers without auth
        // Then: Returns 401 Unauthorized
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_backend.read"})
    void shouldDenyWriteAccessWithReadOnlyScope() {
        // When: POST /api/bookings with only backend.read
        // Then: Returns 403 Forbidden
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void shouldAllowAdminAccessToAllEndpoints() {
        // When: Access admin-only endpoints
        // Then: Returns 200 OK
    }
}
```

**Estimated**: 8-10 tests, ~2-3 hours

---

### Test 3: UserDetailsServiceTest

**Purpose**: Verify database authentication

```java
@SpringBootTest
@ActiveProfiles("test")
class UserDetailsServiceTest {

    @Autowired
    private AppUserDetailsService userDetailsService;

    @Test
    void shouldLoadUserByUsername() {
        // Given: User exists in database (from seed data)
        // When: Load user "admin"
        // Then: Returns UserDetails with correct authorities
    }

    @Test
    void shouldThrowExceptionForNonExistentUser() {
        // When: Load user "nonexistent"
        // Then: Throws UsernameNotFoundException
    }

    @Test
    void shouldLoadUserAuthorities() {
        // When: Load user "admin"
        // Then: Has ROLE_ADMIN, ROLE_OPERATOR, ROLE_USER
    }
}
```

**Estimated**: 3-4 tests, ~1 hour

---

### Test 4: FormLoginTest

**Purpose**: Verify login page and form authentication

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormLoginTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldShowLoginPage() {
        // When: GET /login
        // Then: Returns 200 with login form HTML
    }

    @Test
    void shouldAuthenticateValidCredentials() throws Exception {
        // When: POST /login with username=admin, password=admin123
        // Then: Redirects to success URL, sets session cookie
    }

    @Test
    void shouldRejectInvalidCredentials() {
        // When: POST /login with wrong password
        // Then: Redirects to /login?error
    }

    @Test
    void shouldLogoutUser() {
        // Given: Authenticated user
        // When: GET /logout
        // Then: Clears session, redirects to /login?logout
    }
}
```

**Estimated**: 4-5 tests, ~1-2 hours

---

## Module 2: MCP-Server Integration Tests

**Location**: `mcp-server/src/test/java/me/pacphi/ai/resos/mcp/`

### Test 5: McpEndpointSecurityTest

**Purpose**: Verify MCP endpoints require valid JWTs

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class McpEndpointSecurityTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String backendTokenEndpoint = "http://localhost:8080/oauth2/token";

    @Test
    void shouldDenyAccessToMcpEndpointWithoutToken() {
        // When: GET /mcp/tools without Authorization header
        // Then: Returns 401 Unauthorized
    }

    @Test
    void shouldAllowAccessToActuatorWithoutToken() {
        // When: GET /actuator/health without token
        // Then: Returns 200 OK
    }

    @Test
    void shouldAllowAccessWithValidJWT() {
        // Given: Valid JWT from backend (using mcp-server credentials)
        String token = obtainClientCredentialsToken("mcp-server", "mcp-server-secret");

        // When: GET /mcp/... with Bearer token
        // Then: Returns 200 or appropriate MCP response
    }

    @Test
    void shouldRejectExpiredJWT() {
        // Given: Expired JWT token
        // When: Request with expired token
        // Then: Returns 401 Unauthorized
    }

    @Test
    void shouldRejectInvalidJWT() {
        // Given: Malformed or invalid signature JWT
        // When: Request with invalid token
        // Then: Returns 401 Unauthorized
    }

    private String obtainClientCredentialsToken(String clientId, String clientSecret) {
        // Helper method to get token from backend
    }
}
```

**Dependencies**:

- Requires backend running on 8080 (or use @SpringBootTest with different approach)
- May need `@DirtiesContext` for test isolation

**Challenges**:

- Tests need backend auth server running
- **Solution**: Use `@TestConfiguration` to mock `JwtDecoder` OR use test containers

**Estimated**: 5-6 tests, ~2-3 hours

---

### Test 6: BackendApiOAuth2ClientTest

**Purpose**: Verify mcp-server can call backend with OAuth2

```java
@SpringBootTest
@ActiveProfiles("test")
class BackendApiOAuth2ClientTest {

    @Autowired
    private DefaultApi backendApi;  // Generated API client

    @MockBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Test
    void shouldIncludeOAuth2TokenInBackendCalls() {
        // Given: Mock authorizedClientManager returns valid token
        // When: Call backend API method
        // Then: Request includes Authorization: Bearer <token>
    }

    @Test
    void shouldHandleTokenRefresh() {
        // Given: Expired token, then valid refreshed token
        // When: Call backend API
        // Then: Refreshes token and retries
    }
}
```

**Estimated**: 2-3 tests, ~1-2 hours

---

## Module 3: MCP-Client Integration Tests

**Location**: `mcp-client/src/test/java/me/pacphi/ai/resos/`

### Test 7: OAuth2LoginConfigurationTest

**Purpose**: Verify OAuth2 client configuration

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2LoginConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRedirectToAuthServerForLogin() {
        // When: GET /api/protected-endpoint without auth
        // Then: Redirects to backend OAuth2 authorize endpoint
    }

    @Test
    @WithOAuth2Login(attributes = {/* user attributes */})
    void shouldAccessProtectedEndpointWhenAuthenticated() {
        // Given: Authenticated OAuth2 user
        // When: GET /api/auth/user
        // Then: Returns user info
    }

    @Test
    void shouldReturnAuthStatusForUnauthenticatedUser() {
        // When: GET /api/auth/status (permitAll endpoint)
        // Then: Returns {authenticated: false, loginUrl: "..."}
    }

    @Test
    @WithOAuth2Login
    void shouldReturnAuthStatusForAuthenticatedUser() {
        // Given: Authenticated user
        // When: GET /api/auth/status
        // Then: Returns {authenticated: true, username: "..."}
    }
}
```

**Estimated**: 4-5 tests, ~1-2 hours

---

### Test 8: ChatStreamingWithAuthTest

**Purpose**: Verify chat streaming works with authentication

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class ChatStreamingWithAuthTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private ChatModel chatModel;  // Mock AI model

    @Test
    @WithOAuth2Login
    void shouldStreamChatResponseWhenAuthenticated() {
        // Given: Authenticated user, mock chat model response
        // When: POST /api/v1/resos/stream/chat
        // Then: Returns SSE stream with chat tokens
    }

    @Test
    void shouldDenyChatAccessWithoutAuthentication() {
        // When: POST /api/v1/resos/stream/chat without auth
        // Then: Returns 401 or redirects to login
    }

    @Test
    @WithOAuth2Login
    void shouldIncludeMcpToolCallsInChat() {
        // Given: Authenticated user, MCP server with tools
        // When: Ask question requiring MCP tool
        // Then: Chat response includes MCP tool call results
    }
}
```

**Estimated**: 3-4 tests, ~2-3 hours

---

### Test 9: McpClientOAuth2IntegrationTest

**Purpose**: Verify mcp-client sends OAuth2 tokens to mcp-server

```java
@SpringBootTest
@ActiveProfiles("test")
class McpClientOAuth2IntegrationTest {

    @Autowired
    private McpSyncClientManager mcpClientManager;

    @MockBean
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Test
    void shouldIncludeOAuth2TokenInMcpRequests() {
        // Given: Mock authorized client manager returns token
        // When: Create MCP clients and make request
        // Then: Request includes Authorization header
    }

    @Test
    void shouldUseClientCredentialsForMcpServerCalls() {
        // Given: mcp-client-to-server registration
        // When: McpSyncClientCustomizer is applied
        // Then: Uses client_credentials grant type
    }
}
```

**Estimated**: 2-3 tests, ~1-2 hours

---

## Cross-Module Integration Tests

### Test 10: FullStackOAuth2FlowTest (Optional - Complex)

**Purpose**: End-to-end OAuth2 flow across all services

**Approach**: Use Testcontainers or Spring Boot's test slices

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
class FullStackOAuth2FlowTest {

    // Challenge: Need all 3 services running
    // Options:
    // 1. Use @SpringBootTest with different ports
    // 2. Use Testcontainers to start services
    // 3. Use WireMock to mock services

    @Test
    void fullOAuth2FlowFromUserLoginToChatResponse() {
        // 1. User accesses mcp-client (8081)
        // 2. Redirects to backend OAuth2 authorize (8080)
        // 3. User authenticates → gets authorization code
        // 4. mcp-client exchanges code for tokens
        // 5. User makes chat request
        // 6. mcp-client calls mcp-server with client_credentials token
        // 7. mcp-server calls backend API with client_credentials token
        // 8. Response streams back through MCP to user
    }
}
```

**Recommendation**: **Skip for Phase 6** - too complex, better suited for manual testing or E2E framework

---

## Test Utilities & Helpers

### Shared Test Utilities

**Location**: `backend/src/test/java/me/pacphi/ai/resos/test/`

#### OAuth2TestHelper.java

```java
public class OAuth2TestHelper {

    public static String obtainClientCredentialsToken(
            String tokenEndpoint,
            String clientId,
            String clientSecret,
            String... scopes) {
        // Helper to get OAuth2 token for tests
    }

    public static String decodeJwt(String token) {
        // Helper to decode and inspect JWT claims
    }

    public static MockHttpServletRequestBuilder withBearerToken(String token) {
        // MockMvc request builder with Authorization header
    }
}
```

#### TestDataBuilder.java

```java
public class TestDataBuilder {

    public static AppUserEntity createTestUser(String username, String... roles) {
        // Create test users programmatically
    }

    public static RegisteredClient createTestOAuth2Client(...) {
        // Create test OAuth2 clients
    }
}
```

---

## Test Data Strategy

### Option A: Use Seed Data (Current Approach)

- ✅ **Pros**: Realistic data, matches dev environment
- ❌ **Cons**: Tests depend on seed data, slower startup

### Option B: Programmatic Test Data

- ✅ **Pros**: Fast, isolated, predictable
- ❌ **Cons**: More code, doesn't test seed mechanism

### Option C: Hybrid (Recommended)

- Use seed data for smoke tests
- Use programmatic data for specific scenarios
- Use `@Sql` scripts for complex test data

---

## Test Configuration

### Test Profile Configuration

**Location**: `backend/src/test/resources/application-test.yml`

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb-${random.uuid};MODE=PostgreSQL

  liquibase:
    contexts: test
    # Use test-specific changelogs if needed

# Disable OAuth2 client seeding in tests (already done)
oauth2:
  client:
    mcp-server:
      client-id: test-mcp-server
      client-secret: test-secret

# Fast token expiration for testing
app:
  security:
    issuer-uri: http://localhost:${local.server.port}
    token:
      access-token-ttl: PT5M # 5 minutes for tests
```

---

## Mocking Strategy

### What to Mock

1. **External Services**: Mock backend when testing mcp-server/mcp-client
2. **AI Models**: Mock ChatModel to avoid API calls
3. **Time-Dependent**: Mock clock for token expiration tests

### What NOT to Mock

1. **OAuth2 Infrastructure**: Test real Spring Security OAuth2
2. **Database**: Use H2 in-memory (real JDBC)
3. **Liquibase**: Run real migrations (validates schema)

---

## Test Execution Plan

### Phase 6.1: Backend Tests (Priority: HIGH)

- [ ] Create `OAuth2TokenGenerationTest` (core OAuth2 functionality)
- [ ] Create `ProtectedEndpointSecurityTest` (authorization rules)
- [ ] Create `UserDetailsServiceTest` (authentication)
- [ ] Create `FormLoginTest` (login flow)
- [ ] Create `OAuth2TestHelper` utility

**Estimated Time**: 6-8 hours
**Success Criteria**: All backend OAuth2 flows tested, 90%+ coverage on security classes

### Phase 6.2: MCP-Server Tests (Priority: MEDIUM)

- [ ] Create `McpEndpointSecurityTest` (JWT validation)
- [ ] Create `BackendApiOAuth2ClientTest` (outbound OAuth2)

**Estimated Time**: 3-4 hours
**Success Criteria**: MCP security verified, outbound OAuth2 tested

### Phase 6.3: MCP-Client Tests (Priority: MEDIUM)

- [ ] Create `OAuth2LoginConfigurationTest` (OAuth2 client setup)
- [ ] Create `ChatStreamingWithAuthTest` (authenticated streaming)
- [ ] Create `McpClientOAuth2IntegrationTest` (MCP OAuth2)

**Estimated Time**: 4-5 hours
**Success Criteria**: Frontend OAuth2 login works, MCP client authenticated

### Phase 6.4: Manual E2E Testing (Priority: LOW)

- [ ] Start all 3 services
- [ ] Test browser-based OAuth2 login
- [ ] Test full chat flow
- [ ] Test error scenarios

**Estimated Time**: 2-3 hours
**Success Criteria**: Full stack works end-to-end

---

## Testing Tools & Dependencies

### Required Dependencies

```xml
<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Optional: For fluent API testing -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.5.0</version>
    <scope>test</scope>
</dependency>
```

### Spring Security Test Annotations

- `@WithMockUser` - Mock authenticated user with roles/authorities
- `@WithUserDetails` - Load real user from UserDetailsService
- `@WithOAuth2Login` - Mock OAuth2 authenticated user
- `@WithSecurityContext` - Custom security context

---

## Success Metrics

| Category                         | Target       | Current |
| -------------------------------- | ------------ | ------- |
| **Backend Security Coverage**    | 80%+         | TBD     |
| **MCP-Server Security Coverage** | 70%+         | TBD     |
| **MCP-Client Auth Coverage**     | 70%+         | TBD     |
| **Integration Test Count**       | 25-30 tests  | 0       |
| **Test Execution Time**          | < 60 seconds | TBD     |
| **Build Success Rate**           | 100%         | ✅      |

---

## Risks & Mitigation

| Risk                                        | Impact | Mitigation                             |
| ------------------------------------------- | ------ | -------------------------------------- |
| **Tests require multiple services running** | High   | Use mocks or Testcontainers            |
| **Token expiration during tests**           | Medium | Use short-lived tokens, mock clock     |
| **Database state pollution**                | Medium | Use `@DirtiesContext`, random DB names |
| **Flaky network tests**                     | Low    | Use WireMock for external calls        |
| **Long test execution**                     | Medium | Parallel execution, selective testing  |

---

## Recommended Priority Order

1. **Backend OAuth2 Token Tests** (critical - foundation for everything)
2. **Backend Protected Endpoint Tests** (verify authorization works)
3. **MCP-Server JWT Validation Tests** (verify resource server)
4. **MCP-Client Auth Tests** (verify client OAuth2)
5. **Manual E2E Testing** (validate full stack)

---

## Effort Estimate

| Phase               | Tests           | Hours      | Priority   |
| ------------------- | --------------- | ---------- | ---------- |
| Backend OAuth2      | 15-20 tests     | 6-8h       | **HIGH**   |
| MCP-Server Security | 5-8 tests       | 3-4h       | **MEDIUM** |
| MCP-Client Auth     | 6-10 tests      | 4-5h       | **MEDIUM** |
| Manual E2E          | N/A             | 2-3h       | **LOW**    |
| **Total**           | **26-38 tests** | **15-20h** |            |

---

## Alternative: Smoke Tests Only

If comprehensive testing is too much, create **minimal smoke tests**:

1. **Backend**: 1 test for client_credentials token generation
2. **MCP-Server**: 1 test for JWT validation (401 without, 200 with token)
3. **MCP-Client**: 1 test for OAuth2 login config
4. **Manual**: Quick browser test of full flow

**Estimated**: 3 tests, 2-3 hours

---

## Recommendation

**Approach**: Start with **Backend OAuth2 Tests** (Phase 6.1)

- Most critical component
- Validates foundation for all other security
- Relatively easy to test (no inter-service dependencies)
- Provides confidence for manual E2E testing

Then decide based on results:

- If backend tests reveal issues → fix before proceeding
- If backend tests pass → move to manual E2E testing
- Save mcp-server/mcp-client tests for later if time-constrained

**Next Step**: Approve this plan and implement Backend OAuth2 Tests?
