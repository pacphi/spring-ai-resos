# ADR-007: Frontend API Session Management and SSE Parsing Fixes

## Status

Accepted

## Date

2026-01-07

## Context

After successfully implementing OAuth2 authentication (see ADR-006), users could authenticate via the frontend but the chat functionality was not working. When users submitted questions through the chat interface, they received HTML content (the login page) instead of actual AI responses. This ADR documents the investigation and fixes required to resolve these issues.

## Problem Statement

After OAuth2 login succeeded:

1. User could see authenticated state in the UI (showing username "admin")
2. GET requests to `/api/auth/status` and `/api/auth/user` worked correctly
3. POST requests to `/api/v1/resos/stream/chat` failed - returned HTML login page or 403 errors
4. Once POST requests worked, the response displayed raw SSE format instead of parsed content

## Investigation and Solutions

### Issue 1: CSRF Token Rejection (403 Forbidden)

**Symptom:**

```text
2026-01-07T15:00:15.400 DEBUG ... Invalid CSRF token found for http://localhost:8081/api/v1/resos/stream/chat
2026-01-07T15:00:15.402 DEBUG ... Responding with 403 status code
```

**Root Cause:**
Spring Security's CSRF protection was enabled for all endpoints. The frontend JavaScript `fetch()` calls were not including CSRF tokens in POST requests.

**Solution:**
Disable CSRF for API endpoints in `SecurityConfig.java`. This is a common pattern for REST APIs because:

- APIs use JSON content types which cannot be submitted by HTML forms (primary CSRF attack vector)
- Modern browsers enforce SameSite cookie policies providing additional protection
- Session-based authentication with same-origin requests is already protected

**Code Change - mcp-client SecurityConfig.java:**

```java
.csrf(csrf -> csrf
    // Disable CSRF for API endpoints - they use JSON content type which
    // cannot be submitted by HTML forms, and SameSite cookies provide protection
    .ignoringRequestMatchers("/api/**")
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
)
```

### Issue 2: Session Cookie Not Sent with POST Requests

**Symptom:**
After fixing CSRF, POST requests were still treated as anonymous:

```text
2026-01-07T15:31:15.807 DEBUG ... Securing POST /api/v1/resos/stream/chat
2026-01-07T15:31:15.808 DEBUG ... Set SecurityContextHolder to anonymous SecurityContext
```

Meanwhile, GET requests showed session being retrieved correctly:

```text
2026-01-07T15:30:39.024 DEBUG ... Securing GET /api/auth/status
2026-01-07T15:30:39.024 DEBUG ... Retrieved SecurityContextImpl [Authentication=OAuth2AuthenticationToken...]
```

**Investigation Approach:**
Created a debug filter to log cookie information for all API requests:

**Code - RequestLoggingFilter.java:**

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri.startsWith("/api/")) {
            log.info("=== REQUEST DEBUG: {} {} ===", method, uri);

            Cookie[] cookies = request.getCookies();
            if (cookies != null && cookies.length > 0) {
                log.info("Cookies present: {}", ...);
            } else {
                log.warn("NO COOKIES in request!");
            }

            log.info("Requested Session ID: {}, valid: {}, from cookie: {}",
                sessionId, request.isRequestedSessionIdValid(),
                request.isRequestedSessionIdFromCookie());
        }
        filterChain.doFilter(request, response);
    }
}
```

**Root Cause:**
The JavaScript `fetch()` API has different default behaviors for credentials:

- `credentials: 'same-origin'` (default) - should send cookies for same-origin requests
- `credentials: 'include'` - always sends cookies

In some browser configurations or with certain security policies, the default wasn't sufficient for POST requests to streaming endpoints.

**Solution - Multiple Changes:**

1. **Frontend: Add explicit credentials to fetch calls**

**ChatPage.jsx:**

```javascript
const response = await fetch('/api/v1/resos/stream/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include', // Ensure session cookie is sent
  body: JSON.stringify({
    question: questionText,
  }),
});
```

**AuthContext.jsx:**

```javascript
const response = await fetch('/api/auth/status', { credentials: 'include' });
// ...
const userResponse = await fetch('/api/auth/user', { credentials: 'include' });
```

1. **Backend: Explicit session cookie configuration**

**application.yml:**

```yaml
server:
  servlet:
    session:
      cookie:
        same-site: lax
        http-only: true
```

1. **Backend: Enable request logging for debugging**

**application.yml (dev profile):**

```yaml
spring.mvc.log-request-details: true

logging:
  level:
    org.springframework.web.filter.CommonsRequestLoggingFilter: DEBUG
```

**Verification:**
After fixes, debug filter showed cookies being sent correctly:

```text
=== REQUEST DEBUG: POST /api/v1/resos/stream/chat ===
Cookies present: [JSESSIONID=[REDACTED]]
Requested Session ID: [present], valid: true, from cookie: true
Cookie header: [present, length=43]
```

### Issue 3: SSE Response Not Parsed Correctly

**Symptom:**
Once authentication worked, the chat interface displayed raw SSE format:

```text
🤖 data:Let's
data: check
data: the
data: list
data: of
data: restaurants
```

Instead of the expected parsed text:

```text
🤖 Let's check the list of restaurants...
```

**Root Cause:**
The `ChatController` uses Spring's `SseEmitter` which sends Server-Sent Events with `data:` prefixes:

```java
emitter.send(SseEmitter.event().data(token));
```

The frontend was reading raw bytes without parsing the SSE format:

```javascript
const chunk = decoder.decode(value);
fullAnswer += chunk; // Raw SSE format included
```

**Solution - ChatPage.jsx:**

```javascript
while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  const chunk = decoder.decode(value);
  // Parse SSE format - extract data from "data: <content>" lines
  const lines = chunk.split('\n');
  for (const line of lines) {
    if (line.startsWith('data:')) {
      // Remove "data:" prefix and trim
      const content = line.slice(5);
      if (content) {
        fullAnswer += content;
        setCurrentAnswer((prev) => prev + content);
        setAnswer((prev) => prev + content);
      }
    }
  }
}
```

## Files Modified

### mcp-client Module

| File                                                                | Change                                             |
| ------------------------------------------------------------------- | -------------------------------------------------- |
| `src/main/java/me/pacphi/ai/resos/config/SecurityConfig.java`       | Added CSRF ignore for `/api/**` endpoints          |
| `src/main/java/me/pacphi/ai/resos/config/RequestLoggingFilter.java` | **NEW** - Debug filter for logging cookies/session |
| `src/main/resources/application.yml`                                | Added session cookie config, request logging       |
| `src/main/frontend/src/pages/ChatPage.jsx`                          | Added `credentials: 'include'`, SSE parsing        |
| `src/main/frontend/src/AuthContext.jsx`                             | Added `credentials: 'include'` to fetch calls      |

## What We Tried That Didn't Work

1. **Just adding `credentials: 'include'` without rebuilding frontend** - Changes weren't picked up until `npm run build` was executed and files copied to `target/classes/static/`

2. **Assuming default `credentials: 'same-origin'` would work** - For POST requests to SSE endpoints in some browsers/configurations, explicit `credentials: 'include'` was required

3. **Initial SSE parsing attempt** - First implementation didn't account for the `data:` prefix format properly

## Key Learnings

### 1. CSRF and REST APIs

For JSON-based REST APIs consumed by JavaScript frontends, disabling CSRF protection is a common and acceptable pattern. The combination of:

- JSON content type (not form-submittable)
- Same-origin policy
- SameSite cookie attribute
- Session-based authentication

Provides sufficient protection against CSRF attacks.

### 2. fetch() Credentials Behavior

Always explicitly set `credentials: 'include'` for authenticated API calls, especially:

- POST/PUT/DELETE requests
- Streaming endpoints (SSE, WebSocket upgrades)
- Cross-origin requests (if applicable)

The default `same-origin` behavior may not work consistently across all browsers and endpoint types.

### 3. SSE Parsing in JavaScript

When using `fetch()` with `response.body.getReader()` to consume SSE streams:

- The response is raw bytes, not parsed events
- Must manually parse `data:` lines
- Consider using `EventSource` API for simpler SSE consumption (but it has limitations with POST requests and custom headers)

### 4. Frontend Build Pipeline

Changes to frontend source files require:

1. `npm run build` in the frontend directory
2. Copy `dist/*` to `target/classes/static/`
3. Either restart the server or rely on devtools auto-reload
4. Hard refresh the browser (Cmd+Shift+R) to bypass cache

### 5. Debug Filters Are Invaluable

Creating a high-priority request logging filter that logs cookies, session state, and headers is extremely helpful for diagnosing authentication issues. This should be conditionally enabled only in dev profile.

## Testing Recommendations

### Manual Testing Checklist

1. Fresh browser session (clear cookies)
2. Navigate to http://localhost:8081
3. Verify redirect to login page
4. Login with admin/admin123
5. Verify redirect back to chat interface
6. Verify "admin" shown in header
7. Submit a chat question
8. Verify streaming response appears correctly (no `data:` prefixes)
9. Verify response renders markdown properly

### Automated Test Considerations

1. **SecurityConfig Tests** - Verify CSRF is disabled for `/api/**` but enabled for other endpoints
2. **Session Cookie Tests** - Verify cookies are set with correct attributes (SameSite, HttpOnly)
3. **SSE Integration Tests** - Verify streaming endpoint returns proper SSE format
4. **Frontend E2E Tests** - Verify chat flow works end-to-end with authentication

## Related ADRs

- ADR-006: OAuth2 Authorization Server Integration (prerequisite)

## References

- [MDN: Using Fetch - Sending a request with credentials](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch#sending_a_request_with_credentials_included)
- [Spring Security: CSRF Protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [MDN: Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [Fetch API vs EventSource for SSE](https://stackoverflow.com/questions/34657222/fetch-api-vs-eventsource-for-server-sent-events)
