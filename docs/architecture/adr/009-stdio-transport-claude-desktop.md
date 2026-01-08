# ADR-009: STDIO Transport Support for Claude Desktop Integration

## Status

**Accepted** - Implemented January 2026

## Context

Following the OAuth2 security implementation (ADR-006) and HTTP Streamable transport configuration (ADR-005, ADR-008), the MCP server was only accessible via HTTP with OAuth2 JWT authentication. This configuration works well for the web-based chatbot but prevented Claude Desktop integration, which requires STDIO transport.

### Problem Statement

Claude Desktop communicates with MCP servers using STDIO transport (stdin/stdout), not HTTP. The existing MCP server configuration:

1. Uses `spring-ai-starter-mcp-server-webmvc` (HTTP-only)
2. Requires OAuth2 JWT tokens for `/mcp/**` endpoints
3. Runs an embedded Tomcat server on port 8082

This made the MCP server incompatible with Claude Desktop's native MCP integration.

### Requirements

- Support both HTTP Streamable (web chatbot) and STDIO (Claude Desktop) transports
- Keep HTTP transport with OAuth2 as the default (backward compatible)
- Provide simple build-time selection of transport mode
- Disable OAuth2 when not needed (STDIO mode)

## Decision

Implement dual transport support using **Maven profiles** and **conditional bean configuration**.

### Implementation Details

#### 1. Maven Profiles

**File**: `mcp-server/pom.xml`

Two profiles control which Spring AI MCP starter is included:

```xml
<profiles>
    <!-- Default: HTTP/WebMVC transport with OAuth2 -->
    <profile>
        <id>webmvc</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-oauth2-client</artifactId>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
            </dependency>
        </dependencies>
    </profile>

    <!-- STDIO: For Claude Desktop (no HTTP, no OAuth2) -->
    <profile>
        <id>stdio</id>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-starter-mcp-server</artifactId>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

**Usage**:

- Build for web chatbot: `mvn clean package` (default)
- Build for Claude Desktop: `mvn clean package -Pstdio`

#### 2. Conditional Security Configuration

**File**: `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "security.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {
    // Only loaded when OAuth2 is enabled (default)
}
```

**File**: `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsConfig.java`

OAuth2 beans are conditional:

```java
@Bean
@ConditionalOnProperty(name = "security.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public OAuth2AuthorizedClientService authorizedClientService(...) { }

@Bean
@ConditionalOnProperty(name = "security.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public OAuth2AuthorizedClientManager authorizedClientManager(...) { }

@Bean
public RestClient resosRestClient(
        ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManagerProvider) {
    // Uses ObjectProvider for optional OAuth2 injection
}
```

#### 3. STDIO Profile Configuration

**File**: `mcp-server/src/main/resources/application-stdio.yml`

```yaml
spring:
  main:
    web-application-type: none
    banner-mode: off

  ai:
    mcp:
      server:
        stdio: true

security:
  oauth2:
    enabled: false

logging:
  pattern:
    console: '%msg%n' # Minimal logging to avoid STDIO interference
```

#### 4. Claude Desktop Configuration

**claude_desktop_config.json**:

```json
{
  "mcpServers": {
    "spring-ai-resos": {
      "command": "java",
      "args": ["-Dspring.profiles.active=stdio", "-jar", "/path/to/spring-ai-resos-mcp-server-1.0.0-SNAPSHOT.jar"],
      "env": {
        "RESOS_API_ENDPOINT": "http://localhost:8080/api/v1/resos"
      }
    }
  }
}
```

## Consequences

### Positive

1. **Claude Desktop Support**: MCP tools accessible from Claude Desktop
2. **Backward Compatible**: Default behavior unchanged (HTTP + OAuth2)
3. **Clean Separation**: Transport and security concerns isolated via profiles
4. **Simple Configuration**: Single property (`security.oauth2.enabled`) controls security behavior
5. **Follows Existing Patterns**: Uses same conditional bean pattern as elsewhere in project

### Negative

1. **Two Build Artifacts**: Need separate builds for web and desktop use
2. **Profile Awareness**: Developers must know which profile to use
3. **No Runtime Switching**: Transport mode is compile-time decision

### Neutral

1. **Testing Complexity**: Need tests for both profiles
2. **Documentation**: Must document both transport options clearly

## Test Strategy

**Decision**: Skip test compilation and execution when building with the STDIO profile (`<maven.test.skip>true</maven.test.skip>`).

### Rationale

1. **STDIO is a deployment variant, not a separate product** - Same source code, different transport
2. **Core logic tested via webmvc profile** - `ResOsService`, `DefaultApi`, and MCP tools are fully tested
3. **OAuth2 tests require infrastructure** - `BackendApiOAuth2ClientTest` and `McpEndpointSecurityTest` require `ClientRegistrationRepository` which isn't available in STDIO profile
4. **Industry practice** - Many projects skip tests for "packaging-only" profiles (e.g., native image builds)

### Test Classes

| Test Class                        | Profile     | Description                                                                          |
| --------------------------------- | ----------- | ------------------------------------------------------------------------------------ |
| `BackendApiOAuth2ClientTest`      | webmvc only | Tests OAuth2 client credentials flow                                                 |
| `McpEndpointSecurityTest`         | webmvc only | Tests HTTP endpoint security with JWT                                                |
| `StdioTransportConfigurationTest` | webmvc only | Tests STDIO profile bean configuration (runs with Spring profile, not Maven profile) |

## CI/CD Workflow Integration

### CI Workflow (`.github/workflows/ci.yml`)

Added STDIO compile verification to ensure the profile builds correctly:

```yaml
- name: Build and test (webmvc profile)
  run: ./mvnw clean install
- name: Verify STDIO profile compiles
  run: ./mvnw clean compile -pl mcp-server -Pstdio
```

### Release Workflow (`.github/workflows/release.yml`)

Produces dual artifacts with transport-specific naming:

```yaml
- name: Build all modules
  run: |
    # Build webmvc (default) variant and install to local repo
    # Using 'install' so artifacts are available for single-module stdio build
    ./mvnw clean install -DskipTests
    mv mcp-server/target/spring-ai-resos-mcp-server-${VERSION}.jar \
       mcp-server/target/spring-ai-resos-mcp-server-webmvc-${VERSION}.jar

    # Build stdio variant (dependencies resolved from local repo)
    ./mvnw package -pl mcp-server -Pstdio
    mv mcp-server/target/spring-ai-resos-mcp-server-${VERSION}.jar \
       mcp-server/target/spring-ai-resos-mcp-server-stdio-${VERSION}.jar
```

## Artifact Naming Convention

Release artifacts use transport-specific naming to differentiate the two MCP server variants:

| Artifact                                          | Transport       | OAuth2 | Use Case                      |
| ------------------------------------------------- | --------------- | ------ | ----------------------------- |
| `spring-ai-resos-mcp-server-webmvc-{VERSION}.jar` | HTTP Streamable | Yes    | Web chatbot, cloud deployment |
| `spring-ai-resos-mcp-server-stdio-{VERSION}.jar`  | STDIO           | No     | Claude Desktop integration    |

Local builds (from source) retain the standard naming: `spring-ai-resos-mcp-server-1.0.0-SNAPSHOT.jar`

## Files Changed

| File                                                           | Change                                                     |
| -------------------------------------------------------------- | ---------------------------------------------------------- |
| `mcp-server/pom.xml`                                           | Added `webmvc` and `stdio` Maven profiles with `skipTests` |
| `mcp-server/src/main/java/.../SecurityConfig.java`             | Added `@ConditionalOnProperty`                             |
| `mcp-server/src/main/java/.../ResOsConfig.java`                | Made OAuth2 beans conditional, used `ObjectProvider`       |
| `mcp-server/src/main/resources/application-stdio.yml`          | New STDIO profile configuration                            |
| `mcp-server/src/test/.../StdioTransportConfigurationTest.java` | New test for STDIO bean configuration                      |
| `.github/workflows/ci.yml`                                     | Added STDIO compile verification step                      |
| `.github/workflows/release.yml`                                | Added dual artifact build and collection                   |
| `README.md`                                                    | Updated Claude Desktop instructions with download option   |
| `docs/architecture/07-mcp-architecture.md`                     | Added Transport Options section                            |

## Related Decisions

- [ADR-005: HTTP Streamable Transport](005-http-streamable-transport.md) - Original transport decision
- [ADR-006: OAuth2 Authorization Server](006-oauth2-authorization-server-integration.md) - Security foundation
- [ADR-008: MCP Client-Server Configuration Fixes](008-mcp-client-server-configuration-fixes.md) - HTTP transport configuration

## References

- [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Claude Desktop MCP Configuration](https://docs.anthropic.com/en/docs/claude-desktop/mcp)

## Decision Date

January 2026

## Changelog

| Date     | Change                                  |
| -------- | --------------------------------------- |
| Jan 2026 | Initial ADR for STDIO transport support |
