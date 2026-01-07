# Code Generation Pipeline

The Spring AI ResOs project uses a sophisticated multi-stage code generation pipeline that eliminates boilerplate and ensures consistency from API specification to database schema.

## Pipeline Overview

See [Code Generation Pipeline Diagram](diagrams/code-generation-pipeline.md) for visual flow.

**Complete Pipeline**:

```text
OpenAPI Spec → OpenAPI Generator → HTTP Client (POJOs)
→ Maven Unpack → EntityGenerator (JavaParser) → JDBC Entities
→ Runtime SchemaCreator → Liquibase Changelogs → Database Schema
```

**Benefits**:

- Single source of truth (OpenAPI specification)
- Zero manual entity writing
- Zero manual schema writing
- Type safety throughout pipeline
- Compile-time verification

---

## Stage 1: OpenAPI Specification

### Source File

**File**: `client/src/main/resources/openapi/resos-openapi-modified.yml`

**Origin**: Modified from [ResOs API v1.2](https://documenter.getpostman.com/view/3308304/SzzehLGp?version=latest)

**Modifications**:

- Added security schemes for OAuth2
- Enhanced model descriptions
- Fixed validation constraints
- Added missing relationships

### Example Model Definition

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
      description: Unique customer identifier
    name:
      type: string
      minLength: 1
      maxLength: 255
      description: Customer full name
    email:
      type: string
      format: email
      description: Customer email address
    phone:
      type: string
      pattern: '^\+?[1-9]\d{1,14}$'
      description: Phone number in E.164 format
    createdAt:
      type: string
      format: date-time
      description: Account creation timestamp
    bookingCount:
      type: integer
      minimum: 0
      description: Total number of bookings made
    totalSpent:
      type: number
      format: double
      description: Total amount spent
    metadata:
      type: object
      additionalProperties: true
      description: Additional customer metadata
```

**Key Features**:

- Type specifications with formats
- Validation constraints (min, max, pattern)
- Descriptions for documentation
- Relationships indicated by object references

---

## Stage 2: Client Generation

### Tool

**OpenAPI Generator Maven Plugin** (v7.18.0)

### Configuration

**File**: `client/pom.xml`

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.18.0</version>
    <executions>
        <execution>
            <id>generate-client</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/openapi/resos-openapi-modified.yml</inputSpec>
                <generatorName>java</generatorName>
                <library>spring-http-interface</library>
                <apiPackage>me.pacphi.ai.resos.api</apiPackage>
                <modelPackage>me.pacphi.ai.resos.model</modelPackage>
                <configurationPackage>me.pacphi.ai.resos.configuration</configurationPackage>
                <generateApiTests>false</generateApiTests>
                <generateModelTests>false</generateModelTests>
                <configOptions>
                    <useJakartaEe>true</useJakartaEe>
                    <dateLibrary>java8</dateLibrary>
                    <serializationLibrary>jackson</serializationLibrary>
                    <useBeanValidation>true</useBeanValidation>
                    <performBeanValidation>true</performBeanValidation>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Key Parameters**:

- `library: spring-http-interface` - Uses Spring 6+ HTTP Interface (not OpenFeign or RestTemplate)
- `useJakartaEe: true` - Jakarta EE namespace (Spring Boot 3+ requirement)
- `dateLibrary: java8` - Use `OffsetDateTime`, `LocalDate` (not Date or Calendar)
- `useBeanValidation: true` - Add `@Valid`, `@NotNull` annotations

### Generated Output

**DefaultApi Interface**:

```java
package me.pacphi.ai.resos.api;

import me.pacphi.ai.resos.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.service.annotation.*;

import java.util.List;
import java.util.UUID;

public interface DefaultApi {

    /**
     * GET /customers : List customers
     *
     * @param limit Max number of records to return
     * @param skip Number of records to skip
     * @param sort Field to sort by
     * @param customQuery Custom filter query
     * @return List of customers
     */
    @GetExchange("/customers")
    ResponseEntity<List<Customer>> customersGet(
        @RequestParam(value = "limit", required = false) Integer limit,
        @RequestParam(value = "skip", required = false) Integer skip,
        @RequestParam(value = "sort", required = false) String sort,
        @RequestParam(value = "customQuery", required = false) String customQuery
    );

    @GetExchange("/customers/{id}")
    ResponseEntity<Customer> customersIdGet(@PathVariable("id") UUID id);

    @PostExchange("/customers")
    ResponseEntity<Customer> customersPost(@RequestBody Customer customer);

    // ... more methods
}
```

**Customer Model POJO**:

```java
package me.pacphi.ai.resos.model;

import tools.jackson.annotation.*;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;

@JsonPropertyOrder({
    "id", "name", "email", "phone", "createdAt",
    "bookingCount", "totalSpent", "metadata"
})
public class Customer {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    @JsonProperty("email")
    @NotNull
    @Email
    private String email;

    @JsonProperty("phone")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
    private String phone;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("bookingCount")
    @Min(0)
    private Integer bookingCount;

    @JsonProperty("totalSpent")
    private BigDecimal totalSpent;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    // Getters, setters, equals, hashCode, toString...
}
```

---

## Stage 3: Source Unpacking

### Tool

**Maven Dependency Plugin**

### Configuration

**File**: `entities/pom.xml`

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

**Process**:

1. Maven builds `client` module, creating `client-1.0.0-SNAPSHOT-sources.jar`
2. `entities` module unpacks only `model/` package
3. Excludes `api/` and `configuration/` (not needed for entities)
4. Output: `entities/target/unpacked-sources/me/pacphi/ai/resos/model/*.java`

### Why Source Unpacking?

**Problem**: Generated code is in `client/target/generated-sources/` but we need to transform it in `entities` module.

**Solutions Considered**:

1. Copy files manually (not automated)
2. Use Maven resources plugin (doesn't preserve package structure)
3. Depend on client JAR and use reflection (loses source code)
4. **Unpack sources JAR** ✅ (chosen approach)

**Benefits**:

- Preserves package structure
- Automated (no manual copying)
- Source code available for JavaParser
- Maven reactor handles it automatically

---

## Stage 4: Entity Transformation

### Tool

**EntityGenerator** (JavaParser-based AST transformation)

**Location**: `codegen/src/main/java/me/pacphi/ai/resos/util/EntityGenerator.java`

### Transformation Algorithm

**Input**: OpenAPI POJO (`Customer.java`)
**Output**: JDBC Entity (`CustomerEntity.java`)

**Steps**:

1. **Parse Source File**:

   ```java
   CompilationUnit cu = JavaParser.parse(new File(inputPath));
   ```

2. **Rename Class**:

   ```java
   className = className + "Entity";  // Customer → CustomerEntity
   ```

3. **Replace Annotations**:
   - Remove: `@JsonProperty`, `@JsonCreator`, `@JsonPropertyOrder`
   - Add: `@Table("customer")`, `@Id`, `@Column("field_name")`

4. **Transform Types**:
   - `List<T>` → `Set<T>` (Spring Data JDBC prefers Set)
   - Add `AggregateReference<TargetEntity, UUID>` for foreign keys

5. **Add Conversion Methods**:

   ```java
   public Customer toPojo() {
       return new Customer()
           .id(this.id)
           .name(this.name)
           .email(this.email);
   }

   public static CustomerEntity fromPojo(Customer pojo) {
       CustomerEntity entity = new CustomerEntity();
       entity.setId(pojo.getId());
       entity.setName(pojo.getName());
       entity.setEmail(pojo.getEmail());
       return entity;
   }
   ```

### Example Transformation

**Before (OpenAPI POJO)**:

```java
package me.pacphi.ai.resos.model;

import tools.jackson.annotation.*;
import jakarta.validation.constraints.*;

@JsonPropertyOrder({"id", "name", "email"})
public class Customer {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("name")
    @NotNull
    private String name;

    @JsonProperty("email")
    @Email
    private String email;

    // Constructors, getters, setters...
}
```

**After (JDBC Entity)**:

```java
package me.pacphi.ai.resos.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;

import java.util.UUID;

@Table("customer")
public class CustomerEntity {

    @Id
    @Column("id")
    private UUID id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    // Conversion methods
    public Customer toPojo() {
        Customer pojo = new Customer();
        pojo.setId(this.id);
        pojo.setName(this.name);
        pojo.setEmail(this.email);
        return pojo;
    }

    public static CustomerEntity fromPojo(Customer pojo) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(pojo.getId());
        entity.setName(pojo.getName());
        entity.setEmail(pojo.getEmail());
        return entity;
    }

    // Getters, setters...
}
```

### Build Integration

**entities/pom.xml**:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>generate-entities</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>me.pacphi.ai.resos.util.EntityGenerator</mainClass>
                <arguments>
                    <argument>${project.build.directory}/unpacked-sources</argument>
                    <argument>${project.build.directory}/generated-sources/jdbc</argument>
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

**Process**:

1. Run after source unpacking
2. Transform all files in `unpacked-sources/model/`
3. Output to `generated-sources/jdbc/`
4. Compiled automatically by Maven

---

## Stage 5: Runtime Schema Generation

### Component

**SchemaCreator** (`backend/src/main/java/me/pacphi/ai/resos/config/SchemaCreator.java`)

**Execution**: `@PostConstruct` (runs on application startup)
**Profile**: `dev`, `test` only (not in production)

### Algorithm

#### Step 1: Entity Scanning

```java
@PostConstruct
public void generateSchemas() throws Exception {
    // Scan for @Table entities
    ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    String pattern = "classpath*:me/pacphi/ai/resos/jdbc/**/*.class";
    Resource[] resources = resolver.getResources(pattern);

    List<Class<?>> entities = new ArrayList<>();
    MetadataReaderFactory factory = new CachingMetadataReaderFactory();

    for (Resource resource : resources) {
        MetadataReader reader = factory.getMetadataReader(resource);
        String className = reader.getClassMetadata().getClassName();
        Class<?> clazz = Class.forName(className);

        if (clazz.isAnnotationPresent(Table.class)) {
            entities.add(clazz);
        }
    }
}
```

**Output**: List of all `@Table` annotated classes

#### Step 2: Dependency Graph Construction

```java
private Map<String, Set<String>> buildDependencyGraph(List<Class<?>> entities) {
    Map<String, Set<String>> graph = new HashMap<>();

    for (Class<?> entity : entities) {
        Table tableAnnotation = entity.getAnnotation(Table.class);
        String tableName = tableAnnotation.value();
        Set<String> dependencies = new HashSet<>();

        // Find AggregateReference fields (foreign keys)
        for (Field field : entity.getDeclaredFields()) {
            if (field.getType().equals(AggregateReference.class)) {
                // Extract referenced table name from generic type
                Type genericType = field.getGenericType();
                Class<?> referencedEntity = extractEntityClass(genericType);
                String referencedTable = referencedEntity
                    .getAnnotation(Table.class).value();
                dependencies.add(referencedTable);
            }
        }

        graph.put(tableName, dependencies);
    }

    return graph;
}
```

**Output**: Directed graph of table dependencies

**Example**:

```json
{
  "customer": [],
  "booking": ["customer", "restaurant"],
  "order_01": ["booking"],
  "user_authority": ["app_user", "authority"]
}
```

#### Step 3: Topological Sort

```java
private List<String> topologicalSort(Map<String, Set<String>> graph) {
    List<String> sorted = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Set<String> visiting = new HashSet<>();

    for (String node : graph.keySet()) {
        if (!visited.contains(node)) {
            visit(node, graph, visited, visiting, sorted);
        }
    }

    return sorted;
}

private void visit(String node, Map<String, Set<String>> graph,
                   Set<String> visited, Set<String> visiting,
                   List<String> sorted) {
    if (visiting.contains(node)) {
        throw new IllegalStateException("Circular dependency detected: " + node);
    }

    if (!visited.contains(node)) {
        visiting.add(node);

        for (String dependency : graph.get(node)) {
            visit(dependency, graph, visited, visiting, sorted);
        }

        visiting.remove(node);
        visited.add(node);
        sorted.add(node);
    }
}
```

**Output**: Tables in dependency order

**Example**: `["authority", "app_user", "customer", "restaurant", "booking", "user_authority", "order_01"]`

**Purpose**: Ensures parent tables created before child tables with foreign keys

#### Step 4: Changeset Generation

For each entity in sorted order:

```java
private void generateChangelogForEntity(Class<?> entity, Path outputDir) {
    Table tableAnnotation = entity.getAnnotation(Table.class);
    String tableName = tableAnnotation.value();

    Map<String, Object> changeset = new LinkedHashMap<>();
    changeset.put("id", "create-table-" + tableName);
    changeset.put("author", "schema-creator");

    List<Map<String, Object>> changes = new ArrayList<>();

    // Create table change
    Map<String, Object> createTable = new LinkedHashMap<>();
    createTable.put("tableName", tableName);
    List<Map<String, Object>> columns = new ArrayList<>();

    // Process fields
    for (Field field : entity.getDeclaredFields()) {
        if (field.isAnnotationPresent(Id.class)) {
            columns.add(createIdColumn(field));
        } else if (!field.getType().equals(AggregateReference.class)) {
            columns.add(createColumn(field));
        }
    }

    createTable.put("columns", columns);
    changes.add(Map.of("createTable", createTable));

    // Add foreign key constraints
    for (Field field : entity.getDeclaredFields()) {
        if (field.getType().equals(AggregateReference.class)) {
            changes.add(createForeignKeyConstraint(field, tableName));
        }
    }

    changeset.put("changes", changes);

    // Write to YAML file
    writeYaml(changeset, outputDir.resolve(tableName + ".yaml"));
}
```

### Type Mapping

**Java → SQL Type Mapping**:

| Java Type            | PostgreSQL                 | H2              | Default Value                         |
| -------------------- | -------------------------- | --------------- | ------------------------------------- |
| `UUID`               | `uuid`                     | `uuid`          | `gen_random_uuid()` / `random_uuid()` |
| `String`             | `varchar(255)`             | `varchar(255)`  | -                                     |
| `OffsetDateTime`     | `timestamp with time zone` | `timestamp`     | -                                     |
| `LocalDate`          | `date`                     | `date`          | -                                     |
| `LocalTime`          | `time`                     | `time`          | -                                     |
| `BigDecimal`         | `decimal(19,2)`            | `decimal(19,2)` | -                                     |
| `Integer`            | `integer`                  | `integer`       | -                                     |
| `Long`               | `bigint`                   | `bigint`        | -                                     |
| `Boolean`            | `boolean`                  | `boolean`       | `false`                               |
| `Enum`               | `varchar(50)`              | `varchar(50)`   | -                                     |
| `Map<String,Object>` | `jsonb`                    | `jsonb`         | -                                     |

```java
private String mapJavaTypeToSqlType(Class<?> javaType) {
    if (javaType.equals(UUID.class)) {
        return "uuid";
    } else if (javaType.equals(String.class)) {
        return "varchar(255)";
    } else if (javaType.equals(OffsetDateTime.class)) {
        return isPostgres() ? "timestamp with time zone" : "timestamp";
    } else if (javaType.equals(BigDecimal.class)) {
        return "decimal(19,2)";
    } else if (javaType.equals(Integer.class)) {
        return "integer";
    } else if (javaType.equals(Boolean.class)) {
        return "boolean";
    } else if (javaType.isEnum()) {
        return "varchar(50)";
    } else if (Map.class.isAssignableFrom(javaType)) {
        return "jsonb";
    }
    // Default fallback
    return "varchar(255)";
}
```

### Generated Changeset Example

**Input Entity**:

```java
@Table("customer")
public class CustomerEntity {
    @Id
    private UUID id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;
}
```

**Generated Liquibase YAML** (`db/changelog/generated/customer.yaml`):

```yaml
databaseChangeLog:
  - changeSet:
      id: create-table-customer
      author: schema-creator
      changes:
        - createTable:
            tableName: customer
            columns:
              - column:
                  name: id
                  type: uuid
                  defaultValueComputed: gen_random_uuid()
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: email
                  type: varchar(255)
```

### JAR Execution Handling

**Challenge**: JARs cannot write to classpath resources.

**Solution**: Use temp directory

```java
private Path getChangelogDirectory() {
    if (isRunningFromJar()) {
        // JAR execution: use consistent temp directory
        Path tempDir = applicationTemp.getDir().toPath()
            .resolve("liquibase/changelogs");
        Files.createDirectories(tempDir);

        // Communicate to LiquibaseCustomizer
        System.setProperty(CHANGELOG_DIR_PROPERTY, tempDir.toString());

        // Copy patch files from classpath to temp
        copyPatchFilesToTemp(tempDir);

        return tempDir;
    } else {
        // IDE/filesystem: use target/classes
        return Paths.get("target/classes/db/changelog");
    }
}

private boolean isRunningFromJar() {
    String classPath = getClass().getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .getPath();
    return classPath.endsWith(".jar");
}
```

**LiquibaseCustomizer Integration**:

```java
@Component
public class LiquibaseCustomizer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase) {
            String changelogDir = System.getProperty(
                SchemaCreator.CHANGELOG_DIR_PROPERTY
            );

            if (changelogDir != null) {
                // JAR mode: redirect to file-based changelogs
                SpringLiquibase liquibase = (SpringLiquibase) bean;
                String masterPath = "file:" + changelogDir +
                    "/db.changelog-master.yml";
                liquibase.setChangeLog(masterPath);
                liquibase.setResourceLoader(new FileSystemResourceLoader());
            }
        }
        return bean;
    }
}
```

---

## Stage 6: Liquibase Execution

### Dynamic Master Changelog Generation

Unlike traditional Liquibase setups where the master changelog is a static file, this project **dynamically generates** the master changelog at application startup. The `SchemaCreator` component scans `@Table`-annotated entities and produces:

1. **Individual entity changelogs** in `db/changelog/generated/` (one YAML file per entity)
2. **The master changelog** (`db.changelog-master.yml`) that orchestrates all includes

**Generation Flow**:

```text
Application Startup
       ↓
SchemaCreator.@PostConstruct
       ↓
┌──────────────────────────────────────────┐
│ 1. Scan me.pacphi.ai.resos.jdbc package  │
│ 2. Find all @Table-annotated entities    │
│ 3. Build dependency graph (FK ordering)  │
│ 4. Generate entity changelogs            │
│ 5. Copy static patches from classpath    │
│ 6. Generate master changelog             │
└──────────────────────────────────────────┘
       ↓
SpringLiquibase executes migrations
```

### Generated Master Changelog Structure

The dynamically generated `db.changelog-master.yml` includes changelogs in this order:

```yaml
databaseChangeLog:
  # Generated entity changelogs (sorted for FK dependency order)
  - include:
      file: generated/20250101_000000_authority.yml
      relativeToChangelogFile: true
  - include:
      file: generated/20250101_000001_app_user.yml
      relativeToChangelogFile: true
  - include:
      file: generated/20250101_000002_customer.yml
      relativeToChangelogFile: true
  # ... more entities

  # Static patch files (from classpath resources)
  - include:
      file: patches/001_fix_oauth2_client_column_sizes.yml
      relativeToChangelogFile: true
  - include:
      file: patches/002_fix_oauth2_authorization_column_sizes.yml
      relativeToChangelogFile: true
```

### Static Patch Files

While entity changelogs are generated dynamically, **patch files are static** and stored in the classpath:

**Location**: `backend/src/main/resources/db/changelog/patches/`

Patches are used for schema modifications that cannot be expressed through entity annotations:

- Column size adjustments (e.g., OAuth2 token storage requires larger VARCHAR)
- Custom indexes or constraints
- Data migrations
- Schema fixes discovered post-deployment

**Adding a New Patch**:

1. Create a YAML file in `backend/src/main/resources/db/changelog/patches/`
2. Use numeric prefix for ordering (e.g., `003_add_custom_index.yml`)
3. The patch will automatically be included after all generated changelogs

### Execution Mode Handling

The system handles two execution contexts differently:

| Mode               | Changelog Location                                             | Detection                              |
| ------------------ | -------------------------------------------------------------- | -------------------------------------- |
| **IDE/Filesystem** | `target/classes/db/changelog/`                                 | Classpath resource protocol is `file:` |
| **JAR**            | `ApplicationTemp.getDir("liquibase-changelogs")/db/changelog/` | Classpath resource protocol is `jar:`  |

**JAR Mode Coordination**:

```java
// SchemaCreator sets system property for temp directory
System.setProperty("liquibase.changelog.dir", tempPath.toString());

// LiquibaseCustomizer redirects Liquibase to read from temp directory
liquibase.setChangeLog("file:" + changelogFile.toString());
liquibase.setResourceLoader(new FileSystemResourceLoader());
```

### Liquibase Configuration Classes

Three classes coordinate changelog generation and Liquibase execution:

| Class                      | Role                                                                        |
| -------------------------- | --------------------------------------------------------------------------- |
| **SchemaCreator**          | Generates changelogs from entities, copies patches, writes master changelog |
| **LiquibaseConfiguration** | Ensures `SchemaCreator` runs before `SpringLiquibase` via `@DependsOn`      |
| **LiquibaseCustomizer**    | Redirects Liquibase to temp directory when running from JAR                 |

**LiquibaseConfiguration** (`backend/src/main/java/me/pacphi/ai/resos/config/LiquibaseConfiguration.java`):

```java
@Configuration
@Profile({"dev", "test"})
public class LiquibaseConfiguration implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory) throws BeansException {

        // Ensure Liquibase bean depends on SchemaCreator
        if (beanFactory.containsBeanDefinition("liquibase")) {
            BeanDefinition liquibaseDef = beanFactory.getBeanDefinition("liquibase");
            liquibaseDef.setDependsOn("schemaCreator");
        }
    }
}
```

**Purpose**: Ensures `SchemaCreator.generateSchemas()` runs before `SpringLiquibase.afterPropertiesSet()`

### Database Creation

Liquibase executes in this order:

1. **Check changelog lock**: Ensure no concurrent migrations
2. **Read changelogs**: From generated directory
3. **Compare with DATABASECHANGELOG**: Check what's already applied
4. **Execute new changesets**: Run CREATE TABLE, ADD CONSTRAINT statements
5. **Record in DATABASECHANGELOG**: Track applied migrations

**Example SQL Executed**:

```sql
-- From generated changelog
CREATE TABLE customer (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    name varchar(255) NOT NULL,
    email varchar(255),
    phone varchar(255),
    created_at timestamp with time zone,
    booking_count integer DEFAULT 0,
    total_spent decimal(19,2),
    metadata jsonb
);

-- Foreign key (if applicable)
ALTER TABLE booking
ADD CONSTRAINT fk_booking_customer
FOREIGN KEY (guest_id) REFERENCES customer(id);
```

---

## Complete Build Flow

### Maven Reactor Execution

```bash
mvn clean install
```

**Execution Order**:

1. **codegen** (50ms):
   - Compile `EntityGenerator.java`
   - Package as JAR

2. **client** (5s):
   - Run OpenAPI Generator (`generate-sources` phase)
   - Generate `DefaultApi` and model POJOs
   - Compile generated code
   - Package JAR with sources classifier

3. **entities** (3s):
   - Unpack client sources (`generate-sources` phase)
   - Run `EntityGenerator` main class (`generate-sources` phase)
   - Transform POJOs to JDBC entities
   - Compile entities
   - Package JAR

4. **backend** (10s):
   - Compile backend code (uses entities JAR)
   - Run SchemaCreator on startup (first time only)
   - Generate Liquibase changelogs
   - Package executable JAR

5. **mcp-server** (8s):
   - Compile (uses client JAR)
   - Package executable JAR

6. **mcp-client** (30s):
   - Install Node.js and npm
   - Run `npm install`
   - Run `npm run build` (Vite)
   - Copy React build to `src/main/resources/static/`
   - Compile Java code
   - Package executable JAR

**Total**: ~56 seconds (first build), ~20 seconds (incremental)

### Incremental Builds

Maven reactor only rebuilds changed modules:

```bash
# Only client changed
mvn clean install
# Rebuilds: client, entities (depends on client), any dependent modules

# Only backend Java code changed
cd backend && mvn clean package
# Rebuilds: backend only (entities JAR reused)

# Only React changed
cd mcp-client/src/main/frontend && npm run build
# Rebuilds: frontend only (Spring Boot JAR reused)
```

---

## Benefits of This Approach

### 1. Single Source of Truth

**OpenAPI Spec** defines:

- API endpoints
- Request/response models
- Validation rules
- Documentation

**Everything else derived**:

- HTTP client (Spring HTTP Interface)
- JDBC entities (JavaParser transformation)
- Database schema (Liquibase from entities)

**Benefit**: Change OpenAPI spec → rebuild → everything updates consistently

### 2. Zero Boilerplate

**Without Code Generation**:

- Manually write API client methods (~500 lines)
- Manually write JDBC entities (~800 lines)
- Manually write Liquibase changelogs (~1200 lines)
- **Total**: ~2500 lines of repetitive code

**With Code Generation**:

- Write OpenAPI spec (~400 lines YAML)
- Write EntityGenerator (~300 lines, reusable)
- Write SchemaCreator (~400 lines, reusable)
- **Total**: ~1100 lines, mostly configuration

**Savings**: ~60% less code, 100% less boilerplate

### 3. Type Safety

Compile-time errors for:

- API contract violations (wrong endpoint, parameters)
- Entity field mismatches
- Database schema inconsistencies

### 4. Maintainability

**Adding a new entity**:

1. Add model to OpenAPI spec (~20 lines YAML)
2. Rebuild project
3. Schema automatically created
4. API client automatically updated
5. Controller can use new entity

**Time**: 5 minutes vs hours of manual coding

---

## Challenges & Solutions

| Challenge                     | Solution                                                |
| ----------------------------- | ------------------------------------------------------- |
| JAR cannot write to classpath | Use ApplicationTemp directory                           |
| Circular dependencies         | Topological sort with cycle detection                   |
| Type mapping accuracy         | Comprehensive mapping table, database detection         |
| Build order                   | Maven reactor handles automatically                     |
| Generated code in git?        | .gitignore `target/`, commit OpenAPI spec only          |
| Schema changes in production  | Use manual Liquibase changelogs (disable SchemaCreator) |

---

## Critical Files

| File                                                                          | Purpose                  | Module   |
| ----------------------------------------------------------------------------- | ------------------------ | -------- |
| `client/src/main/resources/openapi/resos-openapi-modified.yml`                | API specification        | client   |
| `client/pom.xml`                                                              | OpenAPI Generator config | client   |
| `codegen/src/main/java/me/pacphi/ai/resos/util/EntityGenerator.java`          | Entity transformation    | codegen  |
| `entities/pom.xml`                                                            | Build orchestration      | entities |
| `backend/src/main/java/me/pacphi/ai/resos/config/SchemaCreator.java`          | Schema generation        | backend  |
| `backend/src/main/java/me/pacphi/ai/resos/config/LiquibaseConfiguration.java` | Bean ordering            | backend  |
| `backend/src/main/java/me/pacphi/ai/resos/config/LiquibaseCustomizer.java`    | JAR execution support    | backend  |

## Related Documentation

- [Code Generation Pipeline Diagram](diagrams/code-generation-pipeline.md) - Visual flowchart
- [Module Architecture](03-module-architecture.md) - Module details
- [Data Architecture](05-data-architecture.md) - Entity and schema details
- [ADR-001: OpenAPI-First](adr/001-openapi-first.md) - Why this approach
- [ADR-003: Dynamic Liquibase](adr/003-dynamic-liquibase.md) - Schema generation rationale
