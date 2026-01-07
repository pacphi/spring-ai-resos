# Testing Strategy

This document details the testing approach, TestContainers integration, and test coverage goals.

## Testing Philosophy

**Principles**:

- **Test Pyramid**: Many unit tests, fewer integration tests, minimal E2E tests
- **Fast Feedback**: Unit tests run in <10s
- **Realistic Integration**: Use TestContainers for database tests
- **Security Testing**: Verify OAuth2 flows and authorization rules
- **No Mocking Where Possible**: Integration tests use real components

---

## Test Structure

### Test Organization

```text
backend/src/test/java/me/pacphi/ai/resos/
├── security/
│   ├── AuthorizationServerIntegrationTest.java
│   ├── ResourceServerIntegrationTest.java
│   ├── AppUserDetailsServiceTest.java
│   └── JwtTokenCustomizerTest.java
├── repository/
│   ├── CustomerRepositoryTest.java
│   ├── BookingRepositoryTest.java
│   └── AppUserRepositoryTest.java
├── csv/
│   ├── DataSeederTest.java
│   ├── AppUserMapperTest.java
│   └── CsvFileProcessorTest.java
├── config/
│   ├── SchemaCreatorTest.java
│   └── LiquibaseConfigurationTest.java
├── controller/
│   ├── CustomerControllerTest.java
│   └── LoginControllerTest.java
└── test/
    ├── TestConfiguration.java
    └── TestDataFactory.java
```

---

## Unit Testing

### Repository Tests

**Purpose**: Test Spring Data JDBC repositories with real database

**Example** (`CustomerRepositoryTest.java`):

```java
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CustomerRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void testSaveAndFindCustomer() {
        // Given
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setName("John Doe");
        customer.setEmail("john@example.com");

        // When
        CustomerEntity saved = customerRepository.save(customer);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(customerRepository.findById(saved.getId()))
            .isPresent()
            .get()
            .satisfies(c -> {
                assertThat(c.getName()).isEqualTo("John Doe");
                assertThat(c.getEmail()).isEqualTo("john@example.com");
            });
    }

    @Test
    void testFindByEmail() {
        // Given
        CustomerEntity customer = createCustomer("test@example.com");
        customerRepository.save(customer);

        // When
        Optional<CustomerEntity> found = customerRepository.findByEmail("test@example.com");

        // Then
        assertThat(found).isPresent();
    }

    @Test
    void testUniqueEmailConstraint() {
        // Given
        customerRepository.save(createCustomer("duplicate@example.com"));

        // When/Then
        assertThatThrownBy(() ->
            customerRepository.save(createCustomer("duplicate@example.com"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

**Key Annotations**:

- `@DataJdbcTest`: Spring Data JDBC test slice
- `@Testcontainers`: Enable TestContainers support
- `@AutoConfigureTestDatabase(Replace.NONE)`: Use TestContainers, not embedded H2
- `@DynamicPropertySource`: Inject TestContainers JDBC URL

### Service Tests

**Example** (`ChatServiceTest.java`):

```java
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatMemory chatMemory;

    @Mock
    private McpSyncClientManager mcpSyncClientManager;

    @InjectMocks
    private ChatService chatService;

    @Test
    void testStreamResponseToQuestion() {
        // Given
        Flux<String> mockStream = Flux.just("Hello", " ", "World");

        when(chatClient.prompt()).thenReturn(mock(ChatClient.PromptSpec.class));
        when(chatClient.prompt().user(anyString())).thenReturn(mock(...));
        when(...stream().content()).thenReturn(mockStream);

        when(mcpSyncClientManager.newMcpSyncClients()).thenReturn(List.of());

        // When
        List<String> tokens = new ArrayList<>();
        AtomicBoolean completed = new AtomicBoolean(false);

        chatService.streamResponseToQuestion(
            "test question",
            tokens::add,                     // onToken
            () -> completed.set(true),       // onComplete
            error -> fail(error.getMessage())  // onError
        );

        // Then
        await().atMost(2, TimeUnit.SECONDS).until(() -> completed.get());
        assertThat(tokens).containsExactly("Hello", " ", "World");
    }
}
```

**Libraries**:

- **Mockito**: Mocking framework
- **Awaitility**: Async assertions
- **AssertJ**: Fluent assertions

---

## Integration Testing

### Full Application Context

**Purpose**: Test complete Spring Boot application with all beans

**Example** (`BackendApplicationTest.java`):

```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.profiles.active=test",
        "app.seed.csv.enabled=false"  // Disable seeding for tests
    }
)
@Testcontainers
class BackendApplicationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        assertThat(port).isGreaterThan(0);
    }

    @Test
    void testHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health",
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
```

### Security Integration Tests

**OAuth2 Flow Testing** (`AuthorizationServerIntegrationTest.java`):

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthorizationServerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testClientCredentialsFlow() {
        // Given
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth("mcp-server", "mcp-server-secret");

        String body = "grant_type=client_credentials&scope=backend.read backend.write";

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
            tokenUrl,
            request,
            Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("access_token", "token_type", "expires_in");

        String accessToken = (String) response.getBody().get("access_token");
        assertThat(accessToken).isNotBlank();

        // Verify JWT structure
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);  // header.payload.signature
    }

    @Test
    void testJwtContainsCustomClaims() {
        // Get access token (from previous test or helper method)
        String accessToken = getAccessToken();

        // Decode JWT (Spring Security JwtDecoder or jwt.io)
        Jwt jwt = jwtDecoder.decode(accessToken);

        // Verify custom claims
        assertThat(jwt.getClaim("roles")).isNotNull();
        assertThat(jwt.getClaim("authorities")).isNotNull();
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://localhost:" + port);
    }

    @Test
    void testResourceServerValidatesToken() {
        // Given
        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        // When
        ResponseEntity<List> response = restTemplate.exchange(
            "http://localhost:" + port + "/api/v1/resos/customers",
            HttpMethod.GET,
            request,
            List.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testUnauthorizedAccessDenied() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/v1/resos/customers",
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

### MCP Integration Tests

**Tool Invocation Testing** (`ResOsServiceTest.java`):

```java
@SpringBootTest
@AutoConfigureMockMvc
class ResOsServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DefaultApi backendApi;  // Mock backend API calls

    @Test
    @WithMockUser(authorities = {"SCOPE_mcp.read"})
    void testGetCustomersTool() throws Exception {
        // Given
        List<Customer> mockCustomers = List.of(
            createCustomer("John Doe", "john@example.com"),
            createCustomer("Jane Smith", "jane@example.com")
        );

        when(backendApi.customersGet(anyInt(), anyInt(), any(), any()))
            .thenReturn(ResponseEntity.ok(mockCustomers));

        // When/Then
        mockMvc.perform(post("/mcp/tools/getCustomers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":100,\"skip\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").isArray())
            .andExpect(jsonPath("$.result.length()").value(2))
            .andExpect(jsonPath("$.result[0].name").value("John Doe"));

        verify(backendApi).customersGet(100, 0, null, null);
    }
}
```

---

## TestContainers

### PostgreSQL Container

**Dependency**:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

**Usage**:

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
    .withDatabaseName("test")
    .withUsername("test")
    .withPassword("test")
    .withInitScript("test-schema.sql");  // Optional init script

@DynamicPropertySource
static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

**Benefits**:

- Real PostgreSQL (not H2)
- Isolated per test class
- Automatic cleanup
- Repeatable tests

### Reusable Container (Singleton)

**For faster test execution**:

```java
public class PostgresTestContainer {

    private static final PostgreSQLContainer<?> container;

    static {
        container = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
        container.start();
    }

    public static PostgreSQLContainer<?> getInstance() {
        return container;
    }
}

// Usage in tests
@DynamicPropertySource
static void registerProperties(DynamicPropertyRegistry registry) {
    PostgreSQLContainer<?> postgres = PostgresTestContainer.getInstance();
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
}
```

**Benefit**: Container starts once, shared across all test classes (~5x faster)

---

## Test Profiles

### Test Profile Configuration

**File**: `src/test/resources/application-test.yml`

```yaml
spring:
  profiles:
    active: test

  datasource:
    # Overridden by @DynamicPropertySource in tests

  liquibase:
    enabled: true
    contexts: test # Only run test changesets

  h2:
    console:
      enabled: false # No H2 console in tests

app:
  seed:
    csv:
      enabled: false # Disable CSV seeding in tests (use test data instead)

  entity:
    schema-generation:
      enabled: true # Enable for schema creation

logging:
  level:
    root: WARN
    me.pacphi.ai.resos: INFO
```

### Test Data Factory

**Purpose**: Create test data programmatically

```java
public class TestDataFactory {

    public static CustomerEntity createCustomer(String name, String email) {
        CustomerEntity customer = new CustomerEntity();
        customer.setId(UUID.randomUUID());
        customer.setName(name);
        customer.setEmail(email);
        customer.setCreatedAt(OffsetDateTime.now());
        customer.setBookingCount(0);
        customer.setTotalSpent(BigDecimal.ZERO);
        return customer;
    }

    public static AppUserEntity createUser(String username, String password) {
        AppUserEntity user = new AppUserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword(new BCryptPasswordEncoder().encode(password));
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setCreatedAt(OffsetDateTime.now());
        return user;
    }

    public static BookingEntity createBooking(UUID customerId, LocalDate date) {
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setGuest(AggregateReference.to(customerId));
        booking.setBookingDate(date);
        booking.setBookingTime(LocalTime.of(19, 0));
        booking.setPeopleCount(4);
        booking.setStatus(BookingStatus.CONFIRMED);
        return booking;
    }
}
```

**Usage in Tests**:

```java
@Test
void testBookingCreation() {
    CustomerEntity customer = createCustomer("Test", "test@example.com");
    customer = customerRepository.save(customer);

    BookingEntity booking = createBooking(customer.getId(), LocalDate.now());
    booking = bookingRepository.save(booking);

    assertThat(booking.getId()).isNotNull();
}
```

---

## Security Testing

### Testing OAuth2 Flows

**Mock Security Context**:

```java
@Test
@WithMockUser(username = "admin", authorities = {"ROLE_ADMIN", "SCOPE_backend.read"})
void testAdminCanAccessCustomers() throws Exception {
    mockMvc.perform(get("/api/v1/resos/customers"))
        .andExpect(status().isOk());
}

@Test
@WithMockUser(username = "user", authorities = {"ROLE_USER"})
void testUserCannotDeleteCustomers() throws Exception {
    mockMvc.perform(delete("/api/v1/resos/customers/123"))
        .andExpect(status().isForbidden());
}

@Test
void testUnauthenticatedAccessDenied() throws Exception {
    mockMvc.perform(get("/api/v1/resos/customers"))
        .andExpect(status().isUnauthorized());
}
```

### JWT Token Testing

**Custom Annotation**:

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockJwtSecurityContextFactory.class)
public @interface WithMockJwt {
    String subject() default "test-user";
    String[] roles() default {"ROLE_USER"};
    String[] scopes() default {};
}

// Usage
@Test
@WithMockJwt(roles = {"ROLE_ADMIN"}, scopes = {"backend.write"})
void testAdminWithScope() throws Exception {
    mockMvc.perform(post("/api/v1/resos/customers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(customerJson))
        .andExpect(status().isCreated());
}
```

---

## Test Coverage Goals

### Unit Tests

**Target Coverage**: 80%

| Component       | Coverage | Status               |
| --------------- | -------- | -------------------- |
| Repositories    | 90%      | ✅                   |
| CSV Mappers     | 85%      | ✅                   |
| Security Config | 75%      | ⚠️ Needs improvement |
| Controllers     | 60%      | ⚠️ Many stubs        |
| Services        | 70%      | ⚠️ Needs expansion   |

### Integration Tests

**Target Coverage**: Key flows tested

| Flow                      | Status        |
| ------------------------- | ------------- |
| OAuth2 Client Credentials | ✅ Tested     |
| OAuth2 Authorization Code | ⏳ Needs test |
| JWT Token Validation      | ✅ Tested     |
| MCP Tool Invocation       | ⏳ Needs test |
| Chat Streaming            | ⏳ Needs test |
| Database Migrations       | ✅ Tested     |
| CSV Data Seeding          | ✅ Tested     |

---

## Test Execution

### Running Tests

```bash
# All tests
mvn test

# Specific module
cd backend && mvn test

# Specific test class
mvn test -Dtest=CustomerRepositoryTest

# Specific test method
mvn test -Dtest=CustomerRepositoryTest#testSaveAndFindCustomer

# Skip tests (for fast builds)
mvn clean install -DskipTests

# Integration tests only
mvn verify -DskipUnitTests
```

### Parallel Execution

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <parallel>classes</parallel>
        <threadCount>4</threadCount>
        <reuseForks>true</reuseForks>
    </configuration>
</plugin>
```

**Benefit**: 3-4x faster test execution

### Test Reports

```bash
# Generate test reports
mvn test

# View report
open backend/target/surefire-reports/index.html

# Code coverage (JaCoCo)
mvn jacoco:report
open backend/target/site/jacoco/index.html
```

---

## Recommended Tests (To Be Implemented)

### 1. SchemaCreator Tests

```java
@Test
void testDependencyGraphBuilding() {
    // Verify topological sort correctness
    List<String> sorted = schemaCreator.sortTables(entities);

    // Authority should come before UserAuthority
    int authorityIndex = sorted.indexOf("authority");
    int userAuthorityIndex = sorted.indexOf("user_authority");
    assertThat(authorityIndex).isLessThan(userAuthorityIndex);
}

@Test
void testCircularDependencyDetection() {
    // Create entities with circular FK references
    assertThatThrownBy(() -> schemaCreator.generateSchemas())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Circular dependency");
}
```

### 2. MCP Tool Tests

```java
@Test
void testGetCustomersWithFiltering() {
    List<Customer> result = resOsService.getCustomers(
        50,  // limit
        0,   // skip
        "name",  // sort
        "booking_count > 5"  // customQuery
    );

    assertThat(result).isNotEmpty();
    assertThat(result).allSatisfy(c ->
        assertThat(c.getBookingCount()).isGreaterThan(5)
    );
}

@Test
void testToolParameterValidation() {
    assertThatThrownBy(() ->
        resOsService.getCustomers(-1, 0, null, null)  // Invalid limit
    ).isInstanceOf(IllegalArgumentException.class);
}
```

### 3. Streaming Tests

```java
@Test
void testSseStreaming() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/resos/stream/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"test\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    // Wait for async completion
    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE));
}
```

### 4. OAuth2 PKCE Flow

```java
@Test
void testPkceAuthorizationCodeFlow() {
    // 1. Start authorization request
    String authorizeUrl = "/oauth2/authorize" +
        "?response_type=code" +
        "&client_id=frontend-app" +
        "&redirect_uri=http://localhost:8081/authorized" +
        "&code_challenge=" + codeChallenge +
        "&code_challenge_method=S256";

    // 2. Expect redirect to login
    mockMvc.perform(get(authorizeUrl))
        .andExpect(status().is3xxRedirection());

    // 3. Login
    mockMvc.perform(formLogin().user("admin").password("admin123"))
        .andExpect(authenticated());

    // 4. Get authorization code (from redirect)
    String authCode = extractAuthorizationCode(redirectUrl);

    // 5. Exchange code for tokens
    mockMvc.perform(post("/oauth2/token")
            .param("grant_type", "authorization_code")
            .param("code", authCode)
            .param("redirect_uri", "http://localhost:8081/authorized")
            .param("client_id", "frontend-app")
            .param("code_verifier", codeVerifier))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").exists())
        .andExpect(jsonPath("$.refresh_token").exists())
        .andExpect(jsonPath("$.id_token").exists());
}
```

---

## Performance Testing

### Load Testing (JMeter)

**Test Plan**:

```xml
<ThreadGroup>
    <numThreads>50</numThreads>
    <rampUp>10</rampUp>
    <loops>100</loops>

    <HTTPSamplerProxy>
        <domain>localhost</domain>
        <port>8080</port>
        <path>/api/v1/resos/customers</path>
        <method>GET</method>
        <headers>
            <Header>
                <name>Authorization</name>
                <value>Bearer ${access_token}</value>
            </Header>
        </headers>
    </HTTPSamplerProxy>
</ThreadGroup>
```

**Metrics**:

- Throughput: Requests per second
- Latency: p50, p95, p99
- Error rate: % of failed requests

**Baseline** (local, H2):

- Throughput: ~500 req/s
- p50 Latency: 20ms
- p95 Latency: 50ms
- Error rate: <0.1%

### Stress Testing

```bash
# Apache Bench
ab -n 10000 -c 100 \
   -H "Authorization: Bearer $TOKEN" \
   http://localhost:8080/api/v1/resos/customers

# Results
Requests per second: 485.23 [#/sec]
Time per request: 206.085 [ms] (mean, across all concurrent requests)
Percentage of requests served within a certain time (ms)
  50%    180
  95%    350
  99%    520
```

---

## CI/CD Integration

### GitHub Actions

**File**: `.github/workflows/ci.yml`

```yaml
name: Java CI with Maven

on:
  push:
    branches: [main, feature/*]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v6

      - name: Set up JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'liberica'
          cache: 'maven'

      - name: Build with Maven
        run: mvn clean install -B

      - name: Run tests
        run: mvn test -B

      - name: Generate coverage report
        run: mvn jacoco:report

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: ./target/site/jacoco/jacoco.xml
          fail_ci_if_error: true

      - name: Verify no security vulnerabilities
        run: mvn org.owasp:dependency-check-maven:check
```

**Test Execution Time**: ~5 minutes (with TestContainers)

---

## Test Utilities

### MockMvc Helpers

```java
public class MockMvcHelpers {

    public static ResultActions authenticatedGet(
            MockMvc mockMvc,
            String url,
            String token) throws Exception {

        return mockMvc.perform(get(url)
            .header("Authorization", "Bearer " + token));
    }

    public static String extractJsonValue(MvcResult result, String jsonPath)
            throws Exception {
        String content = result.getResponse().getContentAsString();
        return JsonPath.parse(content).read(jsonPath);
    }
}
```

### Test Database Setup

```java
@TestConfiguration
public class TestDatabaseConfig {

    @Bean
    public DataSource testDataSource() {
        return DataSourceBuilder.create()
            .driverClassName("org.postgresql.Driver")
            .url(PostgresTestContainer.getInstance().getJdbcUrl())
            .username(PostgresTestContainer.getInstance().getUsername())
            .password(PostgresTestContainer.getInstance().getPassword())
            .build();
    }
}
```

---

## Test Coverage Tools

### JaCoCo (Code Coverage)

**Configuration** (`pom.xml`):

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Commands**:

```bash
# Run tests with coverage
mvn clean test

# Generate report
mvn jacoco:report

# View report
open target/site/jacoco/index.html

# Fail build if coverage below 80%
mvn jacoco:check
```

---

## Continuous Integration

### Test Stages

1. **Unit Tests**: Fast, no external dependencies (~30s)
2. **Integration Tests**: With TestContainers (~3min)
3. **Security Scan**: OWASP Dependency Check (~2min)
4. **Code Quality**: SonarQube analysis (~1min)
5. **Build Verification**: Build all artifacts (~2min)

**Total CI Time**: ~8 minutes

### Failure Handling

**Retry Failed Tests**:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <rerunFailingTestsCount>2</rerunFailingTestsCount>
    </configuration>
</plugin>
```

**Quarantine Flaky Tests**:

```java
@Category(FlakyTest.class)
@Test
void flakyTest() {
    // Test with timing issues
}

// Exclude in CI
mvn test -Dgroups='!FlakyTest'
```

---

## Critical Files

| File                                                                 | Purpose            |
| -------------------------------------------------------------------- | ------------------ |
| `backend/src/test/java/me/pacphi/ai/resos/security/`                 | Security tests     |
| `backend/src/test/java/me/pacphi/ai/resos/test/TestDataFactory.java` | Test data creation |
| `backend/src/test/resources/application-test.yml`                    | Test configuration |
| `.github/workflows/ci.yml`                                           | GitHub Actions CI  |

## Related Documentation

- [Module Architecture](03-module-architecture.md) - Test locations
- [Security Architecture](06-security-architecture.md) - OAuth2 flows to test
- [Build Workflow](10-build-workflow.md) - Build commands
