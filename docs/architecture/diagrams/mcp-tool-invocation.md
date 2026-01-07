# MCP Tool Invocation Flow

This diagram shows the complete flow from user question to tool execution and streaming response.

```mermaid
sequenceDiagram
    actor User
    participant Browser
    participant UI as React SPA<br/>(AuthContext)
    participant ChatController as ChatController<br/>(SSE Endpoint)
    participant ChatService as ChatService<br/>(Orchestrator)
    participant ChatClient as Spring AI<br/>ChatClient
    participant LLM as LLM Provider<br/>(OpenAI/Groq)
    participant ToolProvider as SyncMcpTool<br/>CallbackProvider
    participant MCPClient as MCP Sync<br/>Client
    participant MCPServer as MCP Server<br/>(Port 8082)
    participant ResOsService as ResOsService<br/>(@Tool methods)
    participant BackendAPI as Backend API<br/>(Port 8080)
    participant DB as Database

    User->>Browser: Enter question:<br/>"Show me all customers"
    Browser->>UI: Submit chat message
    UI->>UI: Get auth token from context
    UI->>ChatController: POST /api/v1/resos/stream/chat<br/>Authorization: Bearer {token}<br/>{ "question": "..." }

    Note over ChatController: Create SseEmitter<br/>(5 min timeout)

    ChatController->>ChatService: streamResponseToQuestion(<br/>question, onToken, onComplete, onError)

    Note over ChatService: Build tool callback provider

    ChatService->>ToolProvider: newMcpSyncClients()
    ToolProvider-->>ChatService: List<McpSyncClient>
    ChatService->>ToolProvider: getToolCallbacks()
    ToolProvider-->>ChatService: List<ToolCallback>

    ChatService->>ChatClient: ChatClient.prompt()<br/>.system(systemPrompt)<br/>.user(question)<br/>.toolCallbacks(callbacks)<br/>.stream().content()

    Note over ChatClient,LLM: Streaming begins

    ChatClient->>LLM: API call with:<br/>- System prompt (date, instructions)<br/>- User question<br/>- Available tools (from MCP)

    LLM->>LLM: Analyze question
    LLM->>LLM: Decide: Need to call getCustomers()

    LLM-->>ChatClient: Tool call request:<br/>{<br/>  name: "getCustomers",<br/>  arguments: {<br/>    limit: 100,<br/>    skip: 0<br/>  }<br/>}

    ChatClient->>ToolProvider: Invoke tool callback:<br/>getCustomers(limit=100, skip=0)

    ToolProvider->>MCPClient: HTTP POST to MCP server<br/>Authorization: Bearer {mcp-token}<br/>/mcp/tools/getCustomers

    Note over MCPClient,MCPServer: OAuth2 client credentials flow

    MCPClient->>MCPServer: Authenticated request
    MCPServer->>MCPServer: Validate JWT token
    MCPServer->>ResOsService: getCustomers(limit, skip, sort, query)

    ResOsService->>ResOsService: Prepare backend API call
    ResOsService->>BackendAPI: GET /api/v1/resos/customers<br/>Authorization: Bearer {backend-token}<br/>?limit=100&skip=0

    Note over ResOsService,BackendAPI: OAuth2 client credentials flow

    BackendAPI->>BackendAPI: Validate JWT token<br/>Check scope: backend.read
    BackendAPI->>DB: SELECT * FROM customer<br/>LIMIT 100 OFFSET 0
    DB-->>BackendAPI: Customer records
    BackendAPI-->>ResOsService: JSON array of customers

    ResOsService->>ResOsService: Transform to MCP response
    ResOsService-->>MCPServer: Customer data
    MCPServer-->>MCPClient: Tool result
    MCPClient-->>ToolProvider: Tool execution result
    ToolProvider-->>ChatClient: Return tool output

    ChatClient->>LLM: Send tool result back to LLM

    LLM->>LLM: Process tool result<br/>Format natural language response

    Note over LLM,ChatClient: Stream response tokens

    loop For each token
        LLM-->>ChatClient: Token: "Here"
        ChatClient->>ChatService: onToken("Here")
        ChatService->>ChatController: callback("Here")
        ChatController->>UI: SseEmitter.send("Here")
        UI->>Browser: Update UI
        Browser->>User: Display token

        LLM-->>ChatClient: Token: " are"
        ChatClient->>ChatService: onToken(" are")
        ChatService->>ChatController: callback(" are")
        ChatController->>UI: SseEmitter.send(" are")
        UI->>Browser: Update UI
        Browser->>User: Display token

        LLM-->>ChatClient: Token: " the"
        Note over LLM,User: ... continue streaming ...
    end

    LLM-->>ChatClient: Stream complete
    ChatClient->>ChatService: onComplete()
    ChatService->>ChatController: onComplete()
    ChatController->>UI: SseEmitter.complete()
    UI->>Browser: Close SSE connection
    Browser->>User: Show complete response
```

## Detailed Flow Breakdown

### Phase 1: Request Initiation

**User Interaction**:

```javascript
// React component
const handleSubmit = async (question) => {
  const response = await fetch('/api/v1/resos/stream/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
    },
    body: JSON.stringify({ question }),
  });

  const reader = response.body.getReader();
  // Stream consumption...
};
```

**Backend Controller**:

```java
@PostMapping(path = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestBody Inquiry inquiry) {
    SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

    chatService.streamResponseToQuestion(
        inquiry.question(),
        token -> {
            try {
                emitter.send(SseEmitter.event().data(token));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        },
        emitter::complete,
        emitter::completeWithError
    );

    return emitter;
}
```

### Phase 2: Tool Callback Setup

**ChatService Orchestration**:

```java
public void streamResponseToQuestion(
        String question,
        Consumer<String> onToken,
        Runnable onComplete,
        Consumer<Throwable> onError) {

    // Get MCP clients
    var mcpClients = mcpSyncClientManager.newMcpSyncClients();

    // Build tool callback provider
    var toolCallbackProvider = SyncMcpToolCallbackProvider.builder()
        .mcpClients(mcpClients)
        .build();

    // Stream response
    var stream = chatClient.prompt()
        .system(buildSystemPrompt())
        .user(question)
        .toolCallbacks(toolCallbackProvider.getToolCallbacks())
        .stream()
        .content();

    // Subscribe to stream
    stream.subscribe(onToken, onError, onComplete);
}
```

**System Prompt**:

```java
private String buildSystemPrompt() {
    return String.format("""
        You are a helpful assistant for a restaurant reservation system.
        Current date: %s

        Guidelines:
        - Use ISO 8601 date format (YYYY-MM-DD)
        - When filtering, use the customQuery parameter
        - Be concise and helpful
        """, LocalDate.now());
}
```

### Phase 3: LLM Processing & Tool Call Decision

**LLM Receives**:

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant... Current date: 2026-01-06"
    },
    {
      "role": "user",
      "content": "Show me all customers"
    }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "getCustomers",
        "description": "Fetch customer records with optional filtering",
        "parameters": {
          "type": "object",
          "properties": {
            "limit": { "type": "integer", "description": "Max records" },
            "skip": { "type": "integer", "description": "Offset" },
            "sort": { "type": "string", "description": "Sort field" },
            "customQuery": { "type": "string", "description": "Filter query" }
          }
        }
      }
    }
    // ... other tools (getTables, getFeedback, etc.)
  ]
}
```

**LLM Decides to Call Tool**:

```json
{
  "role": "assistant",
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "getCustomers",
        "arguments": "{\"limit\":100,\"skip\":0}"
      }
    }
  ]
}
```

### Phase 4: Tool Execution

**MCP Client Request**:

```http
POST http://localhost:8082/mcp/tools/getCustomers HTTP/1.1
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "limit": 100,
  "skip": 0,
  "sort": null,
  "customQuery": null
}
```

**MCP Server Tool Method**:

```java
@Tool(description = "Fetch customer records with optional filtering and pagination")
public List<Customer> getCustomers(
        @ToolParam(description = "Maximum number of records to return")
        Integer limit,
        @ToolParam(description = "Number of records to skip")
        Integer skip,
        @ToolParam(description = "Field to sort by")
        String sort,
        @ToolParam(description = "Custom query filter")
        String customQuery
) {
    return resOsApi.customersGet(limit, skip, sort, customQuery);
}
```

**Backend API Call**:

```http
GET http://localhost:8080/api/v1/resos/customers?limit=100&skip=0 HTTP/1.1
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Accept: application/json
```

**Database Query**:

```sql
SELECT id, name, email, phone, created_at, last_booking_at,
       booking_count, total_spent, metadata
FROM customer
ORDER BY created_at DESC
LIMIT 100 OFFSET 0;
```

**Response Chain**:

1. Database → Backend: Customer records as entities
2. Backend → MCP Server: Customer records as POJOs (JSON)
3. MCP Server → MCP Client: Tool result
4. MCP Client → LLM: Tool execution result

### Phase 5: Response Generation & Streaming

**LLM Receives Tool Result**:

```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "[{\"id\":\"123e4567-e89b-12d3-a456-426614174000\",\"name\":\"John Doe\",\"email\":\"john@example.com\",...}]"
}
```

**LLM Generates Response**:

```text
Here are all the customers in the system:

1. **John Doe** (john@example.com)
   - Total bookings: 5
   - Total spent: $342.50

2. **Jane Smith** (jane@example.com)
   - Total bookings: 3
   - Total spent: $189.75

... (continues streaming)
```

**Streaming to UI**:
Each token is sent via SSE as it's generated:

```text
data: Here
data:  are
data:  all
data:  the
data:  customers
data:  in
data:  the
data:  system
data: :
data: \n\n
data: 1
data: .
...
```

## Tool Registration

Tools are auto-discovered from `@Tool` annotations:

```java
@Component
public class ResOsService {

    @Tool(description = "Fetch all restaurant tables")
    public List<Table> getTables() { ... }

    @Tool(description = "Fetch customer records with optional filtering and pagination")
    public List<Customer> getCustomers(...) { ... }

    @Tool(description = "Fetch customer feedback with optional filtering")
    public List<Feedback> getFeedback(...) { ... }

    @Tool(description = "Fetch opening hours for the next two weeks")
    public List<OpeningHours> getOpeningHours() { ... }

    @Tool(description = "Fetch opening hours by ID")
    public OpeningHours getOpeningHoursById(String id) { ... }
}
```

Spring AI MCP Server automatically:

1. Scans for `@Tool` methods
2. Generates JSON Schema from method signatures
3. Exposes tools via MCP protocol
4. Handles tool invocation routing

## Error Handling

### Tool Execution Errors

**Backend API Error**:

```java
try {
    return resOsApi.customersGet(limit, skip, sort, customQuery);
} catch (HttpClientErrorException e) {
    throw new ToolExecutionException(
        "Failed to fetch customers: " + e.getMessage()
    );
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
I apologize, but I'm unable to fetch customer data at the moment.
There appears to be an authentication issue. Please try again later.
```

### Streaming Errors

**SSE Error Handling**:

```java
chatService.streamResponseToQuestion(
    question,
    token -> emitter.send(token),
    () -> emitter.complete(),
    error -> {
        logger.error("Chat streaming error", error);
        emitter.completeWithError(error);
    }
);
```

**UI Error Display**:

```javascript
try {
  // Stream consumption
} catch (error) {
  console.error('Stream error:', error);
  setErrorMessage('Unable to complete request. Please try again.');
}
```

## Performance Considerations

| Layer                | Optimization                                                      |
| -------------------- | ----------------------------------------------------------------- |
| **LLM Streaming**    | Token-by-token prevents buffering delays                          |
| **OAuth2 Tokens**    | Cached until expiry (automatic via OAuth2AuthorizedClientManager) |
| **HTTP Connections** | RestClient with connection pooling                                |
| **Database Queries** | Pagination limits result sizes                                    |
| **SSE**              | Asynchronous, non-blocking I/O                                    |

## Critical Files

| File                                                                            | Purpose            |
| ------------------------------------------------------------------------------- | ------------------ |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatService.java`          | Orchestration      |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatController.java`       | SSE endpoint       |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/McpSyncClientManager.java` | MCP client wrapper |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsService.java`             | Tool definitions   |
| `mcp-server/src/main/java/me/pacphi/ai/resos/mcp/ResOsConfig.java`              | Backend API client |
| `mcp-client/src/main/frontend/src/App.jsx`                                      | React UI           |
