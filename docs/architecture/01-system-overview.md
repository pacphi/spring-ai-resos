# System Overview

## Business Context

Spring AI ResOs is an **AI-powered restaurant reservation chatbot** that demonstrates modern Spring Boot architecture patterns. It enables users to converse naturally with an AI assistant to:

- Search for restaurant availability
- Make and manage reservations
- View customer information
- Access feedback and reviews
- Check restaurant opening hours

### The Problem

Traditional restaurant reservation systems require users to:

1. Navigate complex web forms
2. Manually search through availability calendars
3. Fill out multiple fields for booking details
4. Understand specific terminology and constraints

### The Solution

This application provides a **conversational AI interface** where users simply ask questions in natural language:

> "Show me all customers who booked in the last month"

> "What tables are available for 4 people on Friday?"

> "Find all feedback with ratings below 3 stars"

The AI assistant understands the intent, calls the appropriate backend tools, and presents results in a friendly, conversational format.

## System Capabilities

### Core Features

1. **Natural Language Queries**
   - Conversational interface powered by LLM (OpenAI, Groq, OpenRouter, Ollama)
   - Intent recognition and tool selection
   - Streaming responses for real-time feedback

2. **Restaurant Management**
   - Customer database management
   - Booking system with table assignments
   - Order tracking and management
   - Feedback and review collection

3. **AI Tool Integration**
   - Model Context Protocol (MCP) for tool discovery
   - Spring AI 2.0 for LLM orchestration
   - Dynamic tool callback registration
   - Secure tool execution with OAuth2

4. **Multi-Tenant Security**
   - Role-based access control (USER, OPERATOR, ADMIN)
   - OAuth2 Authorization Server
   - JWT token-based authentication
   - API-level authorization with scopes

5. **Developer Experience**
   - OpenAPI-first development
   - Automatic code generation
   - Dynamic schema management
   - Comprehensive testing support

## Use Cases

### Use Case 1: Customer Inquiry

**Actor**: Restaurant customer (via chatbot)

**Flow**:

1. Customer asks: "Do you have any tables for 6 people this Saturday?"
2. AI determines intent: check table availability
3. System calls `getTables()` tool via MCP
4. Backend queries database for tables with capacity ≥ 6
5. AI presents results conversationally with availability

**Benefits**: No form navigation, instant results, natural interaction

### Use Case 2: Staff Operations

**Actor**: Restaurant operator (ROLE_OPERATOR)

**Flow**:

1. Operator asks: "Show me all bookings for tomorrow"
2. AI calls `getBookings(date=tomorrow)` with OAuth2 credentials
3. Backend validates operator role, returns booking list
4. AI formats results with customer details, table assignments, times

**Benefits**: Quick data access, role-based security, no SQL knowledge needed

### Use Case 3: Management Analytics

**Actor**: Restaurant administrator (ROLE_ADMIN)

**Flow**:

1. Admin asks: "What's our average customer rating this month?"
2. AI calls `getFeedback(customQuery="created_at >= '2026-01-01'")`
3. Backend aggregates ratings, returns statistics
4. AI presents insights with trends and recommendations

**Benefits**: Data-driven decisions, natural analytics queries

### Use Case 4: Customer Service

**Actor**: Customer service rep (ROLE_USER)

**Flow**:

1. Rep asks: "Find customer john@example.com"
2. AI calls `getCustomers(customQuery="email = 'john@example.com'")`
3. Backend returns customer profile with booking history
4. AI shows comprehensive customer view for support

**Benefits**: Fast customer lookup, complete history, contextual information

## Quality Attributes

### Security

**OAuth2 at Every Layer**:

- Frontend: Authorization Code + PKCE for browser security
- Service-to-Service: Client Credentials for backend communication
- JWT Tokens: RSA-256 signed, role and scope claims
- Database-Backed: Token storage enables revocation

**Key Security Features**:

- BCrypt password hashing (strength 12)
- PKCE prevents authorization code interception
- Scope-based API authorization (backend.read, backend.write)
- CORS configuration for cross-origin requests
- CSRF protection for browser-based flows

### Scalability

**Stateless Design**:

- All services are horizontally scalable
- No server-side session state (JWT tokens)
- Database connection pooling
- OAuth2 tokens cached until expiry

**Performance Characteristics**:

- Startup time: ~2-3 seconds per service
- Request latency: ~50-200ms (excluding LLM)
- Concurrent users: Limited by LLM API rate limits, not architecture
- Database queries: Paginated, optimized with indexes

### Maintainability

**API-First Development**:

- OpenAPI spec drives all code generation
- Zero drift between contract and implementation
- Compile-time type safety throughout

**Zero-Boilerplate Patterns**:

- Entities generated from OpenAPI models
- Database schema generated from entities
- OAuth2 clients auto-configured
- MCP tools auto-discovered from `@Tool` annotations

**Comprehensive Documentation**:

- Architecture decision records (ADRs)
- Mermaid diagrams for all flows
- Migration guides for Spring Boot 4
- Code examples with file references

### Observability

**Current Implementation**:

- Spring Boot Actuator endpoints
- Structured logging with correlation IDs
- Health checks for all services
- Git commit info in artifacts

**Recommended Enhancements** (see [Future Enhancements](15-future-enhancements.md)):

- OpenTelemetry for distributed tracing
- Prometheus metrics export
- Grafana dashboards
- Log aggregation (ELK stack)

## Component Responsibilities

### Frontend Layer (React SPA)

**Responsibilities**:

- User authentication via OAuth2 PKCE
- Chat interface with markdown rendering
- SSE consumption for streaming responses
- Theme management (dark/light mode)
- Auth context management

**Technology**: React 18+, Vite, OAuth2 client

**Port**: 8081

### AI & Integration Layer

**MCP Client**:

- Spring AI ChatClient orchestration
- LLM provider abstraction (OpenAI, Groq, etc.)
- Tool callback management
- Conversation memory
- OAuth2 client credentials for MCP server

**MCP Server**:

- Tool provider via Model Context Protocol
- HTTP Streamable transport
- OAuth2 resource server (validates tokens)
- OAuth2 client (calls backend API)
- Tool discovery and registration

**Technology**: Spring AI 2.0, MCP SDK

**Ports**: 8081 (client), 8082 (server)

### Backend Layer

**Authorization Server**:

- JWT token issuance (RSA-256)
- User authentication (BCrypt)
- OAuth2 client registration
- OIDC support
- Token validation endpoints

**ResOs API**:

- Restaurant reservation CRUD operations
- Customer management
- Booking and order management
- Feedback collection
- OAuth2 resource server protection

**Technology**: Spring Boot 4, Spring Authorization Server, Spring Data JDBC

**Port**: 8080

### Data Layer

**Database**:

- Relational data persistence
- Liquibase schema management
- OAuth2 token storage
- User credentials and roles

**Technology**: PostgreSQL 16 (prod), H2 (dev)

**Port**: 5432 (PostgreSQL)

## System Architecture

For the complete architecture diagram, see [High-Level Architecture](diagrams/high-level-architecture.md).

**Key Architectural Patterns**:

1. **Three-Tier OAuth2**: Authorization Server → Resource Server → OAuth2 Client
2. **MCP Integration**: LLM ↔ MCP Client ↔ MCP Server ↔ Backend API
3. **Code Generation**: OpenAPI → Client → Entities → Schema
4. **Event Streaming**: SSE for real-time token delivery
5. **Aggregate-Oriented**: DDD aggregates with Spring Data JDBC

## Technology Decisions

For detailed rationale, see [Architectural Decision Records](adr/):

| Decision                                                              | Rationale               | Impact                        |
| --------------------------------------------------------------------- | ----------------------- | ----------------------------- |
| **OpenAPI-First** ([ADR-001](adr/001-openapi-first.md))               | Single source of truth  | Zero API drift                |
| **Spring Data JDBC** ([ADR-002](adr/002-spring-data-jdbc.md))         | Lightweight ORM         | Faster startup, simpler model |
| **Dynamic Liquibase** ([ADR-003](adr/003-dynamic-liquibase.md))       | Avoid duplicate schemas | Zero manual SQL               |
| **WebMVC over WebFlux** ([ADR-004](adr/004-webmvc-over-webflux.md))   | OAuth2 compatibility    | Better security integration   |
| **HTTP Streamable** ([ADR-005](adr/005-http-streamable-transport.md)) | MCP spec compliance     | Standard transport            |

## Quick Start

### Running Locally

```bash
# Terminal 1: Backend (Auth + API)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2: MCP Server (Tools)
cd mcp-server
export RESOS_API_ENDPOINT=http://localhost:8080/api/v1/resos
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 3: MCP Client (Chatbot)
cd mcp-client
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev
```

### Accessing the Application

- **Chatbot UI**: http://localhost:8081
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Actuator**: http://localhost:8080/actuator

### Default Credentials

| Username | Password    | Roles                                |
| -------- | ----------- | ------------------------------------ |
| admin    | admin123    | ROLE_ADMIN, ROLE_OPERATOR, ROLE_USER |
| operator | operator123 | ROLE_OPERATOR, ROLE_USER             |
| user     | user123     | ROLE_USER                            |

## Next Steps

**For New Developers**:

1. Read [Module Architecture](03-module-architecture.md) to understand the 6-module structure
2. Review [Build Workflow](10-build-workflow.md) for build commands and profiles
3. Explore [Code Generation Pipeline](04-code-generation.md) to see how code is generated

**For Architects**:

1. Review [Security Architecture](06-security-architecture.md) for OAuth2 design
2. Study [Migration Patterns](11-migration-patterns.md) for Spring Boot 4 lessons
3. Read [Design Patterns](12-design-patterns.md) for reusable patterns

**For Operations**:

1. Check [Deployment](13-deployment.md) for Docker Compose setup
2. Review [Testing](14-testing.md) for integration testing strategy
3. See [Future Enhancements](15-future-enhancements.md) for observability recommendations

## Related Documentation

- [Technology Stack](02-technology-stack.md) - Complete tech breakdown
- [High-Level Architecture Diagram](diagrams/high-level-architecture.md) - Visual overview
- [Data Model ERD](diagrams/data-model-erd.md) - Database schema
