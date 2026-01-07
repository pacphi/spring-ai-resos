# AI Integration

This document details the Spring AI 2.0 integration, ChatClient architecture, streaming patterns, and LLM provider support.

## Spring AI 2.0 Overview

**Spring AI** is Spring's framework for building AI-powered applications with:

- Unified API across LLM providers (OpenAI, Anthropic, Groq, Ollama, etc.)
- Tool/function calling support
- Conversation memory management
- Vector stores for RAG (Retrieval-Augmented Generation)
- Model Context Protocol (MCP) integration

**Version**: 2.0.0-M1 (Milestone 1)
**Documentation**: https://docs.spring.io/spring-ai/reference/

---

## LLM Provider Support

### Supported Providers

| Provider   | Model                                              | Profile      | API Key Required |
| ---------- | -------------------------------------------------- | ------------ | ---------------- |
| OpenAI     | gpt-4o-mini                                        | `openai`     | Yes              |
| Groq Cloud | llama-3.3-70b-versatile                            | `groq-cloud` | Yes              |
| OpenRouter | claude-3.7-sonnet, gemini-2.0-flash, deepseek-chat | `openrouter` | Yes              |
| Ollama     | mistral, llama2, etc.                              | `ollama`     | No (local)       |

### Configuration

**OpenAI** (`application-openai.yml`):

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
      chat:
        enabled: true
        options:
          model: gpt-4o-mini
          temperature: 0.7
          max-tokens: 2048
      embedding:
        enabled: true
        options:
          model: text-embedding-ada-002
```

**Groq Cloud** (`application-groq-cloud.yml`):

```yaml
spring:
  ai:
    openai: # Groq uses OpenAI-compatible API
      api-key: ${GROQ_API_KEY}
      base-url: https://api.groq.com/openai
      chat:
        options:
          model: llama-3.3-70b-versatile
          temperature: 0.7
      embedding: # Groq doesn't have embeddings, use OpenAI
        api-key: ${OPENAI_API_KEY}
        options:
          model: text-embedding-ada-002
```

**OpenRouter** (`application-openrouter.yml`):

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENROUTER_API_KEY}
      base-url: https://openrouter.ai/api
      chat:
        options:
          model: anthropic/claude-3.7-sonnet
          temperature: 0.7
```

**Ollama** (local) (`application-ollama.yml`):

```yaml
spring:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        enabled: true
        options:
          model: mistral
          temperature: 0.7
      embedding:
        enabled: true
        options:
          model: nomic-embed-text
```

### Provider Switching

```bash
# Use OpenAI
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev

# Use Groq Cloud
mvn spring-boot:run -Dspring-boot.run.profiles=groq-cloud,dev

# Use Ollama (local)
mvn spring-boot:run -Dspring-boot.run.profiles=ollama,dev
```

**Benefits**:

- Same code works with different providers
- Switch providers without code changes
- Test with cheap models (Ollama), deploy with powerful models (GPT-4)

---

## ChatClient Architecture

### ChatClient Bean

**Auto-configured by Spring AI** (`ChatModel` bean required):

```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
    return builder
        .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
}
```

**Builder Pattern**:

- Fluent API for building chat requests
- Advisors for cross-cutting concerns (memory, logging)
- Tool callbacks for function calling
- Streaming support

### ChatService Orchestration

**Location**: `mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatService.java`

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

        // Get MCP tool callbacks
        List<McpSyncClient> mcpClients = mcpSyncClientManager.newMcpSyncClients();

        SyncMcpToolCallbackProvider toolCallbackProvider =
            SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpClients)
                .build();

        // Build and execute chat request
        var stream = chatClient.prompt()
            .system(buildSystemPrompt())
            .user(question)
            .toolCallbacks(toolCallbackProvider.getToolCallbacks())
            .advisors(new MessageChatMemoryAdvisor(chatMemory))
            .advisors(new SimpleLoggerAdvisor())
            .stream()
            .content();

        // Subscribe to token stream
        stream.subscribe(
            onToken::accept,      // For each token
            onError::accept,      // On error
            onComplete::run       // On completion
        );
    }

    private String buildSystemPrompt() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextWeek = today.plusDays(7);

        return String.format("""
            You are a helpful assistant for a restaurant reservation system.
            Current date: %s
            Tomorrow's date: %s
            Next week: %s

            Guidelines:
            - Always use ISO 8601 date format (YYYY-MM-DD)
            - When filtering by date, use customQuery parameter
            - Example: customQuery="booking_date >= '2026-01-06'"
            - Do NOT use SQL functions like DATE() or NOW()
            - Be conversational and helpful
            - If results are empty, suggest alternatives

            Tool Usage:
            - Use getTables() for table inventory queries
            - Use getCustomers() for customer searches and management
            - Use getFeedback() for reviews and ratings
            - Use getOpeningHours() for schedule information
            - Pagination: Use limit and skip parameters for large result sets
            """, today, tomorrow, nextWeek);
    }
}
```

**System Prompt Purpose**:

- Provide temporal context (current date)
- Guide tool usage patterns
- Set tone and behavior
- Prevent common mistakes (SQL function usage)

### Request Flow

```text
User Question
    ↓
ChatService.streamResponseToQuestion()
    ↓
ChatClient.prompt()
    .system(systemPrompt)      # Context and guidelines
    .user(question)            # User's question
    .toolCallbacks(mcpTools)   # Available tools
    .advisors(memory)          # Conversation history
    .advisors(logger)          # Debug logging
    .stream()                  # Request streaming response
    .content()                 # Extract text content
    ↓
Flux<String> (reactive stream)
    ↓
subscribe(onToken, onError, onComplete)
    ↓
Callback invocations
```

---

## Streaming Response Pattern

### Server-Sent Events (SSE)

**Why SSE?**:

- Real-time token delivery (as LLM generates)
- Standard browser API (EventSource)
- Unidirectional (perfect for AI responses)
- Automatic reconnection
- Simple protocol

**ChatController** (`mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatController.java`):

```java
@RestController
@RequestMapping("/api/v1/resos")
public class ChatController {

    private final ChatService chatService;

    @PostMapping(path = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Inquiry inquiry) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        chatService.streamResponseToQuestion(
            inquiry.question(),

            // onToken: Send each token via SSE
            token -> {
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    logger.error("Failed to send SSE event", e);
                    emitter.completeWithError(e);
                }
            },

            // onComplete: Close SSE connection
            () -> {
                logger.info("Chat streaming completed");
                emitter.complete();
            },

            // onError: Handle errors
            error -> {
                logger.error("Chat streaming error", error);
                emitter.completeWithError(error);
            }
        );

        return emitter;
    }
}
```

**SseEmitter Features**:

- **Timeout**: 5 minutes (300,000ms)
- **Async**: Non-blocking (Tomcat async I/O)
- **Error Handling**: `completeWithError()` for exceptions
- **Completion**: `complete()` signals end of stream

### React SSE Consumption

**Frontend** (`mcp-client/src/main/frontend/src/components/ChatPage.jsx`):

```javascript
const handleSubmit = async (question) => {
  setLoading(true);
  setResponse('');

  try {
    const res = await fetch('/api/v1/resos/stream/chat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`, // From AuthContext
      },
      body: JSON.stringify({ question }),
    });

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }

    // Read SSE stream
    const reader = res.body.getReader();
    const decoder = new TextDecoder();

    let accumulatedResponse = '';

    while (true) {
      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      // Decode chunk
      const chunk = decoder.decode(value, { stream: true });

      // Parse SSE format (data: <content>\n\n)
      const lines = chunk.split('\n');
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const token = line.substring(6); // Remove 'data: ' prefix
          accumulatedResponse += token;
          setResponse(accumulatedResponse); // Update UI immediately
        }
      }
    }

    setLoading(false);
  } catch (error) {
    console.error('Streaming error:', error);
    setError(error.message);
    setLoading(false);
  }
};
```

**User Experience**:

- Tokens appear in real-time
- No waiting for complete response
- Immediate feedback (AI is "thinking")
- Can stop early if answer sufficient

---

## Memory & Context Management

### MessageChatMemoryAdvisor

**Purpose**: Maintain conversation history for context

```java
@Bean
public ChatMemory chatMemory() {
    return new InMemoryChatMemory();
}

// Usage
.advisors(new MessageChatMemoryAdvisor(chatMemory))
```

**Behavior**:

- Stores last N messages (user + assistant)
- Automatically included in each prompt
- Enables multi-turn conversations
- LLM has context of previous questions/answers

**Example Conversation**:

```text
User: Show me all customers
AI: Here are the customers... [uses getCustomers()]

User: Filter for those who spent over $500
AI: [remembers previous context] Here are customers with totalSpent > 500...
```

### SimpleLoggerAdvisor

**Purpose**: Debug logging for AI interactions

```java
.advisors(new SimpleLoggerAdvisor())
```

**Output** (logs):

```text
DEBUG ChatClient - System Prompt: You are a helpful assistant...
DEBUG ChatClient - User Prompt: Show me all customers
DEBUG ChatClient - Tool Call: getCustomers(limit=100, skip=0)
DEBUG ChatClient - Tool Result: [15 customers returned]
DEBUG ChatClient - Response: Here are all the customers in the system...
```

**Benefits**:

- Understand AI decision-making
- Debug tool invocations
- Monitor token usage
- Track prompt engineering effectiveness

---

## Prompt Engineering

### System Prompt Structure

**Components**:

1. **Role Definition**: "You are a helpful assistant for..."
2. **Temporal Context**: Current date, tomorrow, next week
3. **Guidelines**: Format requirements, tool usage patterns
4. **Tool Descriptions**: Brief overview of available tools
5. **Best Practices**: Query optimization, error handling

**Example**:

```java
private String buildSystemPrompt() {
    return String.format("""
        You are a helpful assistant for a restaurant reservation system.
        Current date: %s

        Date Formatting:
        - Always use ISO 8601: YYYY-MM-DD
        - For filters: customQuery="booking_date >= '2026-01-06'"
        - Do NOT use SQL DATE(), NOW(), or other functions

        Tool Usage Patterns:
        - Broad query first, then filter results
        - Use pagination for large datasets (limit, skip)
        - Provide context in responses (e.g., "showing 10 of 50 customers")

        Error Handling:
        - If tool returns empty results, suggest alternatives
        - If tool fails, explain issue to user in friendly way

        Response Style:
        - Be conversational and friendly
        - Format data clearly (tables, lists, etc.)
        - Provide actionable next steps
        """, LocalDate.now());
}
```

**Advanced Techniques**:

- **Few-shot examples**: Include example Q&A in system prompt
- **Chain-of-thought**: Ask AI to explain reasoning
- **Constraints**: Limit response length, format

---

## Tool Callback Registration

### SyncMcpToolCallbackProvider

**Purpose**: Bridge between MCP clients and Spring AI tool callbacks

**Usage**:

```java
// Get MCP clients (autoconfigured)
List<McpSyncClient> mcpClients = mcpSyncClientManager.newMcpSyncClients();

// Build provider
SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
    .mcpClients(mcpClients)
    .build();

// Get tool callbacks
List<ToolCallback> callbacks = provider.getToolCallbacks();
```

**What It Does**:

1. Connects to MCP server(s)
2. Calls `GET /mcp/tools` to discover tools
3. For each tool, creates a `ToolCallback`:

   ```java
   ToolCallback callback = new ToolCallback(
       toolName,        // "getCustomers"
       toolDescription, // "Fetch customer records..."
       toolSchema,      // JSON Schema for parameters
       (args) -> {
           // Invoke MCP server via HTTP
           return mcpClient.invokeTool(toolName, args);
       }
   );
   ```

4. Returns list of callbacks to ChatClient

**ChatClient Integration**:

```java
chatClient.prompt()
    .toolCallbacks(callbacks)  // Register all MCP tools
    .stream()
    .content();
```

**LLM Receives**:

```json
{
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "getCustomers",
        "description": "Fetch customer records with optional filtering and pagination",
        "parameters": {
          "type": "object",
          "properties": {
            "limit": { "type": "integer", "description": "..." },
            "skip": { "type": "integer", "description": "..." }
          }
        }
      }
    }
  ]
}
```

---

## Streaming Architecture

### Flux to Callback Pattern

**Challenge**: Spring AI returns `Flux<String>` (reactive), but we're using WebMVC (servlet)

**Solution**: Subscribe to Flux with callbacks

**ChatService Pattern**:

```java
public void streamResponseToQuestion(
        String question,
        Consumer<String> onToken,       // Called for each token
        Runnable onComplete,            // Called when done
        Consumer<Throwable> onError) {  // Called on error

    // Get stream from ChatClient
    Flux<String> stream = chatClient.prompt()
        .user(question)
        .stream()
        .content();

    // Subscribe with callbacks
    stream.subscribe(
        onToken::accept,       // Token handler
        onError::accept,       // Error handler
        onComplete::run        // Completion handler
    );
}
```

**Why This Works**:

- `Flux` is reactive, but `subscribe()` bridges to imperative
- Callbacks run on reactor thread pool
- SseEmitter is thread-safe
- No blocking operations

### SseEmitter Lifecycle

```java
SseEmitter emitter = new SseEmitter(300_000L);  // 5 min timeout

// Emitter states:
// 1. Created (ready to send events)
emitter.send(SseEmitter.event().data("token"));  // Sending

// 2. Complete (normal end)
emitter.complete();

// 3. Error (exception occurred)
emitter.completeWithError(new IOException("Connection lost"));

// 4. Timeout (5 minutes elapsed)
// Automatic cleanup by Spring
```

**Browser Behavior**:

- Keeps connection open until `complete()` or timeout
- Receives events as `MessageEvent` objects
- Reconnects automatically on error (with `Last-Event-ID`)

---

## Conversation Memory

### InMemoryChatMemory

**Configuration**:

```java
@Bean
public ChatMemory chatMemory() {
    return new InMemoryChatMemory();
}
```

**Behavior**:

- Stores messages in-memory (lost on restart)
- Keyed by conversation ID
- Default: Last 10 messages

**Message Types**:

- **User messages**: Questions from user
- **Assistant messages**: AI responses
- **Tool messages**: Tool execution results

### MessageChatMemoryAdvisor

**Purpose**: Automatically include conversation history in prompts

```java
.advisors(new MessageChatMemoryAdvisor(chatMemory))
```

**What It Does**:

1. Before sending prompt to LLM:
   - Retrieve last N messages for conversation
   - Prepend to current prompt
2. After receiving response:
   - Store user message
   - Store assistant response

**Example Prompt to LLM**:

```json
{
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant..."
    },
    {
      "role": "user",
      "content": "Show me all customers"
    },
    {
      "role": "assistant",
      "content": "Here are the customers: ..."
    },
    {
      "role": "user",
      "content": "Filter for those in New York" // Current question
    }
  ]
}
```

**LLM Understands**:

- Previous context ("all customers" already fetched)
- Can refine previous result
- No need to call tool again (just filter existing data)

### Production Memory Options

**Redis-backed** (future enhancement):

```java
@Bean
public ChatMemory chatMemory(RedisTemplate<String, Object> redisTemplate) {
    return new RedisChatMemory(redisTemplate);
}
```

**Benefits**:

- Persistent across restarts
- Shared across instances (horizontal scaling)
- TTL support (expire old conversations)

---

## Response Generation Flow

### Complete Example

**User Question**: "Show me customers who booked last month"

**Step 1: ChatClient Builds Prompt**:

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant... Current date: 2026-01-06..."
    },
    {
      "role": "user",
      "content": "Show me customers who booked last month"
    }
  ],
  "tools": [
    { "function": { "name": "getCustomers", "parameters": {...} } }
  ],
  "stream": true
}
```

**Step 2: LLM Decides to Use Tool**:

```json
{
  "role": "assistant",
  "tool_calls": [
    {
      "id": "call_abc123",
      "function": {
        "name": "getCustomers",
        "arguments": "{\"customQuery\":\"last_booking_at >= '2025-12-06' AND last_booking_at < '2026-01-06'\"}"
      }
    }
  ]
}
```

**Step 3: Tool Invocation**:

- Spring AI calls registered `ToolCallback`
- Callback invokes MCP client
- MCP client sends HTTP request to MCP server
- MCP server executes `ResOsService.getCustomers()`
- Backend API queried with filter
- Results returned through chain

**Step 4: LLM Receives Tool Result**:

```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "[{\"id\":\"...\",\"name\":\"Alice Johnson\",\"lastBookingAt\":\"2025-12-15T19:00:00Z\",...}]"
}
```

**Step 5: LLM Generates Final Response** (streaming):

```text
Token 1: "Here"
Token 2: " are"
Token 3: " the"
Token 4: " customers"
Token 5: " who"
Token 6: " booked"
Token 7: " last"
Token 8: " month"
Token 9: ":"
Token 10: "\n\n"
Token 11: "1"
Token 12: "."
Token 13: " **Alice"
Token 14: " Johnson**"
...
```

**Step 6: Streaming to User**:

```text
ChatClient → ChatService (onToken callback)
→ ChatController → SseEmitter
→ Browser SSE EventSource
→ React State Update
→ UI Render (immediate)
```

**User Sees**: Tokens appearing in real-time, feels responsive

---

## Model Configuration

### Temperature

**Setting**:

```yaml
spring.ai.openai.chat.options.temperature: 0.7
```

**Values**:

- `0.0`: Deterministic, focused (good for facts)
- `0.7`: Balanced creativity (recommended)
- `1.0+`: Very creative (good for brainstorming)

### Max Tokens

**Setting**:

```yaml
spring.ai.openai.chat.options.max-tokens: 2048
```

**Purpose**: Limit response length (cost control)

### Top P (Nucleus Sampling)

**Setting**:

```yaml
spring.ai.openai.chat.options.top-p: 0.9
```

**Purpose**: Alternative to temperature for controlling randomness

---

## Error Handling

### Tool Execution Errors

**Scenario**: Backend API returns 500 error

**Flow**:

1. `ResOsService.getCustomers()` calls backend API
2. Backend throws exception (database error)
3. `ResOsService` catches, wraps in `McpToolException`
4. MCP Server returns error response to MCP Client
5. Spring AI includes error in LLM prompt
6. LLM generates user-friendly error message

**LLM Response**:

```text
I apologize, but I'm having trouble accessing customer data right now.
The backend service appears to be experiencing an issue.
Please try again in a few moments, or contact support if the problem persists.
```

### Streaming Errors

**Scenario**: Network interruption during streaming

**Flow**:

1. SseEmitter sending tokens
2. `IOException` thrown (connection lost)
3. `emitter.completeWithError(e)` called
4. Browser EventSource receives error event
5. React displays error message

**React Error Handling**:

```javascript
eventSource.onerror = (event) => {
  console.error('SSE error:', event);
  setError('Connection lost. Please refresh and try again.');
  eventSource.close();
};
```

### LLM Rate Limiting

**Scenario**: OpenAI API rate limit exceeded

**Error**:

```text
HTTP 429 Too Many Requests
{
  "error": {
    "message": "Rate limit exceeded",
    "type": "rate_limit_error"
  }
}
```

**Handling**:

```java
catch (HttpStatusCodeException e) {
    if (e.getStatusCode().value() == 429) {
        throw new RateLimitException(
            "LLM provider rate limit exceeded. Please try again later.",
            e
        );
    }
}
```

**User Message**: "The AI service is busy. Please try again in a moment."

---

## Testing

### Unit Testing

**Mock ChatClient**:

```java
@Test
void testStreamResponse() {
    // Mock ChatClient to return test tokens
    Flux<String> mockStream = Flux.just("Hello", " ", "World");
    when(chatClient.prompt().stream().content()).thenReturn(mockStream);

    List<String> tokens = new ArrayList<>();
    AtomicBoolean completed = new AtomicBoolean(false);

    chatService.streamResponseToQuestion(
        "test question",
        tokens::add,
        () -> completed.set(true),
        error -> fail("Should not error")
    );

    // Verify
    assertThat(tokens).containsExactly("Hello", " ", "World");
    assertThat(completed).isTrue();
}
```

### Integration Testing

**With TestContainers**:

```java
@SpringBootTest
@TestConfiguration
static class TestConfig {

    @Bean
    @Primary
    public ChatModel testChatModel() {
        // Use mock or local Ollama for testing
        return new OllamaChatModel(
            OllamaApi.builder().baseUrl("http://localhost:11434").build(),
            OllamaOptions.create().withModel("mistral")
        );
    }
}
```

**Test Tool Invocation**:

```java
@Test
void testToolInvocation() {
    String question = "Show me all customers";

    List<String> tokens = new ArrayList<>();
    chatService.streamResponseToQuestion(
        question,
        tokens::add,
        () -> {},
        error -> fail(error.getMessage())
    );

    String response = String.join("", tokens);
    assertThat(response).contains("customer");
}
```

---

## Performance Optimization

### Token Caching

**OAuth2 tokens cached automatically**:

- MCP Client → MCP Server tokens (1 hour)
- MCP Server → Backend tokens (1 hour)
- No redundant token requests

### Connection Pooling

**RestClient** uses connection pooling:

```java
RestClient.builder()
    .requestFactory(new JdkClientHttpRequestFactory(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
    ))
```

**Benefits**:

- Reuse TCP connections
- Reduce handshake overhead
- Better throughput

### Streaming vs Blocking

**Streaming**:

- First token: ~500ms (LLM starts generating)
- Subsequent tokens: ~20-50ms each
- Total time: 2-5 seconds for full response

**Blocking** (if we waited for complete response):

- First token: ~2-5 seconds (wait for entire response)
- User experience: Perceived as slow

**Improvement**: 4-10x faster perceived response time

---

## Monitoring & Observability

### Metrics (Future Enhancement)

**Recommended Metrics**:

```java
@Bean
public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
}

// In ChatService
meterRegistry.counter("chat.requests.total").increment();
meterRegistry.timer("chat.response.duration").record(() -> {
    // Chat execution
});
meterRegistry.counter("chat.tool.invocations", "tool", toolName).increment();
```

**Grafana Dashboard**:

- Chat requests per minute
- Average response time
- Tool invocation frequency
- Error rate
- Token usage (if LLM API provides it)

### Logging Strategy

**Levels**:

- `INFO`: User questions, tool invocations, response completion
- `DEBUG`: Prompts, tool schemas, LLM API calls
- `ERROR`: Exceptions, tool failures, streaming errors

**Example Logs**:

```text
INFO  ChatController - Received chat request: "Show me all customers"
DEBUG ChatService - System prompt: You are a helpful assistant...
DEBUG ChatService - Tool callbacks registered: 7
INFO  ChatService - Streaming response initiated
DEBUG SyncMcpToolCallbackProvider - Discovered tools from butler: [getTables, getCustomers, ...]
INFO  ResOsService - Invoking tool: getCustomers(limit=100, skip=0)
DEBUG ResOsConfig - Backend API call: GET /api/v1/resos/customers
INFO  ResOsService - Tool execution completed: 15 customers returned
INFO  ChatService - Response streaming completed (25 tokens, 2.3s)
```

---

## Critical Files

| File                                                                            | Purpose              | Lines |
| ------------------------------------------------------------------------------- | -------------------- | ----- |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatService.java`          | AI orchestration     | ~120  |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/ChatController.java`       | SSE streaming        | ~60   |
| `mcp-client/src/main/java/me/pacphi/ai/resos/service/McpSyncClientManager.java` | MCP client wrapper   | ~30   |
| `mcp-client/src/main/resources/application-openai.yml`                          | OpenAI configuration | ~30   |
| `mcp-client/src/main/resources/application-ollama.yml`                          | Ollama configuration | ~25   |

## Related Documentation

- [MCP Architecture](07-mcp-architecture.md) - Tool provider details
- [Frontend Architecture](09-frontend-architecture.md) - React SSE consumption
- [MCP Tool Invocation Diagram](diagrams/mcp-tool-invocation.md) - Complete flow
- [Security Architecture](06-security-architecture.md) - OAuth2 integration
