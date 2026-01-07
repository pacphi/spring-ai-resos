# Security Architecture

This document details the comprehensive OAuth2 security implementation with three-tier design.

## Overview

The Spring AI ResOs project implements a **zero-trust security model** with OAuth2 at every layer:

1. **Tier 1**: Backend as OAuth2 Authorization Server (issues JWT tokens)
2. **Tier 2**: MCP Server as Resource Server + OAuth2 Client
3. **Tier 3**: MCP Client as OAuth2 Client (PKCE + client credentials)

See [OAuth2 Flows Diagram](diagrams/oauth2-flows.md) for complete sequence diagrams.

---

## Security Architecture Diagram

```
┌──────────────┐     OAuth2 PKCE        ┌─────────────────────────┐
│  React SPA   │◄────────────────────────│  Authorization Server   │
│  (Browser)   │   access_token + id     │  (Backend Port 8080)    │
└──────┬───────┘                         │  - User Auth            │
       │                                 │  - JWT Issuance         │
       │ Authenticated Chat              │  - Token Validation     │
       ▼                                 └──────────┬──────────────┘
┌──────────────┐                                   │
│  MCP Client  │  OAuth2 client_credentials        │
│  (Port 8081) │───────────────────────────────────┤
└──────┬───────┘           mcp.read/write          │
       │                                           │
       │ MCP Protocol (HTTP Streamable)            │
       ▼                                           │
┌──────────────┐  OAuth2 Validation                │
│  MCP Server  │◄──────────────────────────────────┤
│  (Port 8082) │                                   │
│ Resource +   │  OAuth2 client_credentials        │
│ Client       │───────────────────────────────────┘
└──────┬───────┘     backend.read/write
       │
       │ API Calls
       ▼
┌──────────────┐  OAuth2 Validation
│  Backend API │◄──────────────────────────────────┘
│  (Port 8080) │
└──────────────┘
```

---

## Tier 1: Authorization Server (Backend)

### Configuration

**Class**: `AuthorizationServerConfig.java` (`@Order(1)`)
**Location**: `backend/src/main/java/me/pacphi/ai/resos/security/AuthorizationServerConfig.java`

### SecurityFilterChain

```java
@Bean
@Order(1)  // Highest priority - matches /oauth2/* first
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
        throws Exception {

    OAuth2AuthorizationServerConfigurer authServerConfigurer =
        new OAuth2AuthorizationServerConfigurer();

    RequestMatcher endpointsMatcher = authServerConfigurer.getEndpointsMatcher();

    http
        .securityMatcher(endpointsMatcher)  // Only /oauth2/*, /.well-known/*
        .with(authServerConfigurer, authServer ->
            authServer.oidc(Customizer.withDefaults())  // Enable OIDC
        )
        .authorizeHttpRequests(authorize ->
            authorize.anyRequest().authenticated()  // All OAuth2 endpoints need auth
        )
        .csrf(csrf ->
            csrf.ignoringRequestMatchers(endpointsMatcher)  // Disable CSRF for OAuth2 endpoints
        );

    return http.build();
}
```

**Endpoints Provided**:
- `GET /oauth2/authorize` - Authorization code request
- `POST /oauth2/token` - Token issuance and refresh
- `POST /oauth2/revoke` - Token revocation
- `POST /oauth2/introspect` - Token introspection
- `GET /.well-known/openid-configuration` - OIDC discovery
- `GET /.well-known/jwks.json` - Public keys for JWT validation
- `GET /userinfo` - OIDC user information

### JWT Token Configuration

**JWK Source** (RSA Key Generation):
```java
@Bean
public JWKSource<SecurityContext> jwkSource() {
    KeyPair keyPair = generateRsaKey();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    RSAKey rsaKey = new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();

    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
}

private static KeyPair generateRsaKey() {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
}
```

**Key Features**:
- **Algorithm**: RSA-256 (2048-bit keys)
- **Key Rotation**: New keys generated on each startup (development)
- **Production**: Use persistent keys from key store
- **Public Key Distribution**: Automatic via `/.well-known/jwks.json`

**JWT Decoder**:
```java
@Bean
public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
}
```

### Custom JWT Claims

**JwtTokenCustomizer** (`backend/src/main/java/me/pacphi/ai/resos/security/JwtTokenCustomizer.java`):

```java
@Component
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        if (context.getTokenType().getValue().equals("access_token")) {
            // Add roles claim
            Set<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());

            context.getClaims().claim("roles", roles);

            // Add authorities claim (roles + scopes)
            Set<String> authorities = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

            context.getClaims().claim("authorities", authorities);
        }

        // Also add roles to ID token (OIDC)
        if (context.getTokenType().getValue().equals("id_token")) {
            Set<String> roles = context.getPrincipal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());

            context.getClaims().claim("roles", roles);
        }
    }
}
```

**Resulting JWT Payload**:
```json
{
  "sub": "admin",
  "aud": ["frontend-app"],
  "nbf": 1704556800,
  "scope": ["openid", "profile", "email", "chat.read", "chat.write"],
  "roles": ["ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_USER"],
  "authorities": [
    "ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_USER",
    "SCOPE_openid", "SCOPE_profile", "SCOPE_email",
    "SCOPE_chat.read", "SCOPE_chat.write"
  ],
  "iss": "http://localhost:8080",
  "exp": 1704560400,
  "iat": 1704556800,
  "jti": "unique-token-id"
}
```

### OAuth2 Client Registration

**RegisteredClientRepository** (JDBC-backed):
```java
@Bean
public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
    return new JdbcRegisteredClientRepository(jdbcTemplate);
}
```

**Seeded Clients** (via `OAuth2ClientSeeder`):

#### Client 1: mcp-server

```java
RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("mcp-server")
    .clientSecret(passwordEncoder.encode("mcp-server-secret"))
    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
    .scope("backend.read")
    .scope("backend.write")
    .tokenSettings(TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofHours(1))
        .refreshTokenTimeToLive(Duration.ofDays(1))
        .build())
    .build();
```

**Purpose**: Service-to-service auth for MCP Server → Backend API calls

#### Client 2: mcp-client

```java
RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("mcp-client")
    .clientSecret(passwordEncoder.encode("mcp-client-secret"))
    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
    .scope("mcp.read")
    .scope("mcp.write")
    .tokenSettings(TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofHours(1))
        .build())
    .build();
```

**Purpose**: MCP Client → MCP Server auth

#### Client 3: frontend-app

```java
RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("frontend-app")
    // No client secret - public client
    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
    .redirectUri("http://localhost:8081/login/oauth2/code/frontend-app")
    .redirectUri("http://localhost:8081/authorized")
    .postLogoutRedirectUri("http://localhost:8081/")
    .scope("openid")
    .scope("profile")
    .scope("email")
    .scope("chat.read")
    .scope("chat.write")
    .clientSettings(ClientSettings.builder()
        .requireProofKey(true)  // PKCE required
        .requireAuthorizationConsent(false)  // Auto-approve for dev
        .build())
    .tokenSettings(TokenSettings.builder()
        .accessTokenTimeToLive(Duration.ofHours(1))
        .refreshTokenTimeToLive(Duration.ofDays(7))
        .reuseRefreshTokens(false)  // Rotate refresh tokens
        .build())
    .build();
```

**Purpose**: React SPA user authentication

**Key Settings**:
- **requireProofKey**: Enforces PKCE (prevents authorization code interception)
- **No client secret**: Public client (browser-based)
- **Refresh token rotation**: New refresh token on each use

### User Authentication

**AppUserDetailsService** (`backend/src/main/java/me/pacphi/ai/resos/security/AppUserDetailsService.java`):

```java
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final UserAuthorityRepository userAuthorityRepository;
    private final AuthorityRepository authorityRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // Load user from database
        AppUserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found: " + username));

        // Load user authorities (join query)
        List<GrantedAuthority> authorities = userAuthorityRepository
            .findByUserId(user.getId())
            .stream()
            .map(UserAuthorityEntity::getAuthorityId)
            .map(authorityRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(AuthorityEntity::getName)
            .map(SimpleGrantedAuthority::new)
            .toList();

        // Return Spring Security User
        return User.builder()
            .username(user.getUsername())
            .password(user.getPassword())  // BCrypt hashed
            .authorities(authorities)
            .accountExpired(!user.getAccountNonExpired())
            .accountLocked(!user.getAccountNonLocked())
            .credentialsExpired(!user.getCredentialsNonExpired())
            .disabled(!user.getEnabled())
            .build();
    }
}
```

**Database Queries**:
```sql
-- Load user
SELECT * FROM app_user WHERE username = ?;

-- Load authorities
SELECT a.name_01
FROM authority a
JOIN user_authority ua ON ua.authority_id = a.id
WHERE ua.user_id = ?;
```

### Authorization Server Settings

```java
@Bean
public AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder()
        .issuer(issuerUri)  // http://localhost:8080
        .build();
}
```

---

## Tier 2: Resource Server (Backend API)

### Configuration

**Class**: `ResourceServerConfig.java` (`@Order(2)`)
**Location**: `backend/src/main/java/me/pacphi/ai/resos/security/ResourceServerConfig.java`

### SecurityFilterChain

```java
@Bean
@Order(2)  // Second priority - matches /api/**, /customers/**, etc.
public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http)
        throws Exception {

    http
        .securityMatcher("/api/**", "/customers/**", "/bookings/**", "/orders/**")
        .authorizeHttpRequests(authorize -> authorize
            // Read operations
            .requestMatchers(HttpMethod.GET, "/api/**").hasAnyAuthority(
                "SCOPE_backend.read",
                "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN"
            )
            // Write operations
            .requestMatchers(HttpMethod.POST, "/api/**").hasAnyAuthority(
                "SCOPE_backend.write",
                "ROLE_OPERATOR", "ROLE_ADMIN"
            )
            .requestMatchers(HttpMethod.PUT, "/api/**").hasAnyAuthority(
                "SCOPE_backend.write",
                "ROLE_OPERATOR", "ROLE_ADMIN"
            )
            .requestMatchers(HttpMethod.DELETE, "/api/**").hasAnyAuthority(
                "SCOPE_backend.write",
                "ROLE_ADMIN"
            )
            // Admin-only endpoints
            .requestMatchers("/customers/**").hasAuthority("ROLE_ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 ->
            oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        )
        .csrf(CsrfConfigurer::disable)  // Stateless API
        .cors(Customizer.withDefaults());

    return http.build();
}
```

**Authorization Rules**:
- **GET requests**: Require `backend.read` scope OR any user role
- **POST/PUT requests**: Require `backend.write` scope OR operator/admin role
- **DELETE requests**: Require `backend.write` scope OR admin role
- **Customer management**: Admin only

### JWT Authentication Converter

**Purpose**: Extract authorities from JWT claims

```java
private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
        new JwtGrantedAuthoritiesConverter();

    // Extract from both "scope" and "authorities" claims
    grantedAuthoritiesConverter.setAuthoritiesClaimName("authorities");
    grantedAuthoritiesConverter.setAuthorityPrefix("");  // No prefix

    JwtAuthenticationConverter authenticationConverter =
        new JwtAuthenticationConverter();
    authenticationConverter.setJwtGrantedAuthoritiesConverter(
        grantedAuthoritiesConverter
    );

    return authenticationConverter;
}
```

**Behavior**:
- Reads `authorities` claim from JWT
- Converts to Spring Security `GrantedAuthority` objects
- Used in `@PreAuthorize`, `hasAuthority()` checks

---

## Tier 2: Resource Server (MCP Server)

### Configuration

**Class**: `SecurityConfig.java`
**Location**: `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java`

### SecurityFilterChain

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").authenticated()  // MCP endpoints require JWT
                .requestMatchers("/actuator/**").permitAll()  // Health checks public
                .anyRequest().permitAll()  // Allow protocol negotiation
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())  // Validate JWT from auth server
            )
            .csrf(CsrfConfigurer::disable)  // Stateless API
            .cors(Customizer.withDefaults())
            .build();
    }
}
```

**Validation Configuration** (`application.yml`):
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_SERVER_URL:http://localhost:8080}
```

**Behavior**:
- MCP client sends request with Bearer token
- Spring Security extracts JWT from `Authorization` header
- Validates signature using public key from `issuer-uri/.well-known/jwks.json`
- Checks expiration, issuer, audience claims
- Extracts authorities from claims
- Allows request if valid

---

## Tier 3: OAuth2 Client (MCP Server)

**Purpose**: Call backend API with client credentials

### Configuration

**ResOsConfig** (`mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsConfig.java`):

```java
@Configuration
public class ResOsConfig {

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        // Build provider chain
        OAuth2AuthorizedClientProvider provider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()  // Support client_credentials grant
                .refreshToken()       // Support token refresh
                .build();

        // Create manager
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
            new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                authorizedClientService
            );
        manager.setAuthorizedClientProvider(provider);

        return manager;
    }

    @Bean
    public RestClient restClient(
            OAuth2AuthorizedClientManager authorizedClientManager,
            @Value("${resos.api.endpoint}") String apiEndpoint) {

        return RestClient.builder()
            .baseUrl(apiEndpoint)
            .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
                authorizedClientManager,
                "mcp-server"  // Registration ID
            ))
            .requestInterceptor(new LoggingInterceptor())
            .build();
    }

    @Bean
    public DefaultApi defaultApi(RestClient restClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();
        return factory.createClient(DefaultApi.class);
    }
}
```

**OAuth2 Client Configuration** (`application.yml`):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          mcp-server:
            client-id: mcp-server
            client-secret: ${MCP_SERVER_SECRET:mcp-server-secret}
            authorization-grant-type: client_credentials
            scope:
              - backend.read
              - backend.write
        provider:
          mcp-server:
            token-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/token
```

**Flow**:
1. MCP Server needs to call backend API
2. `OAuth2ClientHttpRequestInterceptor` intercepts request
3. Checks if valid access token exists for "mcp-server" client
4. If not, calls token endpoint with client credentials
5. Receives JWT access token
6. Adds `Authorization: Bearer {token}` header
7. Makes backend API call
8. Backend validates token, allows request

---

## Tier 3: OAuth2 Client (MCP Client)

### Dual OAuth2 Configuration

MCP Client has **two OAuth2 registrations**:

1. **frontend-app**: For user authentication (browser-based, PKCE)
2. **mcp-client-to-server**: For MCP server communication (service-to-service)

### SecurityConfig

**Location**: `mcp-client/src/main/java/me/pacphi/ai/resos/config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                // Public static resources
                .requestMatchers(
                    "/", "/index.html", "/assets/**",
                    "/favicon.ico", "/manifest.json"
                ).permitAll()
                // Auth status endpoint (for React)
                .requestMatchers("/api/auth/status", "/api/auth/login-url").permitAll()
                // Actuator
                .requestMatchers("/actuator/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true)  // Redirect after login
            )
            .oauth2Client(Customizer.withDefaults())  // Enable OAuth2 client
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:8081"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

### OAuth2 Client Configuration

**McpClientOAuth2Config** (`mcp-client/src/main/java/me/pacphi/ai/resos/config/McpClientOAuth2Config.java`):

```java
@Configuration
public class McpClientOAuth2Config {

    @Bean
    public McpSyncClientCustomizer mcpSyncClientCustomizer(
            OAuth2AuthorizedClientManager authorizedClientManager) {

        return clientBuilder -> {
            // Add OAuth2 authentication to MCP requests
            AuthenticationMcpTransportContextProvider contextProvider =
                new AuthenticationMcpTransportContextProvider();

            OAuth2ClientCredentialsSyncHttpRequestCustomizer requestCustomizer =
                new OAuth2ClientCredentialsSyncHttpRequestCustomizer(
                    authorizedClientManager,
                    "mcp-client-to-server"  // Registration ID
                );

            clientBuilder.transportContext(contextProvider);
            clientBuilder.requestCustomizer(requestCustomizer);
        };
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        OAuth2AuthorizedClientProvider provider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .refreshToken()
                .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
            new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository,
                authorizedClientService
            );
        manager.setAuthorizedClientProvider(provider);

        return manager;
    }
}
```

**Libraries**:
- `org.springaicommunity:mcp-client-security:0.0.5`
- Provides `OAuth2ClientCredentialsSyncHttpRequestCustomizer`
- Handles automatic token fetch/refresh for MCP client

**Application Configuration** (`application.yml`):
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          # User authentication (browser)
          frontend-app:
            client-id: frontend-app
            client-authentication-method: none
            authorization-grant-type: authorization_code
            redirect-uri: http://localhost:8081/login/oauth2/code/frontend-app
            scope:
              - openid
              - profile
              - email
              - chat.read
              - chat.write

          # MCP server communication (service-to-service)
          mcp-client-to-server:
            client-id: mcp-client
            client-secret: ${MCP_CLIENT_SECRET:mcp-client-secret}
            authorization-grant-type: client_credentials
            scope:
              - mcp.read
              - mcp.write

        provider:
          frontend-app:
            issuer-uri: ${AUTH_SERVER_URL:http://localhost:8080}
          mcp-client-to-server:
            token-uri: ${AUTH_SERVER_URL:http://localhost:8080}/oauth2/token
```

---

## Default Security Configuration

### Form Login

**Class**: `DefaultSecurityConfig.java` (`@Order(3)`)
**Location**: `backend/src/main/java/me/pacphi/ai/resos/security/DefaultSecurityConfig.java`

```java
@Bean
@Order(3)  // Lowest priority - catches everything not matched by Auth Server or Resource Server
public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            // Public endpoints
            .requestMatchers("/login", "/logout", "/error").permitAll()
            .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/h2-console/**").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            // OAuth2 endpoints
            .requestMatchers("/oauth2/**", "/.well-known/**").permitAll()
            // Everything else requires auth
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")  // Custom login page
            .permitAll()
        )
        .logout(logout -> logout
            .logoutSuccessUrl("/login?logout")
            .permitAll()
        )
        .csrf(csrf -> csrf
            .ignoringRequestMatchers("/h2-console/**")  // H2 console needs CSRF disabled
        )
        .cors(Customizer.withDefaults());

    return http.build();
}

@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // Cost factor 12
}
```

### Login Page

**Template**: `backend/src/main/resources/templates/login.html` (Thymeleaf)

**Features**:
- Username/password form
- Error message display
- CSRF token (automatic)
- Responsive design

---

## Security Flows

### Flow 1: User Authentication (PKCE)

See [OAuth2 Flows - User Authentication](diagrams/oauth2-flows.md#flow-1-user-authentication-authorization-code--pkce)

**Steps**:
1. User visits http://localhost:8081
2. React SPA checks auth status: `GET /api/auth/status`
3. Not authenticated → redirect to OAuth2 login
4. Browser → `/oauth2/authorization/frontend-app`
5. Spring Security generates PKCE parameters:
   - `code_verifier`: Random 43-128 character string
   - `code_challenge`: Base64URL(SHA256(code_verifier))
6. Redirect to auth server: `/oauth2/authorize?code_challenge=...`
7. User sees login page, enters credentials
8. Auth server validates credentials (BCrypt check)
9. Auth server generates authorization code
10. Redirect back to client: `/login/oauth2/code/frontend-app?code=...`
11. Client exchanges code for tokens:
    - POST `/oauth2/token` with code + code_verifier
    - Auth server validates PKCE (hash(code_verifier) == code_challenge)
    - Returns access_token, refresh_token, id_token (all JWTs)
12. Client stores tokens
13. All API requests include `Authorization: Bearer {access_token}`

**PKCE Security**:
- Prevents authorization code interception
- Even if code is stolen, attacker cannot exchange it (needs code_verifier)
- Essential for public clients (no client secret)

### Flow 2: Client Credentials (MCP Client → MCP Server)

See [OAuth2 Flows - Client Credentials](diagrams/oauth2-flows.md#flow-2-mcp-client-to-mcp-server-client-credentials)

**Steps**:
1. User sends chat message to MCP Client
2. MCP Client needs to call MCP Server tool
3. Check if valid access token exists
4. If not (or expired):
   - POST `/oauth2/token` with:
     - `grant_type=client_credentials`
     - `client_id=mcp-client`
     - `client_secret=mcp-client-secret`
     - `scope=mcp.read mcp.write`
   - Auth server validates client credentials
   - Returns access_token (JWT)
5. Add `Authorization: Bearer {token}` to MCP request
6. MCP Server validates JWT
7. Request allowed

**Automatic Handling**:
- `OAuth2AuthorizedClientManager` caches tokens
- Automatically refreshes when expired
- No manual token management needed

### Flow 3: Client Credentials (MCP Server → Backend)

**Same as Flow 2**, but:
- Client: mcp-server
- Scopes: backend.read, backend.write
- Target: Backend API

**Interceptor Automation**:
```java
RestClient restClient = RestClient.builder()
    .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
        authorizedClientManager,
        "mcp-server"
    ))
    .build();

// Every request automatically gets Bearer token
ResponseEntity<List<Customer>> response = restClient.get()
    .uri("/customers")
    .retrieve()
    .toEntity(new ParameterizedTypeReference<List<Customer>>() {});
```

---

## Security Best Practices Demonstrated

### 1. BCrypt Password Hashing

**Configuration**:
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // Cost factor 12
}
```

**Usage**:
```java
String rawPassword = "admin123";
String hashed = passwordEncoder.encode(rawPassword);
// Result: $2a$12$N0vy...random salt...hashed value
```

**Why Cost 12?**:
- Balance between security and performance
- ~250ms to hash (acceptable for login)
- Makes brute-force attacks expensive

### 2. JWT with RSA Signing

**Why RSA vs HMAC?**:
- **HMAC**: Symmetric (same secret for sign and verify) - any service can create tokens
- **RSA**: Asymmetric (private key signs, public key verifies) - only auth server creates tokens

**Security Benefit**:
- Even if resource server compromised, cannot forge tokens
- Public key can be distributed safely

### 3. PKCE for Public Clients

**Why PKCE?**:
- React SPA runs in browser (cannot keep secrets)
- Authorization code could be intercepted (malicious browser extension, XSS)
- PKCE ensures only the client that requested code can exchange it

**Implementation**:
```java
clientSettings.requireProofKey(true)  // Enforce PKCE
```

### 4. Short-Lived Access Tokens

**Configuration**:
```java
tokenSettings.accessTokenTimeToLive(Duration.ofHours(1))
```

**Why 1 Hour?**:
- Limits damage if token stolen
- Forces periodic re-authentication check
- Refresh tokens available for long sessions

### 5. Refresh Token Rotation

**Configuration**:
```java
tokenSettings.reuseRefreshTokens(false)  // Rotate on use
```

**Why Rotate?**:
- One-time use prevents token reuse attacks
- If refresh token stolen, only works once
- Detection: Multiple uses of same token = attack

### 6. Database-Backed Token Storage

**Tables**:
- `oauth2_authorization` - Active tokens
- `oauth2_authorization_consent` - User consents

**Benefits**:
- Token revocation possible (delete from table)
- Audit trail (who has tokens, when issued)
- Centralized token management
- Can force logout (delete user's tokens)

### 7. Scope-Based Authorization

**Backend API**:
```java
.requestMatchers(HttpMethod.GET, "/api/**")
    .hasAnyAuthority("SCOPE_backend.read", "ROLE_USER")
```

**Why Both Scopes and Roles?**:
- **Scopes**: For service-to-service (mcp-server has backend.read)
- **Roles**: For user-based access (admin has ROLE_ADMIN)
- Flexible authorization rules

### 8. CORS Configuration

**Purpose**: Allow React dev server to call backend

```java
configuration.setAllowedOrigins(List.of(
    "http://localhost:3000",  // Create React App
    "http://localhost:5173",  // Vite dev server
    "http://localhost:8081"   // Production (served by Spring Boot)
));
configuration.setAllowCredentials(true);  // Allow cookies
```

**Security**: Only specific origins allowed, not wildcard (`*`)

### 9. CSRF Protection

**For Browser Requests**:
```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
)
```

**For API Requests**:
```java
.csrf(CsrfConfigurer::disable)  // APIs use Bearer tokens, not cookies
```

**Why Different?**:
- Browser (cookies) → vulnerable to CSRF → need CSRF tokens
- API (Bearer tokens) → not vulnerable to CSRF → disable for convenience

---

## Default Users & Roles

### Seeded Users

| Username | Password | Roles | Description |
|----------|----------|-------|-------------|
| admin | admin123 | ROLE_ADMIN, ROLE_OPERATOR, ROLE_USER | Full access |
| operator | operator123 | ROLE_OPERATOR, ROLE_USER | Staff/manager |
| user | user123 | ROLE_USER | Basic customer |

**Seed File**: `backend/seed-data/users.csv`
**Passwords**: BCrypt hashed during seeding

### Role Permissions

| Role | GET /api/* | POST /api/* | DELETE /api/* | /customers/** |
|------|-----------|------------|--------------|---------------|
| ROLE_USER | ✅ | ❌ | ❌ | ❌ |
| ROLE_OPERATOR | ✅ | ✅ | ❌ | ❌ |
| ROLE_ADMIN | ✅ | ✅ | ✅ | ✅ |

**Scope Permissions**:
| Scope | Allowed Operations |
|-------|-------------------|
| backend.read | GET /api/* |
| backend.write | POST, PUT, DELETE /api/* |
| mcp.read | GET /mcp/* |
| mcp.write | POST /mcp/* |

---

## Token Lifecycle

### Access Token

**Lifetime**: 1 hour
**Format**: JWT (base64url encoded JSON)
**Size**: ~1-2KB (depends on claims)

**Storage**:
- Browser: SessionStorage or memory (React state)
- Service: OAuth2AuthorizedClientService (in-memory cache)
- Database: oauth2_authorization table (for revocation)

### Refresh Token

**Lifetime**: 7 days (frontend-app), 1 day (services)
**Format**: Opaque string (not JWT)
**Storage**: Database only (oauth2_authorization table)

**Rotation**: Yes (new refresh token on each use)

### ID Token (OIDC)

**Lifetime**: 1 hour
**Format**: JWT
**Purpose**: User identity information (name, email, roles)
**Used By**: Frontend to display user profile

---

## Security Headers

### Automatic Headers

Spring Security adds these by default:

```http
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Expires: 0
```

### Custom Headers (Future Enhancement)

```java
http.headers(headers -> headers
    .contentSecurityPolicy("default-src 'self'")
    .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
    .permissionsPolicy(policy ->
        policy.policy("geolocation=(), microphone=(), camera=()")
    )
);
```

---

## Critical Files

| File | Purpose | Lines |
|------|---------|-------|
| `backend/src/main/java/me/pacphi/ai/resos/security/AuthorizationServerConfig.java` | OAuth2 Auth Server | ~200 |
| `backend/src/main/java/me/pacphi/ai/resos/security/ResourceServerConfig.java` | API protection | ~100 |
| `backend/src/main/java/me/pacphi/ai/resos/security/DefaultSecurityConfig.java` | Form login | ~80 |
| `backend/src/main/java/me/pacphi/ai/resos/security/AppUserDetailsService.java` | User loading | ~60 |
| `backend/src/main/java/me/pacphi/ai/resos/security/JwtTokenCustomizer.java` | JWT claims | ~50 |
| `backend/src/main/java/me/pacphi/ai/resos/security/OAuth2ClientSeeder.java` | Client seeding | ~120 |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java` | MCP resource server | ~50 |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsConfig.java` | OAuth2 client | ~150 |
| `mcp-client/src/main/java/me/pacphi/ai/resos/config/SecurityConfig.java` | Frontend security | ~100 |
| `mcp-client/src/main/java/me/pacphi/ai/resos/config/McpClientOAuth2Config.java` | MCP OAuth2 | ~80 |

## Related Documentation

- [OAuth2 Flows Diagram](diagrams/oauth2-flows.md) - Visual sequence diagrams
- [Data Architecture](05-data-architecture.md) - Security entities
- [ADR-004: WebMVC over WebFlux](adr/004-webmvc-over-webflux.md) - OAuth2 compatibility
- [Migration Patterns](11-migration-patterns.md) - Spring Security 7.x patterns
