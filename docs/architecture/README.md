# Spring AI ResOs - Architecture Documentation

> **Comprehensive reference architecture for Spring Boot 4, Spring AI 2.0, and Model Context Protocol integration**

## Overview

Spring AI ResOs is a production-ready reference implementation demonstrating:

- **AI-Powered Application**: Restaurant reservation chatbot using LLM providers (OpenAI, Groq, OpenRouter, Ollama)
- **Model Context Protocol (MCP)**: HTTP Stream able transport with Spring AI 2.0 integration
- **OAuth2 Security**: Three-tier architecture (Authorization Server → Resource Server → OAuth2 Client)
- **API-First Development**: OpenAPI specification drives code generation pipeline
- **Dynamic Schema Generation**: Liquibase changelogs generated from Spring Data JDBC entities
- **Spring Boot 4 Migration**: Documented patterns for WebFlux→WebMVC, Jackson 3.x, Spring Security 7.x

## Why This Architecture Matters

This is a **reference implementation** showcasing:

| Pattern | Demonstrated How |
|---------|-----------------|
| **OpenAPI-First** | Client code generated from spec, transformed to JDBC entities |
| **Zero-Boilerplate Persistence** | Entities drive schema, no manual SQL writing |
| **Microservices Security** | OAuth2 client credentials between services |
| **AI Tool Integration** | Spring AI ChatClient with MCP tool callbacks |
| **Production Patterns** | Liquibase migrations, TestContainers, Docker Compose |
| **Migration Guide** | WebFlux→WebMVC, Jackson 2→3, Spring HTTP Interface |

---

## Documentation Map

### 📊 Getting Started

| Document | Description | Audience |
|----------|-------------|----------|
| [System Overview](01-system-overview.md) | Business context, capabilities, architecture | Everyone |
| [Technology Stack](02-technology-stack.md) | Complete tech breakdown with versions | Developers, Architects |
| [High-Level Architecture](diagrams/high-level-architecture.md) | Component diagram with OAuth2 flows | Architects, DevOps |

### 🏗️ Architecture Deep Dive

| Document | Description | Key Topics |
|----------|-------------|------------|
| [Module Architecture](03-module-architecture.md) | 6 Maven modules explained | Build structure, dependencies |
| [Code Generation Pipeline](04-code-generation.md) | OpenAPI → Entities → Schema | Code generation, JavaParser |
| [Data Architecture](05-data-architecture.md) | Database schema, JDBC patterns | Entities, relationships, CSV seeding |
| [Security Architecture](06-security-architecture.md) | Three-tier OAuth2 design | JWT, PKCE, client credentials |
| [MCP Architecture](07-mcp-architecture.md) | Model Context Protocol integration | Tools, HTTP Streamable |
| [AI Integration](08-ai-integration.md) | Spring AI 2.0 ChatClient | Streaming, memory, LLM providers |
| [Frontend Architecture](09-frontend-architecture.md) | React SPA with OAuth2 PKCE | SSE consumption, Vite build |

### 🔧 Build & Deployment

| Document | Description | Key Topics |
|----------|-------------|------------|
| [Build Workflow](10-build-workflow.md) | Maven multi-module build | Reactor, profiles, plugins |
| [Deployment](13-deployment.md) | Docker Compose, environments | Containers, health checks, production |
| [Testing](14-testing.md) | Testing strategy | TestContainers, integration tests |

### 📚 Patterns & Decisions

| Document | Description | Key Topics |
|----------|-------------|------------|
| [Migration Patterns](11-migration-patterns.md) | Spring Boot 4 migration guide | WebFlux→WebMVC, Jackson 3.x |
| [Design Patterns](12-design-patterns.md) | Architectural patterns | OpenAPI-First, Dynamic Schema |
| [Future Enhancements](15-future-enhancements.md) | Roadmap and recommendations | Caching, observability, scaling |

### 📐 Diagrams

| Diagram | Shows |
|---------|-------|
| [High-Level Architecture](diagrams/high-level-architecture.md) | Complete system with OAuth2 flows |
| [Module Dependencies](diagrams/module-dependencies.md) | Maven build order and dependencies |
| [Code Generation Pipeline](diagrams/code-generation-pipeline.md) | OpenAPI → Database schema flow |
| [Data Model ERD](diagrams/data-model-erd.md) | Database entities and relationships |
| [OAuth2 Flows](diagrams/oauth2-flows.md) | Authentication sequences |
| [MCP Tool Invocation](diagrams/mcp-tool-invocation.md) | End-to-end tool execution |
| [Deployment Architecture](diagrams/deployment-architecture.md) | Docker Compose setup |

### 📋 Architectural Decision Records (ADRs)

| ADR | Decision | Why It Matters |
|-----|----------|----------------|
| [001](adr/001-openapi-first.md) | OpenAPI-First Development | Single source of truth for APIs |
| [002](adr/002-spring-data-jdbc.md) | Spring Data JDBC over JPA | Simpler model, faster startup |
| [003](adr/003-dynamic-liquibase.md) | Dynamic Liquibase Generation | Zero manual schema writing |
| [004](adr/004-webmvc-over-webflux.md) | WebMVC over WebFlux | OAuth2 compatibility, MCP security |
| [005](adr/005-http-streamable-transport.md) | HTTP Streamable Transport | MCP spec compliance, OAuth2 support |

---

## Quick Start Guides

### For New Developers

**Recommended Reading Order**:
1. [System Overview](01-system-overview.md) - Understand the business context
2. [High-Level Architecture Diagram](diagrams/high-level-architecture.md) - See how components fit together
3. [Module Architecture](03-module-architecture.md) - Learn the 6-module structure
4. [Build Workflow](10-build-workflow.md) - Build and run the application
5. [Code Generation Pipeline](04-code-generation.md) - Understand the code generation magic

**Build Commands**:
```bash
# Clone and build
git clone https://github.com/pacphi/spring-ai-resos
cd spring-ai-resos
mvn clean install

# Run backend (Terminal 1)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run MCP server (Terminal 2)
cd mcp-server && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run chatbot (Terminal 3)
cd mcp-client && mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev
```

### For Architects & Migrating Teams

**Recommended Reading Order**:
1. [Technology Stack](02-technology-stack.md) - Understand versions and BOM management
2. [Migration Patterns](11-migration-patterns.md) - Learn from WebFlux→WebMVC migration
3. [Security Architecture](06-security-architecture.md) - Three-tier OAuth2 design
4. [Design Patterns](12-design-patterns.md) - Reusable architectural patterns
5. [ADRs](adr/) - Understand key architectural decisions

**Key Migration Lessons**:
- WebFlux → WebMVC for OAuth2 compatibility
- Jackson 2.x → 3.x (`com.fasterxml` → `tools.jackson`)
- OpenFeign → Spring HTTP Interface
- Spring Security 7.x patterns (`.authorizeHttpRequests()`)
- MCP security requires WebMVC

### For Operations & DevOps

**Recommended Reading Order**:
1. [Deployment Architecture](diagrams/deployment-architecture.md) - Docker Compose setup
2. [Deployment Guide](13-deployment.md) - Production considerations
3. [Testing](14-testing.md) - Integration testing with TestContainers
4. [Future Enhancements](15-future-enhancements.md) - Observability recommendations

**Deployment Commands**:
```bash
# Full stack with Docker Compose
docker-compose -f docker/docker-compose.yml up --build

# View logs
docker-compose logs -f

# Health checks
curl http://localhost:8080/actuator/health  # Backend
curl http://localhost:8082/actuator/health  # MCP Server
curl http://localhost:8081/api/auth/status  # MCP Client
```

---

## Architecture at a Glance

```
┌─────────────────┐         OAuth2 PKCE          ┌──────────────────────┐
│   React SPA     │◄──────────────────────────────│  Authorization       │
│   Port 8081     │                               │  Server (Backend)    │
│   (MCP Client)  │                               │  Port 8080           │
└────────┬────────┘                               └──────────┬───────────┘
         │                                                   │
         │ MCP Protocol (HTTP Streamable)                    │
         ▼                                                   │ OAuth2 JWT
┌─────────────────┐      OAuth2 Client Credentials          │ Validation
│   MCP Server    │◄──────────────────────────────────────────┘
│   Port 8082     │
│   Tool Provider │───────OAuth2 Client Credentials────────┐
└─────────────────┘                                         │
                                                            ▼
                                                   ┌────────────────┐
                                                   │  ResOs API     │
                                                   │  (Backend)     │
                                                   │  Port 8080     │
                                                   └───────┬────────┘
                                                           │
                                                           ▼
                                                   ┌────────────────┐
                                                   │  PostgreSQL /  │
                                                   │  H2 Database   │
                                                   └────────────────┘
```

**Key Components**:
- **Frontend**: React SPA with OAuth2 PKCE authentication
- **MCP Client**: Spring AI ChatClient orchestrating LLM + tools
- **MCP Server**: Exposes ResOs tools via Model Context Protocol
- **Backend**: OAuth2 Authorization Server + ResOs API
- **Database**: PostgreSQL (prod) / H2 (dev) with Liquibase

---

## Quick Reference

### Ports

| Service | Port | Purpose |
|---------|------|---------|
| Backend | 8080 | OAuth2 Auth Server + ResOs API |
| MCP Client | 8081 | React SPA + Chatbot |
| MCP Server | 8082 | MCP Tool Provider |
| PostgreSQL | 5432 | Database (when using postgres profile) |
| Adminer | 8083 | Database admin UI (optional) |

### Databases

| Environment | Database | Configuration |
|-------------|----------|---------------|
| Development | H2 In-Memory | `spring.profiles.active=dev` |
| Production | PostgreSQL | `spring.profiles.active=postgres` |

### LLM Providers

| Provider | Profile | Model |
|----------|---------|-------|
| OpenAI | `openai` | gpt-4o-mini |
| Groq Cloud | `groq-cloud` | llama-3.3-70b-versatile |
| OpenRouter | `openrouter` | claude-3.7-sonnet, gemini-2.0-flash |
| Ollama | `ollama` | Local models |

### Default Users

| Username | Password | Roles |
|----------|----------|-------|
| admin | admin123 | ROLE_ADMIN, ROLE_OPERATOR, ROLE_USER |
| operator | operator123 | ROLE_OPERATOR, ROLE_USER |
| user | user123 | ROLE_USER |

### OAuth2 Clients

| Client ID | Grant Type | Scopes |
|-----------|-----------|--------|
| mcp-server | client_credentials | backend.read, backend.write |
| mcp-client | client_credentials | mcp.read, mcp.write |
| frontend-app | authorization_code + PKCE | openid, profile, email, chat.read, chat.write |

---

## Glossary

| Term | Definition |
|------|------------|
| **MCP** | Model Context Protocol - standard for AI tool integration |
| **PKCE** | Proof Key for Code Exchange - OAuth2 extension for public clients |
| **SSE** | Server-Sent Events - unidirectional HTTP streaming |
| **HTTP Streamable** | MCP's bidirectional HTTP transport (current standard) |
| **JWT** | JSON Web Token - compact token format for OAuth2 |
| **JDBC** | Java Database Connectivity - database access API |
| **JPA** | Java Persistence API - ORM specification (not used here) |
| **Liquibase** | Database schema migration tool |
| **OpenAPI** | API specification standard (formerly Swagger) |
| **JavaParser** | Java source code parser for AST manipulation |
| **BOM** | Bill of Materials - Maven dependency management |
| **Aggregate** | DDD concept - cluster of related entities |
| **AggregateReference** | Spring Data JDBC foreign key pattern |
| **SseEmitter** | Spring MVC class for SSE streaming |
| **RestClient** | Spring 6+ HTTP client (successor to RestTemplate) |

---

## Key Technologies

### Core Framework
- **Spring Boot**: 4.0.1
- **Spring AI**: 2.0.0-M1
- **Spring Cloud**: 2025.1.0
- **Spring Security**: 7.0.2
- **Java**: 25

### Persistence
- **Spring Data JDBC**: Lightweight ORM
- **Liquibase**: 5.0.1
- **PostgreSQL**: 16
- **H2**: In-memory database (dev)

### Security
- **Spring Authorization Server**: OAuth2 + OIDC
- **JWT**: RSA-256 signed tokens
- **BCrypt**: Password hashing

### Build & Code Generation
- **Maven**: 3.9.11
- **OpenAPI Generator**: Client code generation
- **JavaParser**: Entity transformation
- **Spotless**: Code formatting

### Frontend
- **React**: 18+
- **Vite**: 5+
- **Node.js**: 23.4.0

---

## Contributing to Documentation

### File Naming Convention

- Main docs: `NN-topic-name.md` (e.g., `01-system-overview.md`)
- Diagrams: `diagrams/descriptive-name.md`
- ADRs: `adr/NNN-decision-name.md` (e.g., `adr/001-openapi-first.md`)

### Adding New Documentation

1. **Update this README**: Add link in appropriate section
2. **Cross-reference**: Link related documents
3. **Mermaid Diagrams**: Use Mermaid for all diagrams
4. **Code Examples**: Include file paths and line references
5. **Keep Current**: Update when architecture changes

---

## Feedback & Issues

Found an issue or have a suggestion for the architecture documentation?

- **Documentation Issues**: [Create an issue](https://github.com/pacphi/spring-ai-resos/issues) with label `documentation`
- **Architecture Questions**: [Start a discussion](https://github.com/pacphi/spring-ai-resos/discussions)
- **Code Issues**: See main [README.md](../../README.md)

---

## License

This project is licensed under the Apache License 2.0. See [LICENSE](../../LICENSE) for details.

---

## Acknowledgments

**Author**: Chris Phillipson ([@pacphi](https://github.com/pacphi))

**Inspired By**:
- [Spring AI](https://docs.spring.io/spring-ai/reference/) community
- [Model Context Protocol](https://modelcontextprotocol.info/) specification
- [Spring Boot](https://spring.io/projects/spring-boot) team
- [ResOs API](https://documenter.getpostman.com/view/3308304/SzzehLGp?version=latest)

---

**Last Updated**: January 2026
