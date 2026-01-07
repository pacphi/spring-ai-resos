# Module Architecture

This project is organized as a Maven multi-module build with 6 distinct modules, each with specific responsibilities.

## Module Dependency Graph

See [Module Dependencies Diagram](diagrams/module-dependencies.md) for visual representation.

```text
codegen (build-time utility)
   ↓ used by
entities ← depends on client (source unpacking)
   ↓ compile dependency
backend (executable JAR)

client (HTTP client library)
   ↓ compile dependency
mcp-server (executable JAR)

mcp-client (executable JAR)
   ↓ runtime HTTP connection
mcp-server
   ↓ runtime HTTP connection + OAuth2
backend
```

---

## Module 1: codegen

**Purpose**: Entity transformation utilities using JavaParser

### Module Information

| Property        | Value                       |
| --------------- | --------------------------- |
| **Artifact ID** | `spring-ai-resos-codegen`   |
| **Type**        | JAR (build-time tool)       |
| **Runtime**     | No (used during build only) |
| **Location**    | `/codegen/`                 |

### Responsibilities

- Transform OpenAPI-generated POJOs into Spring Data JDBC entities
- AST manipulation using JavaParser
- Annotation replacement (Jackson → Spring Data JDBC)
- Type transformation (List → Set for relationships)
- Add foreign key support (AggregateReference)

### Key Components

**EntityGenerator** (`codegen/src/main/java/me/pacphi/ai/resos/util/EntityGenerator.java`):

- Parses Java source files using JavaParser
- Removes Jackson annotations (`@JsonProperty`, `@JsonCreator`)
- Adds Spring Data JDBC annotations (`@Table`, `@Id`, `@Column`)
- Transforms field types for JDBC compatibility
- Generates `toPojo()` and `fromPojo()` conversion methods

### Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>com.github.javaparser</groupId>
        <artifactId>javaparser-core</artifactId>
        <version>3.27.1</version>
    </dependency>
</dependencies>
```

### Used By

**entities module** during `exec:java` phase:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>me.pacphi.ai.resos.util.EntityGenerator</mainClass>
                <arguments>
                    <argument>${project.build.directory}/unpacked-sources</argument>
                    <argument>${project.build.directory}/generated-sources</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>me.pacphi</groupId>
            <artifactId>spring-ai-resos-codegen</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</plugin>
```

---

## Module 2: client

**Purpose**: OpenAPI-generated HTTP client using Spring HTTP Interface

### Module Information

| Property        | Value                    |
| --------------- | ------------------------ |
| **Artifact ID** | `spring-ai-resos-client` |
| **Type**        | JAR (library)            |
| **Runtime**     | Yes (used by mcp-server) |
| **Location**    | `/client/`               |

### Responsibilities

- Provide type-safe HTTP client for ResOs API
- Generate client code from OpenAPI specification
- Support multiple backend implementations (local or external)
- Provide data models (POJOs) for API communication

### Generated Packages

**me.pacphi.ai.resos.api**:

- `DefaultApi` - Main HTTP client interface
- Methods for all API operations (customers, bookings, orders, etc.)

**me.pacphi.ai.resos.model**:

- Data model POJOs (Customer, Booking, Order, Table, etc.)
- Jackson annotations for JSON serialization
- Bean validation annotations

**me.pacphi.ai.resos.configuration**:

- `ApiClient` - RestClient configuration
- Request interceptors
- Base URL configuration

### OpenAPI Specification

**File**: `client/src/main/resources/openapi/resos-openapi-modified.yml`

**Based On**: ResOs API v1.2 (modified for this project)

**Key Models**:

- Customer, Booking, Order, Table, Area
- Feedback, OpeningHours, Restaurant
- Error responses, pagination models

### Build Configuration

**OpenAPI Generator Plugin** (`client/pom.xml`):

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.18.0</version>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>src/main/resources/openapi/resos-openapi-modified.yml</inputSpec>
                <generatorName>java</generatorName>
                <library>spring-http-interface</library>
                <apiPackage>me.pacphi.ai.resos.api</apiPackage>
                <modelPackage>me.pacphi.ai.resos.model</modelPackage>
                <generateApiTests>false</generateApiTests>
                <configOptions>
                    <useJakartaEe>true</useJakartaEe>
                    <dateLibrary>java8</dateLibrary>
                    <serializationLibrary>jackson</serializationLibrary>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Key Configuration Options**:

- `library: spring-http-interface` - Uses Spring 6+ HTTP Interface (not OpenFeign)
- `useJakartaEe: true` - Jakarta EE namespace (Spring Boot 3+)
- `dateLibrary: java8` - Use Java 8 date/time API
- `serializationLibrary: jackson` - Jackson 3.x for JSON

### Dependencies

```xml
<dependencies>
    <!-- Spring HTTP Interface -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-web</artifactId>
    </dependency>

    <!-- Jackson (3.x) -->
    <dependency>
        <groupId>tools.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>jakarta.validation</groupId>
        <artifactId>jakarta.validation-api</artifactId>
    </dependency>

    <!-- Swagger Annotations -->
    <dependency>
        <groupId>io.swagger.core.v3</groupId>
        <artifactId>swagger-annotations</artifactId>
    </dependency>
</dependencies>
```

### Used By

1. **entities module**: Sources unpacked for transformation
2. **mcp-server module**: Runtime dependency for backend API calls

### Example Generated Code

**DefaultApi Interface**:

```java
public interface DefaultApi {

    @GetExchange("/customers")
    ResponseEntity<List<Customer>> customersGet(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer skip,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String customQuery
    );

    @GetExchange("/customers/{id}")
    ResponseEntity<Customer> customersIdGet(@PathVariable UUID id);

    // ... more methods
}
```

---

## Module 3: entities

**Purpose**: Spring Data JDBC entities generated from OpenAPI models

### Module Information

| Property        | Value                      |
| --------------- | -------------------------- |
| **Artifact ID** | `spring-ai-resos-entities` |
| **Type**        | JAR (library)              |
| **Runtime**     | Yes (used by backend)      |
| **Location**    | `/entities/`               |

### Responsibilities

- Transform client POJOs into JDBC entities
- Add Spring Data JDBC annotations
- Provide entity-to-POJO conversion methods
- Package entities for backend consumption

### Build Process

1. **Unpack client sources**:
   - Maven dependency plugin unpacks `client-sources.jar`
   - Destination: `target/unpacked-sources/`
   - Excludes: `api/` and `configuration/` packages (only models needed)

2. **Transform with EntityGenerator**:
   - Exec plugin runs `EntityGenerator` main class
   - Input: `target/unpacked-sources/`
   - Output: `target/generated-sources/`

3. **Compile entities**:
   - Generated entities compiled to `target/classes/`
   - Packaged as JAR with source classifier

### Generated Package

**me.pacphi.ai.resos.jdbc**:

- `CustomerEntity`, `BookingEntity`, `OrderEntity`
- `TableEntity`, `AreaEntity`, `FeedbackEntity`
- `AppUserEntity`, `AuthorityEntity`, `UserAuthorityEntity`
- OAuth2 entities

### Build Configuration

**Source Unpacking** (`entities/pom.xml`):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>unpack-client-sources</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>unpack</goal>
            </goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>me.pacphi</groupId>
                        <artifactId>spring-ai-resos-client</artifactId>
                        <version>${project.version}</version>
                        <classifier>sources</classifier>
                        <outputDirectory>${project.build.directory}/unpacked-sources</outputDirectory>
                        <excludes>**/api/**,**/configuration/**</excludes>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Dependencies

```xml
<dependencies>
    <!-- Spring Data JDBC -->
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-jdbc</artifactId>
    </dependency>

    <!-- Jackson (for POJO conversion) -->
    <dependency>
        <groupId>tools.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

### Used By

**backend module**: Compile-time dependency for all entities

---

## Module 4: backend

**Purpose**: OAuth2 Authorization Server + ResOs API Resource Server

### Module Information

| Property        | Value                             |
| --------------- | --------------------------------- |
| **Artifact ID** | `spring-ai-resos-backend`         |
| **Type**        | Executable JAR                    |
| **Main Class**  | `SpringAiResOsBackendApplication` |
| **Port**        | 8080                              |
| **Location**    | `/backend/`                       |

### Responsibilities

- **OAuth2 Authorization Server**: Issue JWT tokens for authentication
- **Resource Server**: Protect ResOs API endpoints with OAuth2
- **ResOs API Implementation**: Restaurant reservation system CRUD operations
- **Dynamic Schema Generation**: Generate Liquibase changelogs from entities
- **CSV Data Seeding**: Load seed data from CSV files
- **User Management**: BCrypt password hashing, role-based access control

### Package Structure

```text
me.pacphi.ai.resos
├── config
│   ├── SchemaCreator.java                  # Dynamic Liquibase generation
│   ├── LiquibaseConfiguration.java         # Bean initialization order
│   ├── LiquibaseCustomizer.java            # JAR execution support
│   └── CustomConverters.java               # JDBC type converters
├── security
│   ├── AuthorizationServerConfig.java      # OAuth2 Auth Server (@Order(1))
│   ├── ResourceServerConfig.java           # API protection (@Order(2))
│   ├── DefaultSecurityConfig.java          # Form login (@Order(3))
│   ├── AppUserDetailsService.java          # UserDetailsService impl
│   ├── JwtTokenCustomizer.java             # Custom JWT claims
│   └── OAuth2ClientSeeder.java             # Dev OAuth2 clients
├── controller
│   ├── CustomerController.java             # /api/v1/resos/customers
│   ├── BookingController.java              # /api/v1/resos/bookings (stub)
│   ├── OrderController.java                # /api/v1/resos/orders (stub)
│   ├── TableController.java                # /api/v1/resos/tables (stub)
│   ├── FeedbackController.java             # /api/v1/resos/feedback (stub)
│   ├── OpeningHoursController.java         # /api/v1/resos/openinghours (stub)
│   └── LoginController.java                # /login
├── csv
│   ├── DataSeeder.java                     # CSV loading orchestrator
│   ├── CsvFileProcessor.java               # CSV parsing
│   ├── CsvResourceLoader.java              # File resolution
│   ├── CsvEntityMapper.java                # @CsvEntityMapper annotation
│   ├── RepositoryResolver.java             # Dynamic repository lookup
│   └── impl
│       ├── AppUserMapper.java              # Users CSV → AppUserEntity
│       ├── AuthorityMapper.java            # Authorities CSV → AuthorityEntity
│       ├── UserAuthorityMapper.java        # Join table CSV mapping
│       └── ... (other mappers)
├── repository
│   ├── CustomerRepository.java
│   ├── BookingRepository.java
│   ├── AppUserRepository.java
│   ├── AuthorityRepository.java
│   ├── UserAuthorityRepository.java
│   └── Pageable...Repository.java
└── jdbc (from entities module)
    ├── CustomerEntity.java
    ├── BookingEntity.java
    └── ... (all JDBC entities)
```

### Configuration

**application.yml** (dev profile):

```yaml
server:
  port: 8080

spring:
  application:
    name: spring-ai-resos-backend

  profiles:
    active: dev

  datasource:
    url: jdbc:h2:mem:resos-backend
    driver-class-name: org.h2.Driver
    username: sa
    password:

  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yml

app:
  entity:
    base-package: me.pacphi.ai.resos.jdbc
  seed:
    csv:
      base-path: ./seed-data
      files:
        - authorities.csv
        - users.csv
        - areas.csv
        - tables.csv
        - customers.csv
        - bookings.csv
        - feedback.csv
        - orders.csv
        - openinghours.csv
        - user-authorities.csv
  security:
    issuer-uri: http://localhost:8080
```

### Key Features

1. **Dynamic Schema Generation**:
   - `SchemaCreator` scans `@Table` entities
   - Generates Liquibase changelogs at runtime
   - Handles JAR vs filesystem execution

2. **OAuth2 Authorization Server**:
   - JWT token issuance with RSA signing
   - User authentication with BCrypt
   - OAuth2 client registration
   - OIDC support

3. **Resource Server**:
   - JWT token validation
   - Scope and role-based authorization
   - API endpoint protection

4. **CSV Data Seeding**:
   - Annotation-driven mapper discovery
   - Configurable seed file order
   - BCrypt password hashing during seed

### Critical Files

| File                                      | Purpose                      | Lines |
| ----------------------------------------- | ---------------------------- | ----- |
| `config/SchemaCreator.java`               | Dynamic Liquibase generation | ~400  |
| `security/AuthorizationServerConfig.java` | OAuth2 Auth Server           | ~200  |
| `security/ResourceServerConfig.java`      | API protection               | ~100  |
| `security/JwtTokenCustomizer.java`        | Custom JWT claims            | ~50   |
| `csv/DataSeeder.java`                     | CSV loading orchestrator     | ~150  |

### Build & Run

```bash
# Build (includes Docker image via Jib)
cd backend
mvn clean package

# Run with H2
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run with PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=postgres

# Docker (image built automatically by Jib during mvn package)
docker run -p 8080:8080 spring-ai-resos-backend:test

# Build Docker image manually (if needed)
mvn jib:dockerBuild
```

---

## Module 5: mcp-server

**Purpose**: MCP server exposing ResOs tools for AI agents

### Module Information

| Property        | Value                               |
| --------------- | ----------------------------------- |
| **Artifact ID** | `spring-ai-resos-mcp-server`        |
| **Type**        | Executable JAR                      |
| **Main Class**  | `SpringAiResOsMcpServerApplication` |
| **Port**        | 8082                                |
| **Location**    | `/mcp-server/`                      |

### Responsibilities

- Expose ResOs tools via Model Context Protocol
- HTTP Streamable transport (WebMVC-based)
- Validate JWT tokens from mcp-client (OAuth2 Resource Server)
- Call backend API with client credentials (OAuth2 Client)
- Tool discovery and registration with Spring AI

### Package Structure

```text
me.pacphi.ai.resos.mcp
├── SpringAiResOsMcpServerApplication.java  # Main class
├── ResOsService.java                       # Tool provider (@Tool methods)
├── ResOsConfig.java                        # Backend API client config
└── SecurityConfig.java                     # OAuth2 security
```

### Tool Provider

**ResOsService** (`mcp/ResOsService.java`):

```java
@Component
public class ResOsService {

    private final DefaultApi resOsApi;

    @Tool(description = "Fetch all restaurant tables")
    public List<Table> getTables() {
        return Optional.ofNullable(resOsApi.tablesGet().getBody())
            .orElse(List.of());
    }

    @Tool(description = "Fetch customer records with optional filtering and pagination")
    public List<Customer> getCustomers(
            @ToolParam(description = "Maximum number of records") Integer limit,
            @ToolParam(description = "Number of records to skip") Integer skip,
            @ToolParam(description = "Field to sort by") String sort,
            @ToolParam(description = "Custom query filter") String customQuery
    ) {
        return Optional.ofNullable(
            resOsApi.customersGet(limit, skip, sort, customQuery).getBody()
        ).orElse(List.of());
    }

    @Tool(description = "Fetch opening hours for the next two weeks")
    public List<OpeningHours> getOpeningHours() {
        // Implementation...
    }

    @Tool(description = "Fetch customer feedback with optional filtering")
    public List<Feedback> getFeedback(
            Integer limit, Integer skip, String sort, String customQuery) {
        // Implementation...
    }

    // More tools...
}
```

**Spring AI Behavior**:

- Scans for `@Tool` annotated methods
- Generates JSON Schema from method signatures and `@ToolParam` descriptions
- Exposes tools via MCP protocol at `/mcp/tools/{toolName}`
- Handles parameter binding and result serialization

### Security Configuration

**Dual OAuth2 Role**:

1. **Resource Server**: Validates incoming JWTs from mcp-client
2. **OAuth2 Client**: Calls backend API with client credentials

**SecurityConfig** (`mcp/SecurityConfig.java`):

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**").authenticated()  // Require JWT
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

### Backend API Client Configuration

**ResOsConfig** (`mcp/ResOsConfig.java`):

```java
@Configuration
public class ResOsConfig {

    @Bean
    public RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        return RestClient.builder()
            .baseUrl(resosApiEndpoint)
            .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(
                authorizedClientManager,
                "mcp-server"  // OAuth2 registration ID
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

**Key Components**:

- **RestClient**: JDK HttpClient-based, 30s timeout
- **OAuth2 Interceptor**: Automatically adds Bearer token
- **HttpServiceProxyFactory**: Creates type-safe interface implementation
- **OAuth2AuthorizedClientManager**: Handles token fetch/refresh automatically

### Configuration

**application.yml** (dev profile):

```yaml
server:
  port: 8082

spring:
  application:
    name: spring-ai-resos-mcp-server

  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.2-modified
        # HTTP Streamable transport (default)

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

resos:
  api:
    endpoint: ${RESOS_API_ENDPOINT:https://api.resos.com/v1}
```

### Dependencies

```xml
<dependencies>
    <!-- MCP Server -->
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

    <!-- ResOs API Client -->
    <dependency>
        <groupId>me.pacphi</groupId>
        <artifactId>spring-ai-resos-client</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

### Build & Run

```bash
# Build
cd mcp-server
mvn clean package

# Run
export RESOS_API_ENDPOINT=http://localhost:8080/api/v1/resos
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# With Claude Desktop (STDIO mode)
java -jar target/spring-ai-resos-mcp-server-1.0.0-SNAPSHOT.jar
```

---

## Module 6: mcp-client

**Purpose**: Chatbot web application with React SPA + Spring AI

### Module Information

| Property        | Value                              |
| --------------- | ---------------------------------- |
| **Artifact ID** | `spring-ai-resos-mcp-frontend`     |
| **Type**        | Executable JAR                     |
| **Main Class**  | `SpringAiResOsFrontendApplication` |
| **Port**        | 8081                               |
| **Location**    | `/mcp-client/`                     |

### Responsibilities

- **React SPA**: User interface for chat interactions
- **Spring AI Chat**: Orchestrate LLM conversations with tools
- **MCP Client**: Connect to MCP server for tool invocation
- **OAuth2 Client**: User authentication (PKCE) + service auth (client credentials)
- **SSE Streaming**: Real-time token delivery to browser

### Package Structure

**Backend** (Java):

```text
me.pacphi.ai.resos
├── SpringAiResOsFrontendApplication.java   # Main class
├── config
│   ├── SecurityConfig.java                 # OAuth2 login + security
│   └── McpClientOAuth2Config.java          # MCP OAuth2 customizer
├── controller
│   ├── ChatController.java                 # POST /api/v1/resos/stream/chat
│   └── AuthController.java                 # /api/auth/user, /status
└── service
    ├── ChatService.java                    # AI orchestration
    └── McpSyncClientManager.java           # MCP client wrapper
```

**Frontend** (React):

```text
src/main/frontend/src
├── App.jsx                                 # Main application shell
├── AuthContext.jsx                         # Authentication state
├── components
│   ├── ChatPage.jsx                        # Chat interface
│   ├── LoginPage.jsx                       # Login UI
│   └── ...
├── main.jsx                                # React entry point
└── index.html                              # HTML shell
```

### Chat Service

**ChatService** (`service/ChatService.java`):

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

        // Get MCP clients (lazy initialization)
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
            .advisors(new MessageChatMemoryAdvisor(chatMemory))
            .advisors(new SimpleLoggerAdvisor())
            .stream()
            .content();

        // Subscribe with callbacks
        stream.subscribe(onToken, onError, onComplete);
    }

    private String buildSystemPrompt() {
        return String.format("""
            You are a helpful assistant for a restaurant reservation system.
            Current date: %s

            When filtering data:
            - Use the customQuery parameter for filters
            - Use ISO 8601 date format (YYYY-MM-DD)
            - Be concise and helpful
            """, LocalDate.now());
    }
}
```

### Chat Controller

**ChatController** (`controller/ChatController.java`):

```java
@RestController
@RequestMapping("/api/v1/resos")
public class ChatController {

    private final ChatService chatService;

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
}
```

### Frontend Build

**frontend-maven-plugin** configuration:

```xml
<plugin>
    <groupId>com.github.eirslett</groupId>
    <artifactId>frontend-maven-plugin</artifactId>
    <version>2.0.0</version>
    <configuration>
        <nodeVersion>${node.version}</nodeVersion>
        <npmVersion>${npm.version}</npmVersion>
        <workingDirectory>src/main/frontend</workingDirectory>
    </configuration>
    <executions>
        <execution>
            <id>install-node-and-npm</id>
            <goals>
                <goal>install-node-and-npm</goal>
            </goals>
        </execution>
        <execution>
            <id>npm-install</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <configuration>
                <arguments>install</arguments>
            </configuration>
        </execution>
        <execution>
            <id>npm-build</id>
            <goals>
                <goal>npm</goal>
            </goals>
            <configuration>
                <arguments>run build</arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Build Process**:

1. Install Node.js and npm
2. Run `npm install` to install dependencies
3. Run `npm run build` (Vite production build)
4. Output to `src/main/resources/static/`
5. Spring Boot serves static assets

### Configuration

**application.yml** (openai profile):

```yaml
server:
  port: 8081

spring:
  application:
    name: spring-ai-resos-mcp-frontend

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-ada-002

    mcp:
      client:
        type: SYNC
        initialized: false
        http:
          connections:
            butler:
              url: ${MCP_SERVER_URL:http://localhost:8082}

  security:
    oauth2:
      client:
        registration:
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

### Dependencies

```xml
<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- MCP Client -->
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

    <!-- Thymeleaf (for error pages) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
</dependencies>
```

### React Frontend

**package.json**:

```json
{
  "name": "spring-ai-resos-frontend",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-markdown": "^9.0.2",
    "react-syntax-highlighter": "^15.6.1"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.3.4",
    "vite": "^5.4.11"
  }
}
```

### Build & Run

```bash
# Build (includes React build)
cd mcp-client
mvn clean package

# Run with OpenAI
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev

# Run with Groq Cloud
mvn spring-boot:run -Dspring-boot.run.profiles=groq-cloud,dev

# Frontend dev server (Vite)
cd src/main/frontend
npm run dev
# Access at http://localhost:5173 (proxies to backend at 8081)
```

---

## Cross-Module Communication

### Build Time

```text
codegen → provides utilities → entities
client → provides sources → entities
entities → provides entities → backend
client → provides API client → mcp-server
```

### Runtime

```text
mcp-client → HTTP (OAuth2) → mcp-server
mcp-server → HTTP (OAuth2) → backend
mcp-client → HTTP (OAuth2) → backend (for auth)
```

## Module Artifacts

### JAR Outputs

| Module     | Artifact                                        | Type       | Size (approx) |
| ---------- | ----------------------------------------------- | ---------- | ------------- |
| codegen    | spring-ai-resos-codegen-1.0.0-SNAPSHOT.jar      | Library    | 50KB          |
| client     | spring-ai-resos-client-1.0.0-SNAPSHOT.jar       | Library    | 500KB         |
| entities   | spring-ai-resos-entities-1.0.0-SNAPSHOT.jar     | Library    | 200KB         |
| backend    | spring-ai-resos-backend-1.0.0-SNAPSHOT.jar      | Executable | 80MB          |
| mcp-server | spring-ai-resos-mcp-server-1.0.0-SNAPSHOT.jar   | Executable | 70MB          |
| mcp-client | spring-ai-resos-mcp-frontend-1.0.0-SNAPSHOT.jar | Executable | 75MB          |

**Note**: Executable JARs include all dependencies (Spring Boot fat JAR)

## Related Documentation

- [Module Dependencies Diagram](diagrams/module-dependencies.md) - Visual dependency graph
- [Code Generation Pipeline](04-code-generation.md) - How modules transform code
- [Build Workflow](10-build-workflow.md) - Maven reactor and build commands
