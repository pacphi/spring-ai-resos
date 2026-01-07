# Data Architecture

This document details the complete data model, Spring Data JDBC patterns, dynamic schema generation, and data seeding strategy.

## Data Model Overview

See [Data Model ERD](diagrams/data-model-erd.md) for the complete entity-relationship diagram.

The database consists of three logical domains:

1. **Core Domain**: Restaurant business entities (Customer, Booking, Order, etc.)
2. **Security Domain**: Authentication and authorization (AppUser, Authority)
3. **OAuth2 Domain**: OAuth2 Authorization Server tables

---

## Core Domain Entities

### CustomerEntity

**Table**: `customer`

**Purpose**: Customer information and booking history tracking

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique customer identifier |
| name | VARCHAR(255) | NOT NULL | Customer full name |
| email | VARCHAR(255) | UNIQUE | Customer email address |
| phone | VARCHAR(255) | - | Contact phone number |
| created_at | TIMESTAMP | - | Account creation date |
| last_booking_at | TIMESTAMP | - | Most recent booking date |
| booking_count | INTEGER | DEFAULT 0 | Total bookings made |
| total_spent | DECIMAL(19,2) | - | Cumulative spend |
| metadata | JSONB | - | Additional custom data |

**JDBC Entity**:
```java
@Table("customer")
public class CustomerEntity {
    @Id
    private UUID id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    @Column("phone")
    private String phone;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("last_booking_at")
    private OffsetDateTime lastBookingAt;

    @Column("booking_count")
    private Integer bookingCount;

    @Column("total_spent")
    private BigDecimal totalSpent;

    @Column("metadata")
    private Map<String, Object> metadata;

    public Customer toPojo() { /* conversion */ }
    public static CustomerEntity fromPojo(Customer pojo) { /* conversion */ }
}
```

### BookingEntity

**Table**: `booking`

**Purpose**: Restaurant reservation records

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique booking identifier |
| guest_id | UUID | FK → customer | Customer making booking |
| restaurant_id | UUID | FK → restaurant | Restaurant being booked |
| booking_date | DATE | NOT NULL | Reservation date |
| booking_time | TIME | NOT NULL | Reservation time |
| people_count | INTEGER | NOT NULL | Number of guests |
| duration_minutes | INTEGER | - | Expected duration |
| status | VARCHAR(50) | NOT NULL | pending, confirmed, seated, completed, cancelled |
| metadata | JSONB | - | Additional booking data |
| comments | TEXT | - | Customer comments |
| internal_note | TEXT | - | Staff notes |

**JDBC Entity**:
```java
@Table("booking")
public class BookingEntity {
    @Id
    private UUID id;

    @Column("guest_id")
    private AggregateReference<CustomerEntity, UUID> guest;

    @Column("restaurant_id")
    private AggregateReference<RestaurantEntity, UUID> restaurant;

    @Column("booking_date")
    private LocalDate bookingDate;

    @Column("booking_time")
    private LocalTime bookingTime;

    @Column("people_count")
    private Integer peopleCount;

    @Column("status")
    private BookingStatus status;  // Enum

    @MappedCollection(idColumn = "booking_id")
    private Set<BookingTableEntity> tables;  // Many-to-many via join table

    // ... other fields
}
```

**Relationships**:
- `guest` → CustomerEntity (many-to-one)
- `restaurant` → RestaurantEntity (many-to-one)
- `tables` → Set<BookingTableEntity> (one-to-many, join table)

### OrderEntity

**Table**: `order_01` (note: "order" is reserved keyword)

**Purpose**: Food and beverage orders

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique order identifier |
| booking_id | UUID | FK → booking | Associated reservation |
| status | VARCHAR(50) | NOT NULL | pending, preparing, served, paid |
| total_amount | DECIMAL(19,2) | - | Order total |
| created_at | TIMESTAMP | - | Order creation time |
| updated_at | TIMESTAMP | - | Last update time |

**JDBC Entity**:
```java
@Table("order_01")
public class OrderEntity {
    @Id
    private UUID id;

    @Column("booking_id")
    private AggregateReference<BookingEntity, UUID> booking;

    @Column("status")
    private OrderStatus status;

    @MappedCollection(idColumn = "order_id")
    private Set<OrderItemEntity> items;  // Order line items

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
```

### TableEntity

**Table**: `table_01` (note: "table" is reserved keyword)

**Purpose**: Restaurant seating inventory

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique table identifier |
| area_id | UUID | FK → area | Dining area/section |
| name | VARCHAR(255) | NOT NULL | Table identifier (e.g., T-101) |
| seats_min | INTEGER | - | Minimum capacity |
| seats_max | INTEGER | - | Maximum capacity |
| internal_note | TEXT | - | Staff notes |

**JDBC Entity**:
```java
@Table("table_01")
public class TableEntity {
    @Id
    private UUID id;

    @Column("area_id")
    private AggregateReference<AreaEntity, UUID> area;

    @Column("name")
    private String name;

    @Column("seats_min")
    private Integer seatsMin;

    @Column("seats_max")
    private Integer seatsMax;

    @Column("internal_note")
    private String internalNote;
}
```

### Other Domain Entities

- **AreaEntity** (`area`): Dining sections/zones
- **FeedbackEntity** (`feedback`): Customer reviews with ratings
- **OpeningHoursEntity** (`opening_hours`): Restaurant operating schedule
- **RestaurantEntity** (`restaurant`): Restaurant information

---

## Security Domain Entities

### AppUserEntity

**Table**: `app_user`

**Purpose**: Application user accounts

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique user identifier |
| username | VARCHAR(255) | UNIQUE, NOT NULL | Login username |
| password | VARCHAR(255) | NOT NULL | BCrypt hashed password |
| email | VARCHAR(255) | UNIQUE | User email |
| enabled | BOOLEAN | DEFAULT true | Account enabled flag |
| account_non_expired | BOOLEAN | DEFAULT true | Account not expired |
| account_non_locked | BOOLEAN | DEFAULT true | Account not locked |
| credentials_non_expired | BOOLEAN | DEFAULT true | Credentials not expired |
| created_at | TIMESTAMP | - | Account creation date |
| updated_at | TIMESTAMP | - | Last modification date |

**JDBC Entity**:
```java
@Table("app_user")
public class AppUserEntity {
    @Id
    private UUID id;

    @Column("username")
    private String username;

    @Column("password")
    private String password;  // BCrypt hashed

    @Column("email")
    private String email;

    @Column("enabled")
    private Boolean enabled;

    @Column("account_non_expired")
    private Boolean accountNonExpired;

    @Column("account_non_locked")
    private Boolean accountNonLocked;

    @Column("credentials_non_expired")
    private Boolean credentialsNonExpired;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
```

**UserDetails Integration**:
```java
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final UserAuthorityRepository userAuthorityRepository;
    private final AuthorityRepository authorityRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("User not found: " + username));

        List<GrantedAuthority> authorities = loadAuthorities(user.getId());

        return User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(authorities)
            .accountExpired(!user.getAccountNonExpired())
            .accountLocked(!user.getAccountNonLocked())
            .credentialsExpired(!user.getCredentialsNonExpired())
            .disabled(!user.getEnabled())
            .build();
    }

    private List<GrantedAuthority> loadAuthorities(UUID userId) {
        return userAuthorityRepository.findByUserId(userId).stream()
            .map(ua -> authorityRepository.findById(ua.getAuthorityId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(auth -> new SimpleGrantedAuthority(auth.getName()))
            .toList();
    }
}
```

### AuthorityEntity

**Table**: `authority`

**Purpose**: Role definitions

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Unique authority identifier |
| name_01 | VARCHAR(255) | UNIQUE, NOT NULL | Role name (e.g., ROLE_USER) |

**Note**: Column named `name_01` because "name" is sometimes reserved.

**Default Roles**:
- `ROLE_USER` - Basic customer access
- `ROLE_OPERATOR` - Restaurant staff/manager
- `ROLE_ADMIN` - Full administrative access

### UserAuthorityEntity

**Table**: `user_authority`

**Purpose**: User-to-Role mapping (many-to-many join table)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, NOT NULL | Synthetic join table ID |
| user_id | UUID | FK → app_user, NOT NULL | User reference |
| authority_id | UUID | FK → authority, NOT NULL | Role reference |

**JDBC Entity**:
```java
@Table("user_authority")
public class UserAuthorityEntity {
    @Id
    private UUID id;  // Synthetic ID (Spring Data JDBC requirement)

    @Column("user_id")
    private UUID userId;

    @Column("authority_id")
    private UUID authorityId;
}
```

**Why Synthetic ID?**: Spring Data JDBC requires `@Id` on join tables

---

## OAuth2 Domain Entities

### OAuth2RegisteredClientEntity

**Table**: `oauth2_registered_client`

**Purpose**: OAuth2 client registrations

**Key Columns**:
- `id`: String primary key
- `client_id`: Unique client identifier
- `client_secret`: Hashed client secret
- `authorization_grant_types`: JSON array (authorization_code, client_credentials, etc.)
- `redirect_uris`: JSON array
- `scopes`: JSON array
- `client_settings`: JSON object
- `token_settings`: JSON object (access token TTL, etc.)

**Schema**: Defined by Spring Authorization Server

**Seeded Clients**:
1. **mcp-server**: client_credentials, scopes=[backend.read, backend.write]
2. **mcp-client**: client_credentials, scopes=[mcp.read, mcp.write]
3. **frontend-app**: authorization_code + PKCE, public client

### OAuth2AuthorizationEntity

**Table**: `oauth2_authorization`

**Purpose**: Active access/refresh tokens

**Key Columns**:
- `id`: String primary key
- `registered_client_id`: FK to oauth2_registered_client
- `principal_name`: Username
- `access_token_value`: JWT token
- `access_token_issued_at`, `access_token_expires_at`: Token lifecycle
- `refresh_token_value`: Refresh token
- `id_token_value`: OIDC ID token
- Many more columns for OAuth2 spec compliance

**Schema**: Defined by Spring Authorization Server

**Column Size Patches**:

Many columns need to be TEXT instead of VARCHAR due to large JWT tokens:

```yaml
# db/changelog/patches/002_fix_oauth2_authorization_column_sizes.yml
databaseChangeLog:
  - changeSet:
      id: fix-oauth2-authorization-column-sizes
      author: manual-patch
      changes:
        - modifyDataType:
            tableName: oauth2_authorization
            columnName: access_token_value
            newDataType: text
        - modifyDataType:
            tableName: oauth2_authorization
            columnName: access_token_metadata
            newDataType: text
        # ... more columns
```

---

## Spring Data JDBC Patterns

### Primary Keys

**All entities use UUID primary keys**:

```java
@Id
private UUID id;
```

**Database generation**:
- PostgreSQL: `DEFAULT gen_random_uuid()`
- H2: `DEFAULT random_uuid()`

**Why UUID?**:
- Globally unique (no coordination needed)
- Can generate client-side
- Better for distributed systems
- No auto-increment issues with replication

### Foreign Keys (AggregateReference)

**Pattern**: Use `AggregateReference<TargetEntity, IdType>` for foreign keys

**Example**:
```java
@Column("guest_id")
private AggregateReference<CustomerEntity, UUID> guest;
```

**Benefits**:
- Type-safe references
- Explicit aggregate boundaries
- No automatic loading (prevents N+1)
- Clear ownership

**Database Mapping**:
- Column: `guest_id` (UUID)
- Foreign key constraint: `fk_booking_customer`
- Reference: `customer(id)`

**Usage**:
```java
// Get referenced ID
UUID customerId = booking.getGuest().getId();

// Load referenced entity (explicit)
CustomerEntity customer = customerRepository.findById(customerId)
    .orElseThrow();
```

### One-to-Many (MappedCollection)

**Pattern**: Use `@MappedCollection` for child collections

**Example**:
```java
@Table("booking")
public class BookingEntity {
    @Id
    private UUID id;

    @MappedCollection(idColumn = "booking_id")
    private Set<BookingTableEntity> tables;
}

@Table("booking_tables")
public class BookingTableEntity {
    @Column("booking_id")
    private UUID bookingId;  // Parent FK

    @Column("table_id")
    private AggregateReference<TableEntity, UUID> table;
}
```

**Behavior**:
- Spring Data JDBC loads collection when loading parent
- Deleting parent cascades to children
- Use `Set<T>` not `List<T>` (avoids duplicate issues)

**Database**:
- Join table: `booking_tables`
- Columns: `booking_id`, `table_id`
- Foreign keys to both `booking` and `table_01`

### Many-to-Many Pattern

**Implementation**: Explicit join table entity

**Example**: Booking ↔ Table

```java
// Parent aggregate
@Table("booking")
public class BookingEntity {
    @Id
    private UUID id;

    @MappedCollection(idColumn = "booking_id")
    private Set<BookingTableEntity> tables;  // Join table entities
}

// Join table entity
@Table("booking_tables")
public class BookingTableEntity {
    // No @Id needed for simple join table (Spring Data JDBC handles it)

    @Column("booking_id")
    private UUID bookingId;

    @Column("table_id")
    private AggregateReference<TableEntity, UUID> table;
}

// Other side of relationship
@Table("table_01")
public class TableEntity {
    @Id
    private UUID id;

    // No reference back to bookings (not the aggregate root)
}
```

**Key Points**:
- One side owns the relationship (Booking is aggregate root)
- Other side has no back-reference (Table doesn't know its bookings)
- Join table managed by owning side
- Enforces aggregate boundaries

### Embedded Objects

**Pattern**: Use `@Embedded` for value objects

**Example**:
```java
@Table("booking")
public class BookingEntity {
    @Id
    private UUID id;

    @Embedded.Empty(prefix = "metadata_")
    private Metadata metadata;
}

public class Metadata {
    private String key1;
    private String key2;
}
```

**Database Mapping**:
- No separate table
- Columns: `metadata_key1`, `metadata_key2` (flattened with prefix)

### Entity ↔ POJO Conversion

**Pattern**: Every entity has conversion methods

**Purpose**: Separate persistence model from API model

**Example**:
```java
@Table("customer")
public class CustomerEntity {
    @Id
    private UUID id;

    private String name;
    private String email;

    // Entity → POJO (for API responses)
    public Customer toPojo() {
        Customer pojo = new Customer();
        pojo.setId(this.id);
        pojo.setName(this.name);
        pojo.setEmail(this.email);
        return pojo;
    }

    // POJO → Entity (for API requests)
    public static CustomerEntity fromPojo(Customer pojo) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(pojo.getId());
        entity.setName(pojo.getName());
        entity.setEmail(pojo.getEmail());
        return entity;
    }
}
```

**Usage in Controller**:
```java
@GetMapping("/customers/{id}")
public ResponseEntity<Customer> getCustomer(@PathVariable UUID id) {
    return customerRepository.findById(id)
        .map(CustomerEntity::toPojo)  // Convert to POJO
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}

@PostMapping("/customers")
public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
    CustomerEntity entity = CustomerEntity.fromPojo(customer);  // Convert from POJO
    CustomerEntity saved = customerRepository.save(entity);
    return ResponseEntity.ok(saved.toPojo());
}
```

---

## Repository Layer

### CrudRepository Pattern

**Basic CRUD operations**:

```java
public interface CustomerRepository extends CrudRepository<CustomerEntity, UUID> {

    // Derived query methods
    Optional<CustomerEntity> findByEmail(String email);
    List<CustomerEntity> findByNameContaining(String name);
    List<CustomerEntity> findByBookingCountGreaterThan(Integer count);
}
```

**Provided Methods** (from CrudRepository):
- `save(T entity)` - Insert or update
- `findById(ID id)` - Get by primary key
- `findAll()` - Get all records
- `deleteById(ID id)` - Delete by ID
- `count()` - Count records

### PagingAndSortingRepository Pattern

**For pagination**:

```java
public interface PageableCustomerRepository
        extends PagingAndSortingRepository<CustomerEntity, UUID>,
                CrudRepository<CustomerEntity, UUID> {

    Page<CustomerEntity> findByNameContaining(String name, Pageable pageable);
}
```

**Usage**:
```java
Pageable pageable = PageRequest.of(
    0,           // page number
    20,          // page size
    Sort.by("name").ascending()
);

Page<CustomerEntity> page = repository.findByNameContaining("John", pageable);

int totalPages = page.getTotalPages();
long totalElements = page.getTotalElements();
List<CustomerEntity> customers = page.getContent();
```

### Custom Queries

**SQL queries with @Query**:

```java
public interface AppUserRepository extends CrudRepository<AppUserEntity, UUID> {

    @Query("SELECT * FROM app_user WHERE username = :username")
    Optional<AppUserEntity> findByUsername(@Param("username") String username);

    @Query("""
        SELECT au.* FROM app_user au
        JOIN user_authority ua ON ua.user_id = au.id
        JOIN authority a ON a.id = ua.authority_id
        WHERE a.name_01 = :role
        """)
    List<AppUserEntity> findByRole(@Param("role") String role);
}
```

### Modifying Queries

**For INSERT/UPDATE/DELETE**:

```java
public interface UserAuthorityRepository extends CrudRepository<UserAuthorityEntity, UUID> {

    @Modifying
    @Query("""
        INSERT INTO user_authority (id, user_id, authority_id)
        VALUES (:id, :userId, :authorityId)
        """)
    void insert(
        @Param("id") UUID id,
        @Param("userId") UUID userId,
        @Param("authorityId") UUID authorityId
    );

    @Modifying
    @Query("DELETE FROM user_authority WHERE user_id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
```

---

## Custom Type Converters

### Purpose

Map Java types to database types and vice versa.

### Configuration

**CustomConverters** (`backend/src/main/java/me/pacphi/ai/resos/jdbc/CustomConverters.java`):

```java
@Configuration
public class CustomConverters extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return List.of(
            new StringToOffsetDateTimeConverter(),
            new OffsetDateTimeToStringConverter(),
            new StringToUriConverter(),
            new UriToStringConverter()
        );
    }
}
```

### Converter Implementations

**OffsetDateTime Converters**:
```java
@ReadingConverter
public class StringToOffsetDateTimeConverter
        implements Converter<String, OffsetDateTime> {

    @Override
    public OffsetDateTime convert(String source) {
        return source == null ? null : OffsetDateTime.parse(source);
    }
}

@WritingConverter
public class OffsetDateTimeToStringConverter
        implements Converter<OffsetDateTime, String> {

    @Override
    public String convert(OffsetDateTime source) {
        return source == null ? null : source.toString();
    }
}
```

**URI Converters**:
```java
@ReadingConverter
public class StringToUriConverter implements Converter<String, URI> {

    @Override
    public URI convert(String source) {
        try {
            return source == null ? null : new URI(source);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + source, e);
        }
    }
}

@WritingConverter
public class UriToStringConverter implements Converter<URI, String> {

    @Override
    public String convert(URI source) {
        return source == null ? null : source.toString();
    }
}
```

**Why Needed**: Avoids Java module accessibility issues with `java.net.URI`

---

## CSV Data Seeding

### DataSeeder Component

**Purpose**: Load seed data from CSV files on application startup

**Location**: `backend/src/main/java/me/pacphi/ai/resos/csv/DataSeeder.java`

**Trigger**: CommandLineRunner with profiles `dev`, `seed`, `test`

**Configuration**:
```yaml
app:
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
        - user-authorities.csv  # Last (references users and authorities)
```

### Annotation-Driven Mapper Discovery

**@CsvEntityMapper Annotation**:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface CsvEntityMapper {
    String value();  // CSV filename without extension
}
```

**Example Mapper**:
```java
@CsvEntityMapper("users")
public class AppUserMapper implements EntityMapper<AppUserEntity> {

    private final PasswordEncoder passwordEncoder;

    @Override
    public AppUserEntity mapFromCsv(String[] line) throws CsvMappingException {
        if (line.length < 7) {
            throw new CsvMappingException("Invalid user CSV line");
        }

        AppUserEntity user = new AppUserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(line[0]);
        user.setPassword(passwordEncoder.encode(line[1]));  // BCrypt hash
        user.setEmail(line[2]);
        user.setEnabled(Boolean.parseBoolean(line[3]));
        user.setAccountNonExpired(Boolean.parseBoolean(line[4]));
        user.setAccountNonLocked(Boolean.parseBoolean(line[5]));
        user.setCredentialsNonExpired(Boolean.parseBoolean(line[6]));
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    @Override
    public Class<AppUserEntity> getEntityClass() {
        return AppUserEntity.class;
    }
}
```

### CSV File Format

**users.csv** (`backend/seed-data/users.csv`):
```csv
username;password;email;enabled;accountNonExpired;accountNonLocked;credentialsNonExpired
admin;admin123;admin@example.com;true;true;true;true
operator;operator123;operator@example.com;true;true;true;true
user;user123;user@example.com;true;true;true;true
```

**Delimiter**: Semicolon (`;`)
**Header**: Yes (first line skipped)

**authorities.csv**:
```csv
name_01
ROLE_USER
ROLE_OPERATOR
ROLE_ADMIN
```

**user-authorities.csv**:
```csv
username;authority_name
admin;ROLE_ADMIN
admin;ROLE_OPERATOR
admin;ROLE_USER
operator;ROLE_OPERATOR
operator;ROLE_USER
user;ROLE_USER
```

### Seeding Process

**DataSeeder Algorithm**:
```java
@Override
public void run(ApplicationArguments args) throws Exception {
    // Wait for Liquibase to complete
    waitForLiquibase();

    // Get all mapper beans
    Map<String, EntityMapper<?>> mappers = applicationContext
        .getBeansOfType(EntityMapper.class);

    // Process each configured CSV file
    for (String filename : csvFiles) {
        // Find mapper by @CsvEntityMapper annotation value
        EntityMapper<?> mapper = findMapperForFile(filename, mappers);

        // Load CSV file
        List<String[]> rows = csvFileProcessor.parse(filename);

        // Map to entities
        List<?> entities = rows.stream()
            .map(mapper::mapFromCsv)
            .filter(Objects::nonNull)
            .toList();

        // Resolve repository dynamically
        Class<?> entityClass = mapper.getEntityClass();
        CrudRepository repository = repositoryResolver
            .resolveRepository(entityClass);

        // Persist all entities
        repository.saveAll(entities);

        logger.info("Seeded {} records from {}", entities.size(), filename);
    }
}
```

### Repository Resolver Pattern

**Purpose**: Dynamically find repository for entity class

**RepositoryResolver**:
```java
@Component
public class RepositoryResolver {

    private final ApplicationContext applicationContext;

    public <T, ID> CrudRepository<T, ID> resolveRepository(Class<T> entityClass) {
        Map<String, CrudRepository> repositories =
            applicationContext.getBeansOfType(CrudRepository.class);

        for (CrudRepository<?, ?> repository : repositories.values()) {
            Class<?> repositoryEntityClass = getEntityClass(repository);
            if (repositoryEntityClass.equals(entityClass)) {
                return (CrudRepository<T, ID>) repository;
            }
        }

        throw new IllegalArgumentException(
            "No repository found for entity: " + entityClass.getName()
        );
    }

    private Class<?> getEntityClass(CrudRepository<?, ?> repository) {
        // Use reflection to extract generic type from Repository<T, ID>
        ParameterizedType type = (ParameterizedType) repository.getClass()
            .getGenericInterfaces()[0];
        return (Class<?>) type.getActualTypeArguments()[0];
    }
}
```

**Benefits**:
- No hard-coded repository references
- Extensible (add new entities without code changes)
- Type-safe at runtime

### Seed File Order Importance

**Order Matters** due to foreign key constraints:

1. **authorities.csv** - No dependencies
2. **users.csv** - No dependencies
3. **areas.csv** - No dependencies
4. **tables.csv** - References areas
5. **customers.csv** - No dependencies
6. **bookings.csv** - References customers, restaurants
7. **orders.csv** - References bookings
8. **feedback.csv** - References customers, bookings
9. **openinghours.csv** - References restaurants
10. **user-authorities.csv** - References users AND authorities (MUST BE LAST)

**Violation Example**:
```
Loading user-authorities.csv before users.csv
→ FK constraint violation (user_id references app_user.id which doesn't exist yet)
→ SQLException: foreign key constraint fails
```

---

## Database Support

### H2 (Development)

**Configuration**:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:resos-backend
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
```

**Features**:
- In-memory (data lost on restart)
- Fast startup
- Web console at http://localhost:8080/h2-console
- PostgreSQL compatibility mode

**Limitations**:
- Some PostgreSQL features not supported
- JSONB maps to VARCHAR
- No true UUID type (uses BINARY)

### PostgreSQL (Production)

**Configuration**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/resos
    driver-class-name: org.postgresql.Driver
    username: resos
    password: resos_password
```

**Features**:
- Persistent storage
- JSONB support
- Native UUID type
- Advanced constraints
- Better performance at scale

**Docker Compose**:
```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: resos
      POSTGRES_USER: resos
      POSTGRES_PASSWORD: resos_password
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
```

### Database Type Detection

**In SchemaCreator**:
```java
private boolean isPostgreSQL() {
    return datasourceUrl.contains("postgresql");
}

private boolean isH2() {
    return datasourceUrl.contains("h2");
}

private String getUuidDefaultValue() {
    return isPostgreSQL() ? "gen_random_uuid()" : "random_uuid()";
}
```

---

## Schema Management

### Liquibase Master Changelog

**File**: `backend/src/main/resources/db/changelog/db.changelog-master.yml`

```yaml
databaseChangeLog:
  # Generated entity changelogs (in dependency order)
  - include:
      file: db/changelog/generated/authority.yaml
  - include:
      file: db/changelog/generated/app_user.yaml
  - include:
      file: db/changelog/generated/customer.yaml
  - include:
      file: db/changelog/generated/restaurant.yaml
  - include:
      file: db/changelog/generated/area.yaml
  - include:
      file: db/changelog/generated/table_01.yaml
  - include:
      file: db/changelog/generated/booking.yaml
  - include:
      file: db/changelog/generated/order_01.yaml
  - include:
      file: db/changelog/generated/feedback.yaml
  - include:
      file: db/changelog/generated/opening_hours.yaml
  - include:
      file: db/changelog/generated/user_authority.yaml

  # Manual patches
  - include:
      file: db/changelog/patches/001_fix_oauth2_client_column_sizes.yml
  - include:
      file: db/changelog/patches/002_fix_oauth2_authorization_column_sizes.yml
```

### Manual Patches

**Why Needed**: Some schema features cannot be auto-generated:

1. **OAuth2 Column Sizes**: Spring Authorization Server tables need TEXT columns
2. **Indexes**: Performance indexes not generated automatically
3. **Check Constraints**: Complex validation rules
4. **Triggers**: Business logic in database
5. **Views**: Materialized views for reporting

**Example Patch** (`patches/001_fix_oauth2_client_column_sizes.yml`):
```yaml
databaseChangeLog:
  - changeSet:
      id: fix-oauth2-client-column-sizes
      author: manual-patch
      changes:
        - modifyDataType:
            tableName: oauth2_registered_client
            columnName: client_authentication_methods
            newDataType: text
        - modifyDataType:
            tableName: oauth2_registered_client
            columnName: authorization_grant_types
            newDataType: text
        - modifyDataType:
            tableName: oauth2_registered_client
            columnName: redirect_uris
            newDataType: text
```

### Migration Strategy

**Development**:
- `SchemaCreator` enabled (profiles: dev, test)
- Changelogs regenerated on every startup
- Schema dropped/recreated (via Liquibase context or manual SQL)

**Production**:
- `SchemaCreator` disabled (no dev/test profile)
- Use pre-generated changelogs (committed to git)
- Liquibase applies migrations incrementally
- Never drop tables (only alter)

---

## Constraints & Indexes

### Primary Keys

All tables have UUID primary keys:
```sql
ALTER TABLE customer ADD PRIMARY KEY (id);
```

### Unique Constraints

```sql
ALTER TABLE customer ADD CONSTRAINT uk_customer_email UNIQUE (email);
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_username UNIQUE (username);
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_email UNIQUE (email);
ALTER TABLE authority ADD CONSTRAINT uk_authority_name UNIQUE (name_01);
```

### Foreign Key Constraints

**Naming Convention**: `fk_{table}_{referenced_table}`

```sql
ALTER TABLE booking
ADD CONSTRAINT fk_booking_customer
FOREIGN KEY (guest_id) REFERENCES customer(id);

ALTER TABLE booking
ADD CONSTRAINT fk_booking_restaurant
FOREIGN KEY (restaurant_id) REFERENCES restaurant(id);

ALTER TABLE user_authority
ADD CONSTRAINT fk_user_authority_user
FOREIGN KEY (user_id) REFERENCES app_user(id);

ALTER TABLE user_authority
ADD CONSTRAINT fk_user_authority_authority
FOREIGN KEY (authority_id) REFERENCES authority(id);
```

### Indexes (Recommended)

**Not currently auto-generated**, but recommended for performance:

```sql
CREATE INDEX idx_booking_date ON booking(booking_date);
CREATE INDEX idx_booking_status ON booking(status);
CREATE INDEX idx_booking_guest ON booking(guest_id);
CREATE INDEX idx_customer_email ON customer(email);
CREATE INDEX idx_feedback_customer ON feedback(customer_id);
CREATE INDEX idx_oauth2_auth_client ON oauth2_authorization(registered_client_id);
CREATE INDEX idx_oauth2_auth_principal ON oauth2_authorization(principal_name);
```

---

## Data Access Patterns

### Query Examples

**Simple Find**:
```java
Optional<CustomerEntity> customer = customerRepository.findById(customerId);
```

**Derived Query**:
```java
List<CustomerEntity> customers = customerRepository
    .findByNameContaining("Smith");
```

**Pagination**:
```java
Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
Page<CustomerEntity> page = pageableCustomerRepository
    .findAll(pageable);
```

**Custom Query**:
```java
List<AppUserEntity> admins = userRepository
    .findByRole("ROLE_ADMIN");
```

### Transaction Management

**Default Behavior**:
- `@Transactional` on repository methods
- Read operations: read-only transaction
- Write operations: read-write transaction

**Custom Transactions**:
```java
@Service
public class BookingService {

    @Transactional
    public BookingEntity createBookingWithOrder(Booking booking, Order order) {
        // Save booking
        BookingEntity bookingEntity = BookingEntity.fromPojo(booking);
        bookingEntity = bookingRepository.save(bookingEntity);

        // Save order (same transaction)
        OrderEntity orderEntity = OrderEntity.fromPojo(order);
        orderEntity.setBookingId(bookingEntity.getId());
        orderRepository.save(orderEntity);

        return bookingEntity;
    }
}
```

---

## Critical Files

| File | Purpose | Lines |
|------|---------|-------|
| `backend/src/main/java/me/pacphi/ai/resos/config/SchemaCreator.java` | Dynamic schema generation | ~400 |
| `backend/src/main/java/me/pacphi/ai/resos/csv/DataSeeder.java` | CSV loading orchestrator | ~150 |
| `backend/src/main/java/me/pacphi/ai/resos/csv/impl/AppUserMapper.java` | User CSV mapping | ~60 |
| `backend/src/main/java/me/pacphi/ai/resos/jdbc/CustomConverters.java` | Type converters | ~80 |
| `backend/src/main/resources/db/changelog/db.changelog-master.yml` | Liquibase master | ~40 |
| `backend/seed-data/*.csv` | Seed data files | ~200 total |

## Related Documentation

- [Data Model ERD](diagrams/data-model-erd.md) - Entity relationships
- [Code Generation Pipeline](04-code-generation.md) - How entities are generated
- [Module Architecture](03-module-architecture.md) - Backend module details
- [ADR-002: Spring Data JDBC](adr/002-spring-data-jdbc.md) - Why JDBC over JPA
- [ADR-003: Dynamic Liquibase](adr/003-dynamic-liquibase.md) - Schema generation rationale
