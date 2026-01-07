# Build Workflow

This document details the Maven multi-module build process, dependency management, profiles, and build commands.

## Maven Multi-Module Structure

### Parent POM

**File**: `/pom.xml`
**Artifact**: `spring-ai-resos-parent`
**Packaging**: `pom`

**Parent Inheritance**:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.1</version>
</parent>
```

**Modules**:
```xml
<modules>
    <module>client</module>
    <module>codegen</module>
    <module>entities</module>
    <module>mcp-server</module>
    <module>mcp-client</module>
    <module>backend</module>
</modules>
```

### Maven Reactor Build Order

**Determined by**:
1. Module dependencies (explicit `<dependency>`)
2. Dependency plugin references (e.g., unpacking)
3. Exec plugin dependencies

**Actual Order**:
```
[INFO] Reactor Build Order:
[INFO]
[INFO] spring-ai-resos-codegen
[INFO] spring-ai-resos-client
[INFO] spring-ai-resos-entities
[INFO] spring-ai-resos-backend
[INFO] spring-ai-resos-mcp-server
[INFO] spring-ai-resos-mcp-frontend
```

**Why This Order?**:
1. **codegen**: No dependencies
2. **client**: No dependencies (but depends on codegen for exec plugin)
3. **entities**: Depends on client (source unpacking) and codegen (transformation)
4. **backend, mcp-server, mcp-client**: Can build in parallel

---

## Dependency Management

### Bill of Materials (BOMs)

**Centralized Version Management**:

```xml
<dependencyManagement>
    <dependencies>
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

        <!-- Micrometer BOM -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-bom</artifactId>
            <version>1.16.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Benefits**:
- Consistent versions across modules
- No version conflicts
- Easier upgrades (change BOM version only)
- Spring Boot parent provides base BOM

### Version Properties

**Centralized in Parent POM**:

```xml
<properties>
    <!-- Platform -->
    <java.version>25</java.version>
    <maven.compiler.source>${java.version}</maven.compiler.source>
    <maven.compiler.target>${java.version}</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <!-- Spring AI -->
    <spring-ai.version>2.0.0-M1</spring-ai.version>

    <!-- Libraries -->
    <jackson-databind-nullable.version>0.2.8</jackson-databind-nullable.version>
    <spring-doc.version>3.0.1</spring-doc.version>
    <liquibase.version>5.0.1</liquibase.version>
    <commons-io.version>2.19.0</commons-io.version>
    <junit-jupiter.version>6.0.0</junit-jupiter.version>

    <!-- Frontend -->
    <node.version>v23.4.0</node.version>
    <npm.version>10.9.2</npm.version>

    <!-- Testing -->
    <assertj.version>3.27.6</assertj.version>
</properties>
```

**Reference in Child POMs**:
```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
    <version>${liquibase.version}</version>
</dependency>
```

---

## Common Build Plugins

### Spring Boot Maven Plugin

**Applied to executable modules**: backend, mcp-server, mcp-client

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>4.0.1</version>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <mainClass>me.pacphi.ai.resos.SpringAiResOsBackendApplication</mainClass>
    </configuration>
</plugin>
```

**What It Does**:
- Creates executable JAR (fat JAR with all dependencies)
- Sets Main-Class in MANIFEST.MF
- Preserves original JAR as `.jar.original`

### Git Commit ID Plugin

**Applied to all modules**:

```xml
<plugin>
    <groupId>io.github.git-commit-id</groupId>
    <artifactId>git-commit-id-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>get-the-git-infos</id>
            <goals>
                <goal>revision</goal>
            </goals>
            <phase>initialize</phase>
        </execution>
    </executions>
    <configuration>
        <generateGitPropertiesFile>true</generateGitPropertiesFile>
        <generateGitPropertiesFilename>
            ${project.build.outputDirectory}/git.properties
        </generateGitPropertiesFilename>
        <commitIdGenerationMode>full</commitIdGenerationMode>
    </configuration>
</plugin>
```

**Output**: `git.properties` file with:
- Commit ID
- Branch name
- Build time
- Commit message
- Dirty flag

**Usage**: Visible in Spring Boot banner and Actuator `/info` endpoint

### Spotless (Code Formatting)

**Applied to all modules**:

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <java>
            <excludes>
                <exclude>**/_*.java</exclude>  <!-- Ignore generated files -->
            </excludes>
            <googleJavaFormat>
                <version>1.19.1</version>
                <style>AOSP</style>  <!-- Android Open Source Project style -->
                <reflowLongStrings>true</reflowLongStrings>
                <formatJavadoc>false</formatJavadoc>
            </googleJavaFormat>
            <removeUnusedImports/>
        </java>
    </configuration>
</plugin>
```

**Commands**:
```bash
# Check formatting
mvn spotless:check

# Apply formatting
mvn spotless:apply
```

**Style**: AOSP (4-space indentation, 100-char line width)

### CycloneDX (SBOM Generation)

**Applied to all modules**:

```xml
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>2.9.2</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>makeAggregateBom</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**Output**: `bom.xml` (Software Bill of Materials)

**Purpose**:
- Security scanning (Snyk, OWASP Dependency-Check)
- License compliance
- Vulnerability tracking

### Spring Banner Plugin

**Applied to all modules**:

```xml
<plugin>
    <groupId>ch.acanda.maven</groupId>
    <artifactId>spring-banner-plugin</artifactId>
    <version>1.6.0</version>
    <executions>
        <execution>
            <id>generate-spring-banner</id>
            <phase>generate-resources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <info>Commit: ${git.commit.id.abbrev}, Version: ${project.version}, Active Profiles: ${spring.profiles.active:default}</info>
    </configuration>
</plugin>
```

**Output**: Custom Spring Boot banner with git info

---

## Build Profiles

### Development Profiles

**dev** (H2, OAuth2 seeding, CSV seeding):
```yaml
# backend/src/main/resources/application-dev.yml
spring:
  profiles:
    active: dev

  datasource:
    url: jdbc:h2:mem:resos-backend
    driver-class-name: org.h2.Driver

  h2:
    console:
      enabled: true

  liquibase:
    enabled: true

app:
  seed:
    csv:
      enabled: true
```

**postgres** (PostgreSQL database):
```yaml
# backend/src/main/resources/application-postgres.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/resos
    driver-class-name: org.postgresql.Driver
    username: resos
    password: ${POSTGRES_PASSWORD:resos_password}
```

**test** (Test data seeding):
```yaml
# backend/src/main/resources/application-test.yml
app:
  seed:
    csv:
      enabled: true
      files:
        - test-authorities.csv
        - test-users.csv
```

### LLM Provider Profiles

**openai**:
```yaml
# mcp-client/src/main/resources/application-openai.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
```

**groq-cloud**:
```yaml
# mcp-client/src/main/resources/application-groq-cloud.yml
spring:
  ai:
    openai:
      api-key: ${GROQ_API_KEY}
      base-url: https://api.groq.com/openai
      chat:
        options:
          model: llama-3.3-70b-versatile
```

**ollama**:
```yaml
# mcp-client/src/main/resources/application-ollama.yml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: mistral
```

### Profile Activation

```bash
# Single profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Multiple profiles (comma-separated)
mvn spring-boot:run -Dspring-boot.run.profiles=openai,dev

# Via environment variable
export SPRING_PROFILES_ACTIVE=postgres,prod
mvn spring-boot:run
```

---

## Build Commands

### Full Multi-Module Build

```bash
# Clean and build all modules
mvn clean install

# Skip tests (faster)
mvn clean install -DskipTests

# Offline mode (use cached dependencies)
mvn clean install -o

# Parallel build (use N threads)
mvn clean install -T 1C  # 1 thread per CPU core
mvn clean install -T 4   # 4 threads
```

**Output**: JAR files in each module's `target/` directory

### Module-Specific Builds

```bash
# Build only backend (dependencies built automatically)
cd backend
mvn clean package

# Build only frontend
cd mcp-client
mvn clean package

# Build from root, specific module
mvn clean install -pl mcp-server -am
# -pl: project list (mcp-server)
# -am: also make (build dependencies)
```

### Incremental Builds

```bash
# Build only changed modules
mvn clean install -amd
# -amd: also make dependents (rebuild modules that depend on changed modules)

# Resume from specific module
mvn clean install -rf :spring-ai-resos-backend
# -rf: resume from (skip earlier modules)
```

---

## Build Phases

### Standard Maven Lifecycle

| Phase | Purpose | Key Actions |
|-------|---------|-------------|
| **validate** | Validate project structure | POM validation |
| **initialize** | Initialize build state | Git commit ID generation |
| **generate-sources** | Generate source code | OpenAPI client, entity transformation |
| **process-sources** | Process source files | Copy resources |
| **generate-resources** | Generate resources | Spring banner, frontend build |
| **process-resources** | Copy resources | application.yml to target/ |
| **compile** | Compile source code | javac |
| **process-classes** | Post-process classes | Bytecode enhancement (if any) |
| **generate-test-sources** | Generate test sources | (none) |
| **process-test-sources** | Process test sources | (none) |
| **test-compile** | Compile tests | Test classes |
| **test** | Run tests | JUnit, integration tests |
| **package** | Create JAR/WAR | Executable JAR creation |
| **verify** | Verify package | Integration tests |
| **install** | Install to local repo | ~/.m2/repository |
| **deploy** | Deploy to remote repo | Maven Central, GitHub Packages |

### Custom Phases

**client module**:
- **generate-sources**: OpenAPI Generator creates client code

**entities module**:
- **generate-sources**: Unpack client sources, run EntityGenerator

**mcp-client module**:
- **generate-resources**: Install Node.js, npm install, npm build
- **process-resources**: Copy React build to static/

---

## Build Performance

### Timing Breakdown

**First Build** (no cache):
| Module | Phase | Time |
|--------|-------|------|
| codegen | compile | 5s |
| client | generate-sources | 15s (OpenAPI Generator) |
| client | compile | 8s |
| entities | generate-sources | 5s (unpack + transform) |
| entities | compile | 3s |
| backend | compile | 12s |
| backend | test | 15s (integration tests) |
| mcp-server | compile | 10s |
| mcp-client | generate-resources | 35s (Node + npm + Vite) |
| mcp-client | compile | 8s |
| **Total** | | **~116s** (1m 56s) |

**Incremental Build** (cache warm):
| Scenario | Time |
|----------|------|
| No changes | 8s (validation only) |
| Backend Java change | 20s (backend only) |
| React change | 40s (frontend build only) |
| OpenAPI spec change | 90s (client, entities, dependents) |

### Optimization Strategies

**1. Parallel Build**:
```bash
mvn clean install -T 1C  # 1 thread per core
# 4-core machine: ~70s (vs 116s sequential)
```

**2. Skip Tests**:
```bash
mvn clean install -DskipTests
# Saves ~30s
```

**3. Offline Mode**:
```bash
mvn clean install -o
# Skips dependency updates, saves ~5s
```

**4. Module-Specific**:
```bash
cd backend && mvn package
# Only builds backend (~20s vs 116s full build)
```

**5. Frontend Cache**:
```bash
# Keep node_modules/ in src/main/frontend/
# Maven won't delete it, npm install reuses cache
```

---

## Dependency Resolution

### Repository Priority

1. **Local Repository**: `~/.m2/repository/`
2. **Spring Milestones**: `https://repo.spring.io/milestone` (for Spring AI)
3. **Maven Central**: `https://repo1.maven.org/maven2/`

**Configuration**:
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

**Why Spring Milestones?**: Spring AI 2.0.0-M1 is not in Maven Central

### Dependency Tree

```bash
# View complete dependency tree
mvn dependency:tree

# View tree for specific module
cd backend && mvn dependency:tree

# Find conflicts
mvn dependency:tree -Dverbose -Dincludes=tools.jackson.core:jackson-databind
```

**Example Output**:
```
[INFO] me.pacphi:spring-ai-resos-backend:jar:1.0.0-SNAPSHOT
[INFO] +- org.springframework.boot:spring-boot-starter-web:jar:4.0.1:compile
[INFO] |  +- org.springframework:spring-web:jar:7.0.2:compile
[INFO] |  +- org.springframework:spring-webmvc:jar:7.0.2:compile
[INFO] +- me.pacphi:spring-ai-resos-entities:jar:1.0.0-SNAPSHOT:compile
[INFO] |  +- org.springframework.data:spring-data-jdbc:jar:4.0.0:compile
[INFO] +- tools.jackson.core:jackson-databind:jar:3.0.3:compile
```

### Dependency Analysis

```bash
# Check for unused dependencies
mvn dependency:analyze

# Check for dependency updates
mvn versions:display-dependency-updates

# Check for plugin updates
mvn versions:display-plugin-updates
```

---

## Environment Variables

### Backend

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profiles |
| `SPRING_DATASOURCE_URL` | H2 in-memory | Database connection |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | (empty) | Database password |
| `APP_SECURITY_ISSUER_URI` | `http://localhost:8080` | OAuth2 issuer |
| `CSV_BASE_PATH` | `./seed-data` | Seed data location |

### MCP Server

| Variable | Default | Purpose |
|----------|---------|---------|
| `RESOS_API_ENDPOINT` | `https://api.resos.com/v1` | Backend API URL |
| `AUTH_SERVER_URL` | `http://localhost:8080` | OAuth2 auth server |
| `MCP_SERVER_SECRET` | `mcp-server-secret` | OAuth2 client secret |

### MCP Client

| Variable | Default | Purpose |
|----------|---------|---------|
| `OPENAI_API_KEY` | (required) | OpenAI API key |
| `GROQ_API_KEY` | (required) | Groq Cloud API key |
| `OPENROUTER_API_KEY` | (required) | OpenRouter API key |
| `MCP_SERVER_URL` | `http://localhost:8082` | MCP server URL |
| `AUTH_SERVER_URL` | `http://localhost:8080` | OAuth2 auth server |
| `MCP_CLIENT_SECRET` | `mcp-client-secret` | OAuth2 client secret |

---

## Build Artifacts

### JAR Structure

**Executable JAR** (backend example):
```
spring-ai-resos-backend-1.0.0-SNAPSHOT.jar
├── BOOT-INF/
│   ├── classes/                  # Compiled application classes
│   │   ├── me/pacphi/ai/resos/  # Application code
│   │   ├── db/changelog/         # Liquibase changelogs
│   │   ├── static/               # Static resources
│   │   ├── templates/            # Thymeleaf templates
│   │   ├── application.yml       # Configuration
│   │   ├── git.properties        # Git commit info
│   │   └── banner.txt            # Custom banner
│   └── lib/                      # All dependencies
│       ├── spring-boot-*.jar
│       ├── spring-security-*.jar
│       ├── jackson-*.jar
│       └── ... (100+ JARs)
├── META-INF/
│   ├── MANIFEST.MF               # Main-Class, Class-Path
│   └── maven/                    # POM and properties
└── org/springframework/boot/loader/  # Spring Boot loader classes
```

**Size**: ~80MB (includes all dependencies)

**Execution**:
```bash
java -jar spring-ai-resos-backend-1.0.0-SNAPSHOT.jar
```

### Library JAR

**client module**:
```
spring-ai-resos-client-1.0.0-SNAPSHOT.jar
├── me/pacphi/ai/resos/
│   ├── api/                      # DefaultApi interface
│   ├── model/                    # Customer, Booking, etc.
│   └── configuration/            # ApiClient
├── META-INF/
│   └── MANIFEST.MF
```

**Size**: ~500KB (no dependencies)

**Usage**: Consumed by mcp-server module

---

## CI/CD Integration

### GitHub Actions Workflow

**File**: `.github/workflows/ci.yml` (updated for Spring Boot 4)

```yaml
name: Java CI with Maven

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 25
      uses: actions/setup-java@v4
      with:
        java-version: '25'
        distribution: 'temurin'
        cache: 'maven'

    - name: Build with Maven
      run: mvn clean install -B -DskipTests

    - name: Run tests
      run: mvn test -B

    - name: Upload coverage
      uses: codecov/codecov-action@v4
      with:
        files: ./target/site/jacoco/jacoco.xml
```

**Key Changes for Spring Boot 4**:
- JDK 25 required
- Maven 3.9.11+ recommended
- Cache Maven dependencies for faster builds

---

## Build Troubleshooting

### Common Issues

**Issue**: `NoClassDefFoundError: tools/jackson/databind/ObjectMapper`

**Cause**: Jackson 2.x vs 3.x conflict

**Solution**:
```bash
# Check for Jackson 2.x dependencies
mvn dependency:tree | grep fasterxml

# Exclude from transitive dependencies
<dependency>
    <groupId>some-library</groupId>
    <artifactId>artifact</artifactId>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Issue**: OpenAPI Generator fails

**Cause**: Invalid OpenAPI specification

**Solution**:
```bash
# Validate OpenAPI spec
npx @redocly/cli lint client/src/main/resources/openapi/resos-openapi-modified.yml

# Check generator output
cat client/target/openapi-generator-maven-plugin.log
```

**Issue**: Frontend build fails

**Cause**: Node.js version mismatch

**Solution**:
```xml
<!-- Update node version in parent POM -->
<node.version>v23.4.0</node.version>

<!-- Or delete node installation -->
rm -rf mcp-client/src/main/frontend/node/
mvn clean package  # Re-installs correct version
```

---

## Build Best Practices

### 1. Clean Before Major Changes

```bash
mvn clean  # Remove all target/ directories
```

**When**:
- After switching branches
- Before building from scratch
- After dependency changes
- When weird errors occur

### 2. Install to Local Repository

```bash
mvn clean install  # Not just 'package'
```

**Why**: Other modules need artifacts in `~/.m2/repository/`

### 3. Use Wrapper (Future)

```bash
# Maven Wrapper (not currently in project)
./mvnw clean install
```

**Benefits**: Consistent Maven version across developers

### 4. Verify Build Reproducibility

```bash
# First build
mvn clean install

# Second build (should be identical)
mvn clean install

# Compare JARs
sha256sum backend/target/*.jar
```

### 5. Check for Dependency Conflicts

```bash
mvn dependency:analyze
mvn dependency:tree
mvn versions:display-dependency-updates
```

---

## IDE Integration

### IntelliJ IDEA

**Import Project**:
1. File → Open → Select `pom.xml`
2. Choose "Open as Project"
3. Wait for Maven import
4. Mark `src/main/java` as Sources Root
5. Mark `target/generated-sources/` as Generated Sources Root

**Run Configurations**:
- **Backend**: Main class `SpringAiResOsBackendApplication`, VM options `-Dspring.profiles.active=dev`
- **MCP Server**: Main class `SpringAiResOsMcpServerApplication`, Env vars `RESOS_API_ENDPOINT=http://localhost:8080/api/v1/resos`
- **MCP Client**: Main class `SpringAiResOsFrontendApplication`, VM options `-Dspring.profiles.active=openai,dev`

### VS Code

**Extensions**:
- Java Extension Pack
- Spring Boot Extension Pack
- Maven for Java

**settings.json**:
```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "maven.executable.preferMavenWrapper": false
}
```

---

## Build Output

### Generated Artifacts

After `mvn clean install`:

```
~/.m2/repository/me/pacphi/
├── spring-ai-resos-codegen/
│   └── 1.0.0-SNAPSHOT/
│       ├── spring-ai-resos-codegen-1.0.0-SNAPSHOT.jar
│       └── spring-ai-resos-codegen-1.0.0-SNAPSHOT-sources.jar
├── spring-ai-resos-client/
│   └── 1.0.0-SNAPSHOT/
│       ├── spring-ai-resos-client-1.0.0-SNAPSHOT.jar
│       └── spring-ai-resos-client-1.0.0-SNAPSHOT-sources.jar
├── spring-ai-resos-entities/
│   └── 1.0.0-SNAPSHOT/
│       └── spring-ai-resos-entities-1.0.0-SNAPSHOT.jar
├── spring-ai-resos-backend/
│   └── 1.0.0-SNAPSHOT/
│       ├── spring-ai-resos-backend-1.0.0-SNAPSHOT.jar (executable)
│       └── spring-ai-resos-backend-1.0.0-SNAPSHOT.jar.original
├── spring-ai-resos-mcp-server/
│   └── 1.0.0-SNAPSHOT/
│       └── spring-ai-resos-mcp-server-1.0.0-SNAPSHOT.jar (executable)
└── spring-ai-resos-mcp-frontend/
    └── 1.0.0-SNAPSHOT/
        └── spring-ai-resos-mcp-frontend-1.0.0-SNAPSHOT.jar (executable)
```

**Total Size**: ~240MB (all artifacts with dependencies)

---

## Critical Files

| File | Purpose | Lines |
|------|---------|-------|
| `/pom.xml` | Parent POM with BOM management | ~345 |
| `client/pom.xml` | OpenAPI Generator configuration | ~120 |
| `entities/pom.xml` | Entity transformation build | ~100 |
| `backend/pom.xml` | Backend dependencies | ~200 |
| `mcp-server/pom.xml` | MCP server dependencies | ~150 |
| `mcp-client/pom.xml` | Frontend build integration | ~250 |

## Related Documentation

- [Module Architecture](03-module-architecture.md) - Module details
- [Code Generation Pipeline](04-code-generation.md) - Build-time code generation
- [Deployment](13-deployment.md) - Running built artifacts
- [Module Dependencies Diagram](diagrams/module-dependencies.md) - Build order visualization
