# Phase 7: Production Readiness Tasks

**Status**: ⏳ Not Started
**Date Created**: January 2026
**Prerequisites**: Phases 0-6 complete

---

## Overview

Phase 7 focuses on production hardening, deployment improvements, and documentation enhancements to make the Spring AI ResOs project production-ready.

**Dependencies**:
- ✅ Phase 0-6 Complete (OAuth2 security, tests passing)
- ⏳ Some items in Phase 7 can be done incrementally

---

## 7.1 Security Hardening

### 7.1.1 Review Security Configurations

- [ ] Audit all `SecurityFilterChain` configurations across modules
  - [ ] Backend: AuthorizationServerConfig, ResourceServerConfig, DefaultSecurityConfig
  - [ ] MCP-Server: SecurityConfig
  - [ ] MCP-Client: SecurityConfig
- [ ] Verify principle of least privilege applied
- [ ] Check for overly permissive rules

### 7.1.2 CSRF Protection Review

- [ ] Verify CSRF enabled for browser-based endpoints
- [ ] Confirm CSRF disabled for stateless API endpoints
- [ ] Test CSRF token handling in React SPA

### 7.1.3 Secure Session Cookies

**Current**: Default Spring Security session cookie

**Enhancements**:
- [ ] Add session cookie configuration:
  ```yaml
  server:
    servlet:
      session:
        cookie:
          secure: true       # HTTPS only
          http-only: true    # Prevent XSS
          same-site: strict  # Prevent CSRF
  ```
- [ ] Test session cookies in production-like environment (with TLS)

### 7.1.4 Security Headers

- [ ] Add Content Security Policy (CSP):
  ```java
  http.headers(headers -> headers
      .contentSecurityPolicy("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")
  );
  ```
- [ ] Add HSTS (HTTP Strict Transport Security):
  ```java
  .hsts(hsts -> hsts
      .includeSubDomains(true)
      .maxAgeInSeconds(31536000)  // 1 year
  )
  ```
- [ ] Add X-Frame-Options (prevent clickjacking):
  ```java
  .frameOptions(frame -> frame.deny())
  ```
- [ ] Add Referrer-Policy:
  ```java
  .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
  ```

**Effort**: 2-3 hours

---

## 7.2 Docker Deployment Enhancements

### 7.2.1 Update Docker Compose Files

**Current**: Basic docker-compose.yml exists

**Enhancements**:
- [ ] Add health checks to all services
- [ ] Configure proper `depends_on` with health conditions
- [ ] Add resource limits (CPU, memory)
- [ ] Configure restart policies
- [ ] Add logging configuration
- [ ] Set up networks for service isolation

**Example** (docker/docker-compose.yml):
```yaml
services:
  backend:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
    restart: unless-stopped
```

### 7.2.2 Test Full Stack in Docker

- [ ] Build all Docker images:
  ```bash
  docker-compose build
  ```
- [ ] Start full stack:
  ```bash
  docker-compose up -d
  ```
- [ ] Verify all services start in correct order
- [ ] Test health checks
- [ ] Test end-to-end OAuth2 flow in Docker
- [ ] Test chat functionality
- [ ] Check logs for errors

### 7.2.3 Environment Variable Management

- [ ] Create `.env.example` template:
  ```bash
  # LLM API Keys
  OPENAI_API_KEY=sk-...
  GROQ_API_KEY=gsk_...

  # OAuth2 Secrets
  MCP_SERVER_SECRET=change-in-production
  MCP_CLIENT_SECRET=change-in-production

  # Database
  POSTGRES_PASSWORD=change-in-production
  ```
- [ ] Document all required environment variables
- [ ] Add validation for missing required vars on startup

### 7.2.4 Health Check Improvements

- [ ] Verify Spring Boot Actuator health checks comprehensive:
  - [ ] Database connectivity
  - [ ] OAuth2 token endpoint reachability
  - [ ] Disk space
  - [ ] Memory usage
- [ ] Add custom health indicators:
  - [ ] Backend API connectivity (for mcp-server)
  - [ ] MCP Server connectivity (for mcp-client)
  - [ ] LLM API connectivity (for mcp-client)

**Effort**: 3-4 hours

---

## 7.3 Documentation Updates

### 7.3.1 Update Main README

**Current README.md needs**:
- [ ] Add section on OAuth2 setup and default credentials
- [ ] Document environment variable requirements
- [ ] Add troubleshooting section
- [ ] Update "How to run" with OAuth2 login flow

**Example Addition**:
```markdown
## Default Login Credentials

For development environment:
- **Admin**: username=`admin`, password=`admin123`
- **Operator**: username=`operator`, password=`operator123`
- **User**: username=`user`, password=`user123`

⚠️ **Change these in production!**

## OAuth2 Setup

The application uses OAuth2 for authentication:
1. Backend (port 8080) is the Authorization Server
2. Login at http://localhost:8081 redirects to OAuth2 login
3. After authentication, you'll be redirected back to chat interface
```

### 7.3.2 Environment Variables Documentation

- [ ] Create comprehensive environment variable reference
- [ ] Document required vs optional variables
- [ ] Add default values
- [ ] Provide production configuration examples

### 7.3.3 Developer Quick-Start Guide

- [ ] Create `docs/QUICKSTART.md` or update existing README
- [ ] Include:
  - [ ] Prerequisites checklist
  - [ ] Build command
  - [ ] Run commands (3 terminals)
  - [ ] Access URLs
  - [ ] First login walkthrough
  - [ ] Test chat functionality

### 7.3.4 Testing Procedures Documentation

- [ ] Document how to run tests:
  ```bash
  # All tests
  mvn test

  # Specific module
  cd backend && mvn test
  ```
- [ ] Document expected test results
- [ ] Add troubleshooting for common test failures

**Effort**: 2-3 hours

---

## Additional Production Hardening (Optional)

### Rate Limiting

- [ ] Add rate limiting per OAuth2 client
- [ ] Implement using Bucket4j or similar
- [ ] Configure limits:
  - frontend-app: 100 requests/minute
  - mcp-server: 1000 requests/minute

### Token Revocation

- [ ] Implement admin endpoint to revoke user tokens
- [ ] Add user-initiated token revocation (logout all devices)

### Audit Logging

- [ ] Log all OAuth2 token issuance
- [ ] Log authentication failures
- [ ] Log authorization denials (403)
- [ ] Structure logs for SIEM ingestion

### Secret Rotation

- [ ] Document OAuth2 client secret rotation procedure
- [ ] Add support for graceful secret rotation (dual secrets)

**Effort**: 4-6 hours (optional enhancements)

---

## Acceptance Criteria

Phase 7 is complete when:

**Security**:
- [ ] All security configurations reviewed and hardened
- [ ] Security headers configured (CSP, HSTS, etc.)
- [ ] Session cookies configured with secure flags
- [ ] OWASP dependency scan passes

**Deployment**:
- [ ] Docker Compose full stack runs successfully
- [ ] All health checks passing
- [ ] Services start in correct order with dependencies
- [ ] End-to-end flow works in Docker

**Documentation**:
- [ ] README updated with OAuth2 setup instructions
- [ ] Environment variables documented
- [ ] Quick-start guide available
- [ ] Testing procedures documented

---

## Estimated Effort

| Task | Time Estimate |
|------|---------------|
| Security Hardening | 2-3 hours |
| Docker Enhancements | 3-4 hours |
| Documentation Updates | 2-3 hours |
| Testing & Validation | 2-3 hours |
| **Total** | **9-13 hours** |

**Optional Enhancements**: +4-6 hours

---

## Dependencies & Blockers

**Prerequisites**:
- ✅ Phases 0-6 complete (all done)
- ✅ Integration tests passing (53/53)
- ✅ Security implementation complete

**No Blockers**: Can start Phase 7 immediately

---

## Priority

**Priority**: Medium-High

**Rationale**:
- Core functionality works (OAuth2, chat, MCP)
- Production hardening adds security & reliability
- Not blocking MVP (chatbot works)
- Important for production deployment

**Recommendation**: Complete Phase 7 before first production release

---

## Related Documentation

- [architecture/06-security-architecture.md](../architecture/06-security-architecture.md) - Current security implementation
- [architecture/13-deployment.md](../architecture/13-deployment.md) - Deployment guide
- [architecture/15-future-enhancements.md](../architecture/15-future-enhancements.md) - Broader roadmap
- [archives/UPDATED_REMAINING_TASKS.md](../archives/UPDATED_REMAINING_TASKS.md) - Original task list (Phases 0-6 complete)
