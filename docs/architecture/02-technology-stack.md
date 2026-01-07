# Technology Stack

This document provides a comprehensive breakdown of all technologies, frameworks, and tools used in the Spring AI ResOs project.

## Technology Matrix

### Core Platform

| Category         | Technology   | Version  | Purpose                                  |
| ---------------- | ------------ | -------- | ---------------------------------------- |
| **Language**     | Java         | 25       | Runtime environment with latest features |
| **Build Tool**   | Maven        | 3.9.11   | Multi-module project management          |
| **Framework**    | Spring Boot  | 4.0.1    | Application framework                    |
| **AI Framework** | Spring AI    | 2.0.0-M1 | AI integration and tool management       |
| **Cloud**        | Spring Cloud | 2025.1.0 | Cloud-native patterns                    |

### Spring Ecosystem

| Component                       | Version | Purpose                          |
| ------------------------------- | ------- | -------------------------------- |
| **Spring Security**             | 7.0.2   | Authentication and authorization |
| **Spring Authorization Server** | 1.4.0   | OAuth2 and OIDC provider         |
| **Spring Data JDBC**            | 4.0.0   | Lightweight data persistence     |
| **Spring Web MVC**              | 7.0.2   | Servlet-based web framework      |
| **Spring Boot Actuator**        | 4.0.1   | Production-ready features        |

### AI & MCP Stack

| Component               | Version  | Purpose                       |
| ----------------------- | -------- | ----------------------------- |
| **Spring AI BOM**       | 2.0.0-M1 | AI dependency management      |
| **MCP Server WebMVC**   | 2.0.0-M1 | Model Context Protocol server |
| **MCP Client**          | 2.0.0-M1 | Model Context Protocol client |
| **MCP Client Security** | 0.0.5    | OAuth2 integration for MCP    |

**LLM Providers**:

- **OpenAI**: gpt-4o-mini (chat), text-embedding-ada-002 (embeddings)
- **Groq Cloud**: llama-3.3-70b-versatile
- **OpenRouter**: claude-3.7-sonnet, gemini-2.0-flash, deepseek-chat
- **Ollama**: Local models (mistral, nomic-embed-text)

### Data & Persistence

| Component      | Version | Purpose                        |
| -------------- | ------- | ------------------------------ |
| **Liquibase**  | 5.0.1   | Database schema migration      |
| **PostgreSQL** | 16      | Production database            |
| **H2**         | 2.3.232 | Development in-memory database |
| **HikariCP**   | 6.2.1   | JDBC connection pooling        |

### Security & OAuth2

| Component                  | Version               | Purpose                    |
| -------------------------- | --------------------- | -------------------------- |
| **Spring Security OAuth2** | 7.0.2                 | OAuth2 framework           |
| **Spring Security RSA**    | 1.1.4                 | RSA key generation for JWT |
| **BCrypt**                 | (Spring Security)     | Password hashing           |
| **JJWT**                   | (via Spring Security) | JWT token processing       |

### Serialization & Data Formats

| Component                     | Version | Purpose                                |
| ----------------------------- | ------- | -------------------------------------- |
| **Jackson BOM**               | 3.0.3   | JSON serialization (Jakarta namespace) |
| **Jackson Databind**          | 3.0.3   | Object mapping                         |
| **Jackson Datatype JSR310**   | 3.0.3   | Java 8 date/time support               |
| **Jackson Databind Nullable** | 0.2.8   | Optional field handling                |

### Code Generation

| Component             | Version | Purpose                                    |
| --------------------- | ------- | ------------------------------------------ |
| **OpenAPI Generator** | 7.18.0  | HTTP client generation                     |
| **JavaParser**        | 3.27.1  | AST manipulation for entity transformation |

### Build Plugins

| Plugin                       | Version | Purpose                              |
| ---------------------------- | ------- | ------------------------------------ |
| **spring-boot-maven-plugin** | 4.0.1   | Executable JAR creation              |
| **spring-banner-plugin**     | 1.6.0   | Custom Spring Boot banner            |
| **git-commit-id-plugin**     | 9.0.1   | Git info in artifacts                |
| **spotless-maven-plugin**    | 3.1.0   | Code formatting (Google Java Format) |
| **cyclonedx-maven-plugin**   | 2.9.2   | SBOM generation                      |
| **maven-dependency-plugin**  | 3.9.0   | Source unpacking                     |
| **exec-maven-plugin**        | 3.5.0   | Java execution during build          |
| **frontend-maven-plugin**    | 2.0.0   | React build integration              |

### Frontend Stack

| Component                    | Version | Purpose                   |
| ---------------------------- | ------- | ------------------------- |
| **Node.js**                  | 23.4.0  | JavaScript runtime        |
| **npm**                      | 10.9.2  | Package manager           |
| **React**                    | 18.3.1  | UI framework              |
| **Vite**                     | 5.4.11  | Build tool and dev server |
| **React Markdown**           | 9.0.2   | Markdown rendering        |
| **React Syntax Highlighter** | 15.6.1  | Code syntax highlighting  |

### Testing

| Component            | Version | Purpose                             |
| -------------------- | ------- | ----------------------------------- |
| **JUnit Jupiter**    | 6.0.0   | Testing framework                   |
| **AssertJ**          | 3.27.6  | Fluent assertions                   |
| **Mockito**          | 5.15.2  | Mocking framework                   |
| **Spring Boot Test** | 4.0.1   | Spring test support                 |
| **TestContainers**   | 1.20.4  | Integration testing with containers |

### Development Tools

| Component           | Version           | Purpose               |
| ------------------- | ----------------- | --------------------- |
| **Lombok**          | (via Spring Boot) | Boilerplate reduction |
| **Spring DevTools** | 4.0.1             | Hot reload            |
| **H2 Console**      | 2.3.232           | Database admin UI     |

## Dependency Management

### Bill of Materials (BOMs)

The project uses Maven BOMs for consistent dependency versioning:

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot Parent (provides base BOM) -->
        <parent>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-parent</artifactId>
            <version>4.0.1</version>
        </parent>

        <!-- Spring AI BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>2.0.0-M1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- Jackson BOM (Jakarta namespace) -->
        <dependency>
            <groupId>tools.jackson</groupId>
            <artifactId>jackson-bom</artifactId>
            <version>3.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>

        <!-- JUnit BOM -->
        <dependency>
            <groupId>org.junit</groupId>
            <artifactId>junit-bom</artifactId>
            <version>6.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Version Properties

Centralized in parent POM:

```xml
<properties>
    <!-- Platform -->
    <java.version>25</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>

    <!-- Frameworks -->
    <spring-ai.version>2.0.0-M1</spring-ai.version>

    <!-- Libraries -->
    <liquibase.version>5.0.1</liquibase.version>
    <jackson-databind-nullable.version>0.2.8</jackson-databind-nullable.version>

    <!-- Frontend -->
    <node.version>v23.4.0</node.version>
    <npm.version>10.9.2</npm.version>

    <!-- Testing -->
    <assertj.version>3.27.6</assertj.version>
</properties>
```

## Migration Context

### Jackson 2.x → 3.x

**Key Changes**:

- Package rename: `com.fasterxml.jackson` → `tools.jackson`
- Jakarta EE namespace (not javax)
- BOM version: 3.0.3

**Impact**:

- OpenAPI Generator must use Jakarta EE mode
- All Jackson imports updated
- Custom serializers/deserializers updated

### OpenFeign → Spring HTTP Interface

**Rationale**:

- Spring HTTP Interface is native Spring 6+ feature
- Better integration with RestClient
- Type-safe interfaces without runtime proxying
- Official Spring support vs third-party

**Migration**:

```xml
<!-- OLD -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<!-- NEW -->
<!-- Spring HTTP Interface is part of spring-boot-starter-web -->
```

**OpenAPI Generator Configuration**:

```xml
<configOptions>
    <library>spring-http-interface</library>
</configOptions>
```

### WebFlux → WebMVC

**Rationale**: See [ADR-004](adr/004-webmvc-over-webflux.md)

**Dependency Changes**:

```xml
<!-- OLD -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- NEW -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

## Module Dependencies

### Backend Module

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- Data -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>

    <!-- Entities (internal) -->
    <dependency>
        <groupId>me.pacphi</groupId>
        <artifactId>spring-ai-resos-entities</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- Database Drivers -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

### MCP Server Module

```xml
<dependencies>
    <!-- MCP -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>

    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <!-- Client (for backend API calls) -->
    <dependency>
        <groupId>me.pacphi</groupId>
        <artifactId>spring-ai-resos-client</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

### MCP Client Module

```xml
<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- MCP -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client</artifactId>
    </dependency>

    <!-- MCP Security -->
    <dependency>
        <groupId>org.springaicommunity</groupId>
        <artifactId>mcp-client-security</artifactId>
        <version>0.0.5</version>
    </dependency>

    <!-- Spring AI Models -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>

    <!-- OAuth2 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <!-- Frontend (React) -->
    <!-- Built via frontend-maven-plugin, no Maven dependency -->
</dependencies>
```

## Repository Configuration

### Maven Repositories

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

**Why**: Spring AI 2.0.0-M1 is a milestone release, not in Maven Central

## Profiles

### Available Profiles

| Profile      | Purpose                      | Modules                         |
| ------------ | ---------------------------- | ------------------------------- |
| `dev`        | H2 in-memory, OAuth2 seeding | backend, mcp-server, mcp-client |
| `postgres`   | PostgreSQL database          | backend                         |
| `docker`     | Docker Compose lifecycle     | backend                         |
| `test`       | Test data seeding            | backend                         |
| `openai`     | OpenAI LLM provider          | mcp-client                      |
| `groq-cloud` | Groq Cloud LLM               | mcp-client                      |
| `openrouter` | OpenRouter LLM               | mcp-client                      |
| `ollama`     | Local Ollama models          | mcp-client                      |

### Profile Activation

```bash
# Development with H2
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production with PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=postgres

# Chatbot with OpenAI
cd mcp-client
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev
```

## Version Compatibility Matrix

| Component  | Min Version | Recommended | Max Tested |
| ---------- | ----------- | ----------- | ---------- |
| Java       | 21          | 25          | 25         |
| Maven      | 3.9.0       | 3.9.11      | 3.9.11     |
| PostgreSQL | 14          | 16          | 16         |
| Node.js    | 20          | 23.4.0      | 23.4.0     |
| Docker     | 24.0        | 27.x        | 27.x       |

## Known Compatibility Issues

### Resolved Issues

1. **Jackson 2.x → 3.x**: Package namespace change
   - **Solution**: Use `tools.jackson` BOM, Jakarta EE mode in OpenAPI Generator

2. **WebFlux + OAuth2 + MCP**: Security configuration complexity
   - **Solution**: Migrate to WebMVC (see [ADR-004](adr/004-webmvc-over-webflux.md))

3. **SSE Transport Deprecation**: MCP spec evolved
   - **Solution**: Use HTTP Streamable (see [ADR-005](adr/005-http-streamable-transport.md))

### Current Limitations

1. **Spring AI 2.0.0-M1**: Milestone release, not GA
   - APIs may change before release
   - Check Spring AI release notes for breaking changes

2. **Java 25**: Preview features may require `--enable-preview`
   - Virtual threads (Project Loom) stable
   - Pattern matching stable
   - String templates still preview

## Dependency Tree Visualization

```text
spring-ai-resos-parent
├── codegen (JavaParser)
├── client (OpenAPI → Spring HTTP Interface)
│   └── Uses: Jackson, Swagger Annotations
├── entities (JavaParser transformation)
│   └── Depends on: client (sources), codegen
├── backend (Spring Boot app)
│   └── Depends on: entities, Spring Data JDBC, Liquibase, OAuth2
├── mcp-server (Spring Boot app)
│   └── Depends on: client, Spring AI MCP Server, OAuth2
└── mcp-client (Spring Boot app + React)
    └── Depends on: Spring AI MCP Client, OAuth2, React (via npm)
```

## Related Documentation

- [Module Architecture](03-module-architecture.md) - Detailed module breakdown
- [Build Workflow](10-build-workflow.md) - Maven build process
- [Migration Patterns](11-migration-patterns.md) - Spring Boot 4 migration lessons
