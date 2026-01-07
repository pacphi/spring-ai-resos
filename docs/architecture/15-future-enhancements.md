# Future Enhancements

This document outlines recommended enhancements, known limitations, and technical debt items for the Spring AI ResOs project.

## Roadmap

### Phase 1: Complete Core Functionality (High Priority)

#### 1.1 Controller Implementation Completion

**Status**: Many controllers are stubs

**Current**:

```java
@PostMapping
public ResponseEntity<Booking> createBooking(@RequestBody Booking booking) {
    throw new UnsupportedOperationException("Not implemented");
}
```

**Needed**:

```java
@PostMapping
public ResponseEntity<Booking> createBooking(@RequestBody Booking booking) {
    BookingEntity entity = BookingEntity.fromPojo(booking);
    BookingEntity saved = bookingRepository.save(entity);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(saved.toPojo());
}
```

**Endpoints to Implement**:

- `POST /api/v1/resos/bookings` - Create booking
- `PUT /api/v1/resos/bookings/{id}` - Update booking
- `DELETE /api/v1/resos/bookings/{id}` - Cancel booking
- `POST /api/v1/resos/orders` - Create order
- `POST /api/v1/resos/feedback` - Submit feedback
- `GET /api/v1/resos/tables/availability` - Check table availability

**Effort**: 2-3 days

#### 1.2 Validation & Error Handling

**Add**:

- Bean validation on controller inputs
- Global exception handler
- Standardized error response format
- Field-level error messages

**Example**:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage
            ));

        ErrorResponse response = new ErrorResponse(
            "VALIDATION_ERROR",
            "Request validation failed",
            errors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
}
```

**Effort**: 1-2 days

#### 1.3 Business Logic Services

**Current**: Controllers call repositories directly

**Recommended**: Service layer for business logic

```java
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final TableRepository tableRepository;

    @Transactional
    public Booking createBooking(Booking booking) {
        // 1. Validate customer exists
        customerRepository.findById(booking.getGuestId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        // 2. Check table availability
        boolean available = checkTableAvailability(
            booking.getDate(),
            booking.getTime(),
            booking.getPeopleCount()
        );

        if (!available) {
            throw new BookingException("No tables available");
        }

        // 3. Create booking
        BookingEntity entity = BookingEntity.fromPojo(booking);
        entity.setStatus(BookingStatus.PENDING);
        entity = bookingRepository.save(entity);

        // 4. Send confirmation email (future)
        // emailService.sendBookingConfirmation(entity);

        return entity.toPojo();
    }

    private boolean checkTableAvailability(LocalDate date, LocalTime time, int people) {
        // Complex business logic
        List<TableEntity> availableTables = tableRepository
            .findAvailableTablesForDateTime(date, time, people);

        return !availableTables.isEmpty();
    }
}
```

**Effort**: 3-4 days

---

### Phase 2: Performance Optimization (Medium Priority)

#### 2.1 Redis Caching

**Purpose**: Cache frequently accessed data (customers, tables, opening hours)

**Configuration**:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

**Usage**:

```java
@Cacheable(value = "customers", key = "#id")
public Customer getCustomerById(UUID id) {
    return customerRepository.findById(id)
        .map(CustomerEntity::toPojo)
        .orElseThrow();
}

@CacheEvict(value = "customers", key = "#id")
public void updateCustomer(UUID id, Customer customer) {
    // Update logic
}
```

**Benefit**: 80-90% reduction in database queries for frequently accessed data

**Effort**: 1-2 days

#### 2.2 Database Query Optimization

**Add Indexes**:

```sql
CREATE INDEX idx_booking_date ON booking(booking_date);
CREATE INDEX idx_booking_status ON booking(status);
CREATE INDEX idx_booking_guest ON booking(guest_id);
CREATE INDEX idx_customer_email ON customer(email);
CREATE INDEX idx_feedback_rating ON feedback(rating);
CREATE INDEX idx_oauth2_auth_principal ON oauth2_authorization(principal_name);
```

**Composite Indexes**:

```sql
CREATE INDEX idx_booking_date_status ON booking(booking_date, status);
CREATE INDEX idx_feedback_customer_date ON feedback(customer_id, created_at);
```

**Query Optimization**:

```java
// BEFORE: N+1 query
List<BookingEntity> bookings = bookingRepository.findAll();
for (BookingEntity booking : bookings) {
    CustomerEntity customer = customerRepository.findById(booking.getGuest().getId()).get();
    // Use customer...
}

// AFTER: Single JOIN query
@Query("""
    SELECT b.*, c.* FROM booking b
    LEFT JOIN customer c ON c.id = b.guest_id
    WHERE b.booking_date >= :startDate
    """)
List<BookingWithCustomer> findBookingsWithCustomers(@Param("startDate") LocalDate startDate);
```

**Effort**: 2-3 days

#### 2.3 Connection Pool Tuning

**Monitoring**:

```yaml
management:
  metrics:
    enable:
      hikari: true
```

**Tuning**:

- Monitor `hikaricp.connections.active`
- Monitor `hikaricp.connections.pending`
- Adjust `maximum-pool-size` based on load
- Set `leak-detection-threshold` to find connection leaks

**Effort**: 1 day (monitoring + tuning)

---

### Phase 3: Observability (Medium Priority)

#### 3.1 Distributed Tracing (OpenTelemetry)

**Dependencies**:

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
</dependency>
```

**Configuration**:

```yaml
spring:
  application:
    name: spring-ai-resos-backend

management:
  tracing:
    sampling:
      probability: 1.0 # 100% sampling (dev), 0.1 (prod)
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

**Visualization**: Jaeger, Zipkin, or Grafana Tempo

**Benefit**: Track request flow across all services (Frontend → MCP Client → MCP Server → Backend → Database)

**Effort**: 2-3 days

#### 3.2 Prometheus Metrics

**Enable**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**Custom Metrics**:

```java
@Component
public class ChatMetrics {

    private final MeterRegistry meterRegistry;

    public void recordChatRequest() {
        meterRegistry.counter("chat.requests.total").increment();
    }

    public void recordToolInvocation(String toolName, long durationMs) {
        meterRegistry.counter("chat.tool.invocations",
            "tool", toolName).increment();
        meterRegistry.timer("chat.tool.duration",
            "tool", toolName).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTokenCount(int tokens) {
        meterRegistry.summary("chat.tokens.used").record(tokens);
    }
}
```

**Grafana Dashboards**:

- Chat requests per minute
- Tool invocation frequency
- Average response time
- Error rates by endpoint
- Token usage trends

**Effort**: 2-3 days (metrics + dashboards)

#### 3.3 Structured Logging (JSON)

**Dependency**:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

**Logback Configuration** (`logback-spring.xml`):

```xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeContext>true</includeContext>
            <includeMdc>true</includeMdc>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <logger>logger</logger>
                <level>level</level>
            </fieldNames>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

**Output**:

```json
{
  "@timestamp": "2026-01-06T10:30:45.123Z",
  "level": "INFO",
  "logger": "me.pacphi.ai.resos.service.ChatService",
  "message": "Chat request received",
  "mdc": {
    "traceId": "abc123",
    "spanId": "def456",
    "userId": "admin"
  }
}
```

**Benefits**: ELK Stack ingestion, structured queries

**Effort**: 1 day

---

### Phase 4: Scalability (Low Priority)

#### 4.1 Rate Limiting

**Dependencies**:

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
</dependency>
```

**Implementation**:

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {

        String clientId = getClientId(request);  // From JWT or IP
        Bucket bucket = buckets.computeIfAbsent(clientId, this::createBucket);

        if (bucket.tryConsume(1)) {
            return true;  // Allow request
        } else {
            response.setStatus(429);  // Too Many Requests
            return false;
        }
    }

    private Bucket createBucket(String clientId) {
        return Bucket.builder()
            .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))  // 100 req/min
            .build();
    }
}
```

**Effort**: 1-2 days

#### 4.2 Read Replicas

**Configuration**:

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        // Write operations
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://primary.db:5432/resos")
            .build();
    }

    @Bean
    public DataSource replicaDataSource() {
        // Read operations
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://replica.db:5432/resos")
            .build();
    }

    @Bean
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica) {

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("PRIMARY", primary);
        dataSourceMap.put("REPLICA", replica);

        AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                    ? "REPLICA" : "PRIMARY";
            }
        };

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(primary);
        return routingDataSource;
    }
}
```

**Usage**:

```java
@Transactional(readOnly = true)  // Uses replica
public List<Customer> getAllCustomers() {
    return customerRepository.findAll();
}

@Transactional  // Uses primary
public Customer createCustomer(Customer customer) {
    return customerRepository.save(CustomerEntity.fromPojo(customer));
}
```

**Effort**: 2-3 days

---

### Phase 5: Security Hardening (Medium Priority)

#### 5.1 Token Revocation Endpoint

**Current**: Tokens stored in database but no revocation API

**Add**:

```java
@RestController
@RequestMapping("/api/auth")
public class TokenController {

    private final OAuth2AuthorizationService authorizationService;

    @PostMapping("/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeToken(
            @RequestParam String token,
            @RequestParam String tokenTypeHint) {

        // Find and delete authorization
        authorizationService.remove(/* find by token */);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/revoke-user-tokens")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeAllUserTokens(@RequestParam String username) {
        // Delete all authorizations for user
        return ResponseEntity.ok().build();
    }
}
```

**Effort**: 1 day

#### 5.2 API Rate Limiting by Client

**Per-Client Limits**:

```java
@Component
public class ClientRateLimiter {

    public boolean isAllowed(String clientId) {
        String key = "rate:" + clientId;
        Long current = redisTemplate.opsForValue().increment(key);

        if (current == 1) {
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
        }

        return current <= getLimit(clientId);  // mcp-server: 1000/min, frontend-app: 100/min
    }
}
```

**Effort**: 1 day

#### 5.3 Security Headers

**Add**:

```java
http.headers(headers -> headers
    .contentSecurityPolicy("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")
    .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
    .permissionsPolicy(policy ->
        policy.policy("geolocation=(), microphone=(), camera=()")
    )
    .frameOptions(frameOptions -> frameOptions.deny())
);
```

**Effort**: 0.5 days

---

### Phase 6: Developer Experience (Low Priority)

#### 6.1 Docker Compose for Local Development

**Simplified Setup**:

```yaml
# docker-compose.dev.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: resos
      POSTGRES_USER: resos
      POSTGRES_PASSWORD: dev
    ports:
      - '5432:5432'

  # No application containers (run via Maven for HMR)
```

**Usage**:

```bash
# Start database only
docker-compose -f docker-compose.dev.yml up -d

# Run apps via Maven (hot reload)
cd backend && mvn spring-boot:run
```

**Effort**: 0.5 days

#### 6.2 Test Data Generator

**CLI Tool**:

```bash
# Generate 1000 test customers
java -jar data-generator.jar customers --count 1000

# Generate bookings for date range
java -jar data-generator.jar bookings --start 2026-01-01 --end 2026-01-31
```

**Benefit**: Performance testing, demo data

**Effort**: 2 days

#### 6.3 OpenAPI Documentation Enhancement

**Add**:

- Request/response examples
- Error response documentation
- Authentication examples
- Try-it-out with OAuth2 token

**Swagger UI Configuration**:

```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Spring AI ResOs API")
            .version("1.0.0")
            .description("Restaurant reservation system with AI chatbot"))
        .components(new Components()
            .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
}
```

**Effort**: 1 day

---

## Known Limitations

### Current Limitations

| Limitation                 | Impact                                       | Workaround                          |
| -------------------------- | -------------------------------------------- | ----------------------------------- |
| **InMemoryChatMemory**     | Lost on restart, not shared across instances | Use Redis-backed memory             |
| **No Caching Layer**       | Every request hits database                  | Add Redis caching                   |
| **No Distributed Tracing** | Hard to debug multi-service issues           | Add OpenTelemetry                   |
| **Many Controller Stubs**  | Limited API functionality                    | Implement remaining endpoints       |
| **No Email Notifications** | No booking confirmations                     | Add email service (SendGrid, SES)   |
| **No Payment Integration** | Cannot process payments                      | Add Stripe/PayPal                   |
| **No WebSocket Support**   | Only HTTP for MCP                            | Add WebSocket transport (if needed) |
| **No Multi-Tenancy**       | Single restaurant only                       | Add tenant isolation                |

### Scale Limitations

**Current Architecture Scales To**:

- **Users**: 1,000 concurrent users
- **Requests**: 500 req/s per instance
- **Database**: 10,000 bookings/day
- **Chat**: 50 concurrent chat sessions

**Bottlenecks**:

1. **LLM API Rate Limits**: OpenAI tier limits (e.g., 500 req/min)
2. **Database Connections**: HikariCP pool size × instances
3. **Memory**: InMemoryChatMemory grows unbounded

**Solutions**:

- LLM: Queue system, multiple API keys, local Ollama
- Database: Read replicas, connection pool tuning
- Memory: Redis-backed chat memory, TTL expiration

---

## Technical Debt

### High Priority

1. **Complete Controller Implementations**
   - **Current**: Many endpoints throw `UnsupportedOperationException`
   - **Impact**: Limited API functionality
   - **Effort**: 3-4 days

2. **Add Integration Tests for OAuth2 Flows**
   - **Current**: Manual testing only
   - **Impact**: Regressions not caught
   - **Effort**: 2-3 days

3. **Standardize Error Responses**
   - **Current**: Inconsistent error formats
   - **Impact**: Poor client experience
   - **Effort**: 1 day

### Medium Priority

1. **Improve Test Coverage**
   - **Current**: ~60% overall
   - **Target**: 80% line coverage
   - **Effort**: 3-4 days

2. **Add API Request Validation**
   - **Current**: Limited validation
   - **Impact**: Invalid data in database
   - **Effort**: 1-2 days

3. **Logging Strategy Refinement**
   - **Current**: Inconsistent log levels
   - **Impact**: Hard to debug
   - **Effort**: 1 day

### Low Priority

1. **Javadoc Completion**
   - **Current**: Minimal Javadocs
   - **Impact**: Developer onboarding harder
   - **Effort**: 2 days

2. **Performance Benchmarks**
   - **Current**: No baseline metrics
   - **Impact**: Cannot measure improvements
   - **Effort**: 1-2 days

---

## Recommended Enhancements by Domain

### Security Enhancements

- [ ] Implement token revocation endpoint
- [ ] Add rate limiting per client
- [ ] Enhance CORS policy (production domains)
- [ ] Add OWASP dependency scanning to CI
- [ ] Implement security headers (CSP, HSTS)
- [ ] Add brute-force protection on login
- [ ] Implement account lockout after failed attempts
- [ ] Add 2FA support (TOTP, SMS)

**Priority**: High
**Effort**: 5-6 days

### Performance Enhancements

- [ ] Add Redis caching layer
- [ ] Optimize database queries with indexes
- [ ] Implement query result caching
- [ ] Add connection pool monitoring
- [ ] Optimize Liquibase startup (disable in production)
- [ ] Add database query logging (slow query detection)
- [ ] Implement lazy loading for large collections
- [ ] Add pagination to all list endpoints

**Priority**: Medium
**Effort**: 4-5 days

### Observability Enhancements

- [ ] Add OpenTelemetry distributed tracing
- [ ] Export Prometheus metrics
- [ ] Create Grafana dashboards
- [ ] Implement structured logging (JSON)
- [ ] Add custom health indicators
- [ ] Set up log aggregation (ELK, Splunk)
- [ ] Configure alerts (error rate, latency)
- [ ] Add business metrics (bookings/day, revenue)

**Priority**: Medium
**Effort**: 5-6 days

### Testing Enhancements

- [ ] Expand integration test coverage
- [ ] Add contract testing (Pact)
- [ ] Implement performance tests (JMeter, Gatling)
- [ ] Add chaos engineering tests (Chaos Monkey)
- [ ] Security penetration testing (OWASP ZAP)
- [ ] Add load testing to CI/CD
- [ ] Implement mutation testing (PIT)
- [ ] Add visual regression testing (frontend)

**Priority**: Low
**Effort**: 6-8 days

### Feature Enhancements

- [ ] Implement booking availability search
- [ ] Add email notifications (SendGrid, AWS SES)
- [ ] Implement payment processing (Stripe)
- [ ] Add SMS notifications (Twilio)
- [ ] Implement calendar integration (Google Calendar, iCal)
- [ ] Add loyalty program features
- [ ] Implement reservation waitlist
- [ ] Add special event management

**Priority**: Low (depends on business requirements)
**Effort**: 10-15 days

---

## Migration to Production

### Recommended Timeline

**Month 1: Core Completion**

- Week 1: Implement remaining controllers
- Week 2: Add validation and error handling
- Week 3: Complete integration tests
- Week 4: Security testing and hardening

**Month 2: Performance & Monitoring**

- Week 1: Add caching layer (Redis)
- Week 2: Optimize database queries
- Week 3: Implement distributed tracing
- Week 4: Set up monitoring and dashboards

**Month 3: Production Readiness**

- Week 1: Load testing and tuning
- Week 2: Security audit and fixes
- Week 3: Documentation completion
- Week 4: Deployment automation

**Total**: 3 months to production-ready

---

## Cost Considerations

### LLM API Costs

**OpenAI Pricing** (as of 2026):

- gpt-4o-mini: $0.15/1M input tokens, $0.60/1M output tokens
- Typical chat: 500 input + 300 output tokens
- Cost per chat: $0.00025 (~$0.25 per 1000 chats)

**Monthly Costs** (1000 users, 10 chats/user/month):

- Total chats: 10,000/month
- Cost: ~$2.50/month

**Optimization**:

- Use cheaper models (gpt-3.5-turbo, Ollama local)
- Implement caching (common questions)
- Use smaller context windows

### Infrastructure Costs (AWS example)

| Service                       | Spec                          | Cost/Month      |
| ----------------------------- | ----------------------------- | --------------- |
| **ECS Fargate**               | 2 vCPU, 4GB RAM × 3 instances | $140            |
| **RDS PostgreSQL**            | db.t3.medium (2 vCPU, 4GB)    | $60             |
| **ElastiCache Redis**         | cache.t3.micro                | $15             |
| **Application Load Balancer** | 1 ALB                         | $20             |
| **CloudWatch**                | Logs + metrics                | $10             |
| **S3**                        | Static assets                 | $5              |
| **Total**                     |                               | **~$250/month** |

**Plus**: LLM API costs (~$2.50/month)

**Grand Total**: ~$252.50/month for 1000 active users

---

## Recommendation Priority Matrix

| Enhancement          | Business Value | Technical Complexity | Priority   |
| -------------------- | -------------- | -------------------- | ---------- |
| Complete Controllers | High           | Low                  | **High**   |
| Error Handling       | High           | Low                  | **High**   |
| Integration Tests    | High           | Medium               | **High**   |
| Redis Caching        | Medium         | Low                  | **Medium** |
| Distributed Tracing  | Medium         | Medium               | **Medium** |
| Query Optimization   | Medium         | Medium               | **Medium** |
| Rate Limiting        | Medium         | Low                  | **Medium** |
| Token Revocation     | Low            | Low                  | **Low**    |
| Read Replicas        | Low            | High                 | **Low**    |
| Multi-Tenancy        | Low            | High                 | **Low**    |

**Recommendation**: Focus on High priority items first (foundation), then Medium (performance and monitoring), finally Low (nice-to-have).

---

## Success Metrics

### Application Metrics

**Target**:

- **Availability**: 99.9% uptime (8.7 hours downtime/year)
- **Response Time**: p95 < 200ms (excluding LLM)
- **Error Rate**: < 0.1%
- **Chat Success Rate**: > 95% (tool invocations succeed)

### Performance Metrics

**Target**:

- **Throughput**: 1000 req/s (with caching)
- **Database Queries**: < 10 queries per request
- **Connection Pool**: < 50% utilization
- **Memory Usage**: < 1GB per instance

### Security Metrics

**Target**:

- **Zero Known Vulnerabilities**: OWASP scan clean
- **Token Lifetime**: < 1 hour for access tokens
- **Password Strength**: 80+ bits entropy (BCrypt cost 12)
- **Failed Auth Rate**: < 5%

---

## Related Documentation

- [Deployment](13-deployment.md) - Production deployment guide
- [Testing](14-testing.md) - Current testing strategy
- [Security Architecture](06-security-architecture.md) - Security enhancements context
- [Data Architecture](05-data-architecture.md) - Database optimization context
