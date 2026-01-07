# High-Level Architecture

This diagram shows the complete system architecture with all components and their OAuth2 token flows.

```mermaid
graph TB
    subgraph "Frontend Layer"
        UI[React SPA<br/>Port 8081<br/>OAuth2 PKCE]
    end

    subgraph "AI & Integration Layer"
        MCP_CLIENT[MCP Client<br/>Spring AI Chat<br/>OAuth2 Client]
        MCP_SERVER[MCP Server<br/>Port 8082<br/>Tool Provider<br/>OAuth2 Resource Server]
    end

    subgraph "Backend Layer"
        AUTH[OAuth2 Authorization Server<br/>JWT Tokens<br/>RSA Signing]
        BACKEND[ResOs API<br/>Resource Server<br/>Port 8080]
    end

    subgraph "Data Layer"
        DB[(PostgreSQL / H2<br/>Liquibase Managed<br/>OAuth2 Tokens<br/>User Data)]
    end

    subgraph "External Services"
        LLM[LLM Providers<br/>OpenAI<br/>Groq Cloud<br/>OpenRouter<br/>Ollama]
    end

    UI -->|"1. OAuth2 Login<br/>(authorization_code + PKCE)"| AUTH
    AUTH -->|"2. JWT Access Token"| UI
    UI -->|"3. Authenticated<br/>Chat Request"| MCP_CLIENT
    MCP_CLIENT -->|"4. LLM API Call<br/>(with system + tools)"| LLM
    LLM -->|"5. Tool Call Request"| MCP_CLIENT
    MCP_CLIENT -->|"6. MCP Tool Invocation<br/>(client_credentials token)"| MCP_SERVER
    MCP_SERVER -->|"7. Validate JWT"| AUTH
    MCP_SERVER -->|"8. Backend API Call<br/>(client_credentials token)"| BACKEND
    BACKEND -->|"9. Validate JWT"| AUTH
    BACKEND -->|"10. Query/Update"| DB
    DB -->|"11. Data"| BACKEND
    BACKEND -->|"12. API Response"| MCP_SERVER
    MCP_SERVER -->|"13. Tool Result"| MCP_CLIENT
    MCP_CLIENT -->|"14. Stream to LLM"| LLM
    LLM -->|"15. Streaming Response"| MCP_CLIENT
    MCP_CLIENT -->|"16. SSE Stream"| UI

    AUTH -->|"Token Storage<br/>User Data"| DB

    style UI fill:#e1f5ff,stroke:#0066cc,stroke-width:3px
    style MCP_CLIENT fill:#fff4e6,stroke:#ff9900,stroke-width:3px
    style MCP_SERVER fill:#fff4e6,stroke:#ff9900,stroke-width:3px
    style AUTH fill:#ffe6e6,stroke:#cc0000,stroke-width:3px
    style BACKEND fill:#ffe6e6,stroke:#cc0000,stroke-width:3px
    style DB fill:#e6f7e6,stroke:#009900,stroke-width:3px
    style LLM fill:#f0e6ff,stroke:#9900cc,stroke-width:2px
```

## Component Responsibilities

### Frontend Layer

- **React SPA**: Single-page application providing chat interface
  - OAuth2 PKCE authentication for browser security
  - SSE consumption for streaming responses
  - Markdown rendering with syntax highlighting
  - Dark/light theme support

### AI & Integration Layer

- **MCP Client**: Spring AI chat orchestration
  - Integrates with multiple LLM providers
  - Manages conversation history
  - Coordinates tool invocations
  - OAuth2 client credentials for MCP server communication

- **MCP Server**: Tool provider for AI agents
  - Exposes ResOs tools via Model Context Protocol
  - HTTP Streamable transport (WebMVC)
  - OAuth2 resource server (validates tokens)
  - OAuth2 client (calls backend API)

### Backend Layer

- **Authorization Server**: OAuth2 token issuer
  - JWT tokens with RSA-256 signing
  - User authentication and authorization
  - OAuth2 client registration
  - OIDC support

- **ResOs API**: Restaurant reservation system API
  - Spring Data JDBC for persistence
  - RESTful endpoints
  - OAuth2 resource server protection
  - Dynamic Liquibase schema generation

### Data Layer

- **PostgreSQL/H2**: Relational database
  - OAuth2 token storage
  - User credentials (BCrypt)
  - Restaurant booking data
  - Customer and feedback data

### External Services

- **LLM Providers**: AI model backends
  - OpenAI (gpt-4o-mini)
  - Groq Cloud (llama-3.3-70b-versatile)
  - OpenRouter (claude, gemini)
  - Ollama (local models)

## Security Flow

1. **User Authentication**: Browser redirects to auth server, user logs in, receives JWT
2. **Chat Request**: User sends question to MCP client with auth token
3. **LLM Call**: MCP client calls LLM provider with system prompt and tools
4. **Tool Invocation**: LLM decides to call a tool, MCP client invokes MCP server
5. **Token Validation**: MCP server validates JWT from auth server
6. **Backend Call**: MCP server calls backend API with client credentials token
7. **Response Streaming**: Results stream back through layers to user

## Key Design Principles

- **OAuth2 at Every Layer**: Zero-trust security model
- **Stateless Design**: All services are horizontally scalable
- **Streaming Architecture**: Real-time token-by-token responses
- **API-First**: OpenAPI specification drives code generation
- **Dynamic Schema**: Database schema generated from entities
