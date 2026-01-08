# MCP Architecture

This document details the Model Context Protocol (MCP) integration, tool provider implementation, and client-server communication patterns.

## What is Model Context Protocol?

**MCP** is a protocol specification for enabling AI agents to discover and invoke tools (functions) provided by applications. It standardizes:

- **Tool Discovery**: How AI learns what tools are available
- **Tool Invocation**: How AI calls tools with parameters
- **Transport**: How client and server communicate (HTTP, STDIO, etc.)
- **Security**: How requests are authenticated and authorized

**Benefits**:

- Standardized protocol (not proprietary)
- Multiple transport options (HTTP, STDIO)
- Language-agnostic (JSON-based)
- Security built-in (OAuth2 support)
- Supported by Claude, other AI platforms

**Specification**: [https://modelcontextprotocol.info/](https://modelcontextprotocol.info/)

---

## MCP Server Implementation

### Overview

**Module**: mcp-server
**Port**: 8082
**Main Class**: `SpringAiResOsMcpServerApplication`
**Transport**: HTTP Streamable (WebMVC-based)

### Tool Provider - ResOsService

**Location**: `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsService.java`

**Purpose**: Expose restaurant management tools for AI agents

```java
@Component
public class ResOsService {

    private final DefaultApi resOsApi;  // Backend API client

    public ResOsService(DefaultApi resOsApi) {
        this.resOsApi = resOsApi;
    }

    @Tool(description = "Fetch all restaurant tables")
    public List<Table> getTables() {
        return Optional.ofNullable(resOsApi.tablesGet().getBody())
            .orElse(List.of());
    }

    @Tool(description = "Fetch customer records with optional filtering and pagination")
    public List<Customer> getCustomers(
            @ToolParam(description = "Maximum number of records to return, default 100")
            Integer limit,
            @ToolParam(description = "Number of records to skip for pagination, default 0")
            Integer skip,
            @ToolParam(description = "Field name to sort results by (e.g., 'name', 'email')")
            String sort,
            @ToolParam(description = "SQL-like filter query (e.g., \"email = 'test@example.com'\")")
            String customQuery
    ) {
        return Optional.ofNullable(
            resOsApi.customersGet(limit, skip, sort, customQuery).getBody()
        ).orElse(List.of());
    }

    @Tool(description = "Fetch customer feedback and reviews with optional filtering")
    public List<Feedback> getFeedback(
            Integer limit, Integer skip, String sort, String customQuery) {
        return Optional.ofNullable(
            resOsApi.feedbackGet(limit, skip, sort, customQuery).getBody()
        ).orElse(List.of());
    }

    @Tool(description = "Fetch customer by ID")
    public Customer getCustomerById(
            @ToolParam(description = "Customer UUID") String id) {
        return resOsApi.customersIdGet(UUID.fromString(id)).getBody();
    }

    @Tool(description = "Fetch feedback by ID")
    public Feedback getFeedbackById(
            @ToolParam(description = "Feedback UUID") String id) {
        return resOsApi.feedbackIdGet(UUID.fromString(id)).getBody();
    }

    @Tool(description = "Fetch opening hours for the next two weeks")
    public List<OpeningHours> getOpeningHours() {
        return Optional.ofNullable(resOsApi.openinghoursGet().getBody())
            .orElse(List.of());
    }

    @Tool(description = "Fetch opening hours by ID")
    public OpeningHours getOpeningHoursById(
            @ToolParam(description = "Opening hours UUID") String id) {
        return resOsApi.openinghoursIdGet(UUID.fromString(id)).getBody();
    }
}
```

### Spring AI Tool Discovery

**Automatic Registration**:

Spring AI MCP Server starter automatically:

1. Scans application context for `@Tool` annotated methods
2. Extracts method signatures and parameter types
3. Reads `@ToolParam` descriptions
4. Generates JSON Schema for each tool
5. Exposes tools via MCP protocol

**Generated Tool Schema Example**:

```json
{
  "name": "getCustomers",
  "description": "Fetch customer records with optional filtering and pagination",
  "inputSchema": {
    "type": "object",
    "properties": {
      "limit": {
        "type": "integer",
        "description": "Maximum number of records to return, default 100"
      },
      "skip": {
        "type": "integer",
        "description": "Number of records to skip for pagination, default 0"
      },
      "sort": {
        "type": "string",
        "description": "Field name to sort results by (e.g., 'name', 'email')"
      },
      "customQuery": {
        "type": "string",
        "description": "SQL-like filter query (e.g., \"email = 'test@example.com'\")"
      }
    }
  }
}
```

**AI Receives**: List of available tools with schemas
**AI Decides**: Which tool to call based on user question
**AI Invokes**: Tool with appropriate parameters

### MCP Endpoints

**Auto-Exposed by Spring AI**:

| Endpoint                | Method | Purpose                           |
| ----------------------- | ------ | --------------------------------- |
| `/mcp/info`             | GET    | Server information                |
| `/mcp/tools`            | GET    | List available tools              |
| `/mcp/tools/{toolName}` | POST   | Invoke specific tool              |
| `/mcp/resources`        | GET    | List available resources (if any) |
| `/mcp/prompts`          | GET    | List available prompts (if any)   |

**Example Request**:

```http
POST /mcp/tools/getCustomers HTTP/1.1
Host: localhost:8082
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "limit": 100,
  "skip": 0,
  "sort": "name",
  "customQuery": null
}
```

**Example Response**:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "result": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "name": "John Doe",
      "email": "john@example.com",
      "phone": "+1-555-0100",
      "bookingCount": 5,
      "totalSpent": 342.50
    },
    ...
  ]
}
```

### Backend API Client Configuration

See [ResOsConfig Details](03-module-architecture.md#backend-api-client-configuration) in Module Architecture.

**Key Components**:

- **RestClient**: JDK HttpClient-based HTTP client
- **OAuth2 Interceptor**: Automatically adds Bearer tokens
- **DefaultApi**: Type-safe interface from OpenAPI generation
- **Logging Interceptor**: Request/response logging for debugging

---

## MCP Client Implementation

### Overview

**Module**: mcp-client (Spring AI ChatClient + React SPA)
**Transport**: HTTP to MCP Server at http://localhost:8082

### McpSyncClientManager

**Location**: `mcp-client/src/main/java/me/pacphi/ai/resos/service/McpSyncClientManager.java`

**Purpose**: Lightweight wrapper for Spring AI autoconfigured MCP clients

```java
@Component
public class McpSyncClientManager {

    private final ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider;

    public McpSyncClientManager(ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider) {
        this.mcpSyncClientsProvider = mcpSyncClientsProvider;
    }

    public List<McpSyncClient> newMcpSyncClients() {
        return mcpSyncClientsProvider.getIfAvailable(List::of);
    }
}
```

**Why ObjectProvider?**:

- Lazy initialization (clients created on demand)
- Graceful handling if no clients configured
- Spring Boot autoconfiguration compatible

### MCP Client Configuration

**application.yml**:

```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC # Servlet-based (not ASYNC/WebFlux)
        initialized: false # Lazy init per request
        http:
          connections:
            butler: # Connection name
              url: ${MCP_SERVER_URL:http://localhost:8082}
```

**OAuth2 Integration** (via McpClientOAuth2Config):

```java
@Bean
public McpSyncClientCustomizer mcpSyncClientCustomizer(
        OAuth2AuthorizedClientManager authorizedClientManager) {

    return clientBuilder -> {
        // Transport context provider
        AuthenticationMcpTransportContextProvider contextProvider =
            new AuthenticationMcpTransportContextProvider();

        // Request customizer (adds OAuth2 token)
        OAuth2ClientCredentialsSyncHttpRequestCustomizer requestCustomizer =
            new OAuth2ClientCredentialsSyncHttpRequestCustomizer(
                authorizedClientManager,
                "mcp-client-to-server"
            );

        clientBuilder.transportContext(contextProvider);
        clientBuilder.requestCustomizer(requestCustomizer);
    };
}
```

**Behavior**:

- Every MCP request automatically includes `Authorization: Bearer {token}`
- Token fetched/refreshed automatically
- No manual token management

---

## Tool Invocation Flow

See [MCP Tool Invocation Diagram](diagrams/mcp-tool-invocation.md) for complete sequence.

### Integration with Spring AI ChatClient

**ChatService** (`mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatService.java`):

```java
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final McpSyncClientManager mcpSyncClientManager;

    public void streamResponseToQuestion(
            String question,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        // Get MCP clients
        List<McpSyncClient> mcpClients = mcpSyncClientManager.newMcpSyncClients();

        // Build tool callback provider
        SyncMcpToolCallbackProvider toolCallbackProvider =
            SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpClients)
                .build();

        // Stream response from LLM
        var stream = chatClient.prompt()
            .system(buildSystemPrompt())
            .user(question)
            .toolCallbacks(toolCallbackProvider.getToolCallbacks())
            .advisors(new MessageChatMemoryAdvisor(chatMemory))
            .stream()
            .content();

        // Subscribe to token stream
        stream.subscribe(onToken, onError, onComplete);
    }
}
```

**Process**:

1. User asks question
2. ChatService creates `SyncMcpToolCallbackProvider` with MCP clients
3. ChatClient sends prompt to LLM with available tools
4. LLM analyzes question, decides if tool needed
5. If yes: LLM returns tool call request
6. `SyncMcpToolCallbackProvider` invokes MCP client
7. MCP client sends HTTP request to MCP server (with OAuth2 token)
8. MCP server executes tool, returns result
9. Result sent back to LLM
10. LLM generates response incorporating tool result
11. Response streamed back to user token-by-token

### System Prompt Engineering

**Purpose**: Guide AI on when and how to use tools

```java
private String buildSystemPrompt() {
    LocalDate today = LocalDate.now();
    return String.format("""
        You are a helpful assistant for a restaurant reservation system.
        Current date: %s

        Guidelines:
        - Use ISO 8601 date format (YYYY-MM-DD) for all dates
        - When filtering data, use the customQuery parameter with SQL-like syntax
        - For date comparisons, use ISO format (e.g., "created_at >= '2026-01-01'")
        - Do NOT use SQL functions in customQuery (e.g., avoid DATE(), NOW())
        - Be concise and helpful in your responses
        - If a tool returns empty results, suggest alternative searches

        Available Tools:
        - getTables(): Fetch restaurant table inventory
        - getCustomers(filters): Search and list customer records
        - getFeedback(filters): Fetch customer feedback and reviews
        - getOpeningHours(): Check restaurant operating hours

        Best Practices:
        - Fetch data first, then filter in conversation if needed
        - Use customQuery for precise database filtering
        - Respect pagination (limit, skip parameters)
        """, today);
}
```

**Key Elements**:

- **Current date**: Helps AI understand temporal context
- **Format guidance**: Ensures consistent date handling
- **Tool overview**: Reminds AI what's available
- **Best practices**: Optimizes tool usage patterns

---

## Transport Layer - HTTP Streamable

### Why HTTP Streamable?

See [ADR-005: HTTP Streamable Transport](adr/005-http-streamable-transport.md)

**Advantages**:

- MCP spec standard (SSE deprecated)
- OAuth2 Bearer tokens work naturally
- Spring Security integration seamless
- Compatible with WebMVC (servlet stack)
- Standard HTTP request/response

**vs SSE (Deprecated)**:

- SSE: Unidirectional (server → client only)
- HTTP Streamable: Bidirectional (request + response)
- SSE: OAuth2 difficult (no standard header)
- HTTP Streamable: Bearer token in `Authorization` header

### Configuration

**MCP Server** (`mcp-server/src/main/resources/application.yml`):

```yaml
spring:
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.2-modified
        # HTTP Streamable is the default transport for WebMVC
```

**MCP Client** (`mcp-client/src/main/resources/application.yml`):

```yaml
spring:
  ai:
    mcp:
      client:
        type: SYNC # Servlet-based, not reactive
        initialized: false
        http:
          connections:
            butler:
              url: http://localhost:8082
```

### Request Format

**Tool List Request**:

```http
GET /mcp/tools HTTP/1.1
Host: localhost:8082
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Accept: application/json
```

**Tool List Response**:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "tools": [
    {
      "name": "getTables",
      "description": "Fetch all restaurant tables",
      "inputSchema": {
        "type": "object",
        "properties": {}
      }
    },
    {
      "name": "getCustomers",
      "description": "Fetch customer records with optional filtering and pagination",
      "inputSchema": {
        "type": "object",
        "properties": {
          "limit": { "type": "integer", "description": "Maximum number of records" },
          "skip": { "type": "integer", "description": "Number of records to skip" },
          "sort": { "type": "string", "description": "Field to sort by" },
          "customQuery": { "type": "string", "description": "Filter query" }
        }
      }
    }
  ]
}
```

**Tool Invocation Request**:

```http
POST /mcp/tools/getCustomers HTTP/1.1
Host: localhost:8082
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "limit": 100,
  "skip": 0,
  "sort": "name",
  "customQuery": "booking_count > 5"
}
```

**Tool Invocation Response**:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "result": [
    {
      "id": "uuid-here",
      "name": "Frequent Customer",
      "email": "customer@example.com",
      "bookingCount": 12,
      "totalSpent": 1250.00
    }
  ]
}
```

---

## Tool Callback Integration

### SyncMcpToolCallbackProvider

**Purpose**: Convert MCP tools into Spring AI tool callbacks

**Usage in ChatService**:

```java
// Get MCP clients
List<McpSyncClient> mcpClients = mcpSyncClientManager.newMcpSyncClients();

// Build tool callback provider
SyncMcpToolCallbackProvider toolCallbackProvider =
    SyncMcpToolCallbackProvider.builder()
        .mcpClients(mcpClients)
        .build();

// Get tool callbacks for ChatClient
List<ToolCallback> callbacks = toolCallbackProvider.getToolCallbacks();

// Use in ChatClient
chatClient.prompt()
    .toolCallbacks(callbacks)
    .stream()
    .content();
```

**What it Does**:

1. Connects to MCP server(s) (one or more)
2. Discovers available tools via `GET /mcp/tools`
3. Creates Spring AI `ToolCallback` for each MCP tool
4. When LLM requests tool call, callback invokes MCP server via HTTP
5. Returns result to LLM for response generation

### Tool Callback Example

**LLM Request**:

```json
{
  "role": "assistant",
  "tool_calls": [
    {
      "id": "call_123",
      "type": "function",
      "function": {
        "name": "getCustomers",
        "arguments": "{\"limit\":50,\"skip\":0,\"sort\":\"name\"}"
      }
    }
  ]
}
```

**Callback Execution**:

```java
ToolCallback callback = new ToolCallback("getCustomers", (args) -> {
    // Parse arguments
    Map<String, Object> params = objectMapper.readValue(args, Map.class);

    // Invoke MCP server
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:8082/mcp/tools/getCustomers"))
        .header("Authorization", "Bearer " + getAccessToken())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(args))
        .build();

    HttpResponse<String> response = httpClient.send(request,
        HttpResponse.BodyHandlers.ofString());

    return response.body();  // Return to LLM
});
```

**LLM Receives Result**:

```json
{
  "role": "tool",
  "tool_call_id": "call_123",
  "content": "[{\"id\":\"...\",\"name\":\"John Doe\",\"email\":\"john@example.com\",...}]"
}
```

**LLM Generates Response**:

```text
I found 50 customers. Here are a few notable ones:

1. **John Doe** (john@example.com)
   - Total bookings: 12
   - Total spent: $1,245.50

2. **Jane Smith** (jane@example.com)
   - Total bookings: 8
   - Total spent: $876.25

Would you like me to show more details or filter by specific criteria?
```

---

## Security Integration

### MCP Server Security

**Dual OAuth2 Role**:

1. **Resource Server** (inbound requests):
   - Validates JWT from MCP Client
   - Checks scopes (`mcp.read`, `mcp.write`)
   - Protects `/mcp/**` endpoints

2. **OAuth2 Client** (outbound requests):
   - Calls backend API with client credentials
   - Scopes: `backend.read`, `backend.write`
   - Automatic token refresh

**SecurityConfig** (`mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java`):

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").authenticated()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults()))
            .csrf(CsrfConfigurer::disable)
            .cors(Customizer.withDefaults())
            .build();
    }
}
```

**Configuration** (`application.yml`):

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_SERVER_URL:http://localhost:8080}
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

### MCP Client Security

**OAuth2 for MCP Server Calls**:

Library: `org.springaicommunity:mcp-client-security:0.0.5`

**Configuration** (`McpClientOAuth2Config`):

```java
@Bean
public McpSyncClientCustomizer mcpSyncClientCustomizer(
        OAuth2AuthorizedClientManager authorizedClientManager) {

    return clientBuilder -> {
        // Authentication context
        AuthenticationMcpTransportContextProvider contextProvider =
            new AuthenticationMcpTransportContextProvider();

        // Add OAuth2 to HTTP requests
        OAuth2ClientCredentialsSyncHttpRequestCustomizer requestCustomizer =
            new OAuth2ClientCredentialsSyncHttpRequestCustomizer(
                authorizedClientManager,
                "mcp-client-to-server"  // Client registration ID
            );

        clientBuilder.transportContext(contextProvider);
        clientBuilder.requestCustomizer(requestCustomizer);
    };
}
```

**Automatic Behavior**:

1. MCP Client about to send HTTP request to MCP Server
2. `requestCustomizer` checks for valid access token
3. If none or expired, fetches new token from auth server
4. Adds `Authorization: Bearer {token}` header
5. MCP Server validates token, processes request

---

## Complete Tool Invocation Flow

**End-to-End Example**: "Show me all customers"

1. **User → React UI**: Types question
2. **React → ChatController**: POST `/api/v1/resos/stream/chat`
3. **ChatController → ChatService**: `streamResponseToQuestion()`
4. **ChatService**: Creates `SyncMcpToolCallbackProvider` with MCP clients
5. **ChatClient → LLM**: Sends prompt with available tools
6. **LLM**: Analyzes, decides to call `getCustomers()`
7. **LLM → ChatClient**: Tool call request
8. **ChatClient → ToolCallback**: Execute callback
9. **ToolCallback → MCP Client**: Invoke `getCustomers` tool
10. **MCP Client → OAuth2**: Fetch access token if needed
11. **MCP Client → MCP Server**: HTTP POST `/mcp/tools/getCustomers` with Bearer token
12. **MCP Server → OAuth2**: Validate JWT token
13. **MCP Server → ResOsService**: Execute `getCustomers()` method
14. **ResOsService → OAuth2**: Fetch backend access token if needed
15. **ResOsService → Backend API**: GET `/api/v1/resos/customers` with Bearer token
16. **Backend API → OAuth2**: Validate JWT token
17. **Backend API → Database**: Query customer table
18. **Database → Backend API**: Customer records
19. **Backend API → ResOsService**: JSON response
20. **ResOsService → MCP Server**: Process response
21. **MCP Server → MCP Client**: Return tool result
22. **MCP Client → LLM**: Tool result
23. **LLM**: Generate natural language response
24. **LLM → ChatClient**: Stream tokens
25. **ChatClient → ChatService**: Invoke `onToken` callback
26. **ChatService → ChatController**: Forward token
27. **ChatController → SSE**: Send via SseEmitter
28. **SSE → React UI**: Token received
29. **React UI → User**: Display token

**Security Checkpoints**:

- ✅ User authenticated at MCP Client (session cookie)
- ✅ MCP Client → MCP Server (client credentials JWT)
- ✅ MCP Server → Backend (client credentials JWT)
- **Total**: 2 JWT validations per tool call

---

## Error Handling

### Tool Execution Errors

**Backend API Error** (e.g., 401 Unauthorized):

```java
@Tool
public List<Customer> getCustomers(...) {
    try {
        ResponseEntity<List<Customer>> response =
            resOsApi.customersGet(limit, skip, sort, customQuery);
        return response.getBody();
    } catch (HttpClientErrorException e) {
        logger.error("Backend API error: {}", e.getMessage());
        throw new McpToolException("Failed to fetch customers: " + e.getMessage());
    }
}
```

**MCP Server Error Response**:

```json
{
  "error": {
    "code": "TOOL_EXECUTION_ERROR",
    "message": "Failed to fetch customers: 401 Unauthorized"
  }
}
```

**LLM Handles Error**:
LLM receives error and generates appropriate response:

```text
I apologize, but I'm unable to access customer data at the moment.
There appears to be an authentication issue with the backend service.
Please try again later or contact support if the issue persists.
```

### OAuth2 Token Errors

**Expired Token**:

- `OAuth2AuthorizedClientManager` detects expiration
- Automatically fetches new token
- Retries request
- Transparent to tool implementation

**Invalid Client Credentials**:

```text
OAuth2AuthenticationException: invalid_client
  Invalid client or Invalid client credentials
```

**Resolution**:

- Check client ID and secret match OAuth2 server
- Verify client registered in database
- Check scopes are correct

---

## Transport Options

The MCP server supports two transport mechanisms via Maven profiles:

### HTTP Streamable Transport (Default)

- **Maven Profile**: Default (no profile needed) or `-Pwebmvc`
- **Use Case**: Web-based chatbot, cloud deployment, multi-client
- **Security**: OAuth2 JWT validation
- **Port**: 8082
- **Dependencies**: `spring-ai-starter-mcp-server-webmvc`, OAuth2 starters

```bash
# Build with HTTP transport (default)
cd mcp-server
mvn clean package -DskipTests

# Run with HTTP transport
mvn spring-boot:run -Dspring-boot.run.profiles=cloud,dev
```

### STDIO Transport (Claude Desktop)

- **Maven Profile**: `-Pstdio`
- **Use Case**: Claude Desktop integration, local development
- **Security**: None (local process only)
- **Port**: None (uses stdin/stdout)
- **Dependencies**: `spring-ai-starter-mcp-server` (no OAuth2)

```bash
# Build for Claude Desktop
cd mcp-server
mvn clean package -Pstdio -DskipTests
```

### Transport Comparison

| Aspect        | HTTP Streamable     | STDIO                   |
| ------------- | ------------------- | ----------------------- |
| Deployment    | Network-accessible  | Local process only      |
| Security      | OAuth2 JWT          | Process isolation       |
| Clients       | Multiple            | Single (Claude Desktop) |
| Web Server    | Tomcat on port 8082 | None                    |
| Configuration | `application.yml`   | `application-stdio.yml` |
| Maven Profile | `webmvc` (default)  | `stdio`                 |

---

## With Claude Desktop

MCP Server can run with Claude Desktop using STDIO transport. This requires building with the `stdio` Maven profile.

### Build for Claude Desktop

```bash
cd mcp-server
mvn clean package -Pstdio -DskipTests
```

### Configuration

Add to your Claude Desktop configuration file:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **Linux**: `~/.config/claude/claude_desktop_config.json`

**claude_desktop_config.json**:

```json
{
  "mcpServers": {
    "spring-ai-resos": {
      "command": "java",
      "args": [
        "-Dspring.profiles.active=stdio",
        "-jar",
        "/path/to/spring-ai-resos/mcp-server/target/spring-ai-resos-mcp-server-1.0.0-SNAPSHOT.jar"
      ],
      "env": {
        "RESOS_API_ENDPOINT": "http://localhost:8080/api/v1/resos"
      }
    }
  }
}
```

### Key Configuration Points

| Setting                            | Value   | Purpose                                        |
| ---------------------------------- | ------- | ---------------------------------------------- |
| `spring.profiles.active`           | `stdio` | Activates STDIO profile configuration          |
| `spring.main.web-application-type` | `none`  | Disables HTTP server                           |
| `spring.ai.mcp.server.stdio`       | `true`  | Enables STDIO transport                        |
| `security.oauth2.enabled`          | `false` | Disables OAuth2 (not needed for local process) |

### STDIO vs HTTP Transport

**STDIO Transport** (Claude Desktop):

- Uses `spring-ai-starter-mcp-server`
- Sets `spring.main.web-application-type=none`
- Sets `spring.ai.mcp.server.stdio=true`
- Disables OAuth2 (`security.oauth2.enabled=false`)
- No HTTP server required

**HTTP Streamable Transport** (Web Chatbot):

- Uses `spring-ai-starter-mcp-server-webmvc`
- Runs Tomcat HTTP server on port 8082
- Full OAuth2 JWT security
- Multi-client support

### Limitations

- **STDIO**: Local desktop only (no cloud deployment)
- **HTTP Streamable**: Web-based, cloud-deployable, multi-client

**This project supports both** - use Maven profiles to choose.

---

## Tool Development Best Practices

### 1. Descriptive Names

```java
@Tool(description = "Fetch customer records with optional filtering and pagination")
public List<Customer> getCustomers(...) { }
```

**Good**: Clear purpose, mentions key features (filtering, pagination)
**Bad**: `@Tool(description = "Get customers")` - too vague

### 2. Parameter Descriptions

```java
@ToolParam(description = "Maximum number of records to return, default 100")
Integer limit
```

**Why**: LLM uses descriptions to decide parameter values

### 3. Null Safety

```java
return Optional.ofNullable(resOsApi.customersGet(...).getBody())
    .orElse(List.of());  // Return empty list, not null
```

**Why**: Prevents NullPointerException, gives LLM empty results to handle

### 4. Error Messages

```java
catch (Exception e) {
    throw new McpToolException(
        "Failed to fetch customers: " + e.getMessage(),
        e
    );
}
```

**Why**: LLM can explain error to user in natural language

### 5. Pagination Defaults

```java
public List<Customer> getCustomers(Integer limit, ...) {
    int effectiveLimit = (limit != null) ? limit : 100;  // Default limit
    // Prevents accidental full table scans
}
```

---

## MCP Server Startup

**Log Output**:

```text
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.0.1)

INFO  SpringAiResOsMcpServerApplication - Starting SpringAiResOsMcpServerApplication
INFO  SpringAiResOsMcpServerApplication - The following 1 profile is active: "dev"
INFO  TomcatWebServer - Tomcat initialized with port 8082 (http)
INFO  OAuth2ClientConfiguration - Configured OAuth2 client: mcp-server
INFO  JwtDecoder - Configured JWT decoder with issuer: http://localhost:8080
INFO  ResOsService - Registered @Tool methods: 7
INFO  McpServerController - MCP server endpoints available at /mcp/*
INFO  TomcatWebServer - Tomcat started on port 8082 (http)
INFO  SpringAiResOsMcpServerApplication - Started in 3.2 seconds
```

**Tool Registration**:

```text
Registered Tools:
  - getTables
  - getCustomers
  - getCustomerById
  - getFeedback
  - getFeedbackById
  - getOpeningHours
  - getOpeningHoursById
```

---

## Performance Characteristics

### Tool Invocation Latency

**Breakdown** (typical):

| Step                     | Time      | Notes                                     |
| ------------------------ | --------- | ----------------------------------------- |
| MCP Client → MCP Server  | 5ms       | HTTP connection + OAuth2 token validation |
| MCP Server → Backend API | 10ms      | HTTP connection + OAuth2 token validation |
| Backend API → Database   | 20ms      | Query execution                           |
| Database → Backend API   | 10ms      | Result serialization                      |
| Backend API → MCP Server | 5ms       | Response transfer                         |
| MCP Server → MCP Client  | 5ms       | Response transfer                         |
| **Total**                | **~55ms** | Excludes LLM inference time               |

**LLM Inference**: 100ms - 5 seconds (dominates latency)

### Token Caching

**OAuth2AuthorizedClientManager Caching**:

- Access tokens cached until expiry (1 hour)
- No redundant token requests
- Refresh tokens used automatically when access token expires

**Cache Hit Rate**: ~99% (only 1 token request per hour per client)

---

## Troubleshooting

### MCP Server Won't Start

**Symptom**: `Port 8082 already in use`

**Solution**:

```bash
# Find process using port
lsof -i :8082

# Kill process
kill -9 <PID>
```

### JWT Validation Fails

**Symptom**: `401 Unauthorized` on MCP requests

**Debug**:

1. Check issuer URI matches:
   - MCP Server: `spring.security.oauth2.resourceserver.jwt.issuer-uri`
   - Auth Server: `app.security.issuer-uri`
2. Verify JWKS endpoint accessible: `curl http://localhost:8080/.well-known/jwks.json`
3. Check token expiration: decode JWT at jwt.io
4. Verify scopes in token match required scopes

### Tools Not Discovered

**Symptom**: MCP Client sees 0 tools

**Debug**:

1. Check MCP Server logs for `Registered @Tool methods: N`
2. Verify `ResOsService` is a `@Component`
3. Check package scanning includes `me.pacphi.ai.resos.mcp`
4. Verify `@Tool` annotation imported correctly
5. Check method signatures (must return serializable types)

### Backend API Calls Fail

**Symptom**: Tool execution fails with "Failed to fetch customers"

**Debug**:

1. Check `RESOS_API_ENDPOINT` environment variable
2. Verify backend running on port 8080
3. Check OAuth2 client credentials (mcp-server client)
4. Verify scopes granted: backend.read, backend.write
5. Check backend API logs for request receipt

---

## Critical Files

| File                                                                            | Purpose            | Lines |
| ------------------------------------------------------------------------------- | ------------------ | ----- |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsService.java`             | Tool provider      | ~150  |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsConfig.java`              | Backend API client | ~150  |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/SecurityConfig.java`           | OAuth2 security    | ~50   |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/McpSyncClientManager.java` | Client wrapper     | ~30   |
| `mcp-client/src/main/java/me/pacphi/ai/resos/config/McpClientOAuth2Config.java` | OAuth2 config      | ~80   |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatService.java`          | AI orchestration   | ~120  |

## Related Documentation

- [MCP Tool Invocation Diagram](diagrams/mcp-tool-invocation.md) - Complete sequence diagram
- [AI Integration](08-ai-integration.md) - Spring AI ChatClient details
- [Security Architecture](06-security-architecture.md) - OAuth2 flows
- [ADR-005: HTTP Streamable Transport](adr/005-http-streamable-transport.md) - Transport choice
