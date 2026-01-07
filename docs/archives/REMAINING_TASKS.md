# Remaining Implementation Tasks

**Status as of**: January 2026
**Phase 0 Status**: ✅ Complete (WebFlux → WebMVC Migration)
**Current Phase**: Ready for Phase 1

---

## Phase 0: WebFlux → WebMVC Migration ✅ COMPLETE

- [x] Update mcp-server/pom.xml - swap webflux for webmvc starter
- [x] Update mcp-client/pom.xml - swap webflux for web starter
- [x] Convert mcp-server ResOsConfig.java to RestClient
- [x] Convert mcp-client SecurityConfig to WebMVC
- [x] Convert mcp-client AuthController to servlet API
- [x] Convert mcp-client ChatService to callback streaming
- [x] Convert mcp-client ChatController to SseEmitter
- [x] Convert McpAsyncClientManager to McpSyncClientManager
- [x] Update mcp-server application.yml
- [x] Update mcp-client application.yml
- [x] Fix Maven artifact names
- [x] Test compilation
- [x] Document lessons learned

---

## Phase 1: Database Schema (Liquibase)

### 1.1 Create Security Tables Changelog

- [ ] Create `backend/src/main/resources/db/changelog/generated/security_tables_init.yml`
  - [ ] `app_user` table (id, username, password BCrypt, email, enabled, timestamps)
  - [ ] `authority` table (id, name for ROLE_USER, ROLE_OPERATOR, ROLE_ADMIN)
  - [ ] `user_authority` join table (user_id, authority_id)
  - [ ] `oauth2_registered_client` (Spring Auth Server schema)
  - [ ] `oauth2_authorization` (active tokens)
  - [ ] `oauth2_authorization_consent` (user consents)

### 1.2 Create Seed Data Changelog

- [ ] Create `backend/src/main/resources/db/changelog/generated/security_seed_data.yml`
  - [ ] Insert default authorities (ROLE_USER, ROLE_OPERATOR, ROLE_ADMIN)
  - [ ] Insert default users for dev profile:
    - [ ] admin/admin123 (ROLE_ADMIN)
    - [ ] operator/operator123 (ROLE_OPERATOR)
    - [ ] user/user123 (ROLE_USER)
  - [ ] Insert OAuth2 clients:
    - [ ] mcp-server (client_credentials, scopes: backend.read, backend.write)
    - [ ] frontend-app (authorization_code+PKCE, scopes: openid, profile, chat)

### 1.3 Update Master Changelog

- [ ] Update `backend/src/main/resources/db/changelog/db.changelog-master.yml`
  - [ ] Include security_tables_init.yml
  - [ ] Include security_seed_data.yml

---

## Phase 2: Backend Security (Authorization Server)

### 2.1 Add Dependencies

- [ ] Update `backend/pom.xml` with:
  - [ ] `spring-boot-starter-security`
  - [ ] `spring-security-oauth2-authorization-server`
  - [ ] `spring-boot-starter-oauth2-resource-server`
  - [ ] `spring-boot-starter-thymeleaf`

### 2.2 Create Security Configuration Classes

- [ ] Create `backend/src/main/java/me/pacphi/ai/resos/security/AuthorizationServerConfig.java`
  - [ ] @Order(1) SecurityFilterChain for OAuth2 Authorization Server
  - [ ] JdbcRegisteredClientRepository (database-backed clients)
  - [ ] JdbcOAuth2AuthorizationService (database-backed tokens)
  - [ ] JWKSource for JWT signing (RSA-256)
  - [ ] AuthorizationServerSettings with issuer URI

- [ ] Create `backend/src/main/java/me/pacphi/ai/resos/security/ResourceServerConfig.java`
  - [ ] @Order(2) SecurityFilterChain for `/api/**` endpoints
  - [ ] JWT validation with custom authority converter
  - [ ] Endpoint security rules:
    - [ ] `/api/**/public/**` - permitAll
    - [ ] `/customers/**`, `/feedback/**` - SCOPE_backend.read or ROLE_USER+
    - [ ] `/bookings/**`, `/orders/**` (write) - SCOPE_backend.write or ROLE_OPERATOR+
    - [ ] Admin endpoints - ROLE_ADMIN

- [ ] Create `backend/src/main/java/me/pacphi/ai/resos/security/DefaultSecurityConfig.java`
  - [ ] @Order(3) SecurityFilterChain for form login
  - [ ] Login page at `/login`
  - [ ] Logout handling
  - [ ] CORS configuration for React SPA

- [ ] Create `backend/src/main/java/me/pacphi/ai/resos/security/AppUserDetailsService.java`
  - [ ] Implements UserDetailsService
  - [ ] Queries app_user + user_authority tables
  - [ ] Returns Spring Security User with granted authorities

- [ ] Create `backend/src/main/java/me/pacphi/ai/resos/security/JwtTokenCustomizer.java`
  - [ ] Adds user roles to JWT claims
  - [ ] Adds user ID and email to ID token

### 2.3 Create Login Template

- [ ] Create `backend/src/main/resources/templates/login.html` (Thymeleaf)
  - [ ] Simple branded login page

### 2.4 Update Backend Configuration

- [ ] Update `backend/src/main/resources/application.yml`
  - [ ] Add OAuth2 authorization server settings
  - [ ] Configure JWT token settings
  - [ ] Add CORS origins

---

## Phase 3: MCP-Server Security (Resource Server)

### 3.1 Create Security Configuration

- [ ] Create `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java`
  - [ ] @EnableWebSecurity
  - [ ] SecurityFilterChain protecting `/mcp/**` with authentication
  - [ ] Permit `/actuator/**` without auth
  - [ ] Configure as OAuth2 Resource Server with JWT validation
  - [ ] Disable CSRF for stateless API
  - [ ] Configure CORS

### 3.2 Update Configuration

- [ ] Verify `mcp-server/src/main/resources/application.yml` has:
  - [x] OAuth2 resource server JWT issuer URI (already added in Phase 0)

### 3.3 Test MCP Server

- [ ] Start mcp-server standalone
- [ ] Verify it starts on port 8082 without errors
- [ ] Test accessing `/actuator/health` (should be permitted)
- [ ] Test accessing `/mcp/**` without token (should get 401)

---

## Phase 4: MCP-Client Security & OAuth2 Integration

### 4.1 Configure OAuth2 Client for Backend Auth

- [ ] Verify `mcp-client/src/main/resources/application.yml` has:
  - [x] OAuth2 client registration for frontend-app (already configured)
  - [ ] Add OAuth2 client registration for calling mcp-server (if needed)

### 4.2 Update MCP Client Configuration

- [ ] Update `McpSyncClientManager` or create `McpClientConfig`
  - [ ] Configure RestClient/HttpClient with OAuth2 interceptor
  - [ ] Attach Bearer tokens to requests to mcp-server

### 4.3 Test OAuth2 Flow

- [ ] Verify mcp-client can obtain tokens from backend
- [ ] Verify mcp-client sends tokens to mcp-server
- [ ] Test token validation on mcp-server side

---

## Phase 5: React SPA Updates

### 5.1 Review Frontend Files

- [ ] Review `mcp-client/src/main/frontend/src/App.jsx`
  - [ ] Verify SSE consumption works with SseEmitter backend
  - [ ] Check authentication redirect handling

- [ ] Review `mcp-client/src/main/frontend/src/AuthContext.jsx`
  - [ ] Verify auth state management
  - [ ] Check `/api/auth/status` and `/api/auth/user` calls

### 5.2 Add CORS Configuration (if needed)

- [ ] Create `mcp-client/src/main/java/me/pacphi/ai/resos/config/WebConfig.java`
  - [ ] Implement WebMvcConfigurer
  - [ ] Configure CORS for React dev server origins

### 5.3 Verify Vite Configuration

- [ ] Check `mcp-client/src/main/frontend/vite.config.js`
  - [ ] Verify proxy configuration for `/api` and `/oauth2`

---

## Phase 6: Integration Testing

### 6.1 Backend Testing

- [ ] Start backend on port 8080
- [ ] Test OAuth2 authorization endpoints
  - [ ] `/oauth2/authorize`
  - [ ] `/oauth2/token`
  - [ ] `/oauth2/jwks`
- [ ] Test login page loads at `/login`
- [ ] Test resource server endpoints with valid JWT

### 6.2 MCP-Server Testing

- [ ] Start mcp-server on port 8082
- [ ] Obtain JWT from backend
- [ ] Test `/mcp/**` endpoints with Bearer token
- [ ] Verify 401 without token
- [ ] Verify 200 with valid token

### 6.3 MCP-Client Testing

- [ ] Start mcp-client on port 8081
- [ ] Test OAuth2 login redirect flow
- [ ] Test `/api/auth/status` endpoint
- [ ] Test `/api/auth/user` endpoint after login
- [ ] Verify CSRF token handling

### 6.4 End-to-End Testing

- [ ] Complete OAuth2 login through frontend
- [ ] Submit chat request
- [ ] Verify mcp-client obtains user token
- [ ] Verify chat request flows through MCP architecture:
  - [ ] Frontend → mcp-client (with user session)
  - [ ] mcp-client → mcp-server (with client credentials token)
  - [ ] mcp-server → backend API (with client credentials token)
- [ ] Verify streaming responses work via SseEmitter
- [ ] Test logout flow

---

## Phase 7: Docker Compose & Deployment

### 7.1 Update Docker Compose

- [ ] Update `backend/docker/docker-compose.postgres.yml`
  - [ ] Add healthcheck for orchestration

- [ ] Create `docker/docker-compose.full-stack.yml`
  - [ ] PostgreSQL with healthcheck
  - [ ] Backend (auth server + resource server)
  - [ ] MCP-Server with OAuth2 config
  - [ ] MCP-Client with OAuth2 config
  - [ ] Proper `depends_on` ordering

### 7.2 Test Docker Deployment

- [ ] Build all Docker images
- [ ] Start full stack with docker-compose
- [ ] Verify all services start correctly
- [ ] Test end-to-end flow in Docker environment

---

## Optional Enhancements

### OAuth2 Scope Management

- [ ] Implement fine-grained scopes for different operations
- [ ] Add scope-based authorization to endpoints

### Token Refresh

- [ ] Implement refresh token rotation
- [ ] Handle token expiration gracefully in frontend

### Monitoring & Logging

- [ ] Add structured logging for OAuth2 events
- [ ] Add metrics for token issuance/validation
- [ ] Set up distributed tracing

### Security Hardening

- [ ] Implement rate limiting
- [ ] Add request signing for critical operations
- [ ] Configure secure headers (CSP, HSTS, etc.)
- [ ] Set up secrets management (Vault, AWS Secrets Manager)

---

## Priority Order Recommendation

1. **Phase 1**: Database schema (foundation for everything)
2. **Phase 2**: Backend security (auth server must work first)
3. **Phase 3**: MCP-Server security (validate token flow)
4. **Phase 4**: MCP-Client OAuth2 (complete the chain)
5. **Phase 5**: React SPA updates (user-facing auth)
6. **Phase 6**: Integration testing (validate everything works)
7. **Phase 7**: Docker deployment (productionize)

---

## Estimated Effort

| Phase | Estimated Time |
|-------|---------------|
| Phase 1: Database Schema | 2-3 hours |
| Phase 2: Backend Security | 4-6 hours |
| Phase 3: MCP-Server Security | 1-2 hours |
| Phase 4: MCP-Client OAuth2 | 2-3 hours |
| Phase 5: React SPA | 2-3 hours |
| Phase 6: Integration Testing | 3-4 hours |
| Phase 7: Docker Deployment | 2-3 hours |
| **Total** | **16-24 hours** |

---

## Current Status Summary

✅ **Complete**: Phase 0 (WebFlux → WebMVC Migration)
⏳ **Next Up**: Phase 1 (Database Schema)
🎯 **Goal**: Full OAuth2 security with MCP HTTP Streamable transport
