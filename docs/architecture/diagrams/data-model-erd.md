# Data Model - Entity Relationship Diagram

This diagram shows the complete database schema including domain entities, security entities, and OAuth2 entities.

```mermaid
erDiagram
    %% Core Domain Entities
    CUSTOMER ||--o{ BOOKING : makes
    BOOKING ||--|{ BOOKING_TABLES : has
    BOOKING_TABLES }|--|| TABLE : includes
    TABLE }|--|| AREA : "located in"
    BOOKING ||--o{ ORDER : generates
    ORDER ||--|{ ORDER_ITEM : contains
    CUSTOMER ||--o{ FEEDBACK : provides
    RESTAURANT ||--o{ OPENING_HOURS : defines
    RESTAURANT ||--o{ TABLE : contains

    %% Security Entities
    APP_USER ||--o{ USER_AUTHORITY : has
    USER_AUTHORITY }|--|| AUTHORITY : references

    %% OAuth2 Entities
    OAUTH2_REGISTERED_CLIENT ||--o{ OAUTH2_AUTHORIZATION : issues
    APP_USER ||--o{ OAUTH2_AUTHORIZATION : owns
    APP_USER ||--o{ OAUTH2_AUTHORIZATION_CONSENT : grants

    CUSTOMER {
        uuid id PK
        string name "NOT NULL"
        string email "UNIQUE"
        string phone
        timestamp created_at
        timestamp last_booking_at
        integer booking_count
        decimal total_spent
        jsonb metadata
    }

    BOOKING {
        uuid id PK
        uuid guest_id FK "References CUSTOMER"
        uuid restaurant_id FK
        date booking_date "NOT NULL"
        time booking_time "NOT NULL"
        integer people_count "NOT NULL"
        integer duration_minutes
        string status "ENUM: pending, confirmed, seated, completed, cancelled"
        jsonb metadata
        text comments
        text internal_note
    }

    BOOKING_TABLES {
        uuid booking_id FK
        uuid table_id FK
    }

    TABLE {
        uuid id PK
        uuid area_id FK
        string name "e.g., T-101"
        integer seats_min
        integer seats_max
        text internal_note
    }

    AREA {
        uuid id PK
        uuid restaurant_id FK
        string name "e.g., Main Dining, Patio"
        integer capacity
    }

    ORDER {
        uuid id PK
        uuid booking_id FK "References BOOKING"
        string status "ENUM: pending, preparing, served, paid"
        decimal total_amount
        timestamp created_at
        timestamp updated_at
    }

    ORDER_ITEM {
        uuid id PK
        uuid order_id FK
        string item_name
        integer quantity
        decimal unit_price
        decimal subtotal
    }

    FEEDBACK {
        uuid id PK
        uuid customer_id FK
        uuid booking_id FK
        integer rating "1-5 stars"
        text comment
        timestamp created_at
    }

    RESTAURANT {
        uuid id PK
        string name
        string address
        string phone
        string email
    }

    OPENING_HOURS {
        uuid id PK
        uuid restaurant_id FK
        string day_of_week "ENUM: Monday-Sunday"
        time open_time
        time close_time
        boolean closed
    }

    APP_USER {
        uuid id PK
        string username "UNIQUE, NOT NULL"
        string password "BCrypt hashed"
        string email "UNIQUE"
        boolean enabled "Default: true"
        boolean account_non_expired
        boolean account_non_locked
        boolean credentials_non_expired
        timestamp created_at
        timestamp updated_at
    }

    AUTHORITY {
        uuid id PK
        string name_01 "UNIQUE, e.g., ROLE_USER"
    }

    USER_AUTHORITY {
        uuid id PK
        uuid user_id FK
        uuid authority_id FK
    }

    OAUTH2_REGISTERED_CLIENT {
        string id PK
        string client_id "UNIQUE"
        timestamp client_id_issued_at
        string client_secret
        timestamp client_secret_expires_at
        string client_name
        text client_authentication_methods "JSON"
        text authorization_grant_types "JSON"
        text redirect_uris "JSON"
        text post_logout_redirect_uris "JSON"
        text scopes "JSON"
        text client_settings "JSON"
        text token_settings "JSON"
    }

    OAUTH2_AUTHORIZATION {
        string id PK
        string registered_client_id FK
        string principal_name "Username"
        string authorization_grant_type
        text authorized_scopes
        text attributes "JSON"
        text state
        text authorization_code_value
        timestamp authorization_code_issued_at
        timestamp authorization_code_expires_at
        text authorization_code_metadata
        text access_token_value
        timestamp access_token_issued_at
        timestamp access_token_expires_at
        text access_token_metadata
        string access_token_type
        text access_token_scopes
        text refresh_token_value
        timestamp refresh_token_issued_at
        timestamp refresh_token_expires_at
        text refresh_token_metadata
        text id_token_value
        timestamp id_token_issued_at
        timestamp id_token_expires_at
        text id_token_metadata
        text id_token_claims
        text user_code_value
        timestamp user_code_issued_at
        timestamp user_code_expires_at
        text user_code_metadata
        text device_code_value
        timestamp device_code_issued_at
        timestamp device_code_expires_at
        text device_code_metadata
    }

    OAUTH2_AUTHORIZATION_CONSENT {
        string registered_client_id FK
        string principal_name
        text authorities "Granted scopes/roles"
    }
```

## Entity Categories

### Core Domain Entities

These entities represent the restaurant reservation system business logic:

| Entity           | Purpose                          | Key Relationships                |
| ---------------- | -------------------------------- | -------------------------------- |
| **Customer**     | Customer information and history | → Bookings, Feedback             |
| **Booking**      | Reservation records              | → Customer, Tables (M2M), Orders |
| **Table**        | Restaurant seating inventory     | → Area, Bookings (M2M)           |
| **Area**         | Dining sections/zones            | → Tables, Restaurant             |
| **Order**        | Food/beverage orders             | → Booking, OrderItems            |
| **OrderItem**    | Individual order line items      | → Order                          |
| **Feedback**     | Customer reviews                 | → Customer, Booking              |
| **Restaurant**   | Restaurant information           | → Areas, OpeningHours, Tables    |
| **OpeningHours** | Operating schedule               | → Restaurant                     |

### Security Entities

These entities handle authentication and authorization:

| Entity            | Purpose                 | Storage                         |
| ----------------- | ----------------------- | ------------------------------- |
| **AppUser**       | Application users       | BCrypt password hashing         |
| **Authority**     | Roles (ROLE_USER, etc.) | Name stored in `name_01` column |
| **UserAuthority** | User-Role mapping       | Join table with synthetic ID    |

**Default Roles**:

- `ROLE_USER` - Basic customer access
- `ROLE_OPERATOR` - Staff/manager access
- `ROLE_ADMIN` - Administrative access

**Default Users**:

- admin / admin123 (all roles)
- operator / operator123 (OPERATOR, USER)
- user / user123 (USER only)

### OAuth2 Entities

These entities are part of Spring Authorization Server:

| Entity                         | Purpose                      |
| ------------------------------ | ---------------------------- |
| **OAuth2RegisteredClient**     | OAuth2 client registrations  |
| **OAuth2Authorization**        | Active access/refresh tokens |
| **OAuth2AuthorizationConsent** | User consent records         |

**Seeded OAuth2 Clients**:

1. **mcp-server**: client_credentials, scopes=[backend.read, backend.write]
2. **mcp-client**: client_credentials, scopes=[mcp.read, mcp.write]
3. **frontend-app**: authorization_code + PKCE, public client

## Spring Data JDBC Patterns

### Primary Keys

All entities use `UUID` primary keys:

```java
@Id
private UUID id;
```

### Foreign Keys (AggregateReference)

```java
@Column("guest_id")
private AggregateReference<CustomerEntity, UUID> guest;
```

### One-to-Many (MappedCollection)

```java
@MappedCollection(idColumn = "booking_id")
private Set<BookingTableEntity> tables;
```

### Many-to-Many Join Table

```java
@Table("booking_tables")
public class BookingTableEntity {
    @Column("booking_id")
    private UUID bookingId;

    @Column("table_id")
    private AggregateReference<TableEntity, UUID> table;
}
```

### Embedded Objects

```java
@Embedded.Empty(prefix = "metadata_")
private Metadata metadata;
```

## Database Support

### Development (H2)

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:resos-backend
    driver-class-name: org.h2.Driver
```

### Production (PostgreSQL)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/resos
    driver-class-name: org.postgresql.Driver
```

### Schema Generation

All tables are created via dynamic Liquibase changelog generation:

1. `SchemaCreator` scans `@Table` entities
2. Generates Liquibase YAML files
3. Liquibase applies changelogs on startup

## Indexes & Constraints

### Unique Constraints

- `customer.email` (UNIQUE)
- `app_user.username` (UNIQUE)
- `app_user.email` (UNIQUE)
- `authority.name_01` (UNIQUE)
- `oauth2_registered_client.client_id` (UNIQUE)

### Foreign Key Constraints

All foreign keys use naming convention: `fk_{table}_{referenced_table}`

- `fk_booking_customer` (booking.guest_id → customer.id)
- `fk_booking_restaurant` (booking.restaurant_id → restaurant.id)
- `fk_table_area` (table.area_id → area.id)
- `fk_user_authority_user` (user_authority.user_id → app_user.id)
- `fk_user_authority_authority` (user_authority.authority_id → authority.id)

### Indexes (Future Enhancement)

Recommended indexes for performance:

- `idx_booking_date` on `booking(booking_date)`
- `idx_booking_status` on `booking(status)`
- `idx_customer_email` on `customer(email)`
- `idx_oauth2_authorization_client` on `oauth2_authorization(registered_client_id)`

## Seed Data

CSV files in `backend/seed-data/`:

- `authorities.csv` - ROLE_USER, ROLE_OPERATOR, ROLE_ADMIN
- `users.csv` - admin, operator, user accounts
- `user-authorities.csv` - user-role mappings
- `areas.csv`, `tables.csv`, `customers.csv`, `feedback.csv`, etc.

Loaded via `DataSeeder` with `@CsvEntityMapper` annotated mappers.

## Critical Files

| File                                                                 | Purpose                    |
| -------------------------------------------------------------------- | -------------------------- |
| `backend/src/main/java/me/pacphi/ai/resos/config/SchemaCreator.java` | Dynamic schema generation  |
| `backend/src/main/resources/db/changelog/db.changelog-master.yml`    | Liquibase master changelog |
| `backend/seed-data/*.csv`                                            | Seed data files            |
| `entities/src/main/java/me/pacphi/ai/resos/jdbc/*Entity.java`        | JDBC entity classes        |
