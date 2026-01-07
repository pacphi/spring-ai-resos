# ADR-000: Project Genesis - Spring AI ResOs MVP Development

## Status

**Historical Record** - MVP Completed March 2025, Maintained through November 2025

## Context

This Architecture Decision Record serves as a historical retrospective documenting the foundational decisions and evolution of the Spring AI ResOs project from its inception on March 11, 2025 through the first working MVP on March 31, 2025 (commit `2b7e6b3`), and the subsequent maintenance period maintaining compatibility with Spring Boot 3.5.x and Spring AI 1.x through November 10, 2025 (commit `4cd3b99`).

### Project Vision

The Spring AI ResOs project was conceived as a demonstration of building a Spring AI-enhanced restaurant booking system using an API-first approach. The core goal was to integrate AI chatbot capabilities with a traditional reservation system backend using the Model Context Protocol (MCP).

### Original Roadmap

The project began with a clear MVP definition documented in `ROADMAP.md`:

> MVP is defined as being able to chat via Claude or the ReactJS chatbot.

**Original Punch-list:**

1. Create basic Maven multi-module project structure
2. Seed documentation (README, SPARK, RELEASE, ROADMAP)
3. Code-generate model objects and an OpenFeign client based on OpenAPI derivative
   - Use Claude to help generate from Postman docs
   - Validate spec online until Swagger interface rendered without errors
4. Build a Spring Data module encapsulating model objects from `client` module with in-memory database bootstrap capability
   - Adapt codegen to produce models with Jakarta Persistence annotations
   - Seed with test data for demo convenience
5. Implement an MCP server with function callbacks delegating to DefaultApiClient
   - Define client configuration for use with Claude desktop
6. Implement an MCP client with ReactJS chatbot frontend
7. Author a series of articles about the project on LinkedIn

## Decision

**Build a multi-module Spring Boot application demonstrating AI-enhanced restaurant reservations using OpenAPI-first development and the Model Context Protocol (MCP).**

### Technology Stack at MVP Completion (March 2025 - commit `2b7e6b3`)

| Component             | Version                | Purpose                |
| --------------------- | ---------------------- | ---------------------- |
| **Spring Boot**       | 3.4.x                  | Application framework  |
| **Spring AI**         | 1.0.0-M6 (pre-release) | AI/LLM integration     |
| **Spring MCP**        | Early adoption         | MCP protocol support   |
| **Java**              | 21                     | Runtime platform       |
| **PostgreSQL**        | 16                     | Production database    |
| **H2**                | Latest                 | Development database   |
| **OpenAPI Generator** | 7.x                    | Client code generation |
| **OpenFeign**         | Latest                 | HTTP client generation |

### Technology Stack at End of Maintenance Period (November 2025 - commit `4cd3b99`)

| Component                  | Version   | Purpose                      |
| -------------------------- | --------- | ---------------------------- |
| **Spring Boot**            | 3.5.7     | Application framework        |
| **Spring AI**              | 1.0.3     | AI/LLM integration (GA)      |
| **Spring Cloud**           | 2025.0.0  | Cloud-native patterns        |
| **Spring Framework BOM**   | 6.x       | Core framework               |
| **Spring Security OAuth2** | 6.5.6     | Authentication/authorization |
| **Java**                   | 21        | Runtime platform             |
| **PostgreSQL**             | 16        | Production database          |
| **H2**                     | Latest    | Development database         |
| **OpenAPI Generator**      | 7.17.0    | Client code generation       |
| **SpringDoc OpenAPI**      | 2.8.14    | API documentation            |
| **Micrometer**             | 1.16.0    | Observability                |
| **Liquibase**              | (managed) | Database migrations          |
| **OpenCSV**                | 5.12.0    | CSV data processing          |
| **JavaParser**             | 3.27.1    | Code transformation          |

### Project Evolution Timeline

#### Phase 1: MVP Development (March 11-31, 2025)

**15 commits from `686546e` to `2b7e6b3`**

- **Mar 11**: Initial commit establishing multi-module Maven structure
- **Mar 12**: Added id fields to Guest, Address, Restaurant models; created ROADMAP
- **Mar 12-17**: OpenAPI spec refinement and OpenAI chat configuration
- **Mar 18**: Simplified chat configuration, removed conditional Groq Cloud config
- **Mar 21**: First dependency upgrade cycle, Spring Framework BOM update
- **Mar 31**: **MVP ACHIEVED** - MCP BOM version upgrade completes first working version

> At this point, the core functionality was complete: chat via Claude desktop and ReactJS chatbot operational.

#### Phase 2: Compatible Updates for Spring Boot 3.5.x / Spring AI 1.x (April-November 2025)

**81 commits from `2b7e6b3` to `4cd3b99`**

This phase focused on maintaining compatibility with the Spring Boot 3.5.x and Spring AI 1.x architecture while keeping dependencies current.

- **Jul 2**: Major milestone - upgrade to Spring Boot 3.5.3, Spring Cloud 2025.0.0, Spring AI 1.0.0 (GA release)
- **Jul 30 - Nov 10**: Sustained dependency maintenance via Dependabot
  - 70+ automated dependency updates merged
  - Regular security patches applied
  - Plugin version upgrades for tooling
- **Oct 23**: Spring Boot 3.5.7 (final 3.5.x version before Spring Boot 4)
- **Nov 10**: Final compatible state at commit `4cd3b99`

### Key Architectural Decisions Made

1. **OpenAPI-First Development**: API contract defined first, code generated from spec
2. **Multi-Module Maven Structure**: Separation of concerns (client, backend, mcp-server, mcp-client)
3. **Spring Data JDBC over JPA**: Explicit SQL control, simpler mapping
4. **OpenFeign for HTTP Client**: Type-safe REST client generation (later migrated to Spring HTTP Interface)
5. **MCP for AI Integration**: Model Context Protocol for Claude/chatbot communication
6. **ReactJS Frontend**: Modern SPA for chatbot interface
7. **Liquibase for Migrations**: Version-controlled database schema changes
8. **CSV-based Seed Data**: Flexible test data loading

## Consequences

### Positive

1. **Working MVP Achieved**: Fully functional chat via Claude and ReactJS chatbot
2. **Clean Architecture**: Well-separated modules with clear responsibilities
3. **Maintainable Codebase**: Regular automated updates kept dependencies current
4. **Documented Decisions**: ADRs capture key architectural choices
5. **Extensible Design**: Foundation ready for Spring Boot 4.x migration

### Negative

1. **Incomplete Controller Implementations**: Many endpoints still return `UnsupportedOperationException`
2. **Limited Test Coverage**: Integration tests need expansion
3. **Missing Production Features**: No email notifications, payment processing, or rate limiting
4. **Technical Debt**: Some validation and error handling gaps

### Neutral

1. **OpenFeign Dependency**: Later required migration to Spring HTTP Interface for Spring Boot 4 compatibility
2. **In-Memory Chat Storage**: Acceptable for MVP, requires Redis for production

## Commit Statistics

### Phase 1: MVP Development (`686546e` to `2b7e6b3`)

| Metric                  | Value             |
| ----------------------- | ----------------- |
| **Total Commits**       | 15                |
| **Date Range**          | March 11-31, 2025 |
| **Duration**            | 20 days           |
| **Primary Contributor** | Chris Phillipson  |

### Phase 2: Maintenance (`2b7e6b3` to `4cd3b99`)

| Metric                 | Value                        |
| ---------------------- | ---------------------------- |
| **Total Commits**      | 81                           |
| **Date Range**         | March 31 - November 10, 2025 |
| **Duration**           | ~7.5 months                  |
| **Manual Commits**     | ~11                          |
| **Dependabot Commits** | ~70                          |

### Notable Commits

| Date       | Commit    | Description                                                                   |
| ---------- | --------- | ----------------------------------------------------------------------------- |
| 2025-03-11 | `686546e` | Initial commit                                                                |
| 2025-03-12 | `5b47c79` | Add id fields to models                                                       |
| 2025-03-12 | `4125eec` | First ROADMAP update                                                          |
| 2025-03-31 | `2b7e6b3` | **MVP COMPLETE** - MCP BOM upgrade                                            |
| 2025-07-02 | `f30c34f` | Major upgrade to Spring Boot 3.5.3, Spring Cloud 2025.0.0, Spring AI 1.0.0 GA |
| 2025-08-05 | `78c7059` | Spring AI 1.0.1                                                               |
| 2025-10-02 | `5639898` | Spring AI 1.0.3                                                               |
| 2025-10-23 | `9cf4400` | Spring Boot 3.5.7                                                             |
| 2025-11-10 | `4cd3b99` | Final Spring Boot 3.5.x / Spring AI 1.x state                                 |

## Lessons Learned

1. **API-First Works Well**: OpenAPI specification as contract prevented drift
2. **Automated Dependencies Essential**: Dependabot kept 70+ dependencies current with minimal effort
3. **Incremental Progress**: Small, focused commits easier to review and debug
4. **Spring AI Rapid Evolution**: Framework matured significantly during development (pre-1.0 to 1.0.3)
5. **MCP Integration Viable**: Model Context Protocol provides clean AI tool integration

## Related Documentation

- [ADR-001: OpenAPI-First Development](001-openapi-first.md)
- [ADR-002: Spring Data JDBC](002-spring-data-jdbc.md)
- [ADR-003: Dynamic Liquibase](003-dynamic-liquibase.md)
- [ADR-004: WebMVC over WebFlux](004-webmvc-over-webflux.md)
- [ADR-005: HTTP Streamable Transport](005-http-streamable-transport.md)
- [Future Enhancements](../15-future-enhancements.md) - Supersedes original ROADMAP.md

## References

- [Original ROADMAP.md](../../archives/mvp/ROADMAP.md) - Archived original roadmap
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [ResOs API (Postman)](https://documenter.getpostman.com/view/3308304/SzzehLGp?version=latest)

## Decision Date

- **March 2025**: Project Inception and MVP Development
- **March 31, 2025**: MVP Completion (commit `2b7e6b3`)
- **November 10, 2025**: End of Spring Boot 3.5.x / Spring AI 1.x maintenance (commit `4cd3b99`)

## Author

- Chris Phillipson (Architect)

## Changelog

| Date           | Change                                                            |
| -------------- | ----------------------------------------------------------------- |
| March 2025     | Project inception, ROADMAP.md created                             |
| March 31, 2025 | **MVP completed** at commit `2b7e6b3`                             |
| July 2025      | Major Spring ecosystem upgrade to Spring AI 1.0.0 GA              |
| November 2025  | Final Spring Boot 3.5.x / Spring AI 1.x state at commit `4cd3b99` |
| January 2026   | Historical ADR created, ROADMAP.md archived                       |
