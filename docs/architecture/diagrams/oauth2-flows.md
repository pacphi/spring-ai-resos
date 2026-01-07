# OAuth2 Flows

This diagram shows all OAuth2 authentication and authorization flows in the system.

## Flow 1: User Authentication (Authorization Code + PKCE)

```mermaid
sequenceDiagram
    actor User
    participant Browser
    participant Frontend as React SPA<br/>(Port 8081)
    participant AuthServer as Authorization Server<br/>(Backend Port 8080)
    participant TokenEndpoint as Token Endpoint
    participant DB as Database

    User->>Browser: Visit http://localhost:8081
    Browser->>Frontend: GET /
    Frontend->>Frontend: Check auth status
    Frontend-->>Browser: Redirect to login
    Browser->>AuthServer: GET /oauth2/authorization/frontend-app

    Note over Browser,AuthServer: PKCE: Generate code_verifier & code_challenge

    AuthServer->>AuthServer: Generate authorization code
    AuthServer-->>Browser: Redirect to /login?code_challenge=...
    Browser->>User: Show login form
    User->>Browser: Enter credentials (admin/admin123)
    Browser->>AuthServer: POST /login (username, password)
    AuthServer->>DB: Verify credentials (BCrypt)
    DB-->>AuthServer: User + Authorities
    AuthServer->>AuthServer: Create session
    AuthServer->>DB: Store authorization code
    AuthServer-->>Browser: Redirect with authorization code
    Browser->>TokenEndpoint: POST /oauth2/token<br/>code, code_verifier, client_id
    TokenEndpoint->>TokenEndpoint: Validate PKCE (code_verifier)
    TokenEndpoint->>DB: Exchange code for tokens
    TokenEndpoint->>TokenEndpoint: Generate JWT (RSA-256)<br/>Add custom claims (roles)
    TokenEndpoint-->>Browser: access_token, refresh_token, id_token
    Browser->>Frontend: Store tokens
    Frontend-->>User: Show chat interface
```

## Flow 2: MCP Client to MCP Server (Client Credentials)

```mermaid
sequenceDiagram
    participant MCPClient as MCP Client<br/>(Port 8081)
    participant AuthServer as Authorization Server<br/>(Port 8080)
    participant MCPServer as MCP Server<br/>(Port 8082)
    participant DB as Database

    MCPClient->>MCPClient: User sends chat message
    MCPClient->>MCPClient: AI decides to call tool

    Note over MCPClient,AuthServer: Check if valid access token exists

    alt No valid token or expired
        MCPClient->>AuthServer: POST /oauth2/token<br/>grant_type=client_credentials<br/>client_id=mcp-client<br/>client_secret=***<br/>scope=mcp.read mcp.write
        AuthServer->>DB: Verify client credentials
        AuthServer->>AuthServer: Generate JWT token
        AuthServer-->>MCPClient: access_token (JWT)
        MCPClient->>MCPClient: Cache token until expiry
    end

    MCPClient->>MCPServer: POST /mcp/tools/getCustomers<br/>Authorization: Bearer {token}
    MCPServer->>MCPServer: Extract JWT from header
    MCPServer->>AuthServer: Validate JWT signature<br/>(using JWKS endpoint)
    AuthServer-->>MCPServer: JWT valid, claims
    MCPServer->>MCPServer: Check scopes (mcp.read)
    MCPServer->>MCPServer: Execute tool
    MCPServer-->>MCPClient: Tool result
```

## Flow 3: MCP Server to Backend API (Client Credentials)

```mermaid
sequenceDiagram
    participant MCPServer as MCP Server<br/>(Port 8082)
    participant AuthServer as Authorization Server<br/>(Port 8080)
    participant Backend as Backend API<br/>(Port 8080)
    participant DB as Database

    MCPServer->>MCPServer: Receive tool invocation<br/>(e.g., getCustomers)

    Note over MCPServer,AuthServer: Check if valid access token exists

    alt No valid token or expired
        MCPServer->>AuthServer: POST /oauth2/token<br/>grant_type=client_credentials<br/>client_id=mcp-server<br/>client_secret=***<br/>scope=backend.read backend.write
        AuthServer->>DB: Verify client credentials
        AuthServer->>AuthServer: Generate JWT token
        AuthServer-->>MCPServer: access_token (JWT)
        MCPServer->>MCPServer: Cache token (automatic via OAuth2AuthorizedClientManager)
    end

    MCPServer->>Backend: GET /api/v1/resos/customers<br/>Authorization: Bearer {token}
    Backend->>Backend: Extract JWT from header
    Backend->>AuthServer: Validate JWT signature<br/>(using /.well-known/jwks.json)
    AuthServer-->>Backend: JWT valid, claims
    Backend->>Backend: Check scopes/roles<br/>(backend.read OR ROLE_USER)
    Backend->>DB: Query customers table
    DB-->>Backend: Customer records
    Backend-->>MCPServer: JSON response
    MCPServer->>MCPServer: Transform to tool result
    MCPServer-->>MCPClient: Return to AI
```

## Flow 4: Token Refresh

```mermaid
sequenceDiagram
    participant Browser
    participant Frontend as React SPA
    participant AuthServer as Authorization Server
    participant DB as Database

    Browser->>Frontend: API request
    Frontend->>Frontend: Check access token expiry

    alt Access token expired but refresh token valid
        Frontend->>AuthServer: POST /oauth2/token<br/>grant_type=refresh_token<br/>refresh_token={token}
        AuthServer->>DB: Verify refresh token
        AuthServer->>DB: Check not revoked
        AuthServer->>AuthServer: Generate new access token<br/>Rotate refresh token (optional)
        AuthServer->>DB: Store new tokens
        AuthServer-->>Frontend: new access_token, new refresh_token
        Frontend->>Frontend: Update stored tokens
        Frontend->>Frontend: Retry original request
    else Refresh token expired
        Frontend-->>Browser: Redirect to /login
    end
```

## Token Structure

### Access Token (JWT)

**Header**:
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "generated-key-id"
}
```

**Payload** (custom claims added by JwtTokenCustomizer):
```json
{
  "sub": "admin",
  "aud": ["frontend-app"],
  "nbf": 1704556800,
  "scope": ["openid", "profile", "email", "chat.read", "chat.write"],
  "roles": ["ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_USER"],
  "authorities": ["ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_USER", "SCOPE_openid", "SCOPE_profile"],
  "iss": "http://localhost:8080",
  "exp": 1704560400,
  "iat": 1704556800,
  "jti": "unique-token-id"
}
```

**Signature**: RSA-256 (private key held by Authorization Server)

### Refresh Token

Opaque string (not JWT):
- Stored in database (`oauth2_authorization` table)
- Longer lifetime (hours to days)
- Can be revoked
- Single-use with rotation (optional)

### ID Token (OIDC)

JWT containing user identity:
```json
{
  "sub": "admin",
  "aud": ["frontend-app"],
  "name": "Administrator",
  "email": "admin@example.com",
  "roles": ["ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_USER"],
  "iss": "http://localhost:8080",
  "iat": 1704556800,
  "exp": 1704560400
}
```

## OAuth2 Client Configurations

### Frontend App (React SPA)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          frontend-app:
            client-id: frontend-app
            client-authentication-method: none  # Public client
            authorization-grant-type: authorization_code
            redirect-uri: http://localhost:8081/login/oauth2/code/frontend-app
            post-logout-redirect-uri: http://localhost:8081/
            scope:
              - openid
              - profile
              - email
              - chat.read
              - chat.write
        provider:
          frontend-app:
            issuer-uri: http://localhost:8080
```

**PKCE**: Required for public clients (no client secret)

### MCP Client (Service-to-Service)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          mcp-client-to-server:
            client-id: mcp-client
            client-secret: ${MCP_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope:
              - mcp.read
              - mcp.write
        provider:
          mcp-client-to-server:
            token-uri: http://localhost:8080/oauth2/token
```

### MCP Server (Service-to-Service)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          mcp-server:
            client-id: mcp-server
            client-secret: ${MCP_SERVER_SECRET}
            authorization-grant-type: client_credentials
            scope:
              - backend.read
              - backend.write
        provider:
          mcp-server:
            token-uri: http://localhost:8080/oauth2/token
```

## Security Best Practices Demonstrated

1. **PKCE for Public Clients**: Frontend app uses PKCE to prevent authorization code interception
2. **Client Credentials for Services**: Service-to-service communication uses client credentials (not user passwords)
3. **JWT with RSA Signing**: No shared secrets, keys rotated, signature verification via JWKS
4. **Short-lived Access Tokens**: Typically 1 hour expiry
5. **Refresh Token Rotation**: Optional rotation prevents token reuse attacks
6. **Database Token Storage**: Enables revocation and audit trail
7. **Scope-based Authorization**: Fine-grained access control (backend.read vs backend.write)
8. **Role-based Access Control**: User roles encoded in JWT for application-level authorization

## Token Validation Flow

```mermaid
flowchart TD
    REQUEST[Incoming Request<br/>with Bearer Token]
    EXTRACT[Extract JWT from<br/>Authorization Header]
    PARSE[Parse JWT Header]
    FETCH_KEYS[Fetch Public Keys<br/>from /.well-known/jwks.json]
    VERIFY_SIG[Verify Signature<br/>using Public Key]
    CHECK_EXP[Check Expiration<br/>'exp' claim]
    CHECK_ISS[Check Issuer<br/>'iss' claim]
    CHECK_AUD[Check Audience<br/>'aud' claim]
    EXTRACT_CLAIMS[Extract Claims<br/>roles, scopes, authorities]
    AUTH_CHECK[Authorization Check<br/>Does user have required scope/role?]
    ALLOW[Allow Request]
    REJECT[Reject Request<br/>401 or 403]

    REQUEST --> EXTRACT
    EXTRACT --> PARSE
    PARSE --> FETCH_KEYS
    FETCH_KEYS --> VERIFY_SIG
    VERIFY_SIG -->|Invalid| REJECT
    VERIFY_SIG -->|Valid| CHECK_EXP
    CHECK_EXP -->|Expired| REJECT
    CHECK_EXP -->|Valid| CHECK_ISS
    CHECK_ISS -->|Mismatch| REJECT
    CHECK_ISS -->|Match| CHECK_AUD
    CHECK_AUD -->|Mismatch| REJECT
    CHECK_AUD -->|Match| EXTRACT_CLAIMS
    EXTRACT_CLAIMS --> AUTH_CHECK
    AUTH_CHECK -->|Authorized| ALLOW
    AUTH_CHECK -->|Forbidden| REJECT
```

## Endpoints

### Authorization Server Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /oauth2/authorize` | Authorization code request |
| `POST /oauth2/token` | Token issuance and refresh |
| `POST /oauth2/revoke` | Token revocation |
| `GET /.well-known/openid-configuration` | OpenID Connect discovery |
| `GET /.well-known/jwks.json` | Public keys for JWT verification |
| `POST /oauth2/introspect` | Token introspection |
| `GET /userinfo` | OIDC user info endpoint |

### Authentication Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /login` | Login page (Thymeleaf template) |
| `POST /login` | Form submission |
| `POST /logout` | User logout |
| `GET /api/auth/user` | Current user info (mcp-client) |
| `GET /api/auth/status` | Auth status check (mcp-client) |

## Critical Files

| File | Purpose |
|------|---------|
| `backend/src/main/java/me/pacphi/ai/resos/security/AuthorizationServerConfig.java` | Auth server configuration |
| `backend/src/main/java/me/pacphi/ai/resos/security/JwtTokenCustomizer.java` | Custom JWT claims |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java` | Resource server config |
| `mcp-client/src/main/java/me/pacphi/ai/resos/config/McpClientOAuth2Config.java` | OAuth2 client config |
| `mcp-client/src/main/java/me/pacphi/ai/resos/config/SecurityConfig.java` | Frontend security |
