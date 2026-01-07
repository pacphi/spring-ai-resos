# ADR-003: Dynamic Liquibase Changelog Generation

## Status
**Accepted** - Implemented in backend module

## Context

Database schema management typically follows one of several approaches:

1. **Manual SQL Scripts**: Write CREATE TABLE statements by hand
2. **JPA Schema Generation**: `spring.jpa.hibernate.ddl-auto=update`
3. **Manual Liquibase/Flyway**: Write changelogs manually
4. **Dynamic Generation**: Generate changelogs from entity definitions

### Problem Statement

This project has unique requirements:
- Entities are generated from OpenAPI specifications
- Want zero-boilerplate entity and schema definitions
- Need Liquibase for proper schema versioning and rollback
- Must support both H2 (dev) and PostgreSQL (prod)
- Need to handle JAR execution (no filesystem write access to classpath)
- Want single source of truth (entities define schema)

### Constraints

- Using Spring Data JDBC with `@Table`, `@Id`, `@Column` annotations
- Must work in both IDE/filesystem and JAR execution modes
- Liquibase must run after schema generation
- Need to respect entity dependencies (foreign keys require parent tables first)
- Cannot use JPA schema generation (not using JPA)

## Decision

**Generate Liquibase YAML changelogs dynamically at runtime from Spring Data JDBC entity annotations.**

### Implementation Details

1. **SchemaCreator Component**:
   - Spring `@Component` with `@PostConstruct`
   - Scans package `me.pacphi.ai.resos.jdbc` for `@Table` entities
   - Builds dependency graph from `AggregateReference` fields
   - Performs topological sort for correct table creation order
   - Generates Liquibase YAML changelogs

2. **Dependency Resolution**:
   ```java
   private Map<String, Set<String>> buildDependencyGraph(
           List<Class<?>> entities) {
       Map<String, Set<String>> graph = new HashMap<>();
       for (Class<?> entity : entities) {
           String tableName = getTableName(entity);
           Set<String> dependencies = new HashSet<>();

           // Find AggregateReference fields (foreign keys)
           for (Field field : entity.getDeclaredFields()) {
               if (isAggregateReference(field)) {
                   dependencies.add(getReferencedTableName(field));
               }
           }

           graph.put(tableName, dependencies);
       }
       return graph;
   }
   ```

3. **Type Mapping**:
   | Java Type | PostgreSQL | H2 |
   |-----------|-----------|-----|
   | UUID | uuid | uuid |
   | String | varchar(255) | varchar(255) |
   | OffsetDateTime | timestamp with time zone | timestamp |
   | BigDecimal | decimal(19,2) | decimal(19,2) |
   | Integer | integer | integer |
   | Boolean | boolean | boolean |
   | Enum | varchar(50) | varchar(50) |

4. **JAR Execution Handling**:
   ```java
   private Path getChangelogDirectory() {
       if (isRunningFromJar()) {
           // Use temp directory for JAR execution
           Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"),
                                     "liquibase", "changelogs");
           Files.createDirectories(tempDir);
           System.setProperty("liquibase.changelog.dir",
                             tempDir.toString());
           return tempDir;
       } else {
           // Use target/classes for IDE/filesystem execution
           return Paths.get("target/classes/db/changelog/generated");
       }
   }
   ```

5. **Liquibase Integration**:
   - `LiquibaseConfiguration`: Ensures `SpringLiquibase` bean depends on `SchemaCreator`
   - `LiquibaseCustomizer`: Redirects Liquibase to temp directory when running from JAR

## Consequences

### Positive

1. **Zero Manual Schema Writing**:
   - No duplicate table definitions
   - Entity annotations drive everything
   - Reduce boilerplate by ~1000 lines

2. **Single Source of Truth**:
   - Entities define both object model and database schema
   - Changes to entities automatically update schema
   - Impossible for entity/schema drift

3. **Proper Version Control**:
   - Liquibase changelogs still exist (generated files)
   - Can track schema evolution in git
   - Rollback support via Liquibase

4. **Dependency Awareness**:
   - Tables created in correct order (topological sort)
   - Foreign keys only added after parent tables exist
   - Circular dependency detection

5. **Database Portability**:
   - Same entity code works with H2, PostgreSQL
   - Type mapping handles database differences
   - Liquibase ensures consistent behavior

6. **JAR Execution Support**:
   - Works in containerized environments
   - Temp directory approach enables write access
   - System property communication with Liquibase

### Negative

1. **Runtime Complexity**:
   - Schema generation runs on every application startup
   - `@PostConstruct` adds ~200-300ms to startup time
   - More moving parts vs static changelogs

2. **Limited Flexibility**:
   - Can only generate what's expressible in entity annotations
   - Complex constraints require manual patches
   - Custom SQL (triggers, functions) need separate changelogs

3. **JAR Execution Special Handling**:
   - Temp directory approach adds complexity
   - Must detect JAR vs filesystem execution
   - System properties for communication feel hacky

4. **Debugging Difficulty**:
   - Generated changelogs not immediately visible
   - Must check temp directory to see actual SQL
   - Errors in generation can be hard to diagnose

5. **Build Tool Dependency**:
   - Tightly coupled to Spring Boot lifecycle
   - Cannot run Liquibase separately in CI/CD easily
   - May need separate migration container

### Neutral

1. **Generated File Management**:
   - Generated files can be .gitignored or committed
   - Trade-off: gitignore = cleaner repo, commit = visibility

2. **Performance at Scale**:
   - Fast enough for ~20 entities
   - Unknown behavior with 100+ entities
   - Likely fine but untested

## Alternatives Considered

### Alternative 1: Manual Liquibase Changelogs

**Approach**: Write `createTable` changelogs by hand.

**Pros**:
- Full control over schema
- Industry standard approach
- No runtime generation overhead
- Easy to debug (see the SQL)

**Cons**:
- Duplicate effort (entities + changelogs)
- Easy to drift (forget to update changelog)
- 2x maintenance burden
- No guarantee of entity/schema consistency

**Rejected Because**: Want zero-boilerplate, single source of truth for generated entities.

### Alternative 2: JPA Schema Generation

**Approach**: Use `spring.jpa.hibernate.ddl-auto=update`.

**Pros**:
- Automatic schema generation
- Widely used
- No Liquibase needed

**Cons**:
- Not using JPA (using Spring Data JDBC)
- No migration versioning
- No rollback capability
- Production anti-pattern (schema updates without control)
- Cannot track schema evolution in git

**Rejected Because**: Not using JPA, and want proper versioning with Liquibase.

### Alternative 3: Flyway with Manual Scripts

**Approach**: Use Flyway instead of Liquibase, write SQL migrations manually.

**Pros**:
- Simpler than Liquibase (just SQL)
- No XML/YAML overhead
- Easy to understand

**Cons**:
- Still requires manual SQL writing
- Duplicate entity/schema definitions
- No changelog generation from entities

**Rejected Because**: Same issue as manual Liquibase - want dynamic generation.

### Alternative 4: Build-Time Code Generation

**Approach**: Generate changelogs during Maven build, not at runtime.

**Pros**:
- No runtime overhead
- Generated files visible immediately
- Can commit generated changelogs to git

**Cons**:
- More complex Maven plugin setup
- Need entities compiled before schema generation
- May not work well with IDE hot reload

**Rejected Because**: Runtime generation simpler, and startup overhead acceptable.

## Implementation Notes

### Key Components

**SchemaCreator** (`backend/src/main/java/me/pacphi/ai/resos/config/SchemaCreator.java`):
```java
@Component
public class SchemaCreator {

    @PostConstruct
    public void generateSchemas() {
        List<Class<?>> entities = scanEntities();
        Map<String, Set<String>> depGraph = buildDependencyGraph(entities);
        List<String> sortedTables = topologicalSort(depGraph);

        for (String tableName : sortedTables) {
            generateChangelog(tableName);
        }

        updateMasterChangelog();
    }
}
```

**LiquibaseConfiguration**:
```java
@Configuration
public class LiquibaseConfiguration implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory) {

        // Ensure Liquibase depends on SchemaCreator
        BeanDefinition liquibase = beanFactory.getBeanDefinition("liquibase");
        liquibase.setDependsOn("schemaCreator");
    }
}
```

**LiquibaseCustomizer**:
```java
@Component
public class LiquibaseCustomizer implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(
            Object bean, String beanName) {

        if (bean instanceof SpringLiquibase && isRunningFromJar()) {
            SpringLiquibase liquibase = (SpringLiquibase) bean;

            // Redirect to file-based changelogs
            String changelogDir = System.getProperty("liquibase.changelog.dir");
            liquibase.setChangeLog("file:" + changelogDir + "/db.changelog-master.yml");
            liquibase.setResourceLoader(new FileSystemResourceLoader());
        }

        return bean;
    }
}
```

### Generated Changelog Example

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

**Generated Changelog** (`db/changelog/generated/customer.yaml`):
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

### Master Changelog

**db/changelog/db.changelog-master.yml**:
```yaml
databaseChangeLog:
  # Generated entity changelogs
  - include:
      file: db/changelog/generated/customer.yaml
  - include:
      file: db/changelog/generated/booking.yaml

  # Manual patches
  - include:
      file: db/changelog/patches/001_fix_oauth2_client_column_sizes.yml
```

## Performance Impact

### Startup Time

| Phase | Time | Notes |
|-------|------|-------|
| Entity scanning | 50ms | PathMatchingResourcePatternResolver |
| Dependency graph | 30ms | Topological sort |
| Changelog generation | 120ms | 15 entities × ~8ms each |
| Liquibase execution | 800ms | Database operations |
| **Total** | **~1000ms** | Acceptable for development |

### Memory Usage

- Entity metadata: ~1KB per entity
- Generated changelogs: ~2KB per entity
- Total overhead: ~45KB for 15 entities (negligible)

## Lessons Learned

1. **Bean Initialization Order Matters**: Use `@DependsOn` or `BeanFactoryPostProcessor`
2. **JAR Detection**: Check `getClass().getProtectionDomain().getCodeSource()`
3. **System Properties for Communication**: Simple way to pass info to Liquibase
4. **Topological Sort is Necessary**: Random order causes FK constraint violations
5. **Type Mapping is Tricky**: UUID handling differs between PostgreSQL and H2
6. **Manual Patches Still Needed**: Some schema features can't be auto-generated

## Future Enhancements

1. **Index Generation**: Auto-generate indexes from `@Indexed` annotations
2. **Constraint Generation**: Support CHECK constraints, UNIQUE combinations
3. **View Generation**: Generate database views from annotated methods
4. **Migration Testing**: Dry-run mode to preview schema changes
5. **Performance**: Cache entity metadata between restarts

## Related Decisions

- [ADR-001: OpenAPI-First](001-openapi-first.md) - Entities generated from OpenAPI
- [ADR-002: Spring Data JDBC](002-spring-data-jdbc.md) - Uses `@Table` annotations
- [ADR-004: WebMVC over WebFlux](004-webmvc-over-webflux.md) - Blocking I/O model

## References

- [Liquibase Documentation](https://docs.liquibase.com/)
- [Spring Data JDBC Reference](https://docs.spring.io/spring-data/jdbc/docs/current/reference/html/)
- [Topological Sorting](https://en.wikipedia.org/wiki/Topological_sorting)
- [Spring Bean Lifecycle](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)

## Decision Date

January 2026 (Initial Architecture)

## Reviewers

- Chris Phillipson (Architect)

## Changelog

| Date | Change |
|------|--------|
| Jan 2026 | Initial decision document |
