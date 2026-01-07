# Code Generation Pipeline

This diagram shows the complete code generation flow from OpenAPI specification to database schema.

```mermaid
flowchart TD
    START([Developer writes<br/>OpenAPI Spec])

    subgraph "Step 1: OpenAPI Specification"
        SPEC[resos-openapi-modified.yml<br/>📄 API Contract<br/>Models, Endpoints]
    end

    subgraph "Step 2: Client Generation"
        PLUGIN[OpenAPI Generator<br/>Maven Plugin<br/>Library: spring-http-interface]
        GEN_CLIENT[Generated Client Code<br/>DefaultApi.java<br/>Customer.java, Booking.java]
    end

    subgraph "Step 3: Source Unpacking"
        UNPACK[Maven Dependency Plugin<br/>Unpack client-sources.jar]
        UNPACKED[Unpacked POJOs<br/>entities/target/unpacked-sources/]
    end

    subgraph "Step 4: Entity Transformation"
        ENTITY_GEN[EntityGenerator<br/>JavaParser AST Transformation]
        TRANSFORM[Transform POJOs<br/>Jackson → Spring Data JDBC<br/>List → Set, Add AggregateReference]
        JDBC_ENTITIES[JDBC Entities<br/>CustomerEntity.java<br/>@Table, @Id, @Column]
    end

    subgraph "Step 5: Runtime Schema Generation"
        SCHEMA_CREATOR[SchemaCreator<br/>@PostConstruct<br/>Scans @Table entities]
        DEP_GRAPH[Build Dependency Graph<br/>Topological Sort<br/>FK Detection]
        GEN_CHANGELOG[Generate Liquibase YAML<br/>Type Mapping<br/>Constraint Creation]
        CHANGELOG_FILES[Liquibase Changelogs<br/>db/changelog/generated/]
    end

    subgraph "Step 6: Database Migration"
        LIQUIBASE[Liquibase Execution<br/>Apply Changelogs]
        DB[(Database<br/>PostgreSQL / H2<br/>Tables with Constraints)]
    end

    START --> SPEC
    SPEC -->|"mvn generate-sources"| PLUGIN
    PLUGIN --> GEN_CLIENT
    GEN_CLIENT -->|"mvn package"| UNPACK
    UNPACK --> UNPACKED
    UNPACKED -->|"mvn exec:java"| ENTITY_GEN
    ENTITY_GEN --> TRANSFORM
    TRANSFORM --> JDBC_ENTITIES
    JDBC_ENTITIES -->|"Application Startup"| SCHEMA_CREATOR
    SCHEMA_CREATOR --> DEP_GRAPH
    DEP_GRAPH --> GEN_CHANGELOG
    GEN_CHANGELOG --> CHANGELOG_FILES
    CHANGELOG_FILES --> LIQUIBASE
    LIQUIBASE --> DB

    style START fill:#e1f5ff,stroke:#0066cc,stroke-width:2px
    style SPEC fill:#fff4e6,stroke:#ff9900,stroke-width:2px
    style PLUGIN fill:#ffe6e6,stroke:#cc0000,stroke-width:2px
    style GEN_CLIENT fill:#e6f7e6,stroke:#009900,stroke-width:2px
    style ENTITY_GEN fill:#f0e6ff,stroke:#9900cc,stroke-width:2px
    style JDBC_ENTITIES fill:#e6f7e6,stroke:#009900,stroke-width:2px
    style SCHEMA_CREATOR fill:#ffe6e6,stroke:#cc0000,stroke-width:2px
    style DB fill:#e6f7e6,stroke:#009900,stroke-width:3px
```

## Pipeline Stages Explained

### Stage 1: OpenAPI Specification

**File**: `client/src/main/resources/openapi/resos-openapi-modified.yml`

The OpenAPI specification is the **single source of truth** for:

- API contract (endpoints, request/response schemas)
- Data models (Customer, Booking, Order, etc.)
- Validation rules
- Documentation

Example model definition:

```yaml
Customer:
  type: object
  required:
    - name
    - email
  properties:
    id:
      type: string
      format: uuid
    name:
      type: string
      minLength: 1
    email:
      type: string
      format: email
    phone:
      type: string
```

### Stage 2: Client Generation

**Tool**: OpenAPI Generator Maven Plugin
**Configuration**: `client/pom.xml`

Key configuration:

```xml
<configuration>
    <inputSpec>src/main/resources/openapi/resos-openapi-modified.yml</inputSpec>
    <generatorName>java</generatorName>
    <library>spring-http-interface</library>
    <generateApiTests>false</generateApiTests>
</configuration>
```

**Output**:

- `DefaultApi.java` - Type-safe HTTP client interface
- `Customer.java` - POJO with Jackson annotations
- `ApiClient.java` - RestClient configuration

### Stage 3: Source Unpacking

**Tool**: Maven Dependency Plugin

Unpacks client module sources to `entities/target/unpacked-sources/` for transformation.

**Configuration in `entities/pom.xml`**:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>unpack</goal>
            </goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>me.pacphi</groupId>
                        <artifactId>spring-ai-resos-client</artifactId>
                        <classifier>sources</classifier>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Excluded**: API and configuration classes (only models needed)

### Stage 4: Entity Transformation

**Tool**: EntityGenerator (JavaParser-based)
**Location**: `codegen/src/main/java/me/pacphi/ai/resos/codegen/EntityGenerator.java`

**Transformations Applied**:

1. **Annotation Replacement**:
   - Remove `@JsonProperty`, `@JsonCreator`
   - Add `@Table("table_name")`
   - Add `@Id` for primary key
   - Add `@Column("column_name")` for fields

2. **Type Transformations**:
   - `List<T>` → `Set<T>` for relationships
   - Add `AggregateReference<EntityType, UUID>` for foreign keys
   - Add `@MappedCollection` for one-to-many relationships

3. **Naming Conventions**:
   - Class: `Customer` → `CustomerEntity`
   - Table: `Customer` → `customer` (snake_case)
   - Columns: Camel case preserved

**Example Transformation**:

**Before (OpenAPI POJO)**:

```java
public class Customer {
    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    @NotNull
    private String name;

    @JsonProperty("email")
    private String email;
}
```

**After (JDBC Entity)**:

```java
@Table("customer")
public class CustomerEntity {
    @Id
    private UUID id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    public Customer toPojo() { /* conversion */ }
    public static CustomerEntity fromPojo(Customer pojo) { /* conversion */ }
}
```

### Stage 5: Runtime Schema Generation

**Component**: `SchemaCreator` (Spring `@PostConstruct`)
**Location**: `backend/src/main/java/me/pacphi/ai/resos/config/SchemaCreator.java`

**Algorithm**:

1. **Entity Scanning**:
   - Scan package `me.pacphi.ai.resos.jdbc`
   - Find all classes with `@Table` annotation
   - Extract metadata (table name, columns, foreign keys)

2. **Dependency Resolution**:
   - Build directed graph of entity dependencies
   - Detect `AggregateReference` fields (foreign keys)
   - Topological sort to determine creation order

3. **Changelog Generation**:
   - For each entity in dependency order:
     - Generate `createTable` changeset
     - Map Java types to SQL types
     - Add `addColumn` for foreign keys
   - Write YAML files to `db/changelog/generated/`

4. **JAR Execution Handling**:
   - Detect if running from JAR
   - Write changelogs to temp directory
   - Set system property for Liquibase

**Type Mapping**:

| Java Type      | SQL Type (PostgreSQL)    | SQL Type (H2) |
| -------------- | ------------------------ | ------------- |
| UUID           | uuid                     | uuid          |
| String         | varchar(255)             | varchar(255)  |
| OffsetDateTime | timestamp with time zone | timestamp     |
| BigDecimal     | decimal(19,2)            | decimal(19,2) |
| Integer        | integer                  | integer       |
| Boolean        | boolean                  | boolean       |
| Enum           | varchar(50)              | varchar(50)   |

### Stage 6: Database Migration

**Tool**: Liquibase 5.0.1

**Master Changelog**: `db/changelog/db.changelog-master.yml`

Includes:

- Generated entity changelogs
- Manual patches (OAuth2 column sizes)
- Seed data (if applicable)

**Execution**: Automatic on application startup

## Benefits of This Approach

1. **Single Source of Truth**: OpenAPI spec drives everything
2. **Zero Manual Schema Writing**: Database schema generated from code
3. **Type Safety**: Compile-time checks throughout pipeline
4. **Consistency**: No drift between API contract and database
5. **Maintainability**: Change OpenAPI spec, rebuild, schema updates

## Challenges & Solutions

| Challenge                              | Solution                               |
| -------------------------------------- | -------------------------------------- |
| JAR execution can't write to classpath | Temp directory with system property    |
| Circular dependencies in entities      | Topological sort with cycle detection  |
| Type mapping accuracy                  | Comprehensive mapping table            |
| Build order complexity                 | Maven reactor handles it automatically |

## Build Commands

```bash
# Full pipeline execution
mvn clean install

# Just client generation
cd client && mvn clean generate-sources

# Just entity transformation
cd entities && mvn clean compile exec:java

# Run backend with schema generation
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Critical Files

| File                                                              | Purpose                  |
| ----------------------------------------------------------------- | ------------------------ |
| `client/src/main/resources/openapi/resos-openapi-modified.yml`    | API specification        |
| `client/pom.xml`                                                  | OpenAPI Generator config |
| `codegen/src/main/java/.../EntityGenerator.java`                  | Transformation logic     |
| `entities/pom.xml`                                                | Build orchestration      |
| `backend/src/main/java/.../SchemaCreator.java`                    | Schema generation        |
| `backend/src/main/resources/db/changelog/db.changelog-master.yml` | Liquibase master         |
